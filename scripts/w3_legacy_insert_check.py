#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""存量初始化脚本 17 条 INSERT 与新约束的兼容性检查。

做法：把 hrent-api/sql/house_rent.sql 里的每条 INSERT 单独拿到已跑完 V1~V7 的
验证库里执行，记录成功/失败与错误原文；再统计新字段是否被老语句写空。

用法：python scripts/w3_legacy_insert_check.py --db house_rent_vr --json logs/w3_insert.json
"""
import argparse
import json
import os
import re
import subprocess
import sys

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL = os.path.join(ROOT, "hrent-api", "sql", "house_rent.sql")
USER = os.environ.get("DB_USERNAME", "root")
PWD = os.environ.get("DB_PASSWORD", "root")


def run(db, sql):
    p = subprocess.run([MYSQL, "-u" + USER, "-p" + PWD, "--default-character-set=utf8mb4",
                        "-e", "USE `%s`; %s" % (db, sql)],
                       capture_output=True, text=True, encoding="utf-8")
    return p.returncode, (p.stderr or "").strip()


def q(db, sql):
    rc, err = run(db, sql)
    if rc != 0:
        raise RuntimeError(err)
    p = subprocess.run([MYSQL, "-u" + USER, "-p" + PWD, "-N", "-B",
                        "--default-character-set=utf8mb4", "-e", "USE `%s`; %s" % (db, sql)],
                       capture_output=True, text=True, encoding="utf-8")
    return [line.split("\t") for line in p.stdout.strip().splitlines() if line]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default="house_rent_vr")
    ap.add_argument("--json", default="")
    a = ap.parse_args()

    with open(SQL, encoding="utf-8") as f:
        text = f.read()
    # 只取 INSERT 段：从第一条 INSERT 到最后一条语句结束
    stmts = []
    for m in re.finditer(r"INSERT\s+INTO.*?;", text, re.S | re.I):
        s = m.group(0)
        # 压缩换行便于定位，但原样执行
        stmts.append((text[:m.start()].count("\n") + 1, s))

    results = []
    for line, s in stmts:
        table = re.search(r"INSERT\s+INTO\s+([A-Za-z_][\w]*)", s, re.I).group(1)
        rc, err = run(a.db, s)
        msg = ""
        if rc != 0:
            bad = [x for x in err.splitlines() if "ERROR" in x.upper() or "error" in x]
            msg = (bad[0] if bad else err.splitlines()[-1])[:300]
        results.append({"line": line, "table": table, "ok": rc == 0, "error": msg})
        print("%-4s L%-4d %-24s %s" % ("OK" if rc == 0 else "FAIL", line, table,
                                       results[-1]["error"]))

    nulls = {}
    for tbl, col, src in [
        ("hrent_house_resource", "money_fen", "money"),
        ("hrent_apply_rent", "deposit_fen", "deposit"),
        ("hrent_house_renting", "money_fen", "money"),
        ("hrent_house_renting", "start_date", "start_time"),
        ("hrent_house_renting", "end_date", "end_time"),
    ]:
        try:
            rows = q(a.db, "SELECT SUM(%s IS NOT NULL), SUM(%s IS NOT NULL AND %s IS NULL) FROM %s"
                     % (src, src, col, tbl))[0]
            nulls["%s.%s" % (tbl, col)] = {"source_rows": rows[0], "null_target": rows[1]}
        except RuntimeError as e:
            nulls["%s.%s" % (tbl, col)] = {"error": str(e)[:120]}

    print("\n老语句写入后新字段为空的统计：")
    for k, v in nulls.items():
        print("  %-34s %s" % (k, v))

    fails = [r for r in results if not r["ok"]]
    print("\n总判定：%s（%d/%d 条 INSERT 失败）"
          % ("PASS" if not fails else "FAIL", len(fails), len(results)))
    if a.json:
        os.makedirs(os.path.dirname(a.json) or ".", exist_ok=True)
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"db": a.db, "pass": not fails, "inserts": results,
                       "new_column_nulls": nulls}, f, ensure_ascii=False, indent=2)
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
