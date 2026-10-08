#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""发布前真实素材、双语内容、署名、许可及敏感文件追踪检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def need(ok, label):
    if not ok:
        raise SystemExit("FAIL: " + label)


qr = {
    "wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
}
for name, digest in qr.items():
    for folder in ["docs/images", "frontend/public/brand"]:
        need(
            hashlib.sha256((ROOT / folder / name).read_bytes()).hexdigest() == digest,
            "original image " + folder + "/" + name,
        )
for filename in ["README.md", "README.en.md"]:
    text = (ROOT / filename).read_text()
    need(
        "[中文](README.md) | [English](README.en.md)" in text,
        "language switch " + filename,
    )
    need("https://www.zhuatech.cn/" in text, "official website " + filename)
    refs = re.findall(r'!\[[^\]]*\]\(([^)]+)\)|<img[^>]+src="([^"]+)"', text)
    screenshots = set()
    for group in refs:
        target = next(x for x in group if x)
        need(
            not target.startswith(("http:", "https:", "data:")), "local image " + target
        )
        file = ROOT / target
        need(file.is_file(), "image exists " + target)
        if target.startswith("docs/screenshots/"):
            need(
                file.read_bytes().startswith(b"\xff\xd8\xff")
                and file.stat().st_size > 10000,
                "real JPEG " + target,
            )
            screenshots.add(target)
    need(len(screenshots) >= 6, "six running screen classes " + filename)
    for target in re.findall(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", text):
        if not target.startswith(("https:", "http:", "mailto:", "#")):
            need((ROOT / target.split("#")[0]).is_file(), "relative link " + target)
cn, en = [(ROOT / p).read_text() for p in ["README.md", "README.en.md"]]
need("知华科技（上海如静知华信息科技有限公司）" in cn, "Chinese company")
need(
    "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)" in en,
    "English company",
)
need(all(name in cn for name in qr), "both Chinese QR references")
need(
    "wechat-" not in en and "contact-zh" not in en, "English excludes contact QR images"
)
need(
    all(
        value in en
        for value in [
            "han@zhuatech.cn",
            "jack@zhuatech.cn",
            "https://wa.me/8617521234993",
        ]
    ),
    "English contacts",
)
need(
    "非商业" in cn
    and "non-commercial" in en.lower()
    and "未经书面授权不得商用" in (ROOT / "LICENSE").read_text(),
    "consistent non-commercial license",
)
for path in [
    *ROOT.glob("backend/src/**/*.java"),
    *ROOT.glob("frontend/src/**/*.js"),
    *ROOT.glob("frontend/src/**/*.vue"),
    *ROOT.glob("scripts/*.py"),
]:
    source = path.read_text()
    need(
        "https://www.zhuatech.cn/" in source and "zhuatech2" in source,
        "source attribution " + str(path.relative_to(ROOT)),
    )
# Check real Git ignore behavior, including when the file does not exist in CI.
for name in [
    ".env",
    "output/quality-state.json",
    "private-backups/qa.zip",
    ".venv/pyvenv.cfg",
    "frontend/node_modules/test.txt",
    "backend/target/test.txt",
]:
    p = subprocess.run(["git", "check-ignore", "-q", name], cwd=ROOT)
    need(p.returncode == 0, "private/generated path ignored " + name)
tracked = (
    subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
)
for name in filter(None, tracked):
    need(
        not name.startswith(
            (
                "output/",
                "private-backups/",
                ".venv/",
                "frontend/node_modules/",
                "backend/target/",
            )
        )
        and not (Path(name).name.startswith(".env") and name != ".env.example"),
        "no sensitive/generated tracked file " + name,
    )
    p = ROOT / name
    if p.suffix in [".jpg", ".png"]:
        continue
    text = p.read_text()
    for pattern in [
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
        r"ghp_[A-Za-z0-9]{30,}",
        r"github_pat_[A-Za-z0-9_]{40,}",
        r"AKIA[0-9A-Z]{16}",
    ]:
        need(not re.search(pattern, text), "credential pattern scan " + name)
need(
    "-DskipTests" not in (ROOT / "backend/Dockerfile").read_text(),
    "Docker does not skip tests",
)
print(
    "PASS: bilingual docs, original QR bytes, screenshots, links, attribution, license and tracked-file safety checks"
)
