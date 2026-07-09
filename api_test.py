"""Lombok 改造后烟测脚本"""
import requests
import json
from datetime import datetime

SERVICES = {
    "user": "http://localhost:8081",
    "product": "http://localhost:8084",
    "order": "http://localhost:8082",
    "coupon": "http://localhost:8083",
}

def call(name, method, path, **kw):
    url = SERVICES[name] + path
    try:
        r = requests.request(method, url, timeout=10, **kw)
        body = r.text
        try:
            body = json.dumps(r.json(), ensure_ascii=False)
        except Exception:
            pass
        print(f"[{r.status_code}] {method} {path} -> {body[:200]}")
        return r
    except Exception as e:
        print(f"[ERR] {method} {path} -> {e}")
        return None

results = []

# user-service
r = call("user", "GET", "/users?username=alice")
results.append(("user.list", r is not None and r.status_code == 200))

# product-service
r = call("product", "GET", "/products")
results.append(("product.list", r is not None and r.status_code == 200))
if r and r.status_code == 200:
    data = r.json().get("data", [])
    if data:
        pid = data[0]["id"]
        r = call("product", "GET", f"/products/{pid}")
        results.append(("product.get", r is not None and r.status_code == 200))
        r = call("product", "GET", "/products/hot-rank?topN=3")
        results.append(("product.hotRank", r is not None and r.status_code == 200))

# order-service
r = call("order", "GET", "/orders?userId=1")
results.append(("order.listByUser", r is not None and r.status_code == 200))

# coupon-service
r = call("coupon", "POST", "/coupon/grab?couponId=1", headers={"UserId": "1"})
results.append(("coupon.grab", r is not None and r.status_code == 200))

# 汇总
print("\n========== SMOKE TEST RESULTS ==========")
passed = sum(1 for _, ok in results if ok)
for name, ok in results:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
print(f"\nTotal: {passed}/{len(results)} passed at {datetime.now().strftime('%H:%M:%S')}")
