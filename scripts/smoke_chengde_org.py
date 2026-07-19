# -*- coding: utf-8 -*-
import json
import re
import subprocess
import tempfile
from pathlib import Path

tmp = Path(tempfile.gettempdir())


def login(user: str, password: str) -> str:
    f = tmp / "login.json"
    f.write_text(json.dumps({"username": user, "password": password}), encoding="ascii")
    raw = subprocess.check_output(
        [
            "curl.exe", "-s", "-X", "POST", "http://127.0.0.1:8080/api/v1/auth/login",
            "-H", "Content-Type: application/json",
            "--data-binary", f"@{f}",
        ],
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    m = re.search(r'"accessToken":"([^"]+)"', raw)
    if not m:
        print("LOGIN_FAIL", user, raw[:200])
        raise SystemExit(1)
    print("LOGIN_OK", user)
    return m.group(1)


def get_json(path: str, token: str):
    out = tmp / "api.json"
    subprocess.check_call(
        [
            "curl.exe", "-s", "-o", str(out),
            f"http://127.0.0.1:8080{path}",
            "-H", f"Authorization: Bearer {token}",
        ]
    )
    return json.loads(out.read_text(encoding="utf-8"))


token = login("gongan", "Test@12345")
me = get_json("/api/v1/auth/me", token) if False else None
# some projects use /system/orgs
orgs = get_json("/api/v1/system/orgs", token)
recs = orgs.get("data") or []
codes = {o.get("orgCode") for o in recs}
print("ORG_COUNT", len(recs))
print("HAS_POLICE", "OK" if "100200" in codes else "FAIL")
print("HAS_ROOT", "OK" if "100000" in codes else "FAIL")
print("HAS_ADMIN", "OK" if "100002" in codes else "FAIL")
police = next((o for o in recs if o.get("orgCode") == "100200"), None)
print("POLICE_NAME", (police or {}).get("orgName"))

# confirm gongan org via users list as sys_admin
admin = login("sys_admin", "Test@12345")
users = get_json("/api/v1/system/users?page=1&size=200", admin)
rows = ((users.get("data") or {}).get("records")) or users.get("data") or []
if isinstance(rows, dict):
    rows = rows.get("records") or []
g = next((u for u in rows if u.get("username") == "gongan"), None)
print("GONGAN_USER", "OK" if g else "FAIL", (g or {}).get("orgName") or (g or {}).get("orgId"))
