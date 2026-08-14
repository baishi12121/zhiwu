#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
秒杀 Phase 2 混沌验收脚本。

默认只执行安全的压测与对账；需要重启 MySQL/RabbitMQ/秒杀服务时，显式传入对应命令。
示例：
  python scripts/seckill_chaos_test.py all --requests 500 --stock 100 --users 500
  python scripts/seckill_chaos_test.py mq-restart --rabbit-stop "net stop RabbitMQ" --rabbit-start "net start RabbitMQ"
"""

import argparse
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOADTEST = ROOT / "scripts" / "seckill_loadtest.py"


def run_cmd(cmd, label, allow_missing=False):
    if not cmd:
        if allow_missing:
            print(f"[chaos] skip {label}: 未提供命令")
            return True
        print(f"[chaos] FAIL {label}: 未提供命令")
        return False
    print(f"[chaos] {label}: {cmd}")
    result = subprocess.run(cmd, shell=True, cwd=ROOT)
    ok = result.returncode == 0
    print(f"[chaos] {label}: {'PASS' if ok else 'FAIL'}")
    return ok


def loadtest(args, mode="run"):
    cmd = [
        sys.executable, str(LOADTEST), mode,
        "--requests", str(args.requests),
        "--stock", str(args.stock),
        "--users", str(args.users),
        "--settle", str(args.settle),
    ]
    if args.reset and mode in ("run", "load"):
        cmd.append("--reset")
    print("[chaos] loadtest:", " ".join(cmd))
    return subprocess.run(cmd, cwd=ROOT).returncode == 0


def reconcile(args):
    cmd = [
        sys.executable, str(LOADTEST), "reconcile",
        "--stock", str(args.stock),
        "--users", str(args.users),
        "--settle", str(args.settle),
    ]
    print("[chaos] reconcile:", " ".join(cmd))
    return subprocess.run(cmd, cwd=ROOT).returncode == 0


def scenario_db_exception(args):
    print("[chaos] 场景一：DB 异常 / 慢恢复")
    ok = loadtest(args, "run")
    if args.mysql_stop and args.mysql_start:
        ok = run_cmd(args.mysql_stop, "mysql-stop") and ok
        time.sleep(args.outage_seconds)
        ok = run_cmd(args.mysql_start, "mysql-start") and ok
    print("[chaos] 预期：消费端重试 1s/5s/30s，最终失败进 DLQ；DB 恢复后可靠投递链路可继续消费")
    return reconcile(args) and ok


def scenario_mq_restart(args):
    print("[chaos] 场景二：MQ 重启 / 网络断开")
    ok = run_cmd(args.rabbit_stop, "rabbit-stop", allow_missing=True)
    time.sleep(args.outage_seconds)
    ok = run_cmd(args.rabbit_start, "rabbit-start", allow_missing=True) and ok
    ok = loadtest(args, "run") and ok
    print("[chaos] 预期：mq_message 待发送记录被 retryExpired 补发，不丢消息")
    return reconcile(args) and ok


def scenario_consumer_kill(args):
    print("[chaos] 场景三：消费者宕机 / 重启")
    ok = loadtest(args, "run")
    if args.seckill_stop and args.seckill_start:
        ok = run_cmd(args.seckill_stop, "seckill-stop") and ok
        time.sleep(args.outage_seconds)
        ok = run_cmd(args.seckill_start, "seckill-start") and ok
    print("[chaos] 预期：在途回收与消息重投恢复，无超卖无重复")
    return reconcile(args) and ok


def main():
    parser = argparse.ArgumentParser(description="秒杀 Phase 2 混沌验收脚本")
    parser.add_argument("scenario", choices=["all", "db-exception", "mq-restart", "consumer-kill", "reconcile"])
    parser.add_argument("--requests", type=int, default=500)
    parser.add_argument("--stock", type=int, default=100)
    parser.add_argument("--users", type=int, default=500)
    parser.add_argument("--settle", type=int, default=90)
    parser.add_argument("--reset", action="store_true")
    parser.add_argument("--outage-seconds", type=int, default=10)
    parser.add_argument("--mysql-stop", default="")
    parser.add_argument("--mysql-start", default="")
    parser.add_argument("--rabbit-stop", default="")
    parser.add_argument("--rabbit-start", default="")
    parser.add_argument("--seckill-stop", default="")
    parser.add_argument("--seckill-start", default="")
    args = parser.parse_args()

    scenarios = {
        "db-exception": scenario_db_exception,
        "mq-restart": scenario_mq_restart,
        "consumer-kill": scenario_consumer_kill,
        "reconcile": reconcile,
    }
    if args.scenario == "all":
        results = {name: func(args) for name, func in scenarios.items()}
    else:
        results = {args.scenario: scenarios[args.scenario](args)}

    print("\n[chaos] Phase 2 验收报告")
    for name, ok in results.items():
        print(f"[chaos] {name}: {'PASS' if ok else 'FAIL'}")
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
