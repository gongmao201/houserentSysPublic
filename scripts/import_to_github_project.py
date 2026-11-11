# -*- coding: utf-8 -*-
"""
批量把 docs/HouseRent-v2.2_看板导入清单.csv 录入 GitHub Projects。

流程：REST 在仓库建 Issue（标签承载优先级/分组/模块）-> GraphQL 挂入 Project -> 写 Status 单选。
仅用 Python 标准库，不装第三方依赖。本项目无 Priority / Iteration 字段，优先级与分组走仓库标签。

用法（PowerShell）：
  1) 先把 Token 放进环境变量（不要写进脚本）：
       $env:GITHUB_TOKEN="ghp_xxx"
  2) 探测项目与字段选项（不建任何 Issue）：
       python scripts/import_to_github_project.py probe
  3) 干跑：只打印每条将如何映射，不写仓库：
       python scripts/import_to_github_project.py dry-run
  4) 确认无误后正式执行：
       python scripts/import_to_github_project.py apply

可选参数：
  --project-number N   指定 Project 编号（probe 会列出全部）
  --csv 路径          覆盖默认 CSV 路径
"""

import csv
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

# ---------- 配置 ----------
OWNER = "gongmao201"
REPO = "houserentSysPublic"
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CSV = ROOT / "docs" / "HouseRent-v2.2_看板导入清单.csv"

API = "https://api.github.com"
GRAPHQL = "https://api.github.com/graphql"

# 中文值 -> Status 选项名别名（小写、去空格后包含匹配）
STATUS_ALIASES = {
    "待办": ["todo", "backlog", "待办", "todos", "to do"],
    "进行中": ["in progress", "inprogress", "doing", "进行中"],
    "阻塞": ["blocked", "block", "阻塞"],
    "已取消": ["canceled", "cancelled", "取消", "已取消"],
}

# 状态选项无法补齐时的归并兜底：阻塞并入 Todo；已取消关闭 Issue、不挂项目
STATUS_FALLBACK = {"阻塞": "Todo"}
CLOSED_WHEN_NO_OPTION = {"已取消"}

# 自动建标签时的配色；其余模块标签用默认灰色
LABEL_COLORS = {
    "P0": "#d73a49",
    "P1": "#fb8500",
    "P2": "#0e8a16",
    "当前版本": "#1d76db",
    "积压": "#c5def5",
}
DEFAULT_LABEL_COLOR = "#eeeeee"


# ---------- 网络 ----------
def _headers(token):
    return {
        "Accept": "application/vnd.github+json",
        "User-Agent": "house rent project importer",
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }


