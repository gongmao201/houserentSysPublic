#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""枚举 14 个存量 Controller 的 HTTP 入口，供冒烟脚本使用。

输出 TSV：controller \t httpMethod \t path \t javaMethod
"""
import os
import re
import sys

ROOT = r"D:\ruan\houserentSysPublic-main\hrent-api\src\main\java\com\wt\hrentSystem"
TARGETS = [
    "common/controller/DictController.java",
    "common/controller/MenuController.java",
    "common/controller/MessageController.java",
    "common/controller/SysCommonController.java",
    "common/controller/UserController.java",
    "hrent/controller/ApplyRentController.java",
    "hrent/controller/ApplyViewController.java",
    "hrent/controller/CollectionController.java",
    "hrent/controller/HouseRentingController.java",
    "hrent/controller/HouseResourceController.java",
    "hrent/controller/PicController.java",
    "hrent/controller/RentController.java",
    "hrent/controller/TagController.java",
    "wechat/web/WeChatController.java",
]

MAP_RE = re.compile(r'@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)\s*(?:\(\s*(?:value\s*=\s*)?(?:{?\s*)?["\']([^"\']*)["\'])?')


def main():
    rows = []
    for rel in TARGETS:
        path = os.path.join(ROOT, rel.replace("/", os.sep))
        with open(path, encoding="utf-8") as f:
            src = f.read()
        cls_at = src.find("class ")
        header = src[:cls_at]
        m = MAP_RE.search(header)
        base = (m.group(2) or "") if m else ""
        # 方法级：按注解出现位置切分，取其后 200 个字符内的 public 方法名
        for am in MAP_RE.finditer(src):
            ann, val = am.group(1), am.group(2) or ""
            if am.start() < cls_at:
                continue
            tail = src[am.end():am.end() + 400]
            mm = re.search(r"public\s+[\w<>\[\],\s.?]+\s+(\w+)\s*\(", tail)
            if not mm:
                continue
            method = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT",
                      "DeleteMapping": "DELETE"}.get(ann, "POST")
            full = "/" + "/".join(x.strip("/") for x in (base, val) if x.strip("/"))
            full = full.replace("//", "/")
            rows.append((os.path.basename(rel)[:-5], method, full, mm.group(1)))
    for r in rows:
        print("\t".join(r))
    print("TOTAL\t%d" % len(rows), file=sys.stderr)


if __name__ == "__main__":
    main()
