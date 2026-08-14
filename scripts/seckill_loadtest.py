#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
秒杀 Phase 1 压测脚本（mall-seckill-service, 8089）。

端到端链路：网关(8080) → mall-seckill-service(8089) → Redis Lua 扣库存 + MQ 异步建单。
按 `doc/秒杀方案分阶段实施计划.md` 3.5 验收标准设计：

  - 并发 500 请求抢 100 库存 → 订单数 = 100（无超卖）
  - Redis 扣减数 = MQ 消息数 = 订单数 = seckill_item.seckill_stock 扣减数（库存正确性）
  - 无重复订单（uk_user_activity_item 幂等 + mq_message 唯一键）
  - 单机 500 QPS，端到端 P99 < 800ms

用法：
  # 一键完整验收：造数 + 重置 + 500并发抢100库存 + 对账
  python scripts/seckill_loadtest.py run --reset --requests 500 --stock 100 --users 500

  # 只压测：200 并发持续 30s 打满 500 QPS，结束后对账
  python scripts/seckill_loadtest.py load --requests 0 --concurrency 200 \
        --duration 30 --qps 500 --stock 100000 --users 2000

  # 只对账（压测后跑）
  python scripts/seckill_loadtest.py reconcile --stock 100

  # 只造数 / 只清理
  python scripts/seckill_loadtest.py seed --users 2000
  python scripts/seckill_loadtest.py reset --stock 100

前置条件：
  1. Nacos / MySQL / Redis / RabbitMQ 已启动（见 CLAUDE.md「Build & Run」）
  2. mall-seckill-service(8089) 已启动并完成预热；网关(8080) 已启动
  3. 所有服务共用同一 MALL_JWT_SECRET —— 本脚本用它离线签发压测用户 JWT，
     长度不足 32 字节会签发/校验失败（与 JwtTokenService 要求一致）

说明：
  - 响应码语义（Result<T>）：200=成功入队(queued)，4001=库存不足，4002=已达限购，
    4003=活动未开始/已结束，401=未认证。
  - 秒杀建单是异步的：压测结束后消费者仍在消费，reconcile 会等待订单数收敛。