def http(method, url, token, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=_headers(token), method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {e.code} {method} {url}\n{msg}")


def gql(token, query, variables=None):
    body = {"query": query}
    if variables:
        body["variables"] = variables
    res = http("POST", GRAPHQL, token, body)
    if res.get("errors"):
        raise SystemExit(f"GraphQL 错误：{json.dumps(res['errors'], ensure_ascii=False, indent=2)}")
    return res["data"]


def gql_safe(token, query, variables=None):
    """容错版：单字段 NOT_FOUND 等错误不抛异常，返回 (data, errors)。"""
    body = {"query": query}
    if variables:
        body["variables"] = variables
    res = http("POST", GRAPHQL, token, body)
    return res.get("data") or {}, res.get("errors") or []


# ---------- 数据读取 ----------
def read_csv(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def norm(s):
    return (s or "").strip().lower().replace(" ", "")


def match_option(csv_value, aliases, options):
    """options: [{"id":..., "name":...}]；返回匹配 option 或 None。"""
    keys = [norm(csv_value)] + [norm(a) for a in aliases.get(csv_value, [])]
    for opt in options:
        on = norm(opt["name"])
        if any(k and k in on for k in keys):
            return opt
    return None


# ---------- GitHub 数据 ----------
def repo_owner(token):
    """REST 取仓库归属的真实 login 与类型（User / Organization）。"""
    res = http("GET", f"{API}/repos/{OWNER}/{REPO}", token)
    return res["owner"]["login"], res["owner"]["type"]


def _projects_by_user(token, login):
    q = """
    query($l:String!){
      user(login:$l){ projectsV2(first:20){ nodes{ id number title } } }
    }"""
    data, errs = gql_safe(token, q, {"l": login})
    if data.get("user") and data["user"].get("projectsV2"):
        return data["user"]["projectsV2"]["nodes"]
    return []


def _projects_by_org(token, login):
    q = """
    query($l:String!){
      organization(login:$l){ projectsV2(first:20){ nodes{ id number title } } }
    }"""
    data, _ = gql_safe(token, q, {"l": login})
    if data.get("organization") and data["organization"].get("projectsV2"):
        return data["organization"]["projectsV2"]["nodes"]
    return []


def _projects_by_viewer(token):
    q = "query{ viewer{ login projectsV2(first:20){ nodes{ id number title } } } }"
    data, _ = gql_safe(token, q)
    if data.get("viewer") and data["viewer"].get("projectsV2"):
        return data["viewer"]["projectsV2"]["nodes"]
    return []


def list_projects(token, owner_login, owner_type):
    nodes = []
    seen = set()
    sources = [_projects_by_user(token, owner_login),
               _projects_by_org(token, owner_login),
               _projects_by_viewer(token)]
    if owner_type == "Organization":
        sources.insert(0, _projects_by_org(token, owner_login))
    for batch in sources:
        for p in batch:
            if p["id"] not in seen:
                seen.add(p["id"]); nodes.append(p)
    return nodes


def get_project_fields(token, project_id):
    q = """
    query($pid:ID!){
      node(id:$pid){
        ... on ProjectV2{
          title
          fields(first:30){ nodes{
            __typename
            ... on ProjectV2Field{ id name dataType }
            ... on ProjectV2SingleSelectField{ id name dataType options{ id name } }
            ... on ProjectV2MultiSelectField{ id name dataType }
            ... on ProjectV2IterationField{ id name dataType configuration{ iterations{ id title } } }
          } }
        }
      }
    }"""
    node = gql(token, q, {"pid": project_id})["node"]
    # 归一化：把迭代选项从 configuration 提到 iterations，供下游统一读取
    for f in node["fields"]["nodes"]:
        cfg = f.get("configuration")
        if cfg and cfg.get("iterations"):
            f["iterations"] = cfg["iterations"]
    return node


def introspect_schema(token):
    # 自省限制：单次查询 __Type.fields 最多 2 个，故拆成两次请求
    q1 = """
    query {
      union: __type(name:"ProjectV2FieldConfiguration"){ possibleTypes{ name } }
    }"""
    q2 = """
    query {
      status: __type(name:"ProjectV2StatusField"){ fields{ name } }
      iteration: __type(name:"ProjectV2IterationField"){ fields{ name } }
    }"""
    out = {}
    out["union"] = gql(token, q1).get("union")
    out["status_and_iteration"] = gql(token, q2)
    return out


def get_existing_titles(token):
    titles = set()
    page = 1
    while True:
        url = f"{API}/repos/{OWNER}/{REPO}/issues?state=all&per_page=100&page={page}"
        batch = http("GET", url, token)
        if not batch:
            break
        for it in batch:
            if "pull_request" not in it:  # 只算 Issue
                titles.add(it["title"])
        if len(batch) < 100:
            break
        page += 1
    return titles


def get_repo_labels(token):
    url = f"{API}/repos/{OWNER}/{REPO}/labels?per_page=100"
    return {lab["name"] for lab in http("GET", url, token)}


def collect_labels(row):
    """汇总一条 Issue 的标签：优先级 + 分组 + 模块，去重保序。"""
    raw = [row["优先级"], row["迭代"]] + [x for x in row["标签"].split(";") if x]
    labels, seen = [], set()
    for x in raw:
        x = (x or "").strip()
        if x and x not in seen:
            seen.add(x); labels.append(x)
    return labels


def ensure_labels(token, wanted):
    """缺失的仓库标签自动创建，返回现存标签集合。"""
    existing = get_repo_labels(token)
    for name in wanted:
        if name in existing:
            continue
        color = LABEL_COLORS.get(name, DEFAULT_LABEL_COLOR).lstrip("#")
        http("POST", f"{API}/repos/{OWNER}/{REPO}/labels", token,
             {"name": name, "color": color, "description": "项目看板导入"})
        existing.add(name)
        print(f"  新建标签：{name}")
    return existing


def create_issue(token, row, labels):
    body = (
        f"**分配（角色占位，成员入区后再指派）**：{row['分配人员']}\n\n"
        f"**分组**：{row['迭代']}　|　**优先级**：{row['优先级']}　|　**模块标签**：{row['标签']}\n\n"
        f"{row['描述']}\n"
    )
    payload = {"title": row["标题"], "body": body, "labels": labels}
    res = http("POST", f"{API}/repos/{OWNER}/{REPO}/issues", token, payload)
    return res["number"], res["node_id"], labels


def close_issue(token, number):
    """以 not_planned 关闭 Issue（对应“明确不做”的条目）。"""
    http("PATCH", f"{API}/repos/{OWNER}/{REPO}/issues/{number}", token,
         {"state": "closed", "state_reason": "not_planned"})


def add_to_project(token, project_id, content_id):
    q = """
    mutation($pid:ID!,$cid:ID!){
      addProjectV2ItemById(input:{projectId:$pid,contentId:$cid}){
        item{ id }
      }
    }"""
    res = gql(token, q, {"pid": project_id, "cid": content_id})
    return res["addProjectV2ItemById"]["item"]["id"]


def set_field(token, project_id, item_id, field_id, value):
    q = """
    mutation($pid:ID!,$iid:ID!,$fid:ID!,$value:ProjectV2FieldValue!){
      updateProjectV2ItemFieldValue(input:{projectId:$pid,itemId:$iid,fieldId:$fid,value:$value}){
        projectV2Item{ id }
      }
    }"""
    gql(token, q, {"pid": project_id, "iid": item_id, "fid": field_id, "value": value})


# ---------- 主流程 ----------
def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "dry-run"
    project_number = None
    csv_path = DEFAULT_CSV
    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--project-number":
            project_number = int(args[i + 1]); i += 2
        elif args[i] == "--csv":
            csv_path = Path(args[i + 1]); i += 2
        else:
            i += 1

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        raise SystemExit("未读取到 GITHUB_TOKEN。请先执行：$env:GITHUB_TOKEN=\"ghp_你的令牌\"")

    if mode == "introspect":
        print(json.dumps(introspect_schema(token), ensure_ascii=False, indent=2))
        return

    rows = read_csv(csv_path)
    print(f"CSV 条目数：{len(rows)}（{csv_path.name}）\n")

    global OWNER
    owner_login, owner_type = repo_owner(token)
    OWNER = owner_login
    print(f"仓库归属：{owner_login}（{owner_type}）\n")

    projects = list_projects(token, owner_login, owner_type)
    if not projects:
        raise SystemExit("未找到任何 Project，请确认 Token 有权限且归属正确。")
    print("== 可用 Project ==")
    for p in projects:
        print(f"  #{p['number']}  {p['title']}  (id={p['id']})")

    if project_number is None:
        if len(projects) == 1:
            project = projects[0]
        else:
            raise SystemExit("存在多个 Project，请用 --project-number 指定编号。")
    else:
        project = next((p for p in projects if p["number"] == project_number), None)
        if not project:
            raise SystemExit(f"找不到编号 {project_number} 的 Project。")
    print(f"\n选中 Project：#{project['number']} {project['title']}\n")

    fields = get_project_fields(token, project["id"])["fields"]["nodes"]
    fmap = {f["name"]: f for f in fields}
    print("== Project 字段 ==")
    for f in fields:
        extra = ""
        if f.get("options"):
            extra = " 选项: " + ", ".join(o["name"] for o in f["options"])
        if f.get("iterations"):
            extra = " 迭代: " + ", ".join(x["title"] for x in f["iterations"])
        print(f"  [{f['dataType']}] {f['name']}{extra}")

    status_field = next((f for f in fields if f.get("dataType") == "STATUS"), None)
    if not status_field:  # 兜底：Status 在本 schema 归为单选，按字段名识别
        status_field = next((f for f in fields if norm(f["name"]) == "status"), None)

    # 预映射预览
    print("\n== 映射预览 ==")
    plan = []
    warns = []
    options = (status_field or {}).get("options", [])
    for row in rows:
        st = match_option(row["现状"], STATUS_ALIASES, options)
        closed = False
        note = ""
        if not st and row["现状"] in STATUS_FALLBACK:
            st = match_option(STATUS_FALLBACK[row["现状"]], STATUS_ALIASES, options)
            note = f"{row['现状']}→{STATUS_FALLBACK[row['现状']]}"
        elif not st and row["现状"] in CLOSED_WHEN_NO_OPTION:
            closed = True
            note = "关闭 Issue，不挂项目"
        if not st and not closed:
            warns.append(f"状态未匹配：{row['现状']} <- {row['标题']}")
        labels = collect_labels(row)
        plan.append({"row": row, "status": st, "closed": closed, "labels": labels, "note": note})
        status_txt = "关闭(不挂项目)" if closed else (st["name"] if st else "⚠未匹配")
        if note and not closed:
            status_txt += f"（{note}）"
        print(f"  {row['优先级']:>3} | {row['现状']:<4} | 状态={status_txt} "
              f"| 标签=[{', '.join(labels)}] | {row['标题']}")

    if warns:
        print("\n== 状态未匹配告警（既无选项也无兜底）==")
        for w in warns:
            print("  ⚠ " + w)

    if mode == "probe":
        print("\nprobe 模式结束，未创建任何 Issue。")
        return

    wanted_labels = sorted({l for r in rows for l in collect_labels(r)})
    existing = get_existing_titles(token) if mode == "apply" else set()

    if mode == "dry-run":
        print("\ndry-run 结束，未写仓库。确认映射与告警后执行：python scripts/import_to_github_project.py apply")
        print("apply 时将自动补建以下缺失标签：" + ", ".join(wanted_labels))
        return

    # apply：先补齐标签，再逐条建 Issue、挂项目、写 Status
    print("\n== 检查并补齐仓库标签 ==")
    labels_ok = ensure_labels(token, wanted_labels)

    created = skipped = closed_n = 0
    for item in plan:
        row = item["row"]
        if row["标题"] in existing:
            print(f"跳过（已存在）：{row['标题']}")
            skipped += 1
            continue
        labels = [l for l in item["labels"] if l in labels_ok]
        number, node_id, labels = create_issue(token, row, labels)
        if item["closed"]:
            close_issue(token, number)
            closed_n += 1
            print(f"已建并关闭 Issue #{number}（不挂项目）：{row['标题']}  标签={labels or '无'}")
            continue
        print(f"已建 Issue #{number}：{row['标题']}  标签={labels or '无'}")
        item_id = add_to_project(token, project["id"], node_id)
        if item["status"]:
            set_field(token, project["id"], item_id, status_field["id"],
                      {"singleSelectOptionId": item["status"]["id"]})
        created += 1

    print(f"\n完成：挂项目新建 {created} 条，关闭不挂项目 {closed_n} 条，跳过已存在 {skipped} 条。")


if __name__ == "__main__":
    main()
