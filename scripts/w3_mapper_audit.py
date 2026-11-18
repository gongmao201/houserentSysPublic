#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""W3 三方对账（Mapper XML ↔ 数据库 ↔ Java 类）。

对 12 个存量 mapper 做静态核对：
  A. namespace 对应 DAO 接口是否存在
  B. 接口声明（含 BaseDao 继承）的每个抽象方法，XML 里是否有同名语句（缺了运行期必炸）
  C. 语句引用的表是否存在于数据库
  D. resultMap 的 column 是否在引用表中存在，或在 SQL 里以别名形式出现
  E. resultMap 的 property（支持 a.b 嵌套路径）是否在实体类（含父类）里有对应字段
  F. resultMap / resultType / parameterType 的 Java 类型是否存在
  G. insert 语句是否漏写"非空且无默认值"的列

用法：python scripts/w3_mapper_audit.py --db hrent_w3 --json logs/w3_mapper_audit.json
"""
import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = os.path.join(ROOT, "hrent-api")
MAPPER_DIR = os.path.join(API, "src", "main", "resources", "mapper")
JAVA_DIR = os.path.join(API, "src", "main", "java")
USER = os.environ.get("DB_USERNAME", "root")
PWD = os.environ.get("DB_PASSWORD", "root")

LEGACY = [
    "common/DictDao.xml", "common/MenuDao.xml", "common/MessageDao.xml", "common/UserDao.xml",
    "hrent/ApplyRentDao.xml", "hrent/ApplyViewDao.xml", "hrent/CollectionDao.xml",
    "hrent/HouseRentingDao.xml", "hrent/HouseResourceDao.xml", "hrent/PicDao.xml",
    "hrent/RentDao.xml", "hrent/TagDao.xml",
]


def q(db, sql):
    p = subprocess.run([MYSQL, "-u" + USER, "-p" + PWD, "-N", "-B",
                        "--default-character-set=utf8mb4", "-e", sql],
                       capture_output=True, text=True, encoding="utf-8")
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    return [line.split("\t") for line in p.stdout.strip().splitlines() if line]


def load_schema(db):
    cols = {}
    for t, c, nullable, default, extra in q(db, (
            "SELECT table_name,column_name,is_nullable,IFNULL(column_default,'<NULL>'),extra "
            "FROM information_schema.columns WHERE table_schema='%s'" % db)):
        cols.setdefault(t, {})[c] = {"nullable": nullable, "default": default, "extra": extra}
    return cols


def build_class_index():
    idx = {}
    for dirpath, _, files in os.walk(JAVA_DIR):
        for fn in files:
            if fn.endswith(".java"):
                fqn = os.path.relpath(os.path.join(dirpath, fn), JAVA_DIR)[:-5].replace(os.sep, ".")
                idx.setdefault(fn[:-5], fqn)
    return idx


CLASS_INDEX = {}


def java_file(fqn):
    return os.path.join(JAVA_DIR, fqn.replace(".", os.sep) + ".java")


def src_of(fqn):
    p = java_file(fqn)
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8") as f:
        return f.read()


FIELDS_CACHE = {}


def fields_of(fqn):
    if fqn in FIELDS_CACHE:
        return FIELDS_CACHE[fqn]
    src = src_of(fqn)
    if src is None:
        return set()
    fields = set(re.findall(r"(?:private|protected|public)\s+[\w<>\[\],\s.?]+\s+(\w+)\s*[;=]", src))
    m = re.search(r"extends\s+([\w.]+)", src)
    if m:
        parent = m.group(1)
        if "." not in parent:
            parent = CLASS_INDEX.get(parent)
        if parent:
            fields |= fields_of(parent)
    FIELDS_CACHE[fqn] = fields
    return fields


def method_names(fqn):
    src = src_of(fqn)
    if src is None:
        return None
    methods = set(re.findall(r"[\w<>\[\],\s.?]+\s+(\w+)\s*\([^;{]*\)\s*;", src))
    m = re.search(r"extends\s+([\w.]+)", src)
    if m:
        parent = m.group(1)
        if "." not in parent:
            parent = CLASS_INDEX.get(parent)
        if parent:
            methods |= method_names(parent) or set()
    return methods


TABLE_RE = re.compile(r"\b(?:from|join|update|into)\s+([A-Za-z_][\w]*)", re.I)
INSERT_RE = re.compile(r"insert\s+into\s+([A-Za-z_][\w]*)\s*\(([^)]*)\)", re.I)
ALIAS_RE = re.compile(r"\bas\s+[\"'`]?([A-Za-z_][\w]*)", re.I)


def audit(schema, name):
    path = os.path.join(MAPPER_DIR, name.replace("/", os.sep))
    issues, notes = [], []
    info = {"mapper": name, "namespace": "", "statements": 0, "tables": [],
            "issues": issues, "notes": notes}
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        issues.append("XML 解析失败：%s" % e)
        return info
    root = tree.getroot()
    ns = root.get("namespace") or ""
    info["namespace"] = ns
    iface = method_names(ns) if ns else None
    if iface is None:
        issues.append("namespace 对应接口不存在：%s" % ns)

    sql_ids = {e.get("id") for e in root.iter("sql")}
    stmts = [e for e in root if e.tag in ("select", "insert", "update", "delete")]
    info["statements"] = len(stmts)
    stmt_ids = {e.get("id") for e in stmts}
    stmt_sql = {e.get("id"): " ".join(t.strip() for t in e.itertext()) for e in stmts}

    if iface:
        missing = sorted(m for m in iface if m not in stmt_ids)
        if missing:
            issues.append("接口方法没有对应语句（运行期会 BindingException）：%s" % ",".join(missing))
        extra = sorted(i for i in stmt_ids if i not in iface)
        if extra:
            notes.append("XML 有语句但接口未声明（死代码，不影响运行）：%s" % ",".join(extra))

    tables = set()
    for e in stmts:
        sql = stmt_sql[e.get("id")]
        for t in TABLE_RE.findall(sql):
            if t.lower() != "dual":
                tables.add(t)
        for ref in re.findall(r'<include[^>]*refid="([^"]+)"', ET.tostring(e, encoding="unicode")):
            if ref not in sql_ids:
                issues.append("语句 %s 引用了未定义的 sql 片段 %s" % (e.get("id"), ref))
        it = INSERT_RE.search(sql)
        if it and e.tag == "insert":
            tbl, raw_cols = it.group(1), it.group(2)
            tbl_cols = schema.get(tbl, {})
            if not tbl_cols:
                issues.append("insert(%s) 目标表不存在：%s" % (e.get("id"), tbl))
            else:
                given = {c.strip().strip("`") for c in raw_cols.split(",") if c.strip()}
                missing = [c for c, m in tbl_cols.items()
                           if m["nullable"] == "NO" and m["default"] == "<NULL>"
                           and m["extra"] != "auto_increment" and c not in given]
                if missing:
                    issues.append("insert(%s) 漏写非空无默认列：%s" % (e.get("id"), ",".join(missing)))
    info["tables"] = sorted(tables)
    for t in sorted(tables):
        if t not in schema:
            issues.append("引用了不存在的表：%s" % t)

    all_sql = " ".join(stmt_sql.values())
    aliases = set(ALIAS_RE.findall(all_sql))

    for rm in root.iter("resultMap"):
        rtype = rm.get("type")
        if not rtype:
            continue
        if not os.path.exists(java_file(rtype)):
            issues.append("resultMap %s 的 type 不存在：%s" % (rm.get("id"), rtype))
            continue
        fields = fields_of(rtype)
        for tag in list(rm):
            if tag.tag not in ("id", "result"):
                continue
            col, prop = tag.get("column"), tag.get("property")
            if col and tables and col not in aliases \
                    and not any(col in schema.get(t, {}) for t in tables):
                issues.append("resultMap %s 列 %s 既不在表里也不是 SQL 别名" % (rm.get("id"), col))
            if prop:
                head = prop.split(".")[0]
                if head not in fields:
                    issues.append("resultMap %s 属性 %s 不在类 %s（含父类）中"
                                  % (rm.get("id"), prop, rtype.rsplit(".", 1)[-1]))
    for e in root.iter():
        for attr in ("resultType", "parameterType"):
            fqn = e.get(attr)
            if fqn and "." in fqn and not fqn.startswith("java.") \
                    and not os.path.exists(java_file(fqn)):
                issues.append("%s=%s 对应的 Java 类不存在" % (attr, fqn))
    return info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default="hrent_w3")
    ap.add_argument("--json", default="")
    a = ap.parse_args()
    global CLASS_INDEX
    CLASS_INDEX = build_class_index()
    schema = load_schema(a.db)
    results = [audit(schema, m) for m in LEGACY]
    bad = 0
    for r in results:
        status = "PASS" if not r["issues"] else "FAIL"
        if r["issues"]:
            bad += 1
        print("%-26s %-4s 语句%2d 表:%s" % (r["mapper"], status, r["statements"], ",".join(r["tables"])))
        for i in r["issues"]:
            print("    [FAIL] " + i)
        for n in r["notes"]:
            print("    [INFO] " + n)
    print("\n总判定：%s（%d/12 个 mapper 有问题）" % ("PASS" if bad == 0 else "FAIL", bad))
    if a.json:
        os.makedirs(os.path.dirname(a.json) or ".", exist_ok=True)
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"db": a.db, "pass": bad == 0, "mappers": results}, f, ensure_ascii=False, indent=2)
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
