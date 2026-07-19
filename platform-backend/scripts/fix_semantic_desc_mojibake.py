# -*- coding: utf-8 -*-
"""Fix UTF-8→CP1252/latin1 mojibake in ing_data_column.semantic_desc / column_name.

Stored text often mixes:
- CP1252 printable remaps (€ “ ” …) for bytes 0x80–0x9F
- raw C1 controls (U+0081 etc.) for undefined CP1252 slots
Reverse by mapping each char back to one original byte, then UTF-8 decode.
"""
from __future__ import annotations

import pymysql

CONN = dict(host="localhost", user="root", password="", database="smart_city", charset="utf8mb4")


def chinese_count(text: str) -> int:
    return sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")


def looks_mojibake(text: str) -> bool:
    if not text:
        return False
    # Typical markers of UTF-8 bytes misread as Western encodings
    markers = ("Ã", "Â", "å", "æ", "ç", "è", "é", "ï", "Ÿ", "€", "š", "œ", "ˆ", "‰")
    if any(m in text for m in markers):
        return True
    if any(0x80 <= ord(ch) <= 0x9F for ch in text):
        return True
    return chinese_count(text) == 0 and any(ord(ch) > 127 for ch in text)


def char_to_byte(ch: str) -> int | None:
    o = ord(ch)
    if o <= 0xFF:
        return o
    try:
        b = ch.encode("cp1252")
    except UnicodeEncodeError:
        return None
    if len(b) != 1:
        return None
    return b[0]


def undo_western_mojibake(text: str) -> str | None:
    buf = bytearray()
    for ch in text:
        b = char_to_byte(ch)
        if b is None:
            return None
        buf.append(b)
    try:
        return bytes(buf).decode("utf-8")
    except UnicodeDecodeError:
        return None


def try_fix(text: str) -> str | None:
    if not text or not looks_mojibake(text):
        return None
    best = text
    best_score = chinese_count(text)
    cur = text
    for _ in range(4):
        cand = undo_western_mojibake(cur)
        if cand is None:
            break
        score = chinese_count(cand)
        if score > best_score:
            best = cand
            best_score = score
            cur = cand
            continue
        # accept if we removed mojibake markers even with same CJK count
        if score == best_score and looks_mojibake(cur) and not looks_mojibake(cand):
            best = cand
            cur = cand
            continue
        break
    return best if best != text else None


def main() -> None:
    conn = pymysql.connect(**CONN)
    cur = conn.cursor()
    cur.execute(
        "SELECT id, column_name, semantic_desc FROM ing_data_column "
        "WHERE (semantic_desc IS NOT NULL AND semantic_desc <> '') "
        "   OR (column_name IS NOT NULL AND column_name <> '')"
    )
    updates = []
    for row_id, column_name, semantic_desc in cur.fetchall():
        new_name = try_fix(column_name) if column_name else None
        new_desc = try_fix(semantic_desc) if semantic_desc else None
        if new_name or new_desc:
            updates.append((new_name or column_name, new_desc if new_desc is not None else semantic_desc, row_id))
            print(
                "id=%s name=%s desc=%s -> %s"
                % (
                    row_id,
                    bool(new_name),
                    bool(new_desc),
                    (new_desc or "")[:40],
                )
            )
    for name, desc, row_id in updates:
        cur.execute(
            "UPDATE ing_data_column SET column_name=%s, semantic_desc=%s WHERE id=%s",
            (name, desc, row_id),
        )
    conn.commit()
    print("updated", len(updates), "rows")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
