#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""14 个存量 Controller 冒烟：逐个调用入口，判定"路由能进、业务码 200"。

跳过：删除/保存/注册/上传/微信登录等破坏性或需要文件、第三方配置的入口；
其余入口统一带 token + id=1/pageNo=1/pageSize=10，避免"空参 500"淹没迁移问题。
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENDPOINTS = os.path.join(ROOT, "scripts", "w3_endpoints.py")

SKIP_PREFIX = (
    "/user/delete", "/user/save", "/user/register", "/user/wechatSave", "/user/uploadHead",
    "/menu/save", "/menu/delete", "/menu/keepRoleMenu",
    "/message/save", "/message/delete",
    "/tag/save", "/tag/delete",
    "/rent/save", "/rent/delete",
    "/applyRent/save", "/applyRent/delete", "/applyRent/paySuccess",
    "/applyView/save", "/applyView/delete",
    "/houseResource/save", "/houseResource/delete", "/houseResource/uploadPic",
    "/pic/uploadPic", "/pic/save", "/pic/delete",
    "/sysCommon/register", "/wechat/login",
    "/houseRenting/save", "/houseRenting/delete",
    "/dict/save", "/dict/delete", "/collection/save",
)


ID_MAP = {
    "/dict/": "0", "/menu/": "100", "/message/": "9701", "/user/": "1",
    "/applyRent/": "9401", "/applyView/": "9301",
    "/houseResource/": "HR20260101000001",
    "/collection/": "3e577a8fa2ca4f9aa685ecb95d0cffd4",
    "/houseRenting/": "3c19606d00394999841bc7fbf25fdce1",
    "/pic/": "1", "/rent/": "9601", "/tag/": "9001",
    "/wechat/getHouseById": "HR20260101000001", "/wechat/getUserById": "1",
    "/wechat/getPic": "1",
}


def id_for(path):
    for k, v in ID_MAP.items():
        if path.startswith(k):
            return v
    return "1"


def load_endpoints():
    p = subprocess.run([sys.executable, ENDPOINTS], capture_output=True, text=True, encoding="utf-8")
    rows = []
    for line in p.stdout.splitlines():
        parts = line.split("\t")
        if len(parts) == 4:
            rows.append(parts)
    return rows


def post_form(base, path, fields, token=None, timeout=15):
    data = urllib.parse.urlencode(fields).encode("utf-8")
    req = urllib.request.Request(base + path, data=data, method="POST",
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    if token:
        req.add_header("token", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:400]
    except Exception as e:  # noqa
        return 0, "%s: %s" % (type(e).__name__, e)


def post_json(base, path, payload, token=None, timeout=15):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(base + path, data=data, method="POST",
                                 headers={"Content-Type": "application/json;charset=UTF-8"})
    if token:
        req.add_header("token", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:400]
    except Exception as e:  # noqa
        return 0, "%s: %s" % (type(e).__name__, e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8899/hrent")
    ap.add_argument("--login-name", default="admin")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--json", default="")
    a = ap.parse_args()

    code, body = post_form(a.base, "/user/login",
                           {"loginName": a.login_name, "password": a.password})
    token, login_ok = "", False
    try:
        j = json.loads(body)
        login_ok = str(j.get("code")) == "200" and bool(j.get("data"))
        token = j.get("data") or ""
    except Exception:
        j = {}
    print("登录：http=%s code=%s token=%s" % (code, j.get("code"),
                                          (token[:16] + "...") if token else "-"))

    rows = load_endpoints()
    results = []
    for ctrl, method, path, jm in rows:
        if path.startswith(SKIP_PREFIX):
            results.append({"controller": ctrl, "path": path, "java": jm,
                            "skipped": True, "ok": True, "message": "skip"})
            continue
        qs = {"id": id_for(path), "pageNo": "1", "pageSize": "10",
              "loginName": a.login_name, "currentUser.role": "0"}
        if path.endswith("/login") or path.endswith("/loginJson"):
            qs["password"] = a.password
        else:
            qs.pop("loginName")
        code, body = post_form(a.base, path, qs, token)
        if code == 415:  # @RequestBody 的入口：改用 JSON 再试一次
            payload = {"id": qs["id"], "pageNo": 1, "pageSize": 10,
                       "currentUser": {"role": "0"}, "loginName": a.login_name}
            if path.endswith("/login") or path.endswith("/loginJson"):
                payload["password"] = a.password
            code, body = post_json(a.base, path, payload, token)
        try:
            j = json.loads(body)
            bcode = j.get("code")
            msg = (j.get("message") or "")[:50]
        except Exception:
            bcode, msg = None, body[:50].replace("\n", " ")
        ok = code == 200 and str(bcode) == "200"
        if not ok and code == 200 and "已存在" in msg:
            ok = True  # 唯一性校验入口：返回"已存在"说明查重逻辑正常
        results.append({"controller": ctrl, "path": path, "java": jm, "http": code,
                        "business": bcode, "message": msg, "ok": ok, "skipped": False})
        print("%-4s %-24s %-40s http=%-3s code=%s %s" %
              ("OK" if ok else "FAIL", ctrl, path, code, bcode, "" if ok else msg))

    by_ctrl = {}
    for r in results:
        slot = by_ctrl.setdefault(r["controller"], [0, 0, 0])
        slot[0 if r["ok"] else 1] += 1
        if r["skipped"]:
            slot[2] += 1
    print("\n按 Controller 汇总（通过 / 失败 / 跳过）：")
    for c, (ok, bad, sk) in sorted(by_ctrl.items()):
        print("  %-26s %3d / %3d / %3d  %s" % (c, ok - sk, bad, sk, "PASS" if bad == 0 else "FAIL"))
    bad = [r for r in results if not r["ok"]]
    print("\n总判定：%s（入口 %d，失败 %d，跳过 %d）" %
          ("PASS" if not bad and login_ok else "FAIL", len(results), len(bad),
           sum(1 for r in results if r["skipped"])))
    if a.json:
        os.makedirs(os.path.dirname(a.json) or ".", exist_ok=True)
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"login": login_ok, "endpoints": results}, f, ensure_ascii=False, indent=2)
    return 0 if not bad and login_ok else 1


if __name__ == "__main__":
    sys.exit(main())
