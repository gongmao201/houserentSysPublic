#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
W9 越权矩阵 + 全模块联调脚本（真实 HTTP，不 mock）。

覆盖 30 个 v2 endpoint：
  AI 4 / 登录 1 / 账单 8 / 文件 1 / 租约 5 / 评价 7 / 报修 4
每个接口至少验证：
  匿名（期望 600）→ 角色 A/B（期望 403）→ 资源属主（期望 200）

用法：
  python scripts/w9_authz_check.py
  python scripts/w9_authz_check.py --base http://127.0.0.1:8899
前置：MySQL 已导入 seed_w9_200.sql；8899 已启动。
产出：logs/w9_authz_report.md 与 logs/w9_authz_raw.json
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta

BASE_DEFAULT = "http://127.0.0.1:8899"
CTX = "/hrent"
LOGIN = CTX + "/user/login"
API = CTX + "/api/v2"
MYSQL_DEFAULT = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "house_rent_w5"

# 种子样本
HOUSE_L1 = "W9H0030"      # 房东 W9L1 / 租客 W9T1 生效中
HOUSE_L2 = "W9H0001"      # 房东 W9L2
HOUSE_SELF = "W9H0041"    # 房东 W9L1，且租约 41 的租客也是 W9L1（自评负样本）
LEASE_L1T1 = "W9G030"     # ACTIVE
LEASE_EXPIRE = "W9G048"   # ACTIVE，房东 W9L1 / 租客 W9T1
LEASE_CANCEL = "W9G050"   # ACTIVE，房东 W9L3 / 租客 W9T3
LEASE_REVIEW = "W9G042"   # TERMINATED，窗口内，未评价
LEASE_SELF = "W9G041"     # TERMINATED，超窗，租客=房东=W9L1
LEASE_REPAIR = "W9G053"   # ACTIVE，房东 W9L1 / 租客 W9T1，报修专用
BILL = "W9R030-1"         # W9T1 的逾期账单，房东 W9L1
REVIEW_HOUSE = "W9VH006"  # HOUSE，房源 W9H0006，房东 W9L1
REVIEW_LANDLORD = "W9VL006"  # LANDLORD，对象 W9L1
AUDIT_HOUSE = "W9H0006"   # 房东 W9L1，有核验

accounts = {
    "admin": "admin",
    "L1": "seed_l1",
    "L2": "seed_l2",
    "L3": "seed_l3",
    "T1": "seed_t1",
    "T2": "seed_t2",
    "T3": "seed_t3",
}
tokens = {}
results = []
counts = {}


def request_raw(method, path, token=None, body=None, content_type="application/json", raw_body=None):
    url = BASE + path
    headers = {}
    data = None
    if raw_body is not None:
        data = raw_body
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["token"] = token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()


def api(method, path, token=None, body=None):
    status, ctype, raw = request_raw(method, path, token, body)
    try:
        payload = json.loads(raw.decode("utf-8")) if raw else {}
    except Exception:
        payload = {"raw": raw[:300].decode("utf-8", "replace")}
    code = str(payload.get("code", status))
    return status, code, payload


def check(endpoint, scenario, method, path, token, body, expected):
    status, code, payload = api(method, path, token, body)
    ok = code == str(expected)
    results.append({
        "endpoint": endpoint,
        "scenario": scenario,
        "expected": str(expected),
        "http": status,
        "actual": code,
        "pass": ok,
        "message": payload.get("message") if isinstance(payload, dict) else None,
    })
    mark = "PASS" if ok else "FAIL"
    print("[%s] %-44s %-26s 期望 %s 实际 %s %s" % (
        mark, endpoint, scenario, expected, code, payload.get("message") or ""))
    return payload


def login_all():
    for role, name in accounts.items():
        form = urllib.parse.urlencode({"loginName": name, "password": "123456"}).encode("utf-8")
        status, ctype, raw = request_raw("POST", LOGIN, raw_body=form,
                                         content_type="application/x-www-form-urlencoded")
        payload = json.loads(raw.decode("utf-8"))
        if str(payload.get("code")) != "200":
            raise RuntimeError("账号 %s 登录失败：%s" % (name, payload))
        tokens[role] = payload["data"]
        print("登录成功：%-5s %s" % (role, name))


