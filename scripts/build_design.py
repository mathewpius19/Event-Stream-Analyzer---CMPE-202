#!/usr/bin/env python3
"""Render docs/design.md and UML PNGs to docs/design.pdf (requires ReportLab)."""
from html import escape
from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import (
    BaseDocTemplate, Frame, Image, NextPageTemplate, PageBreak, PageTemplate,
    Paragraph, Spacer,
)

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
PORTRAIT = letter
WIDE = (1224, 792)  # 17 x 11 inches, to keep the UML labels legible.
INK = colors.HexColor("#142535")
ACCENT = colors.HexColor("#15756f")
GRAY = colors.HexColor("#52616b")

STYLES = {
    "title": ParagraphStyle("Title", fontName="Helvetica-Bold", fontSize=21,
                            leading=25, textColor=INK, spaceAfter=15),
    "heading": ParagraphStyle("Heading", fontName="Helvetica-Bold", fontSize=12.5,
                              leading=16, textColor=ACCENT, spaceBefore=12,
                              spaceAfter=7, keepWithNext=True),
    "body": ParagraphStyle("Body", fontName="Helvetica", fontSize=10,
                           leading=14, textColor=INK, spaceAfter=8,
                           alignment=TA_LEFT),
    "bullet": ParagraphStyle("Bullet", fontName="Helvetica", fontSize=9.7,
                             leading=13.4, textColor=INK, spaceAfter=6,
                             leftIndent=12, firstLineIndent=-9),
    "caption": ParagraphStyle("Caption", fontName="Helvetica", fontSize=10,
                              leading=14, textColor=GRAY, spaceAfter=7),
}


def inline(text):
    text = escape(text)
    return re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)


def page_chrome(canvas, doc):
    width, height = canvas._pagesize
    canvas.saveState()
    canvas.setStrokeColor(ACCENT)
    canvas.setLineWidth(1)
    canvas.line(45, height - 35, width - 45, height - 35)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(GRAY)
    canvas.drawString(45, height - 27, "CMPE-202  |  EVENT STREAM ANALYZER")
    canvas.drawRightString(width - 45, height - 27, "mathewolickal  |  November 18, 2026")
    canvas.drawString(45, 25, "Design, patterns, UML, and extensibility")
    canvas.drawRightString(width - 45, 25, str(doc.page))
    canvas.restoreState()


def template(name, size):
    width, height = size
    frame = Frame(45, 45, width - 90, height - 100,
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    return PageTemplate(id=name, frames=[frame], pagesize=size, onPage=page_chrome)


def text_blocks(section, wide=False):
    blocks = []
    for block in re.split(r"\n\s*\n", section.strip()):
        block = block.strip()
        if not block:
            continue
        if "<!-- DIAGRAM:" in block:
            for line in block.splitlines():
                if line.startswith("# "):
                    blocks.append(Paragraph(inline(line[2:]), STYLES["title"]))
                match = re.search(r"<!-- DIAGRAM: (.+?) -->", line)
                if match:
                    image = Image(str(DOCS / match.group(1)))
                    scale = min(1134 / image.imageWidth, 555 / image.imageHeight)
                    image.drawWidth = image.imageWidth * scale
                    image.drawHeight = image.imageHeight * scale
                    image.hAlign = "CENTER"
                    blocks.extend([image, Spacer(1, 12)])
        elif block.startswith("# "):
            blocks.append(Paragraph(inline(block[2:]), STYLES["title"]))
        elif block.startswith("## "):
            blocks.append(Paragraph(inline(block[3:]), STYLES["heading"]))
        elif block.startswith("- "):
            for bullet in re.split(r"\n(?=- )", block):
                text = " ".join(bullet[2:].splitlines())
                blocks.append(Paragraph("- " + inline(text), STYLES["bullet"]))
        else:
            style = STYLES["caption"] if wide else STYLES["body"]
            blocks.append(Paragraph(inline(" ".join(block.splitlines())), style))
    return blocks


def main():
    sections = (DOCS / "design.md").read_text(encoding="utf-8").split("<!-- PAGE -->")
    doc = BaseDocTemplate(str(DOCS / "design.pdf"), pagesize=PORTRAIT,
                          title="Event Stream Analyzer - Design",
                          author="mathewolickal", subject="CMPE-202 design and extensibility",
                          pageCompression=1)
    doc.addPageTemplates([template("portrait", PORTRAIT), template("wide", WIDE)])
    story = []
    for index, section in enumerate(sections):
        wide = "<!-- DIAGRAM:" in section
        if index:
            story.extend([NextPageTemplate("wide" if wide else "portrait"), PageBreak()])
        story.extend(text_blocks(section, wide))
    doc.build(story)
    print(DOCS / "design.pdf")


if __name__ == "__main__":
    main()
