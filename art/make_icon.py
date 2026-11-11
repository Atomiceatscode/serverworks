"""Generates the mod icon: a small server rack with three drive bays and status LEDs.

Run: python3 art/make_icon.py
Draws on a 32x32 grid and upscales with nearest-neighbour so it stays crisp.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/serverworks/icon.png"

BG = (24, 28, 38, 255)
FRAME_DARK = (38, 44, 58, 255)
FRAME = (70, 80, 100, 255)
FRAME_LIGHT = (112, 126, 152, 255)
BAY = (20, 23, 31, 255)
BAY_EDGE = (52, 60, 78, 255)
SLOT = (92, 104, 128, 255)
GREEN = (60, 220, 130, 255)
GREEN_DIM = (28, 110, 70, 255)
AMBER = (245, 180, 60, 255)
VENT = (30, 35, 46, 255)


def rect(px, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = colour


def main():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()

    # Rounded backdrop.
    rect(px, 1, 0, 30, 31, BG)
    rect(px, 0, 1, 31, 30, BG)

    # Rack body with a lit top edge and shaded right/bottom edge.
    rect(px, 5, 3, 26, 28, FRAME)
    rect(px, 5, 3, 26, 3, FRAME_LIGHT)
    rect(px, 5, 3, 5, 28, FRAME_LIGHT)
    rect(px, 26, 4, 26, 28, FRAME_DARK)
    rect(px, 6, 28, 26, 28, FRAME_DARK)

    # Three drive bays.
    leds = [GREEN, GREEN, AMBER]
    for i, led in enumerate(leds):
        top = 5 + i * 6
        rect(px, 7, top, 24, top + 4, BAY_EDGE)
        rect(px, 8, top + 1, 23, top + 3, BAY)
        rect(px, 9, top + 2, 17, top + 2, SLOT)  # disk slot
        px[21, top + 2] = led
        px[22, top + 2] = GREEN_DIM if led == GREEN else FRAME_DARK

    # Vent grille at the bottom.
    rect(px, 7, 23, 24, 26, VENT)
    for x in range(8, 24, 2):
        rect(px, x, 24, x, 25, BAY_EDGE)

    # Feet.
    rect(px, 7, 29, 9, 29, FRAME_DARK)
    rect(px, 22, 29, 24, 29, FRAME_DARK)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.resize((256, 256), Image.NEAREST).save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