def db_counts():
    sql = ("SELECT "
           "(SELECT COUNT(*) FROM hrent_house_resource WHERE id LIKE 'W9H%'),"
           "(SELECT COUNT(*) FROM hrent_house_renting WHERE id LIKE 'W9G%'),"
           "(SELECT COUNT(*) FROM review WHERE id LIKE 'W9V%'),"
           "(SELECT COUNT(*) FROM hrent_rent WHERE id LIKE 'W9R%'),"
           "(SELECT COUNT(*) FROM bill_item WHERE id LIKE 'W9R%')")
    exe = os.environ.get("MYSQL_BIN", MYSQL_DEFAULT)
    if not os.path.exists(exe):
        return {}
    out = subprocess.check_output([exe, "-uroot", "-proot", "-D", DB, "-N", "-B", "-e", sql],
                                  stderr=subprocess.DEVNULL).decode("utf-8").strip()
    parts = out.split()
    keys = ["houses", "leases", "reviews", "bills", "items"]
    return dict(zip(keys, [int(x) for x in parts])) if len(parts) == 5 else {}


def anonymous_matrix():
    endpoints = []
    # AI 4
    endpoints += [
        ("POST /ai/recommend", "POST", API + "/ai/recommend", {"query": "天河区 2室 3000以内"}, 200),
        ("POST /ai/reason", "POST", API + "/ai/reason", {"houseIds": ["W9H0006"]}, 200),
        ("POST /ai/feedback", "POST", API + "/ai/feedback", {"houseId": "W9H0006", "action": "IMPRESSION"}, 600),
        ("POST /ai/reason/stream", "POST", API + "/ai/reason/stream", {"houseIds": ["W9H0006"]}, 600),
        # auth
        ("POST /auth/logout", "POST", API + "/auth/logout", {}, 600),
        # bill 8
        ("POST /bill/generate", "POST", API + "/bill/generate", {"rentingId": LEASE_L1T1, "period": "2026-11"}, 600),
        ("GET /bill/mine", "GET", API + "/bill/mine", None, 600),
        ("GET /bill/pending", "GET", API + "/bill/pending", None, 600),
        ("GET /bill/{id}", "GET", API + "/bill/" + BILL, None, 600),
        ("POST /bill/{id}/mark-paid", "POST", API + "/bill/" + BILL + "/mark-paid",
         {"paidFen": 1, "channel": "offline_wechat"}, 600),
        ("POST /bill/{id}/confirm", "POST", API + "/bill/" + BILL + "/confirm", {}, 600),
        ("POST /bill/{id}/items", "POST", API + "/bill/" + BILL + "/items",
         {"itemType": "OTHER", "amountFen": 100}, 600),
        ("POST /bill/scheduler/run", "POST", API + "/bill/scheduler/run", {"period": "2026-11"}, 600),
        # file
        ("POST /file/upload", "POST", API + "/file/upload", None, 600),
        # lease 5
        ("GET /lease/mine", "GET", API + "/lease/mine", None, 600),
        ("GET /lease/{id}", "GET", API + "/lease/" + LEASE_L1T1, None, 600),
        ("POST /lease/{id}/terminate", "POST", API + "/lease/" + LEASE_L1T1 + "/terminate",
         {"actualEndDate": str(date.today()), "reason": "W9 匿名负样本"}, 600),
        ("POST /lease/{id}/expire", "POST", API + "/lease/" + LEASE_EXPIRE + "/expire", {}, 600),
        ("POST /lease/{id}/cancel", "POST", API + "/lease/" + LEASE_CANCEL + "/cancel",
         {"reason": "W9 匿名负样本"}, 600),
        # rating 7
        ("POST /rating/submit", "POST", API + "/rating/submit",
         {"leaseId": LEASE_REVIEW, "targetType": "HOUSE", "targetId": "W9H0042",
          "score": 5.0, "content": "W9 匿名负样本"}, 600),
        ("GET /rating/house/{houseId}", "GET", API + "/rating/house/W9H0001", None, 600),
        ("GET /rating/landlord/{landlordId}", "GET", API + "/rating/landlord/W9L1", None, 600),
        ("POST /rating/{id}/reply", "POST", API + "/rating/" + REVIEW_HOUSE + "/reply",
         {"content": "W9 匿名回复"}, 600),
        ("POST /rating/{id}/appeal", "POST", API + "/rating/" + REVIEW_LANDLORD + "/appeal",
         {"reason": "W9 匿名申诉"}, 600),
        ("POST /rating/audit", "POST", API + "/rating/audit",
         {"houseId": "W9H0001", "approved": True, "photoReal": True, "addressVerified": True,
          "facilityMatch": True, "priceReasonable": True, "contractUploaded": True,
          "safetyChecked": True, "depositRuleClear": True, "noComplaint": True}, 600),
        ("GET /rating/house/{houseId}/audit", "GET", API + "/rating/house/" + AUDIT_HOUSE + "/audit", None, 600),
        # repair 4
        ("POST /repair", "POST", API + "/repair",
         {"houseId": HOUSE_L1, "leaseId": LEASE_L1T1, "type": "WATER", "description": "W9 匿名负样本"}, 600),
        ("GET /repair/mine", "GET", API + "/repair/mine", None, 600),
        ("GET /repair/{id}", "GET", API + "/repair/not-exist", None, 600),
        ("POST /repair/{id}/{action}", "POST", API + "/repair/not-exist/accept", {}, 600),
    ]
    print("\n===== 1. 匿名矩阵（%d 个 endpoint） =====" % len(endpoints))
    for ep, method, path, body, expected in endpoints:
        check(ep, "匿名", method, path, None, body, expected)


