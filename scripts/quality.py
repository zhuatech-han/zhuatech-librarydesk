#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅在全新本机测试实例建立TEST数据并验证真实流程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, json, os, secrets, time
from pathlib import Path
from urllib.parse import urlparse
import requests

ROOT = Path(__file__).resolve().parents[1]
COUNT = 0


def check(ok, label):
    global COUNT
    if not ok:
        raise AssertionError(label)
    COUNT += 1


class Session:
    """真实同源HTTP会话，读取CSRF并禁止自动重放写请求。知华科技 https://www.zhuatech.cn/。"""

    def __init__(self, base):
        self.base = base
        self.session = requests.Session()

    def call(self, method, path, body=None, status=200, code=None, raw=False):
        headers = {}
        if method not in ["GET", "HEAD"]:
            csrf = self.session.get(self.base + "/api/auth/csrf", timeout=20)
            check(csrf.status_code == 200, "CSRF bootstrap")
            data = csrf.json()
            headers[data["header"]] = data["token"]
        response = self.session.request(
            method, self.base + "/api" + path, json=body, headers=headers, timeout=30
        )
        check(
            response.status_code == status,
            f"{method} {path}: expected {status}, got {response.status_code}; {response.text[:160] if status != 200 else ''}",
        )
        if code:
            check(response.json().get("code") == code, "expected business error")
        return response if raw else response.json()

    def login(self, username, password):
        return self.call(
            "POST", "/auth/login", {"username": username, "password": password}
        )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="http://127.0.0.1:8127")
    p.add_argument("--env-file", default=str(ROOT / ".env"))
    p.add_argument("--state", default=str(ROOT / "output/quality-state.json"))
    a = p.parse_args()
    if urlparse(a.base).hostname not in ["127.0.0.1", "localhost", "::1"]:
        p.error("QA requires a disposable localhost instance")
    env = dict(
        line.split("=", 1)
        for line in Path(a.env_file).read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    admin = Session(a.base)
    check(
        requests.get(a.base + "/actuator/health", timeout=20).json()["status"] == "UP",
        "health",
    )
    admin.call("GET", "/books", status=401, code="UNAUTHENTICATED")
    check(
        requests.post(a.base + "/api/books", json={}, timeout=20).status_code == 403,
        "CSRF required",
    )
    profile = admin.login(env["ADMIN_USERNAME"], env["ADMIN_PASSWORD"])
    check(profile["scope"] == "ALL", "bootstrap administrator")
    check(len(profile["menus"]) >= 9, "enabled initial navigation")
    check(
        admin.call("GET", "/books")["total"] == 0, "Refuse QA unless catalog is empty"
    )
    users = admin.call("GET", "/admin/users")
    check(len(users) == 1 and "passwordHash" not in users[0], "private bootstrap")
    admin.call(
        "PUT",
        "/admin/users/1",
        {**users[0], "enabled": False, "password": ""},
        409,
        "LAST_ADMIN",
    )
    role = admin.call("GET", "/admin/roles")[0]
    admin.call(
        "PUT", "/admin/roles/1", {**role, "permissions": ["catalog"]}, 409, "LAST_ADMIN"
    )
    d = admin.call("GET", "/admin/departments")[0]
    admin.call(
        "PUT", "/admin/departments/1", {**d, "enabled": False}, 409, "LAST_ADMIN"
    )
    otherdept = admin.call(
        "POST",
        "/admin/departments",
        {"name": "TEST 分馆", "zone": "Europe/Berlin", "enabled": True},
    )
    passwords = {}
    accounts = {}
    sessions = {}
    for user, name, rid, did in [
        ("test-librarian", "TEST 馆员", 2, 1),
        ("test-reader", "TEST 读者一", 3, 1),
        ("test-second", "TEST 读者二", 3, 1),
        ("test-third", "TEST 读者三", 3, 1),
        ("test-other", "TEST 分馆馆员", 2, otherdept["id"]),
        ("test-viewer", "TEST 统计查看员", 4, 1),
        ("test-unregistered", "TEST 未开证读者", 3, 1),
    ]:
        passwords[user] = "Aa9" + secrets.token_hex(16)
        accounts[user] = admin.call(
            "POST",
            "/admin/users",
            {
                "username": user,
                "displayName": name,
                "roleId": rid,
                "departmentId": did,
                "enabled": True,
                "password": passwords[user],
            },
        )
        sessions[user] = Session(a.base)
        sessions[user].login(user, passwords[user])
    staff = sessions["test-librarian"]
    reader = sessions["test-reader"]
    second = sessions["test-second"]
    third = sessions["test-third"]
    other = sessions["test-other"]
    viewer = sessions["test-viewer"]
    cards = {}
    for index, user in enumerate(["test-reader", "test-second", "test-third"]):
        cards[user] = staff.call(
            "POST",
            "/patrons",
            {
                "accountId": accounts[user]["id"],
                "cardNo": f"P-00{index+1}",
                "active": True,
                "note": "TEST 自测借阅证",
            },
        )
    check(
        reader.call("GET", "/my")["patron"]["id"] == cards["test-reader"]["id"],
        "own patron identity",
    )
    reader.call("GET", "/patrons", status=403, code="FORBIDDEN")
    staff.call("GET", "/admin/users", status=403, code="FORBIDDEN")
    check(
        "accounts" not in reader.call("GET", "/options"),
        "reader options hide other accounts",
    )
    books = []
    copies = []
    titles = [
        ("TEST 图书：木兰诗", "佚名", "GENERAL"),
        ("TEST 图书：论语", "孔子及弟子", "GENERAL"),
        ("TEST 图书：自然观察手册", "测试编者", "SCIENCE"),
        ("TEST 图书：社区阅读指南", "测试编者", "GENERAL"),
        ("TEST 图书：软件工程学习记录", "测试编者", "SCIENCE"),
        ("TEST 图书：独立书店运营笔记", "测试编者", "BUSINESS"),
    ]
    for i, (title, author, category) in enumerate(titles):
        b = staff.call(
            "POST",
            "/books",
            {
                "departmentId": 1,
                "title": title,
                "author": author,
                "isbn": "",
                "category": category,
                "language": "zh-CN",
                "description": "TEST 验收书目，仅用于流程验证。",
                "active": True,
            },
        )
        books.append(b)
        copies.append(
            staff.call(
                "POST",
                f"/books/{b['id']}/copies",
                {
                    "barcode": f"LIB-00{i+1}",
                    "shelf": f"A-{i+1:02d}",
                    "note": "TEST 单册",
                },
            )
        )
    b, c = books[0], copies[0]
    bid = b["id"]
    cid = c["id"]
    pid = cards["test-reader"]["id"]
    sid = cards["test-second"]["id"]
    tid = cards["test-third"]["id"]
    check(
        staff.call("GET", "/books?size=1&page=2&sort=title")["items"][0]["book"]["id"]
        != staff.call("GET", "/books?size=1&page=1&sort=title")["items"][0]["book"][
            "id"
        ],
        "server pagination",
    )
    check(reader.call("GET", "/books?q=木兰")["total"] == 1, "Chinese search")
    staff.call("GET", "/books?sort=untrusted", status=400, code="INVALID_INPUT")
    staff.call(
        "POST",
        f"/books/{bid}/copies",
        {"barcode": "lib-001", "shelf": "", "note": ""},
        409,
        "BARCODE_DUPLICATE",
    )
    staff.call(
        "POST",
        "/books",
        {**b, "title": "INVALID", "isbn": "9780000000000"},
        400,
        "ISBN_INVALID",
    )
    other.call("GET", f"/books/{bid}", status=403, code="OUT_OF_SCOPE")
    other.call("GET", "/lookup?barcode=LIB-001", status=403, code="OUT_OF_SCOPE")
    reader.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": pid, "version": c["version"]},
        403,
        "FORBIDDEN",
    )
    viewer.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": pid, "version": c["version"]},
        403,
        "FORBIDDEN",
    )
    reader.call("POST", "/holds", {"bookId": bid, "patronId": sid}, 403, "OUT_OF_SCOPE")
    sessions["test-unregistered"].call(
        "POST", "/holds", {"bookId": bid}, 409, "PATRON_NOT_REGISTERED"
    )
    loan = staff.call(
        "POST", "/checkout", {"copyId": cid, "patronId": pid, "version": c["version"]}
    )

    def lookup(code="LIB-001"):
        return staff.call("GET", "/lookup?barcode=" + code)

    def checkin(l, condition="AVAILABLE", note=""):
        return staff.call(
            "POST",
            f"/loans/{l['id']}/return",
            {"version": l["version"], "condition": condition, "note": note},
        )

    check(lookup()["copy"]["status"] == "ON_LOAN", "copy marked on loan")
    check(
        len(reader.call("GET", "/records/loans?mine=true")["items"]) == 1,
        "own loan visible",
    )
    check(
        second.call("GET", "/records/loans?mine=true")["total"] == 0,
        "other patron has no private loan",
    )
    check(
        other.call("GET", "/records/loans")["total"] == 0, "other library has no loan"
    )
    staff.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": sid, "version": lookup()["copy"]["version"]},
        409,
        "COPY_BUSY",
    )
    cnow = lookup()["copy"]
    staff.call(
        "PUT", f"/copies/{cid}", {**cnow, "status": "AVAILABLE"}, 409, "COPY_BUSY"
    )
    hold2 = second.call("POST", "/holds", {"bookId": bid})
    hold3 = third.call("POST", "/holds", {"bookId": bid})
    check(hold2["status"] == hold3["status"] == "WAITING", "holds wait while on loan")
    reader.call(
        "POST",
        f"/loans/{loan['id']}/renew",
        {"version": loan["version"]},
        409,
        "HOLD_WAITING",
    )
    reader.call(
        "POST",
        f"/holds/{hold2['id']}/cancel",
        {"version": hold2["version"], "reason": "TEST 不应生效"},
        403,
        "OUT_OF_SCOPE",
    )
    checkin(loan)
    check(lookup()["hold"]["hold"]["patronId"] == sid, "first holder allocated copy")
    staff.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": pid, "version": lookup()["copy"]["version"]},
        409,
        "HOLD_RESERVED",
    )
    readyhold = lookup()["hold"]["hold"]
    second.call(
        "POST",
        f"/holds/{readyhold['id']}/cancel",
        {"version": readyhold["version"], "reason": "TEST 主动取消"},
    )
    check(lookup()["hold"]["hold"]["patronId"] == tid, "cancel promotes next reader")
    cnow = lookup()["copy"]
    loan3 = staff.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": tid, "version": cnow["version"]},
    )
    check(
        third.call("GET", "/records/holds?mine=true")["items"][0]["hold"]["status"]
        == "FULFILLED",
        "pickup fulfils hold",
    )
    renew = third.call(
        "POST", f"/loans/{loan3['id']}/renew", {"version": loan3["version"]}
    )
    check(
        renew["renewals"] == 1 and renew["dueDate"] > loan3["dueDate"],
        "renew extends due date",
    )
    third.call(
        "POST",
        f"/loans/{loan3['id']}/renew",
        {"version": loan3["version"]},
        409,
        "VERSION_CONFLICT",
    )
    checkin(renew, "DAMAGED", "TEST 封面破损")
    check(lookup()["copy"]["status"] == "DAMAGED", "damaged return")
    hold = reader.call("POST", "/holds", {"bookId": bid})
    check(hold["status"] == "WAITING", "damaged copy not assigned")
    cnow = lookup()["copy"]
    staff.call(
        "PUT",
        f"/copies/{cid}",
        {**cnow, "status": "AVAILABLE", "note": "TEST 修复后可借"},
    )
    staff.call("POST", "/holds/process", {})
    check(
        lookup()["hold"]["hold"]["patronId"] == pid,
        "repaired copy assigned on processing",
    )
    cnow = lookup()["copy"]
    again = staff.call(
        "POST",
        "/checkout",
        {"copyId": cid, "patronId": pid, "version": cnow["version"]},
    )
    staff.call(
        "POST",
        f"/loans/{again['id']}/return",
        {"version": again["version"], "condition": "LOST", "note": ""},
        400,
        "INVALID_INPUT",
    )
    checkin(again, "LOST", "TEST 读者报告遗失")
    check(lookup()["copy"]["status"] == "LOST", "lost copy and closed loan")
    staff.call(
        "DELETE",
        f"/copies/{cid}?version={lookup()['copy']['version']}",
        status=409,
        code="RECORD_REFERENCED",
    )
    # A separate title leaves a valid on-loan record for the actual reader and report screenshots.
    c2 = copies[1]
    loan2 = staff.call(
        "POST",
        "/checkout",
        {"copyId": c2["id"], "patronId": sid, "version": c2["version"]},
    )
    reader.call("POST", "/holds", {"bookId": books[1]["id"]})
    # Ready pickup is a distinct record, not fabricated by the screenshot layer.
    second.call("POST", "/holds", {"bookId": books[2]["id"]})
    staff.call(
        "PUT",
        f"/books/{books[2]['id']}",
        {**books[2], "active": False},
        409,
        "ACTIVE_HOLDS",
    )
    # Archive an unused title and ensure reader filtering.
    archived = staff.call(
        "PUT", f"/books/{books[5]['id']}", {**books[5], "active": False}
    )
    check(reader.call("GET", "/books")["total"] == 5, "archived title hidden")
    reader.call("GET", f"/books/{archived['id']}", status=404, code="NOT_FOUND")
    viewer.call("GET", "/reports")
    reader.call("GET", "/reports", status=403, code="FORBIDDEN")
    csv = staff.call("GET", "/reports/export", raw=True)
    check(
        csv.headers["Content-Type"].startswith("text/csv") and "LIB-002" in csv.text,
        "UTF-8 CSV export",
    )
    check(
        all(pw not in csv.text for pw in passwords.values()),
        "CSV contains no passwords",
    )
    check(
        "LIB-002" not in other.call("GET", "/reports/export", raw=True).text,
        "CSV cross-library guard",
    )
    reader.call("GET", "/reports/export", status=403, code="FORBIDDEN")
    # Last administrator, account identity and persistence must remain intact.
    auser = accounts["test-reader"]
    admin.call(
        "PUT",
        f"/admin/users/{auser['id']}",
        {**auser, "departmentId": otherdept["id"], "password": ""},
        409,
        "ACCOUNT_ASSIGNED",
    )
    check(len(staff.call("GET", "/audit")) > 25, "real audit events persisted")
    # Use parallel genuine HTTP sessions for a duplicate-checkout race.
    from concurrent.futures import ThreadPoolExecutor

    racecopy = copies[3]

    # Status is read directly; no business request is automatically replayed.
    def contender(patron):
        s = Session(a.base)
        s.login("test-librarian", passwords["test-librarian"])
        token = s.session.get(a.base + "/api/auth/csrf", timeout=20).json()
        return s.session.post(
            a.base + "/api/checkout",
            json={
                "copyId": racecopy["id"],
                "patronId": patron,
                "version": racecopy["version"],
            },
            headers={token["header"]: token["token"]},
            timeout=30,
        ).status_code

    with ThreadPoolExecutor(max_workers=2) as pool:
        statuses = sorted(pool.map(contender, [pid, sid]))
    check(statuses == [200, 409], "concurrent checkout single winner")
    check(lookup("LIB-004")["copy"]["status"] == "ON_LOAN", "race state consistent")
    # Full CRUD on unused records without erasing circulation history.
    disposable = staff.call(
        "POST",
        "/books",
        {
            "departmentId": 1,
            "title": "TEST 待删除空书目",
            "author": "TEST",
            "isbn": "",
            "category": "GENERAL",
            "language": "en",
            "description": "",
            "active": True,
        },
    )
    dc = staff.call(
        "POST",
        f"/books/{disposable['id']}/copies",
        {"barcode": "DELETE-001", "shelf": "", "note": ""},
    )
    staff.call("DELETE", f"/copies/{dc['id']}?version={dc['version']}")
    staff.call("DELETE", f"/books/{disposable['id']}?version={disposable['version']}")
    staff.call("GET", f"/books/{disposable['id']}", status=404, code="NOT_FOUND")
    for setting in admin.call("GET", "/admin/settings"):
        if setting["code"] == "max_renewals":
            admin.call(
                "PUT",
                f"/admin/settings/{setting['id']}",
                {**setting, "value": "21"},
                400,
                "INVALID_INPUT",
            )
    state = {
        "base": a.base,
        "passwords": passwords,
        "accounts": accounts,
        "bookIds": [b["id"] for b in books],
        "snapshots": {
            str(b["id"]): staff.call("GET", f"/books/{b['id']}") for b in books
        },
        "patrons": staff.call("GET", "/patrons"),
        "loans": staff.call("GET", "/records/loans?size=100")["items"],
        "holds": staff.call("GET", "/records/holds?size=100")["items"],
        "auditSnapshot": staff.call("GET", "/audit"),
        "csvSha": hashlib.sha256(
            staff.call("GET", "/reports/export", raw=True).content
        ).hexdigest(),
        "checks": COUNT,
    }
    target = Path(a.state)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    os.chmod(target, 0o600)
    print(
        f"PASS: {COUNT} genuine HTTP assertions. Private QA state saved; no credentials printed."
    )


if __name__ == "__main__":
    main()