"""

import argparse
import base64
import collections
import hashlib
import hmac
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

try:
    import httpx
    import pymysql
    import redis as redis_lib
except ImportError as exc:  # pragma: no cover
    print(f"[seckill-loadtest] 缺少依赖 {exc.name}，请先安装：pip install pymysql redis httpx")
    sys.exit(2)

# --------------------------------------------------------------------------
# JWT 离线签发（HS256，与 mall-common-security 的 JwtTokenService 对齐）
# --------------------------------------------------------------------------


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def mint_jwt(secret: str, user_id: int, issuer: str, ttl: int, now: int = None) -> str:
    """按 JwtTokenService.buildToken 的载荷结构签发 access token。"""
    now = now or int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": str(user_id),
        "iss": issuer,
        "iat": now,
        "exp": now + ttl,
        "type": "ACCESS",
        "nickname": f"loadtest-{user_id}",
        "avatar": "",
        "memberLevel": "NORMAL",
        "client": "miniapp",
    }
    h = _b64(json.dumps(header, separators=(",", ":"), ensure_ascii=False).encode())
    p = _b64(json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode())
    sig = hmac.new(secret.encode("utf-8"), f"{h}.{p}".encode(), hashlib.sha256).digest()
    return f"{h}.{p}.{_b64(sig)}"


# --------------------------------------------------------------------------
# 配置
# --------------------------------------------------------------------------


class Cfg:
    pass


def parse_args():
    p = argparse.ArgumentParser(
        prog="seckill_loadtest.py",
        description="秒杀 Phase 1 压测：造数 / 重置 / 并发压测 / 对账",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("mode", nargs="?", default="run",
                   choices=["run", "seed", "reset", "load", "reconcile"],
                   help="run=造数+重置+压测+对账(默认)；其余为单步操作")
    # 压测参数
    p.add_argument("--base-url", default="http://localhost:8080", help="网关地址(默认8080)，直连秒杀可用 http://localhost:8089")
    p.add_argument("--activity-id", type=int, default=1, help="秒杀活动ID(默认1，种子数据)")
    p.add_argument("--item-id", type=int, default=1, help="秒杀商品项ID(默认1，SKU维度)")
    p.add_argument("--stock", type=int, default=100, help="重置后的初始库存(同时写 seckill_item 与 Redis)")
    p.add_argument("--quantity", type=int, default=1, help="每单购买数量")
    p.add_argument("--users", type=int, default=500, help="压测用户池大小(造数规模)")
    p.add_argument("--id-start", type=int, default=2000000, help="压测用户起始ID(避开现有用户)")
    p.add_argument("--requests", type=int, default=0,
                   help=">0: 一次性发 N 个请求(每用户一个，测并发正确性)；0: 持续压测(默认)")
    p.add_argument("--concurrency", type=int, default=200, help="持续压测并发 worker 数")
    p.add_argument("--duration", type=int, default=30, help="持续压测时长(秒)")
    p.add_argument("--qps", type=int, default=0, help="目标 QPS 节流(0=不限速，打满)")
    p.add_argument("--max-inflight", type=int, default=500, help="一次性压测的最大并发在途请求")
    p.add_argument("--settle", type=int, default=60, help="对账前等待订单收敛的最大秒数")
    p.add_argument("--jwt-secret", default=os.environ.get("MALL_JWT_SECRET", ""),
                   help="JWT 签名密钥，须与各服务 mall.jwt.secret 一致(默认取环境变量 MALL_JWT_SECRET)")
    p.add_argument("--issuer", default="zhiwu-mall", help="JWT iss 声明(默认 zhiwu-mall)")
    p.add_argument("--jwt-ttl", type=int, default=7200, help="压测 token 有效期秒")
    # 存储
    p.add_argument("--db-host", default="127.0.0.1")
    p.add_argument("--db-port", type=int, default=3306)
    p.add_argument("--db-user", default=os.environ.get("MALL_DB_USERNAME", "root"))
    p.add_argument("--db-pass", default=os.environ.get("MALL_DB_PASSWORD", "123456"))
    p.add_argument("--db-name", default="mall")
    p.add_argument("--redis-host", default="127.0.0.1")
    p.add_argument("--redis-port", type=int, default=6379)
    p.add_argument("--redis-db", type=int, default=1, help="与 application.yml 的 redis database 对齐(默认1)")
    # 行为开关
    p.add_argument("--reset", action="store_true",
                   help="run/load 前清理该 activity+item 的秒杀数据并重置库存(破坏性)")
    p.add_argument("--no-seed", action="store_true", help="load 前跳过自动造数(需已存在用户池)")
    return p.parse_args()


def make_cfg(a) -> Cfg:
    c = Cfg()
    for k, v in vars(a).items():
        setattr(c, k, v)
    if not c.jwt_secret:
        c.jwt_secret = "zhiwu-mall-secret-please-change-in-production!"
        print(f"[config] 未设置 MALL_JWT_SECRET，使用代码默认密钥（仅适合本地联调环境）")
    if len(c.jwt_secret.encode()) < 32:
        print(f"[config] 警告：JWT secret 仅 {len(c.jwt_secret.encode())} 字节，少于 HMAC-SHA256 要求的 32 字节，token 将被拒绝")
    c.jwt_secret_masked = c.jwt_secret[:8] + "..." + c.jwt_secret[-4:]
    return c


# --------------------------------------------------------------------------
# MySQL / Redis 助手
# --------------------------------------------------------------------------


def db_connect(c):
    return pymysql.connect(
        host=c.db_host, port=c.db_port, user=c.db_user, password=c.db_pass,
        database=c.db_name, charset="utf8mb4", autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


def redis_connect(c):
    return redis_lib.Redis(host=c.redis_host, port=c.redis_port, db=c.redis_db, decode_responses=True)


def _fmt_number(n):
    return f"{n:,}"


# --------------------------------------------------------------------------
# 造数（幂等）：user + user_address
# --------------------------------------------------------------------------


def seed_data(c, n_users=None):
    """为压测用户池造 user + user_address。幂等：已存在的用户/地址跳过。"""
    n_users = n_users or c.users
    db = db_connect(c)
    try:
        cur = db.cursor()
        lo, hi = c.id_start, c.id_start + n_users - 1

        # 1) user
        existing = {r["id"] for r in _q(cur, "SELECT id FROM `user` WHERE id BETWEEN %s AND %s", (lo, hi))}
        rows = []
        for uid in range(lo, hi + 1):
            if uid in existing:
                continue
            mobile = "1" + str(uid % 10_000_000_000).zfill(10)
            rows.append((uid, f"sec{uid}", f"压测用户{uid}", mobile))
        for i in range(0, len(rows), 1000):
            chunk = rows[i:i + 1000]
            cur.executemany(
                "INSERT INTO `user` (id, account, nickname, mobile, gender, member_level, is_admin, status) "
                "VALUES (%s, %s, %s, %s, 0, 'NORMAL', 0, 1) "
                "ON DUPLICATE KEY UPDATE account=VALUES(account)",
                chunk,
            )
        if rows:
            print(f"[seed] 新增 user {_fmt_number(len(rows))} 个 (id {lo}~{hi})")

        # 2) user_address（每个压测用户一条，供下单取地址）
        existing_addr = {r["user_id"] for r in _q(
            cur, "SELECT user_id FROM user_address WHERE user_id BETWEEN %s AND %s", (lo, hi))}
        addr_rows = []
        for uid in range(lo, hi + 1):
            if uid in existing_addr:
                continue
            mobile = "1" + str(uid % 10_000_000_000).zfill(10)
            addr_rows.append((uid, f"收货人{uid}", mobile, "110000", "110100", "110101",
                              "北京市 北京市 东城区", f"压测地址-{uid}", "100000"))
        for i in range(0, len(addr_rows), 1000):
            chunk = addr_rows[i:i + 1000]
            cur.executemany(
                "INSERT INTO user_address (user_id, receiver, contact, province_code, city_code, "
                "county_code, full_location, address, postal_code, is_default) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                chunk,
            )
        if addr_rows:
            print(f"[seed] 新增 user_address {_fmt_number(len(addr_rows))} 条")
        print(f"[seed] 用户池就绪：{_fmt_number(n_users)} 个用户，地址已绑定")
    finally:
        db.close()


def fetch_address_map(c, n_users=None):
    """返回 {user_id: address_id}，压测时取当前用户自己的收货地址。"""
    n_users = n_users or c.users
    db = db_connect(c)
    try:
        cur = db.cursor()
        lo, hi = c.id_start, c.id_start + n_users - 1
        rows = _q(cur, "SELECT id, user_id FROM user_address WHERE user_id BETWEEN %s AND %s",
                  (lo, hi))
        m = {r["user_id"]: r["id"] for r in rows}
        missing = n_users - len(m)
        if missing > 0:
            print(f"[load] 警告：{missing} 个用户缺少收货地址，请先运行 seed")
        return m
    finally:
        db.close()


# --------------------------------------------------------------------------
# 重置（破坏性）：清秒杀数据 + 重置库存
# --------------------------------------------------------------------------


def reset_data(c):
    """清理 activity+item 的秒杀痕迹并重置库存：order/order_item/mq_message + Redis 键。"""
    print(f"[reset] 清理 activity={c.activity_id} item={c.item_id} 的秒杀数据（破坏性）...")
    db = db_connect(c)
    try:
        cur = db.cursor()
        cur.execute(
            "DELETE FROM order_item WHERE order_id IN "
            "(SELECT id FROM `order` WHERE activity_id=%s AND seckill_item_id=%s AND order_source=2)",
            (c.activity_id, c.item_id))
        cur.execute("DELETE FROM `order` WHERE activity_id=%s AND seckill_item_id=%s AND order_source=2",
                    (c.activity_id, c.item_id))
        cur.execute("DELETE FROM mq_message WHERE activity_id=%s AND seckill_item_id=%s",
                    (c.activity_id, c.item_id))
        cur.execute("UPDATE seckill_item SET seckill_stock=%s WHERE id=%s", (c.stock, c.item_id))
        print(f"[reset] DB 清理完成，seckill_item({c.item_id}).seckill_stock -> {c.stock}")
    finally:
        db.close()

    r = redis_connect(c)
    try:
        stock_key = f"mall:seckill:stock:{c.activity_id}:{c.item_id}"
        r.delete(stock_key)
        for pat in (f"mall:seckill:user:{c.activity_id}:{c.item_id}:*",
                    f"mall:seckill:order:*:{c.activity_id}:{c.item_id}"):
            for k in r.scan_iter(match=pat, count=500):
                r.delete(k)
        r.set(stock_key, str(c.stock))
        print(f"[reset] Redis 重置完成：{stock_key} -> {c.stock}，用户/订单幂等键已清空")
    finally:
        r.close()


def check_activity_open(c):
    db = db_connect(c)
    try:
        cur = db.cursor()
        row = _q(cur, "SELECT start_time, end_time, enabled FROM seckill_activity WHERE id=%s",
                 (c.activity_id,))
        if not row:
            print(f"[check] 警告：seckill_activity id={c.activity_id} 不存在")
            return
        row = row[0]
        if not row["enabled"]:
            print(f"[check] 警告：活动 id={c.activity_id} 已禁用，所有请求将返回 4003")
            return
        print(f"[check] 活动窗口 {row['start_time']} ~ {row['end_time']}，enabled={row['enabled']}")
    finally:
        db.close()


def _q(cur, sql, args=None):
    cur.execute(sql, args)
    return cur.fetchall()


# --------------------------------------------------------------------------
# 并发压测引擎（httpx async）
# --------------------------------------------------------------------------


class Stats:
    LABELS = {
        200: "ok(queued)",
        4001: "stock(库存不足)",
        4002: "limit(已达限购)",
        4003: "closed(活动未开始)",
        401: "auth(未认证)",
        -1: "bad-resp(非契约响应)",
        -2: "err(网络/超时)",
    }

    def __init__(self):
        self.codes = collections.Counter()
        self.lats = []

    def add(self, code, lat_ms):
        self.codes[code] += 1
        self.lats.append(lat_ms)

    def report(self, elapsed):
        total = len(self.lats)
        print(f"\n========== 压测结果 ==========")
        print(f"总请求        : {_fmt_number(total)}")
        for code in sorted(self.codes):
            label = self.LABELS.get(code, f"code{code}")
            print(f"  {label:<16}: {self.codes[code]:>7,}")
        qps = total / elapsed if elapsed > 0 else 0
        print(f"耗时(秒)      : {elapsed:.2f}")
        print(f"QPS           : {qps:,.0f}")
        if self.lats:
            ms = sorted(self.lats)
            def pct(p):
                return ms[min(len(ms) - 1, int((p / 100) * (len(ms) - 1)))]
            print(f"延迟(ms)      : P50={pct(50):.0f}  P90={pct(90):.0f}  "
                  f"P99={pct(99):.0f}  P999={pct(99.9):.0f}  max={max(ms):.0f}")
        ok = self.codes.get(200, 0)
        if ok:
            print(f"[目标] 单机 500 QPS / P99<800ms：请以上述数据为准（P99={pct(99):.0f}ms）")
        return ok


class RateLimiter:
    """简单令牌桶，把并发打到目标 QPS（qps<=0 时不限速）。线程安全版，供线程池驱动。"""

    def __init__(self, qps):
        self.qps = qps
        self.tokens = 0.0
        self.last = time.monotonic()
        self.lock = threading.Lock()

    def acquire(self):
        if self.qps <= 0:
            return
        while True:
            with self.lock:
                now = time.monotonic()
                self.tokens = min(self.qps, self.tokens + (now - self.last) * self.qps)
                self.last = now
                if self.tokens >= 1:
                    self.tokens -= 1
                    return
                wait = (1 - self.tokens) / self.qps
            time.sleep(max(0.001, wait))


def fire(client, c, token, address):
    """发一次 execute 请求，返回 (业务码, 耗时ms)。同步版本，配合线程池驱动高并发。"""
    url = f"{c.base_url}/seckill/{c.activity_id}/execute"
    payload = {"seckillItemId": c.item_id, "quantity": c.quantity, "addressId": address}
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    t0 = time.perf_counter()
    try:
        resp = client.post(url, json=payload, headers=headers)
        lat = (time.perf_counter() - t0) * 1000
        try:
            return int(resp.json().get("code")), lat
        except Exception:
            return -1, lat
    except Exception:
        return -2, (time.perf_counter() - t0) * 1000


def build_pool(c):
    """生成 (token, address_id) 用户池。"""
    addrs = fetch_address_map(c)
    missing = [uid for uid in range(c.id_start, c.id_start + c.users) if uid not in addrs]
    if missing:
        print(f"[load] 错误：{len(missing)} 个用户没有地址，请先运行 seed 后再 load")
        sys.exit(3)
    pool = []
    for uid in range(c.id_start, c.id_start + c.users):
        pool.append((mint_jwt(c.jwt_secret, uid, c.issuer, c.jwt_ttl), addrs[uid]))
    return pool


def run_oneshot(c, client, pool, stats):
    """一次性并发 N 个请求。

    用线程池而非 async 协程：Windows 上 async httpx 的并发吞吐被压到 ~250 QPS 天花板，
    线程池 + keep-alive 才能真实反映服务端能力（实测 500+ QPS）。"""
    n = c.requests if c.requests > 0 else len(pool)

    def one(i):
        token, addr = pool[i % len(pool)]
        code, lat = fire(client, c, token, addr)
        return code, lat

    with ThreadPoolExecutor(max_workers=c.max_inflight) as ex:
        for code, lat in ex.map(one, range(n)):
            stats.add(code, lat)
    return stats


def run_sustained(c, client, pool, stats):
    limiter = RateLimiter(c.qps)
    deadline = time.monotonic() + c.duration
    idx = 0
    lock = threading.Lock()

    def next_pair():
        nonlocal idx
        with lock:
            token, addr = pool[idx % len(pool)]
            idx += 1
        return token, addr

    def worker():
        while time.monotonic() < deadline:
            limiter.acquire()
            token, addr = next_pair()
            code, lat = fire(client, c, token, addr)
            stats.add(code, lat)

    with ThreadPoolExecutor(max_workers=c.concurrency) as ex:
        futures = [ex.submit(worker) for _ in range(c.concurrency)]
        for f in futures:
            f.result()
    return stats


def load_sync(c):
    check_activity_open(c)
    pool = build_pool(c)
    print(f"[load] base-url={c.base_url} 活动={c.activity_id} 商品项={c.item_id} "
          f"库存={c.stock} 用户池={len(pool)} JWT密钥={c.jwt_secret_masked}")

    # Windows 上 async httpx 的并发吞吐被明显压低（实测 ~250 QPS 天花板），
    # 这里改用同步客户端 + 线程池 + keep-alive，让压测真实反映服务端能力。
    client = httpx.Client(
        timeout=httpx.Timeout(30.0, connect=5.0),
        limits=httpx.Limits(max_connections=max(c.concurrency, c.max_inflight),
                            max_keepalive_connections=max(c.concurrency, c.max_inflight)),
    )
    stats = Stats()
    t0 = time.monotonic()
    if c.requests > 0:
        print(f"[load] 一次性并发 {c.requests} 个请求（线程池 max-workers={c.max_inflight}）...")
        run_oneshot(c, client, pool, stats)
    else:
        print(f"[load] 持续压测 {c.duration}s，{c.concurrency} 并发，"
              f"目标 {c.qps if c.qps else '不限'} QPS ...")
        run_sustained(c, client, pool, stats)
    client.close()
    elapsed = time.monotonic() - t0
    stats.report(elapsed)
    return stats


def run_load(c):
    load_sync(c)


# --------------------------------------------------------------------------
# 对账（验收）
# --------------------------------------------------------------------------


def _wait_quiescence(c, initial, timeout):
    """等秒杀订单数收敛：连续 3 次(2s 间隔)读数不变或超时。"""
    db = db_connect(c)
    try:
        cur = db.cursor()
        last, stable = None, 0
        t0 = time.monotonic()
        while time.monotonic() - t0 < timeout:
            cur.execute(
                "SELECT COUNT(*) n FROM `order` WHERE activity_id=%s AND seckill_item_id=%s AND order_source=2",
                (c.activity_id, c.item_id))
            n = cur.fetchone()["n"]
            if n == last:
                stable += 1
            else:
                last, stable = n, 0
            if stable >= 3:
                print(f"[reconcile] 订单数收敛于 {n}（等待 {time.monotonic() - t0:.0f}s）")
                return True
            time.sleep(2)
        print(f"[reconcile] 警告：{timeout}s 内订单数未收敛（当前 {last}），可能有在途消息未消费完")
        return False
    finally:
        db.close()


def reconcile(c):
    print(f"\n========== 对账 (activity={c.activity_id} item={c.item_id}) ==========")
    db = db_connect(c)
    try:
        cur = db.cursor()
        item = _q(cur, "SELECT seckill_stock FROM seckill_item WHERE id=%s", (c.item_id,))
        if not item:
            print("[reconcile] 错误：seckill_item 不存在")
            sys.exit(3)
        db_remaining = item[0]["seckill_stock"]
        cur.execute(
            "SELECT COUNT(*) n, COUNT(DISTINCT CONCAT(user_id,':',activity_id,':',seckill_item_id)) d "
            "FROM `order` WHERE activity_id=%s AND seckill_item_id=%s AND order_source=2",
            (c.activity_id, c.item_id))
        row = cur.fetchone()
        orders, orders_distinct = row["n"], row["d"]
        cur.execute("SELECT COUNT(*) n FROM mq_message WHERE activity_id=%s AND seckill_item_id=%s AND status=4",
                    (c.activity_id, c.item_id))
        msg_done = cur.fetchone()["n"]
        cur.execute("SELECT status, COUNT(*) n FROM mq_message WHERE activity_id=%s AND seckill_item_id=%s GROUP BY status",
                    (c.activity_id, c.item_id))
        msg_hist = {r["status"]: r["n"] for r in cur.fetchall()}
    finally:
        db.close()

    r = redis_connect(c)
    try:
        v = r.get(f"mall:seckill:stock:{c.activity_id}:{c.item_id}")
        redis_remaining = int(v) if v is not None else None
    finally:
        r.close()

    initial = c.stock
    redis_deduct = initial - redis_remaining if redis_remaining is not None else None
    db_deduct = initial - db_remaining

    print(f"初始库存(重置值)  : {initial:,}")
    print(f"Redis 剩余库存    : {redis_remaining}")
    print(f"DB 剩余库存       : {db_remaining:,}")
    print(f"秒杀订单数        : {orders:,}")
    print(f"  去重后订单数     : {orders_distinct:,}")
    print(f"mq_message 完成数 : {msg_done:,}  状态分布: {msg_hist or {}}")
    print(f"Redis 扣减        : {redis_deduct}   DB 扣减: {db_deduct:,}")

    checks = []
    # 无超卖
    checks.append(("无超卖(订单<=初始库存)", orders <= initial,
                   f"{orders} <= {initial}"))
    # 库存正确性：DB 扣减 == 订单 == mq_message 完成
    checks.append(("DB扣减==订单数", db_deduct == orders,
                   f"{db_deduct} == {orders}"))
    checks.append(("订单数==mq完成数", orders == msg_done,
                   f"{orders} == {msg_done}"))
    if redis_remaining is not None:
        checks.append(("Redis扣减==订单数", redis_deduct == orders,
                       f"{redis_deduct} == {orders}"))
    # 无重复订单
    checks.append(("无重复订单(唯一键生效)", orders == orders_distinct,
                   f"{orders} == {orders_distinct}"))

    print("\n----- 验收项 -----")
    all_pass = True
    for name, ok, detail in checks:
        print(f"  [{'PASS' if ok else 'FAIL'}] {name:<22} {detail}")
        all_pass &= ok
    if not all_pass:
        print("\n[reconcile] 存在 FAIL 项，请结合 seckill-service 日志排查")
        sys.exit(1)
    print("\n[reconcile] 全部验收项通过 ✔")


# --------------------------------------------------------------------------
# 模式调度
# --------------------------------------------------------------------------


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    a = parse_args()
    c = make_cfg(a)

    if c.mode == "seed":
        seed_data(c)
    elif c.mode == "reset":
        reset_data(c)
        check_activity_open(c)
    elif c.mode == "load":
        if c.reset:
            reset_data(c)
        if not c.no_seed:
            seed_data(c)
        run_load(c)
        if not c.no_seed or c.reset:
            _wait_quiescence(c, c.stock, c.settle)
            reconcile(c)
    elif c.mode == "reconcile":
        _wait_quiescence(c, c.stock, c.settle)
        reconcile(c)
    else:  # run
        check_activity_open(c)
        if c.reset:
            reset_data(c)
        seed_data(c)
        run_load(c)
        _wait_quiescence(c, c.stock, c.settle)
        reconcile(c)


if __name__ == "__main__":
    main()
