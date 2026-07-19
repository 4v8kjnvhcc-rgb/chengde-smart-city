from pathlib import Path
import json
import re
import subprocess
import tempfile
import os

login = Path(tempfile.gettempdir()) / "login.json"
login.write_text('{"username":"sys_admin","password":"Test@12345"}', encoding="ascii")
raw = subprocess.check_output(
    [
        "curl.exe", "-s", "-X", "POST", "http://127.0.0.1:8080/api/v1/auth/login",
        "-H", "Content-Type: application/json",
        "--data-binary", f"@{login}",
    ],
    text=True,
    encoding="utf-8",
    errors="replace",
)
m = re.search(r'"accessToken":"([^"]+)"', raw)
if not m:
    print("LOGIN_FAIL")
    raise SystemExit(1)
token = m.group(1)
print("LOGIN_OK")
out = Path(tempfile.gettempdir()) / "menus-me.json"
subprocess.check_call(
    [
        "curl.exe", "-s", "-o", str(out),
        "http://127.0.0.1:8080/api/v1/system/menus/me",
        "-H", f"Authorization: Bearer {token}",
    ]
)
# Prefer utf-8; fall back
blob = out.read_bytes()
for enc in ("utf-8", "utf-8-sig", "gbk"):
    try:
        text = blob.decode(enc)
        data = json.loads(text)
        break
    except Exception:
        data = None
else:
    print("PARSE_FAIL", blob[:200])
    raise SystemExit(1)

# Flatten paths and names
names, paths, codes = [], [], []

def walk(nodes):
    for n in nodes or []:
        names.append(n.get("menuName") or "")
        paths.append(n.get("path") or "")
        codes.append(n.get("mCode") or "")
        walk(n.get("children") or [])

root = data.get("data") if isinstance(data, dict) else data
walk(root)

checks = [
    ("身份与权限" in names, "HAS_IAM"),
    ("安全与合规" in names, "HAS_SEC"),
    ("平台运维" in names, "HAS_OPS"),
    ("集成运维" in names, "HAS_INTEG"),
    ("/system/uum" in paths, "HAS_UUM"),
    ("/system/access" in paths, "HAS_ACCESS"),
    ("/integration/ds" in paths, "HAS_DS"),
    ("/exchange/application/supply-config" in paths, "HAS_SUPPLY"),
    ("/system/users" not in paths, "NO_USERS_SIDEBAR"),
    ("/system/roles" not in paths, "NO_ROLES_SIDEBAR"),
    ("/system/exchange" not in paths, "NO_SYS_EXCHANGE"),
    ("M139A" in codes, "HAS_M139A"),
    (any(n == "统一用户管理" and c == "M139" for n, c in zip(names, codes)), "UUM_M139"),
    ("标签库" in names, "HAS_TAG_LIB"),
]
fail = 0
for ok, label in checks:
    print(("OK" if ok else "FAIL"), label)
    if not ok:
        fail += 1

# sample system subtree
print("--- system children ---")
def find(nodes, pred):
    for n in nodes or []:
        if pred(n):
            return n
        hit = find(n.get("children") or [], pred)
        if hit:
            return hit
    return None

sysn = find(root, lambda n: (n.get("path") or "") == "/system")
if sysn:
    for c in sysn.get("children") or []:
        print(c.get("menuName"), "->", [x.get("menuName") for x in (c.get("children") or [])])

print("SMOKE_PASS" if fail == 0 else f"SMOKE_FAIL count={fail}")
