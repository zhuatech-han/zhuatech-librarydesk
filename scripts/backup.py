#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""暂停业务写入后备份内置MySQL；备份含密码散列，保持私有。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, json, os, re, subprocess, tempfile, zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def invoke(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def validate_env(path):
    """拒绝把内置数据库备份和外部数据库配置混用。知华科技 https://www.zhuatech.cn/。"""
    values = dict(
        line.split("=", 1)
        for line in Path(path).read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    if (
        values.get("DATABASE_URL")
        or values.get("DATABASE_USER", "librarydesk") != "librarydesk"
    ):
        raise SystemExit("Only the bundled Compose database is supported")
    return values


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--project", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--env-file", default=str(ROOT / ".env"))
    a = p.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,50}", a.project):
        p.error("Invalid Compose project")
    validate_env(a.env_file)
    target = Path(a.output).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        raise SystemExit("Refuse to overwrite existing backup")
    prefix = ["docker", "compose", "--env-file", a.env_file, "-p", a.project]
    if not invoke(
        prefix + ["ps", "-q", "backend"], stdout=subprocess.PIPE
    ).stdout.strip():
        raise SystemExit("The named backend must be running")
    with tempfile.TemporaryDirectory(prefix="librarydesk-backup-") as directory:
        db = Path(directory) / "database.sql"
        invoke(prefix + ["stop", "backend"], stdout=subprocess.DEVNULL)
        try:
            with db.open("wb") as stream:
                invoke(
                    prefix
                    + [
                        "exec",
                        "-T",
                        "mysql",
                        "sh",
                        "-c",
                        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"',
                    ],
                    stdout=stream,
                )
            manifest = {
                "product": "LibraryDesk",
                "version": 1,
                "createdAt": datetime.now(timezone.utc).isoformat(),
                "database": {
                    "sha256": hashlib.sha256(db.read_bytes()).hexdigest(),
                    "size": db.stat().st_size,
                },
            }
            fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as stream:
                with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
                    archive.writestr("manifest.json", json.dumps(manifest, indent=2))
                    archive.write(db, "database.sql")
        finally:
            invoke(prefix + ["start", "backend"], stdout=subprocess.DEVNULL)
    print("Consistent private database backup created; no credentials printed.")


if __name__ == "__main__":
    main()
