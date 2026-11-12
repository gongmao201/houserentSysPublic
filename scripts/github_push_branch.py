#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
通过 GitHub REST API 把本地 git 索引提交到指定仓库的新分支（无需 github.com 直连，仅用 api.github.com）。

背景：本机 hosts 把 github.com/api.github.com 指向 127.0.0.1，且 github.com:443 不可达，
只有 api.github.com 的 20.205.243.168 可连，因此走 HTTPSConnection + 指定 IP + SNI 的方式访问 API。

用法：
  set GITHUB_TOKEN=ghp_xxx
  python scripts/github_push_branch.py diff
  python scripts/github_push_branch.py apply --owner hqy-b --branch feat/redis-jwt-openapi

参数：
  --owner   目标仓库 owner（默认 hqy-b，即 fork 后的账号）
  --repo    仓库名（默认 houserentSysPublic）
  --branch  新分支名（默认 feat/redis-jwt-openapi）
  --base    基线分支（默认 main）
  --message 提交信息
  --deletions  同时删除远端存在但本地没有的文件（默认不动远端多余文件）
"""

import argparse
import base64
import http.client
import json
import os
import socket
import ssl
import subprocess
import sys
import time
from pathlib import Path

API_HOST = "api.github.com"
API_IP = os.environ.get("GITHUB_API_IP", "20.205.243.168")
ROOT = Path(__file__).resolve().parent.parent
GIT = r"C:\Program Files\Git\cmd\git.exe" if os.path.exists(r"C:\Program Files\Git\cmd\git.exe") else "git"

CTX = ssl.create_default_context()


class Api:
    def __init__(self, token):
        self.token = token

    def _conn(self):
        class Conn(http.client.HTTPSConnection):
            def __init__(self):
                super().__init__(API_HOST, 443, timeout=90)
                self._ip = API_IP

            def connect(self):
                raw = socket.create_connection((self._ip, 443), self.timeout)
                self.sock = CTX.wrap_socket(raw, server_hostname=API_HOST)

        return Conn()

    def call(self, method, path, body=None, accept="application/vnd.github+json"):
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {
            "Authorization": "Bearer " + self.token,
            "Accept": accept,
            "User-Agent": "hrent-push-script",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if data is not None:
            headers["Content-Type"] = "application/json"
        last = None
        for attempt in range(3):
            try:
                c = self._conn()
                c.request(method, path, body=data, headers=headers)
                r = c.getresponse()
                raw = r.read()
                c.close()
                if r.status in (200, 201, 204):
                    return json.loads(raw.decode("utf-8")) if raw else {}
                last = "HTTP %s %s" % (r.status, raw.decode("utf-8", "replace")[:400])
                if r.status in (429, 500, 502, 503, 504):
                    time.sleep(3 * (attempt + 1))
                    continue
                raise RuntimeError(last)
            except (socket.timeout, OSError) as e:
                last = "%s: %s" % (type(e).__name__, e)
                time.sleep(2 * (attempt + 1))
        raise RuntimeError(last)


def git(*args):
    out = subprocess.run([GIT] + list(args), cwd=str(ROOT), capture_output=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.decode("utf-8", "replace"))
    return out.stdout


def local_entries():
    """返回 {path: (mode, sha)}，取自本地索引（已按 .gitignore 过滤）"""
    entries = {}
    raw = git("-c", "core.quotepath=false", "ls-files", "-s").decode("utf-8", "replace")
    for line in raw.splitlines():
        if not line.strip():
            continue
        meta, path = line.split("\t", 1)
        mode, sha, _stage = meta.split()
        entries[path] = (mode, sha)
    return entries


def blob_bytes(sha):
    return git("cat-file", "blob", sha)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["diff", "apply"])
    ap.add_argument("--owner", default="hqy-b")
    ap.add_argument("--repo", default="houserentSysPublic")
    ap.add_argument("--branch", default="feat/redis-jwt-openapi")
    ap.add_argument("--base", default="main")
    ap.add_argument("--message", default="feat: Redis 接入、JWT 升级 jjwt 0.11.5、OpenAPI 全量 spec、Flyway baseline 验证")
    ap.add_argument("--deletions", action="store_true")
    args = ap.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        sys.exit("缺少环境变量 GITHUB_TOKEN")

    api = Api(token)
    repo_path = "/repos/%s/%s" % (args.owner, args.repo)

    ref = api.call("GET", "%s/git/ref/heads/%s" % (repo_path, args.base))
    head_sha = ref["object"]["sha"]
    commit = api.call("GET", "%s/git/commits/%s" % (repo_path, head_sha))
    base_tree = commit["tree"]["sha"]
    tree = api.call("GET", "%s/git/trees/%s?recursive=1" % (repo_path, base_tree))
    if tree.get("truncated"):
        print("警告：远端 tree 被截断，差异判断可能不完整")
    remote = {e["path"]: e["sha"] for e in tree["tree"] if e["type"] == "blob"}

    local = local_entries()
    changed = {p: v for p, v in local.items() if remote.get(p) != v[1]}
    deleted = sorted(set(remote) - set(local))

    total_bytes = 0
    for p in changed:
        total_bytes += len(blob_bytes(local[p][1]))

    print("远端基线 %s=%s" % (args.base, head_sha[:10]))
    print("本地文件 %d，远端文件 %d" % (len(local), len(remote)))
    print("需新增/更新 %d 个（%.2f MB）" % (len(changed), total_bytes / 1048576.0))
    print("远端多余文件 %d 个%s" % (len(deleted), "" if args.deletions else "（默认不删除）"))
    for p in sorted(changed)[:20]:
        print("  M", p)
    if len(changed) > 20:
        print("  ... 其余 %d 个" % (len(changed) - 20))
    for p in deleted[:10]:
        print("  D", p)

    if args.mode == "diff":
        return

    # 1) 上传 blob（内容取自本地 git 索引，已应用 .gitattributes 归一化）
    uploaded = {}
    for i, (p, (mode, sha)) in enumerate(sorted(changed.items()), 1):
        content = blob_bytes(sha)
        resp = api.call("POST", "%s/git/blobs" % repo_path, {
            "content": base64.b64encode(content).decode("ascii"),
            "encoding": "base64",
        })
        uploaded[p] = (mode, resp["sha"])
        if i % 25 == 0 or i == len(changed):
            print("  已上传 %d/%d" % (i, len(changed)))

    # 2) 组装 tree
    tree_entries = []
    for p, (mode, sha) in uploaded.items():
        tree_entries.append({"path": p, "mode": mode, "type": "blob", "sha": sha})
    if args.deletions:
        for p in deleted:
            tree_entries.append({"path": p, "mode": "100644", "type": "blob", "sha": None})

    new_tree = api.call("POST", "%s/git/trees" % repo_path, {
        "base_tree": base_tree,
        "tree": tree_entries,
    })

    # 3) 提交
    new_commit = api.call("POST", "%s/git/commits" % repo_path, {
        "message": args.message,
        "tree": new_tree["sha"],
        "parents": [head_sha],
        "author": {"name": "hqy-b", "email": "hqy-b@users.noreply.github.com"},
        "committer": {"name": "hqy-b", "email": "hqy-b@users.noreply.github.com"},
    })

    # 4) 建分支
    api.call("POST", "%s/git/refs" % repo_path, {
        "ref": "refs/heads/" + args.branch,
        "sha": new_commit["sha"],
    })

    print("完成：%s/%s 分支 %s @ %s" % (args.owner, args.repo, args.branch, new_commit["sha"]))
    print("分支地址：https://github.com/%s/%s/tree/%s" % (args.owner, args.repo, args.branch))


if __name__ == "__main__":
    main()
