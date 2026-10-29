"""生成 app/src/main/assets/subjects.json（含拼音全拼与首字母，用于检索）。
用法：pip install pypinyin && python3 tools/gen_data.py
"""
import json
import os
import re

from pypinyin import Style, lazy_pinyin

from subjects import S

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "app", "src", "main", "assets", "subjects.json")

for s in S:
    han = re.sub(r"（.*?）", "", s["name"])
    s["py"] = "".join(lazy_pinyin(han))
    s["pyi"] = "".join(lazy_pinyin(han, style=Style.FIRST_LETTER))

os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    json.dump(S, f, ensure_ascii=False, separators=(",", ":"))
print(f"wrote {len(S)} subjects -> {os.path.normpath(out)}")
