#!/usr/bin/env python3
"""Compose the Play gallery from untouched Android screencap PNGs. Requires Pillow."""
import struct
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
SIZE = (1080, 1920)
SOURCE_SIZE = (1080, 2040)
PAPER = "#F6F7EF"
INK = "#192E22"
GREEN = "#164837"
STATUS_BAR_PX = 64  # Top system chrome in the 1080 × 2040 source captures.
FRAMES = ("01-month", "02-expense", "03-goal", "04-debt", "05-year")
CAPTIONS = {
    "ru-RU": (
        ("Куда ушли деньги", "за месяц?"),
        ("Запишите покупку",),
        ("Сколько уже есть", "на поездку?"),
        ("Сколько осталось", "вернуть?"),
        ("Доходы и расходы", "за год"),
    ),
    "en-US": (
        ("Where did my money", "go this month?"),
        ("Log an everyday", "purchase"),
        ("How much have I", "saved for my trip?"),
        ("How much is still owed?",),
        ("Income and spending", "for the year"),
    ),
}


def font(size):
    face = ImageFont.truetype(str(ROOT / "fonts/Roboto-Variable.ttf"), size)
    axes = {b"Width": 100, b"Weight": 685}
    face.set_variation_by_axes([axes.get(axis["name"], axis["default"])
                                for axis in face.get_variation_axes()])
    return face


def render(raw, caption):
    with Image.open(raw) as source:
        if source.size != SOURCE_SIZE:
            raise ValueError(f"Expected a phone capture of size {SOURCE_SIZE}: {raw}")
        screen = source.convert("RGB").crop((0, STATUS_BAR_PX, source.width, source.height))
    # Scale the contiguous Android capture proportionally; don't retouch it.
    screen.thumbnail((932, 1610), Image.Resampling.LANCZOS)
    x = (SIZE[0] - screen.width) // 2
    y = 268
    surface = Image.new("RGB", SIZE, PAPER)
    silhouette = Image.new("L", SIZE)
    ImageDraw.Draw(silhouette).rectangle(
        (x - 1, y + 9, x + screen.width, y + screen.height + 9), fill=40,
    )
    surface.paste("#334738", (0, 0, *SIZE), silhouette.filter(ImageFilter.GaussianBlur(18)))
    ink = ImageDraw.Draw(surface)
    ink.rectangle((x - 1, y - 1, x + screen.width, y + screen.height), fill="#DDE5D6")
    surface.paste(screen, (x, y))

    ink.rounded_rectangle((x, 56, x + 60, 63), radius=4, fill=GREEN)
    for size in range(76, 67, -1):
        face = font(size)
        if max(ink.textlength(line, font=face) for line in caption) <= SIZE[0] - 2 * x:
            break
    else:
        raise ValueError(f"Caption doesn't fit: {caption}")
    line_step = 83
    first_baseline = 145 if len(caption) == 2 else 184
    for i, line in enumerate(caption):
        ink.text((x, first_baseline + line_step * i), line, font=face, fill=INK, anchor="ls")
    return surface


def verify_play_png(path):
    with path.open("rb") as stream:
        header = stream.read(29)
    if len(header) != 29 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError(f"Not a PNG: {path}")
    if struct.unpack(">IIBB", header[16:26]) != (*SIZE, 8, 2) or path.stat().st_size > 8_000_000:
        raise ValueError(f"Expected a 24-bit PNG of size {SIZE}, at most 8 MB: {path}")
    with Image.open(path) as png:
        if "transparency" in png.info:
            raise ValueError(f"Unexpected PNG transparency: {path}")
        png.verify()


def preview(paths, dest):
    width = 300
    height = round(width * SIZE[1] / SIZE[0])
    outer = 28
    gap = 18
    sheet = Image.new(
        "RGB", (2 * outer + width * len(paths) + gap * (len(paths) - 1), 2 * outer + height), "#E0E7DB",
    )
    for i, path in enumerate(paths):
        with Image.open(path) as frame:
            thumb = frame.resize((width, height), Image.Resampling.LANCZOS)
            sheet.paste(thumb, (outer + i * (width + gap), outer))
    sheet.save(dest, optimize=True)


def main():
    (ROOT / "preview").mkdir(exist_ok=True)
    for locale, captions in CAPTIONS.items():
        folder = ROOT / "final" / locale
        folder.mkdir(parents=True, exist_ok=True)
        outputs = []
        for name, text in zip(FRAMES, captions, strict=True):
            output = folder / f"{name}.png"
            render(ROOT / "raw" / locale / output.name, text).save(output, optimize=True)
            verify_play_png(output)
            outputs.append(output)
            print(f"{output.relative_to(ROOT)} · {output.stat().st_size:,} bytes")
        preview(outputs, ROOT / "preview" / f"{locale}.png")


if __name__ == "__main__":
    main()
