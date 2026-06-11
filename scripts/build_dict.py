#!/usr/bin/env python3
"""LumiRead 离线词典构建脚本(轨道 A,FACTS#F14)。

输入(dict_sources/,由 FACTS#F14 的官方 URL 下载):
  - dict/                       WordNet 3.1 解包目录(data.* / index.*)
  - cedict_1_0_ts_utf-8_mdbg.txt.gz   CC-CEDICT 快照

输出:
  - app/src/main/assets/dict/lumi_dict.db   SQLite,随 APK 打包

表结构(FACTS#F14):
  entries(term TEXT NOT NULL, lang TEXT NOT NULL, definition TEXT NOT NULL, example TEXT)
  index idx_entries_term_lang ON entries(term, lang)

数据策略:
  - WordNet:每个 lemma 取**第一义项**(index.* 中 synset 顺序即词频序,首个 = 最常用义),
    gloss 的分号前段为释义、首个引号段为例句。多词性时按 n > v > adj > adv 优先取一条。
  - CC-CEDICT:每个简体词条取前 3 个义项拼接;繁体与简体不同则各存一行(查询命中率)。
    剔除纯交叉引用义项(variant of / see also 开头)。
"""
import gzip
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "dict_sources"
OUT = ROOT / "app" / "src" / "main" / "assets" / "dict" / "lumi_dict.db"

POS_PRIORITY = ["noun", "verb", "adj", "adv"]


def parse_wordnet():
    """lemma(lower, 空格还原) -> (definition, example)。"""
    glosses = {}  # (pos, offset) -> gloss
    for pos in POS_PRIORITY:
        for line in (SRC / "dict" / f"data.{pos}").open(encoding="latin-1"):
            if line.startswith(" "):  # 版权头
                continue
            parts = line.split("|")
            if len(parts) < 2:
                continue
            offset = line.split(" ", 1)[0]
            glosses[(pos, offset)] = parts[1].strip()

    entries = {}
    for pos in POS_PRIORITY:
        for line in (SRC / "dict" / f"index.{pos}").open(encoding="latin-1"):
            if line.startswith(" "):
                continue
            fields = line.split()
            if len(fields) < 5:
                continue
            lemma = fields[0]
            # index.* 行尾是 synset offsets(8 位数字),首个 = 最常用义。
            offsets = [f for f in fields if re.fullmatch(r"\d{8}", f)]
            if not offsets:
                continue
            gloss = glosses.get((pos, offsets[0]))
            if not gloss:
                continue
            term = lemma.replace("_", " ")
            if term in entries:  # 已被更高优先级词性占用
                continue
            definition, example = split_gloss(gloss)
            if definition:
                entries[term] = (definition, example)
    return entries


def split_gloss(gloss: str):
    """gloss = 'definition; "example"; "example2"' → (definition, first_example)。"""
    m = re.search(r'"([^"]+)"', gloss)
    example = m.group(1).strip() if m else None
    definition = gloss.split(';')[0].strip()
    return definition, example


CROSS_REF = re.compile(r"^(variant of |see also |see |old variant of |archaic variant of |used in )")


def parse_cedict():
    """term -> definition(简繁各一行)。"""
    entries = {}
    path = SRC / "cedict_1_0_ts_utf-8_mdbg.txt.gz"
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            m = re.match(r"^(\S+) (\S+) \[[^\]]*\] /(.+)/\s*$", line)
            if not m:
                continue
            trad, simp, defs_raw = m.groups()
            defs = [d.strip() for d in defs_raw.split("/") if d.strip()]
            defs = [d for d in defs if not CROSS_REF.match(d)]
            if not defs:
                continue
            definition = "; ".join(defs[:3])
            for term in {simp, trad}:
                # 同词条多读音(多行):保留义项最多的那行。
                if term not in entries or len(definition) > len(entries[term]):
                    entries[term] = definition
    return entries


def main():
    if not (SRC / "dict" / "data.noun").exists():
        sys.exit("缺 WordNet 数据:先按 FACTS#F14 下载并解包到 dict_sources/dict/")
    if not (SRC / "cedict_1_0_ts_utf-8_mdbg.txt.gz").exists():
        sys.exit("缺 CC-CEDICT:先按 FACTS#F14 下载到 dict_sources/")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    db = sqlite3.connect(OUT)
    db.execute("CREATE TABLE entries(term TEXT NOT NULL, lang TEXT NOT NULL, definition TEXT NOT NULL, example TEXT)")

    wn = parse_wordnet()
    db.executemany(
        "INSERT INTO entries VALUES (?, 'EN', ?, ?)",
        ((t, d, e) for t, (d, e) in wn.items()),
    )
    print(f"WordNet EN entries: {len(wn)}")

    cc = parse_cedict()
    db.executemany(
        "INSERT INTO entries VALUES (?, 'ZH', ?, NULL)",
        ((t, d) for t, d in cc.items()),
    )
    print(f"CC-CEDICT ZH entries: {len(cc)}")

    db.execute("CREATE INDEX idx_entries_term_lang ON entries(term, lang)")
    db.commit()
    # 紧凑化,减小 asset 体积。
    db.execute("VACUUM")
    db.close()
    print(f"OK -> {OUT} ({OUT.stat().st_size / 1024 / 1024:.1f} MB)")

    # 自检:硬验证门用词必须命中(任务书 §1.3)。
    db = sqlite3.connect(OUT)
    for term, lang in [("caterpillar", "EN"), ("毛毛虫", "ZH")]:
        row = db.execute(
            "SELECT definition FROM entries WHERE term = ? AND lang = ?", (term, lang)
        ).fetchone()
        if not row:
            sys.exit(f"自检失败:{term}/{lang} 未命中!")
        print(f"自检 {term} ({lang}): {row[0][:80]}")
    db.close()


if __name__ == "__main__":
    main()
