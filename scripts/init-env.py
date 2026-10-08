#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""生成独立本机凭证，拒绝覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import os
import secrets
from pathlib import Path

root = Path(__file__).resolve().parents[1]
text = (root / ".env.example").read_text()
for key in ["MYSQL_ROOT_PASSWORD", "DATABASE_PASSWORD", "ADMIN_PASSWORD"]:
    text = text.replace(key + "=\n", key + "=Aa9" + secrets.token_hex(20) + "\n")
fd = os.open(root / ".env", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as f:
    f.write(text)
print("Created private .env; use ADMIN_PASSWORD from this file. Do not commit it.")
