#!/usr/bin/env python3
"""生成自制(无版权)绘本 OCR 测试集(轨道 A 任务书 §1.2)。

输出 testdata/picture_books/<category>/page_001.jpg + page_001.json(ground truth)。
八类:en_simple / zh_simple / bilingual / low_light / curved_page / two_page_spread /
speech_bubble / image_heavy_text_light。

全部用 Pillow 渲染纯自制文本与简单几何插画 —— 零版权风险。
同步复制到 app/src/androidTest/assets/picture_books/ 供真机指标测试。
"""
import json
import math
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "testdata" / "picture_books"
ANDROID_TEST_ASSETS = ROOT / "app" / "src" / "androidTest" / "assets" / "picture_books"

W, H = 1200, 1600


def font(size, zh=False):
    candidates = (
        ["C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"]
        if zh
        else ["C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/calibri.ttf"]
    )
    for c in candidates:
        if Path(c).exists():
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def base_page(color=(252, 248, 238)):
    img = Image.new("RGB", (W, H), color)
    d = ImageDraw.Draw(img)
    return img, d


def draw_sun(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 205, 60))
    for i in range(12):
        a = i * math.pi / 6
        d.line(
            [cx + r * 1.2 * math.cos(a), cy + r * 1.2 * math.sin(a),
             cx + r * 1.6 * math.cos(a), cy + r * 1.6 * math.sin(a)],
            fill=(255, 180, 40), width=8,
        )


def draw_bear(d, cx, cy, s):
    brown = (150, 100, 60)
    d.ellipse([cx - s, cy - s * 0.7, cx + s, cy + s], fill=brown)        # body
    d.ellipse([cx - s * 0.6, cy - s * 1.5, cx + s * 0.6, cy - s * 0.4], fill=brown)  # head
    for dx in (-0.5, 0.5):
        d.ellipse([cx + dx * s - s * 0.2, cy - s * 1.7, cx + dx * s + s * 0.2, cy - s * 1.3], fill=brown)
    d.ellipse([cx - s * 0.15, cy - s * 1.05, cx + s * 0.15, cy - s * 0.75], fill=(80, 50, 30))


def save(img, category, gt):
    cat_dir = OUT / category
    cat_dir.mkdir(parents=True, exist_ok=True)
    img_path = cat_dir / "page_001.jpg"
    img.save(img_path, quality=88)
    (cat_dir / "page_001.json").write_text(
        json.dumps(gt, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"{category}: {img_path}")


def gt(expected, lang, **kw):
    return {"image": "page_001.jpg", "expected_text": expected, "language": lang, **kw}


def en_simple():
    img, d = base_page()
    draw_sun(d, 950, 250, 90)
    draw_bear(d, 350, 800, 140)
    f = font(64)
    text = "The little bear is looking for his mum."
    d.text((120, 1200), text, fill=(40, 40, 40), font=f)
    d.text((120, 1320), "He walks under the warm sun.", fill=(40, 40, 40), font=f)
    save(img, "en_simple", gt(
        "The little bear is looking for his mum.\nHe walks under the warm sun.", "en",
        forbidden_hallucinations=["forest", "rabbit", "school"],
    ))


def zh_simple():
    img, d = base_page()
    draw_sun(d, 950, 250, 90)
    draw_bear(d, 350, 800, 140)
    f = font(68, zh=True)
    d.text((120, 1200), "小熊在找妈妈。", fill=(40, 40, 40), font=f)
    d.text((120, 1330), "太阳暖暖地照着他。", fill=(40, 40, 40), font=f)
    save(img, "zh_simple", gt("小熊在找妈妈。\n太阳暖暖地照着他。", "zh",
                              forbidden_hallucinations=["森林", "兔子", "学校"]))


def bilingual():
    img, d = base_page()
    draw_bear(d, 600, 600, 130)
    d.text((120, 1150), "小熊有 3 个苹果。", fill=(40, 40, 40), font=font(64, zh=True))
    d.text((120, 1290), "The bear has 3 apples.", fill=(40, 40, 40), font=font(58))
    save(img, "bilingual", gt("小熊有 3 个苹果。\nThe bear has 3 apples.", "mixed",
                              protected_tokens=["3"]))


def low_light():
    img, d = base_page()
    draw_bear(d, 600, 700, 140)
    d.text((120, 1200), "Good night, little bear.", fill=(40, 40, 40), font=font(64))
    img = ImageEnhance.Brightness(img).enhance(0.30)
    save(img, "low_light", gt("Good night, little bear.", "en",
                              expect_low_quality=True))


def curved_page():
    img, d = base_page()
    d.text((150, 700), "The bear climbs the big hill.", fill=(40, 40, 40), font=font(60))
    img = img.rotate(8, expand=False, fillcolor=(220, 210, 195))
    save(img, "curved_page", gt("The bear climbs the big hill.", "en"))


def two_page_spread():
    img = Image.new("RGB", (W * 2, H), (252, 248, 238))
    d = ImageDraw.Draw(img)
    d.rectangle([W - 6, 0, W + 6, H], fill=(180, 170, 150))  # 书脊
    d.text((150, 1200), "Left page: the bear wakes up.", fill=(40, 40, 40), font=font(56))
    d.text((W + 150, 1200), "Right page: he eats honey.", fill=(40, 40, 40), font=font(56))
    d.text((560, 1500), "8", fill=(120, 120, 120), font=font(40))          # 左页码
    d.text((W + 560, 1500), "9", fill=(120, 120, 120), font=font(40))      # 右页码
    save(img, "two_page_spread", gt(
        "Left page: the bear wakes up.\nRight page: he eats honey.", "en",
        page_numbers_to_exclude=["8", "9"],
    ))


def speech_bubble():
    img, d = base_page()
    draw_bear(d, 400, 900, 140)
    d.ellipse([550, 300, 1100, 560], fill=(255, 255, 255), outline=(60, 60, 60), width=5)
    d.polygon([(640, 540), (560, 680), (730, 560)], fill=(255, 255, 255), outline=(60, 60, 60))
    d.text((620, 380), "Where is\nmy honey?", fill=(40, 40, 40), font=font(52))
    d.text((120, 1300), "The bear asks loudly.", fill=(40, 40, 40), font=font(60))
    save(img, "speech_bubble", gt("Where is\nmy honey?\nThe bear asks loudly.", "en"))


def image_heavy_text_light():
    img, d = base_page((200, 230, 250))
    draw_sun(d, 900, 300, 120)
    draw_bear(d, 400, 700, 180)
    for i in range(6):
        d.ellipse([100 + i * 180, 1150, 220 + i * 180, 1270], fill=(120, 190, 120))
    d.text((420, 1420), "Spring!", fill=(40, 40, 40), font=font(70))
    save(img, "image_heavy_text_light", gt("Spring!", "en"))


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    en_simple(); zh_simple(); bilingual(); low_light()
    curved_page(); two_page_spread(); speech_bubble(); image_heavy_text_light()
    # 同步到 androidTest assets(真机指标测试读这里)。
    if ANDROID_TEST_ASSETS.exists():
        shutil.rmtree(ANDROID_TEST_ASSETS)
    shutil.copytree(OUT, ANDROID_TEST_ASSETS)
    print(f"synced -> {ANDROID_TEST_ASSETS}")


if __name__ == "__main__":
    main()