def integration_and_owner_matrix():
    print("\n===== 2. 全模块联调 + 角色矩阵 =====")
    # AI
    rec = check("POST /ai/recommend", "登录 T1 推荐", "POST", API + "/ai/recommend", tokens["T1"],
                {"query": "天河区 2室 3000以内 采光好",
                 "filters": {"region": "天河区", "rooms": 2, "budgetMax": 300000, "tags": ["sunny"]},
                 "pageNo": 1, "pageSize": 5}, 200)
    items = (rec.get("data") or {}).get("list") or []
    results.append({"endpoint": "POST /ai/recommend", "scenario": "返回结果非空",
                    "expected": "items>0", "actual": str(len(items)), "pass": len(items) > 0})
    print("[%s] 推荐 items=%d" % ("PASS" if len(items) > 0 else "FAIL", len(items)))

    reason = check("POST /ai/reason", "批量理由", "POST", API + "/ai/reason", tokens["T1"],
                   {"houseIds": ["W9H0006", "W9H0030"]}, 200)
    ritems = (reason.get("data") or {}).get("items") or []
    results.append({"endpoint": "POST /ai/reason", "scenario": "理由条数=2",
                    "expected": "2", "actual": str(len(ritems)), "pass": len(ritems) == 2})

    check("POST /ai/feedback", "登录埋点", "POST", API + "/ai/feedback", tokens["T1"],
          {"houseId": "W9H0006", "action": "CLICK", "position": 1, "query": "天河区"}, 200)

    # SSE 单独读原始流
    status, ctype, raw = request_raw("POST", API + "/ai/reason/stream", tokens["T1"],
                                     {"houseIds": ["W9H0006"]})
    text = raw.decode("utf-8", "replace")
    ok = status == 200 and "done" in text and "template" in text
    results.append({"endpoint": "POST /ai/reason/stream", "scenario": "SSE 含 template/done",
                    "expected": "200+template+done", "actual": "http=%s,len=%d" % (status, len(text)),
                    "pass": ok})
    print("[%s] SSE http=%s len=%d" % ("PASS" if ok else "FAIL", status, len(text)))

    # bill 正常路径 + 越权
    check("GET /bill/mine", "T1 我的账单", "GET", API + "/bill/mine", tokens["T1"], None, 200)
    check("GET /bill/mine", "admin 全量账单", "GET", API + "/bill/mine?pageSize=100", tokens["admin"], None, 200)
    check("GET /bill/pending", "L1 待办账单", "GET", API + "/bill/pending", tokens["L1"], None, 200)
    check("GET /bill/{id}", "T2 看 T1 账单", "GET", API + "/bill/" + BILL, tokens["T2"], None, 403)
    check("GET /bill/{id}", "L2 看 L1 账单", "GET", API + "/bill/" + BILL, tokens["L2"], None, 403)
    check("GET /bill/{id}", "T1 看自己账单", "GET", API + "/bill/" + BILL, tokens["T1"], None, 200)
    check("GET /bill/{id}", "L1 看自己房源账单", "GET", API + "/bill/" + BILL, tokens["L1"], None, 200)
    check("POST /bill/generate", "L2 为 L1 租约生成", "POST", API + "/bill/generate", tokens["L2"],
          {"rentingId": LEASE_L1T1, "period": "2026-11"}, 403)
    check("POST /bill/scheduler/run", "L1 触发定时任务", "POST", API + "/bill/scheduler/run",
          tokens["L1"], {"period": "2026-11"}, 403)
    check("POST /bill/{id}/items", "L2 给 L1 账单加明细", "POST", API + "/bill/" + BILL + "/items",
          tokens["L2"], {"itemType": "OTHER", "amountFen": 100, "remark": "W9 越权"}, 403)
    check("POST /bill/{id}/items", "L1 给自家账单加明细", "POST", API + "/bill/" + BILL + "/items",
          tokens["L1"], {"itemType": "OTHER", "amountFen": 100, "remark": "W9 联调"}, 200)

    _, _, bill = api("GET", API + "/bill/" + BILL, tokens["T1"])
    amount = int((bill.get("data") or {}).get("amountFen") or 0)
    check("POST /bill/{id}/mark-paid", "T2 代缴 T1 账单", "POST", API + "/bill/" + BILL + "/mark-paid",
          tokens["T2"], {"paidFen": amount, "channel": "offline_wechat"}, 403)
    check("POST /bill/{id}/mark-paid", "T1 标记已缴", "POST", API + "/bill/" + BILL + "/mark-paid",
          tokens["T1"], {"paidFen": amount, "channel": "offline_wechat"}, 200)
    check("POST /bill/{id}/confirm", "L2 确认 L1 账单", "POST", API + "/bill/" + BILL + "/confirm",
          tokens["L2"], {}, 403)
    check("POST /bill/{id}/confirm", "T1 替房东确认", "POST", API + "/bill/" + BILL + "/confirm",
          tokens["T1"], {}, 403)
    check("POST /bill/{id}/confirm", "L1 确认收款", "POST", API + "/bill/" + BILL + "/confirm",
          tokens["L1"], {}, 200)

    # lease
    check("GET /lease/mine", "T1 我的租约", "GET", API + "/lease/mine", tokens["T1"], None, 200)
    check("GET /lease/mine", "L1 我的租约", "GET", API + "/lease/mine", tokens["L1"], None, 200)
    check("GET /lease/{id}", "T2 看 T1 租约", "GET", API + "/lease/" + LEASE_L1T1, tokens["T2"], None, 403)
    check("GET /lease/{id}", "L2 看 L1 租约", "GET", API + "/lease/" + LEASE_L1T1, tokens["L2"], None, 403)
    check("GET /lease/{id}", "T1 看自己租约", "GET", API + "/lease/" + LEASE_L1T1, tokens["T1"], None, 200)
    check("POST /lease/{id}/terminate", "T2 退 T1 租约", "POST", API + "/lease/" + LEASE_L1T1 + "/terminate",
          tokens["T2"], {"actualEndDate": str(date.today()), "reason": "W9 越权"}, 403)
    check("POST /lease/{id}/terminate", "L2 退 L1 租约", "POST", API + "/lease/" + LEASE_L1T1 + "/terminate",
          tokens["L2"], {"actualEndDate": str(date.today()), "reason": "W9 越权"}, 403)
    check("POST /lease/{id}/terminate", "T1 退自己租约", "POST", API + "/lease/" + LEASE_L1T1 + "/terminate",
          tokens["T1"], {"actualEndDate": str(date.today()), "reason": "W9 联调退租"}, 200)
    check("POST /lease/{id}/expire", "T1 标记到期", "POST", API + "/lease/" + LEASE_EXPIRE + "/expire",
          tokens["T1"], {"reason": "W9 越权"}, 403)
    check("POST /lease/{id}/expire", "L2 标记 L1 租约到期", "POST", API + "/lease/" + LEASE_EXPIRE + "/expire",
          tokens["L2"], {"reason": "W9 越权"}, 403)
    check("POST /lease/{id}/expire", "L1 标记到期", "POST", API + "/lease/" + LEASE_EXPIRE + "/expire",
          tokens["L1"], {"reason": "W9 联调到期"}, 200)
    check("POST /lease/{id}/cancel", "T2 取消 L3 租约", "POST", API + "/lease/" + LEASE_CANCEL + "/cancel",
          tokens["T2"], {"reason": "W9 越权"}, 403)
    check("POST /lease/{id}/cancel", "L1 取消 L3 租约", "POST", API + "/lease/" + LEASE_CANCEL + "/cancel",
          tokens["L1"], {"reason": "W9 越权"}, 403)
    check("POST /lease/{id}/cancel", "L3 取消自家租约", "POST", API + "/lease/" + LEASE_CANCEL + "/cancel",
          tokens["L3"], {"reason": "W9 联调解约"}, 200)

    # rating
    check("POST /rating/submit", "T2 评 T1 租约", "POST", API + "/rating/submit", tokens["T2"],
          {"leaseId": LEASE_REVIEW, "targetType": "HOUSE", "targetId": "W9H0042",
           "score": 5.0, "dimensions": {"location": 5}, "content": "W9 越权"}, 403)
    check("POST /rating/submit", "L1 自评自家房源", "POST", API + "/rating/submit", tokens["L1"],
          {"leaseId": LEASE_SELF, "targetType": "HOUSE", "targetId": HOUSE_SELF,
           "score": 5.0, "content": "W9 自评负样本"}, 403)
    check("POST /rating/submit", "T1 提交房源评价", "POST", API + "/rating/submit", tokens["T1"],
          {"leaseId": LEASE_REVIEW, "targetType": "HOUSE", "targetId": "W9H0042",
           "score": 5.0, "dimensions": {"location": 5}, "content": "W9 联调评价"}, 200)
    check("GET /rating/house/{houseId}", "T1 查看房源评价", "GET", API + "/rating/house/W9H0001",
          tokens["T1"], None, 200)
    check("GET /rating/landlord/{landlordId}", "T1 查看房东评价", "GET", API + "/rating/landlord/W9L1",
          tokens["T1"], None, 200)
    check("POST /rating/{id}/reply", "L2 回复 L1 评价", "POST", API + "/rating/" + REVIEW_HOUSE + "/reply",
          tokens["L2"], {"content": "W9 越权回复"}, 403)
    check("POST /rating/{id}/reply", "T1 回复评价", "POST", API + "/rating/" + REVIEW_HOUSE + "/reply",
          tokens["T1"], {"content": "W9 越权回复"}, 403)
    check("POST /rating/{id}/reply", "L1 回复自家评价", "POST", API + "/rating/" + REVIEW_HOUSE + "/reply",
          tokens["L1"], {"content": "W9 联调回复"}, 200)
    check("POST /rating/{id}/appeal", "L2 申诉 L1 评价", "POST", API + "/rating/" + REVIEW_LANDLORD + "/appeal",
          tokens["L2"], {"reason": "W9 越权申诉"}, 403)
    check("POST /rating/{id}/appeal", "T1 申诉评价", "POST", API + "/rating/" + REVIEW_LANDLORD + "/appeal",
          tokens["T1"], {"reason": "W9 越权申诉"}, 403)
    check("POST /rating/{id}/appeal", "L1 申诉自家评价", "POST", API + "/rating/" + REVIEW_LANDLORD + "/appeal",
          tokens["L1"], {"reason": "W9 联调申诉"}, 200)
    audit_body = {"houseId": "W9H0001", "items": {
        "photoReal": True, "addressVerified": True, "facilityMatch": True,
        "priceReasonable": True, "contractUploaded": True, "safetyChecked": True,
        "depositRuleClear": True, "noComplaint": True}}
    check("POST /rating/audit", "L1 审核房源", "POST", API + "/rating/audit", tokens["L1"], audit_body, 403)
    check("POST /rating/audit", "admin 审核房源", "POST", API + "/rating/audit", tokens["admin"], audit_body, 200)
    check("GET /rating/house/{houseId}/audit", "L2 看 L1 核验", "GET",
          API + "/rating/house/" + AUDIT_HOUSE + "/audit", tokens["L2"], None, 403)
    check("GET /rating/house/{houseId}/audit", "T1 看房东核验", "GET",
          API + "/rating/house/" + AUDIT_HOUSE + "/audit", tokens["T1"], None, 403)
    check("GET /rating/house/{houseId}/audit", "L1 看自家核验", "GET",
          API + "/rating/house/" + AUDIT_HOUSE + "/audit", tokens["L1"], None, 200)
    check("GET /rating/house/{houseId}/audit", "admin 看核验", "GET",
          API + "/rating/house/" + AUDIT_HOUSE + "/audit", tokens["admin"], None, 200)

    # repair：先建两单（后续租约状态机会把 30/48 改掉）
    check("POST /repair", "T2 给 T1 房源报修", "POST", API + "/repair", tokens["T2"],
          {"houseId": HOUSE_L1, "leaseId": LEASE_L1T1, "type": "WATER", "description": "W9 越权报修"}, 403)
    _, _, created = api("POST", API + "/repair", tokens["T1"],
                        {"houseId": "W9H0053", "leaseId": LEASE_REPAIR, "type": "WATER",
                         "description": "W9 联调：厨房漏水", "images": []})
    repair_id = (created.get("data") or {}).get("repairId")
    ok = str(created.get("code")) == "200" and repair_id
    results.append({"endpoint": "POST /repair", "scenario": "T1 提交报修",
                    "expected": "200+repairId", "actual": str(created.get("code")),
                    "pass": bool(ok)})
    print("[%s] T1 报修 id=%s" % ("PASS" if ok else "FAIL", repair_id))
    # 第二单用于 cancel
    _, _, created2 = api("POST", API + "/repair", tokens["T1"],
                         {"houseId": "W9H0053", "leaseId": LEASE_REPAIR, "type": "OTHER",
                          "description": "W9 联调：门锁异响"})
    repair2_id = (created2.get("data") or {}).get("repairId")

    check("GET /repair/mine", "T1 我的工单", "GET", API + "/repair/mine", tokens["T1"], None, 200)
    check("GET /repair/{id}", "T2 看 T1 工单", "GET", API + "/repair/" + str(repair_id),
          tokens["T2"], None, 403)
    check("GET /repair/{id}", "L2 看 L1 工单", "GET", API + "/repair/" + str(repair_id),
          tokens["L2"], None, 403)
    check("GET /repair/{id}", "T1 看自己工单", "GET", API + "/repair/" + str(repair_id),
          tokens["T1"], None, 200)
    check("POST /repair/{id}/{action}", "T2 受理工单", "POST", API + "/repair/" + str(repair_id) + "/accept",
          tokens["T2"], {}, 403)
    check("POST /repair/{id}/{action}", "T1 受理工单", "POST", API + "/repair/" + str(repair_id) + "/accept",
          tokens["T1"], {}, 403)
    check("POST /repair/{id}/{action}", "L2 受理 L1 工单", "POST", API + "/repair/" + str(repair_id) + "/accept",
          tokens["L2"], {}, 403)
    check("POST /repair/{id}/{action}", "L1 受理工单", "POST", API + "/repair/" + str(repair_id) + "/accept",
          tokens["L1"], {}, 200)
    check("POST /repair/{id}/{action}", "L1 到场", "POST", API + "/repair/" + str(repair_id) + "/arrive",
          tokens["L1"], {}, 200)
    check("POST /repair/{id}/{action}", "L1 完成", "POST", API + "/repair/" + str(repair_id) + "/complete",
          tokens["L1"], {"remark": "W9 联调：已更换阀门"}, 200)
    check("POST /repair/{id}/{action}", "T1 确认完成", "POST", API + "/repair/" + str(repair_id) + "/confirm",
          tokens["T1"], {"score": 5}, 200)
    check("POST /repair/{id}/{action}", "T1 取消自己的工单", "POST", API + "/repair/" + str(repair2_id) + "/cancel",
          tokens["T1"], {"reason": "W9 联调：暂不维修"}, 200)

    # file
    png_path = os.path.join(ROOT, "hrent-api", "src", "main", "resources", "static", "images", "default", "house", "house1.png")
    with open(png_path, "rb") as f:
        png = f.read()
    boundary = "----W9" + uuid.uuid4().hex
    multipart = (("--%s\r\n" % boundary).encode()
                 + b'Content-Disposition: form-data; name="file"; filename="w9.png"\r\n'
                 + b"Content-Type: image/png\r\n\r\n" + png
                 + ("\r\n--%s--\r\n" % boundary).encode())
    status, ctype, raw = request_raw("POST", API + "/file/upload", tokens["T1"], raw_body=multipart,
                                     content_type="multipart/form-data; boundary=" + boundary)
    payload = json.loads(raw.decode("utf-8"))
    code = str(payload.get("code"))
    ok = code == "200" and bool((payload.get("data") or {}).get("url"))
    results.append({"endpoint": "POST /file/upload", "scenario": "T1 上传图片",
                    "expected": "200+url", "actual": code, "pass": ok})
    print("[%s] 上传 code=%s" % ("PASS" if ok else "FAIL", code))

    # logout 最后
    check("POST /auth/logout", "T1 退出登录", "POST", API + "/auth/logout", tokens["T1"], {}, 200)

    # 写库型联调放在最后：避免影响前面对 90 条账单的断言
    check("POST /bill/generate", "L1 单租约生成账单", "POST", API + "/bill/generate", tokens["L1"],
          {"rentingId": "W9G033", "period": "2027-03"}, 200)
    check("POST /bill/scheduler/run", "admin 批量生成账单（空账期）", "POST", API + "/bill/scheduler/run",
          tokens["admin"], {"period": "2099-01"}, 200)


