#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""W3 三方对账（老字段 / 新字段 / 抽样）。

用法：
    python scripts/w3_reconcile.py --db hrent_w3
    python scripts/w3_reconcile.py --db hrent_w3 --json logs/w3_reconcile.json

判定：任何一项 mismatches > 0 则退出码 1。
接口侧（第三方的"接口"）由 scripts/w3_smoke.py 抽样比对，见 docs/27 报告。
"""
import argparse
import json
import os
import subprocess
import sys

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
USER = os.environ.get("DB_USERNAME", "root")
PWD = os.environ.get("DB_PASSWORD", "root")


def q(db, sql):
    p = subprocess.run(
        [MYSQL, "-u" + USER, "-p" + PWD, "-N", "-B", "--default-character-set=utf8mb4",
         "-e", "USE `%s`; %s" % (db, sql)],
        capture_output=True, text=True, encoding="utf-8",
    )
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    rows = [line.split("\t") for line in p.stdout.strip().splitlines() if line]
    return rows


def i(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default="hrent_w3")
    ap.add_argument("--json", default="")
    a = ap.parse_args()

    checks = []

    def add(name, source, target, rows, mismatches, detail=""):
        checks.append({
            "name": name, "source": source, "target": target,
            "rows": rows, "mismatches": mismatches, "detail": detail,
            "pass": mismatches == 0,
        })

    # ---------- 金额：房源租金 ----------
    r = q(a.db, """
        SELECT COUNT(*),
               SUM(money IS NOT NULL),
               SUM(money IS NOT NULL AND money_fen IS NULL),
               SUM(ABS(ROUND(money * 100) - money_fen) > 0),
               COALESCE(SUM(ROUND(money * 100)), 0) - COALESCE(SUM(money_fen), 0)
        FROM hrent_house_resource
    """)[0]
    add("房源租金 money → money_fen", "hrent_house_resource.money",
        "hrent_house_resource.money_fen", i(r[1]), i(r[2]) + i(r[3]),
        "总差(分)=%s；抽样100行最大差=%s" % (r[4], q(a.db,
            "SELECT COALESCE(MAX(ABS(ROUND(money*100)-money_fen)),0) FROM ("
            "SELECT money, money_fen FROM hrent_house_resource WHERE money IS NOT NULL LIMIT 100) t")[0][0]))

    # ---------- 金额：押金 ----------
    r = q(a.db, """
        SELECT COUNT(*),
               SUM(deposit IS NOT NULL),
               SUM(deposit IS NOT NULL AND deposit_fen IS NULL),
               SUM(ABS(ROUND(deposit * 100) - deposit_fen) > 0)
        FROM hrent_apply_rent
    """)[0]
    add("押金 deposit → deposit_fen", "hrent_apply_rent.deposit",
        "hrent_apply_rent.deposit_fen", i(r[1]), i(r[2]) + i(r[3]))

    # ---------- 金额：租约租金 ----------
    r = q(a.db, """
        SELECT SUM(money IS NOT NULL),
               SUM(money IS NOT NULL AND money_fen IS NULL),
               SUM(ABS(ROUND(money * 100) - money_fen) > 0)
        FROM hrent_house_renting
    """)[0]
    add("租约租金 money → money_fen", "hrent_house_renting.money",
        "hrent_house_renting.money_fen", i(r[0]), i(r[1]) + i(r[2]))

    # ---------- 金额：账单（V5 从租约回填） ----------
    r = q(a.db, """
        SELECT COUNT(*),
               SUM(r.amount_fen IS NOT NULL),
               SUM(r.amount_fen IS NOT NULL AND g.money_fen IS NOT NULL
                   AND ABS(r.amount_fen - g.money_fen) > 0)
        FROM hrent_rent r JOIN hrent_house_renting g ON g.id = r.renting_id
    """)[0]
    add("账单 amount_fen ← 租约 money_fen", "hrent_house_renting.money_fen",
        "hrent_rent.amount_fen", i(r[1]), i(r[2]))

    # ---------- 日期：VARCHAR → DATE ----------
    r = q(a.db, """
        SELECT SUM(start_time IS NOT NULL),
               SUM(start_time IS NOT NULL AND start_date IS NULL),
               SUM(ABS(DATEDIFF(start_date, DATE(COALESCE(
                     STR_TO_DATE(start_time,'%Y-%m-%d %H:%i:%s'),
                     STR_TO_DATE(start_time,'%Y-%m-%d'),
                     STR_TO_DATE(start_time,'%Y/%m/%d'))))) > 0),
               SUM(end_time IS NOT NULL),
               SUM(end_time IS NOT NULL AND end_date IS NULL),
               SUM(ABS(DATEDIFF(end_date, DATE(COALESCE(
                     STR_TO_DATE(end_time,'%Y-%m-%d %H:%i:%s'),
                     STR_TO_DATE(end_time,'%Y-%m-%d'),
                     STR_TO_DATE(end_time,'%Y/%m/%d'))))) > 0)
        FROM hrent_house_renting
    """)[0]
    add("起租日 start_time → start_date", "hrent_house_renting.start_time",
        "hrent_house_renting.start_date", i(r[0]), i(r[1]) + i(r[2]))
    add("到期日 end_time → end_date", "hrent_house_renting.end_time",
        "hrent_house_renting.end_date", i(r[3]), i(r[4]) + i(r[5]))

    # ---------- 角色：单值 INT → user_role ----------
    r = q(a.db, """
        SELECT COUNT(*), SUM(r.id IS NULL)
        FROM sys_user u LEFT JOIN user_role r ON r.user_id = u.id
        WHERE u.del_flag = 0
    """)[0]
    add("角色 sys_user.role → user_role", "sys_user.role", "user_role.user_id",
        i(r[0]), i(r[1]))

    r = q(a.db, """
        SELECT COUNT(*), SUM(NOT (
            (u.role = 0 AND r.role = 'TENANT') OR
            (u.role = 1 AND r.role = 'LANDLORD') OR
            (u.role = 2 AND r.role = 'ADMIN')))
        FROM sys_user u JOIN user_role r ON r.user_id = u.id
        WHERE u.del_flag = 0
    """)[0]
    add("角色取值映射 0/1/2 → TENANT/LANDLORD/ADMIN", "sys_user.role",
        "user_role.role", i(r[0]), i(r[1]))

    bad = sum(c["mismatches"] for c in checks if not c["pass"])
    width = max(len(c["name"]) for c in checks)
    print("数据库：%s" % a.db)
    print("%-*s | %-28s | %-30s | %6s | %6s | %s" %
          (width, "检查项", "源", "目标", "行数", "不一致", "结论"))
    for c in checks:
        print("%-*s | %-28s | %-30s | %6d | %6d | %s" %
              (width, c["name"], c["source"], c["target"], c["rows"], c["mismatches"],
               "PASS" if c["pass"] else "FAIL"))
        if c["detail"]:
            print("    " + c["detail"])
    print("\n总判定：%s（不一致项 %d）" % ("PASS" if bad == 0 else "FAIL",
                                        sum(1 for c in checks if not c["pass"])))

    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"db": a.db, "pass": bad == 0, "checks": checks}, f,
                      ensure_ascii=False, indent=2)
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
