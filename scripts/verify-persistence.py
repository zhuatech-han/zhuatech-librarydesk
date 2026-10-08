#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅核对指定本机测试实例，保留私有凭证与历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, json, os
from pathlib import Path
from urllib.parse import urlparse
from quality import ROOT, Session, check


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True)
    p.add_argument("--state", default=str(ROOT / "output/quality-state.json"))
    p.add_argument("--refresh-snapshot", action="store_true")
    a = p.parse_args()
    if urlparse(a.base).hostname not in ["127.0.0.1", "localhost", "::1"]:
        p.error("Only disposable localhost instances")
    path = Path(a.state)
    state = json.loads(path.read_text())
    s = Session(a.base)
    s.login("test-librarian", state["passwords"]["test-librarian"])
    ids = [x["book"]["id"] for x in s.call("GET", "/books?size=100")["items"]]
    actual = {str(b): s.call("GET", f"/books/{b}") for b in ids}
    audit = s.call("GET", "/audit")
    records = {
        "patrons": s.call("GET", "/patrons"),
        "loans": s.call("GET", "/records/loans?size=100")["items"],
        "holds": s.call("GET", "/records/holds?size=100")["items"],
    }
    digest = hashlib.sha256(
        s.call("GET", "/reports/export", raw=True).content
    ).hexdigest()
    if a.refresh_snapshot:
        state.update(records)
        state.update(
            {
                "bookIds": ids,
                "snapshots": actual,
                "auditSnapshot": audit,
                "csvSha": digest,
            }
        )
        path.write_text(json.dumps(state, ensure_ascii=False, indent=2))
        os.chmod(path, 0o600)
        print("Private post-UI snapshots updated; no credentials printed.")
        return
    check(set(ids) == set(state["bookIds"]), "same persisted catalog IDs")
    count = 1
    for b, data in actual.items():
        for key in ["book", "copies", "available", "waiting", "holds", "events"]:
            check(data[key] == state["snapshots"][b][key], f"Persisted {key}")
            count += len(data[key]) if isinstance(data[key], list) else 1
    for key, value in records.items():
        check(value == state[key], f"Persisted {key}")
        count += len(value)
    check(digest == state["csvSha"], "same CSV bytes")
    count += 1
    audits = {x["id"]: x for x in audit}
    for old in state["auditSnapshot"]:
        check(audits.get(old["id"]) == old, "immutable older audit")
        count += 1
    print(
        f"PASS: {count} persisted record/collection/hash checks after restart or restore."
    )


if __name__ == "__main__":
    main()