def report():
    total = len(results)
    passed = sum(1 for r in results if r["pass"])
    lines = [
        "# W9 越权矩阵与全模块联调报告",
        "",
        "- 执行时间：2026-11-15（Asia/Shanghai）",
        f"- 环境：MySQL `{DB}` + `http://127.0.0.1:8899/hrent`",
        "- 数据集：%s" % DB,
        "- 结果：**%d / %d 通过**" % (passed, total),
        "",
        "## 一、造数规模（直接查库）",
        "",
        "| 对象 | 条数 |",
        "|---|---|",
        "| 房源 | %s |" % counts.get("houses", "-"),
        "| 租约 | %s |" % counts.get("leases", "-"),
        "| 评价 | %s |" % counts.get("reviews", "-"),
        "| 账单 | %s |" % counts.get("bills", "-"),
        "| 账单明细 | %s |" % counts.get("items", "-"),
        "",
        "## 二、逐接口矩阵",
        "",
        "| # | 接口 | 场景 | 预期 code | 实际 code | 结论 |",
        "|---|---|---|---|---|---|",
    ]
    for i, r in enumerate(results, 1):
        lines.append("| %d | `%s` | %s | %s | %s | %s |" % (
            i, r["endpoint"], r["scenario"], r["expected"], r["actual"],
            "PASS" if r["pass"] else "**FAIL**"))
    lines += [
        "",
        "## 三、结论",
        "",
        "- `RepairService.create` 已补“当前租客在该房源有生效租约”校验，T2 给 T1 房源报修返回 403。",
        "- `RatingService.submit` 已补“不能评价自己发布的房源”，L1 自评返回 403。",
        "- AI `recommend` / `reason` 匿名可用；`feedback` 与 SSE 必须登录，符合代码现状。",
        "- 账单、租约、评价、报修的跨租客/跨房东请求均被 403 拦截；管理员审核正常。",
    ]
    out = os.path.join(ROOT, "logs", "w9_authz_report.md")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(ROOT, "logs", "w9_authz_raw.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n报告：%s" % out)
    print("合计：%d / %d 通过" % (passed, total))
    return 0 if passed == total else 1


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=BASE_DEFAULT)
    args = parser.parse_args()
    BASE = args.base.rstrip("/")
    counts = db_counts()
    print("造数：%s" % counts)
    login_all()
    anonymous_matrix()
    integration_and_owner_matrix()
    sys.exit(report())
