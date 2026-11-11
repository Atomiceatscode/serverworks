"""Generates Serverworks pixel art: item, block and GUI textures.

Run: python3 art/make_textures.py

Everything is drawn in code with a fixed random seed, so the output is reproducible and a
texture is changed by editing its function here rather than by hand-painting a PNG.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/serverworks/textures"


def rgba(hex_colour, alpha=255):
    hex_colour = hex_colour.lstrip("#")
    return tuple(int(hex_colour[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def shade(colour, amount):
    """Lightens (amount > 0) or darkens (amount < 0) a colour by a fixed step per channel."""
    return tuple(max(0, min(255, c + amount)) for c in colour[:3]) + (colour[3],)


class Canvas:
    def __init__(self, width, height):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w = width
        self.h = height

    def set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = colour

    def rect(self, x0, y0, x1, y1, colour):
        """Filled rectangle with inclusive corners."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def noise(self, x0, y0, x1, y1, rng, spread):
        """Adds subtle per-pixel brightness variation so flat metal does not look like plastic."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if self.px[x, y][3]:
                    self.px[x, y] = shade(self.px[x, y], rng.randint(-spread, spread))

    def save(self, relative):
        out = ROOT / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(out)
        print(f"wrote {out.relative_to(ROOT.parent.parent.parent.parent.parent.parent)}")


# ---------------------------------------------------------------- items


def disk(body, body_light, body_dark, label, label_line, written):
    """A 3.5-inch style disk. The 10x10 label window at (3..12, 2..11) is where
    DiskLabelRenderer draws the project icon, so written disks keep it plain."""
    c = Canvas(16, 16)
    outline = shade(body_dark, -28)
    # Body with a clipped top-right corner, like a real disk.
    c.rect(1, 1, 14, 14, body)
    c.rect(1, 1, 14, 1, body_light)
    c.rect(1, 1, 1, 14, body_light)
    c.rect(14, 2, 14, 14, body_dark)
    c.rect(2, 14, 14, 14, body_dark)
    for x in range(1, 15):
        c.set(x, 0, outline)
        c.set(x, 15, outline)
    for y in range(1, 15):
        c.set(0, y, outline)
        c.set(15, y, outline)
    c.set(14, 1, outline)
    c.set(15, 1, (0, 0, 0, 0))
    c.set(14, 0, (0, 0, 0, 0))
    c.set(15, 2, outline)
    c.set(13, 0, outline)
    c.set(13, 1, body_light)

    # Label window.
    c.rect(3, 2, 12, 11, label)
    if written:
        # Printed frame that shows while the icon is still loading.
        c.rect(3, 2, 12, 2, label_line)
        c.rect(3, 11, 12, 11, label_line)
        c.rect(3, 2, 3, 11, label_line)
        c.rect(12, 2, 12, 11, label_line)
        for i in range(3):
            c.rect(6, 5 + i * 2, 9, 5 + i * 2, label_line)
    else:
        for y in (4, 6, 8):
            c.rect(4, y, 11, y, label_line)
        c.rect(3, 11, 12, 11, shade(label, -24))

    # Metal shutter along the bottom edge.
    metal = rgba("#aeb7c6")
    c.rect(5, 12, 11, 14, metal)
    c.rect(5, 12, 11, 12, shade(metal, 26))
    c.rect(8, 13, 9, 14, rgba("#2a2f3a"))
    c.set(11, 13, shade(metal, -30))
    c.set(11, 14, shade(metal, -30))
    return c


def cartridge():
    """A chunky cartridge with a wide label window in the same place as a disk's, for the pack icon."""
    c = Canvas(16, 16)
    body = rgba("#7a3fb8")
    light = shade(body, 40)
    dark = shade(body, -40)
    outline = shade(dark, -30)
    c.rect(1, 0, 14, 15, outline)
    c.rect(2, 1, 13, 14, body)
    c.rect(2, 1, 13, 1, light)
    c.rect(2, 1, 2, 14, light)
    c.rect(13, 2, 13, 14, dark)
    # Grip ridges along the top.
    for x in range(4, 12, 2):
        c.set(x, 1, dark)
    # Label window.
    label = rgba("#efe6fa")
    c.rect(3, 2, 12, 11, label)
    c.rect(3, 11, 12, 11, rgba("#c9b6e6"))
    # Gold contacts along the bottom edge.
    gold = rgba("#e6c04a")
    for x in range(3, 13, 2):
        c.rect(x, 13, x, 14, gold)
    c.rect(2, 15, 13, 15, outline)
    return c


def link_card():
    """A slim card with a chip and an antenna loop."""
    c = Canvas(16, 16)
    body = rgba("#2c8f8f")
    c.rect(2, 3, 13, 12, shade(body, -50))
    c.rect(3, 4, 12, 11, body)
    c.rect(3, 4, 12, 4, shade(body, 40))
    gold = rgba("#e6c04a")
    c.rect(4, 6, 6, 9, gold)
    c.set(5, 7, shade(gold, -50))
    c.set(5, 8, shade(gold, -50))
    loop = rgba("#bff5f0")
    for x in range(8, 12):
        c.set(x, 6, loop)
        c.set(x, 9, loop)
    c.set(11, 7, loop)
    c.set(11, 8, loop)
    c.set(8, 7, loop)
    return c


def manual():
    """A slate-blue binder with a rack emblem and a bookmark."""
    c = Canvas(16, 16)
    cover = rgba("#2e4a7a")
    c.rect(2, 1, 13, 14, shade(cover, -45))
    c.rect(3, 1, 13, 13, cover)
    c.rect(3, 1, 13, 1, shade(cover, 35))
    c.rect(2, 1, 2, 14, rgba("#1a2a45"))
    c.rect(3, 14, 13, 14, rgba("#e9e6da"))
    c.rect(4, 14, 13, 14, rgba("#cfcab8"))
    # Emblem: a tiny rack with two lit bays.
    c.rect(6, 4, 10, 10, rgba("#aab3c6"))
    c.rect(7, 5, 9, 6, rgba("#1b202b"))
    c.rect(7, 8, 9, 9, rgba("#1b202b"))
    c.set(9, 5, rgba("#4fe08a"))
    c.set(9, 8, rgba("#4fe08a"))
    c.rect(12, 0, 12, 3, rgba("#d8563a"))
    return c


def make_items():
    disk(rgba("#4a5263"), rgba("#667089"), rgba("#363c4a"), rgba("#e9e6da"), rgba("#bdb8a6"), False) \
        .save("item/blank_disk.png")
    disk(rgba("#2f62c4"), rgba("#5b8cf0"), rgba("#1f4390"), rgba("#dfe6f5"), rgba("#9fb2da"), True) \
        .save("item/mod_disk.png")
    cartridge().save("item/pack_cartridge.png")
    link_card().save("item/link_card.png")
    manual().save("item/operators_manual.png")


# ---------------------------------------------------------------- block


STEEL = rgba("#8a93a6")
STEEL_LIGHT = rgba("#aab3c6")
STEEL_DARK = rgba("#5e667a")
STEEL_DEEP = rgba("#3d4352")
SCREEN = rgba("#0d1512")
GLOW = rgba("#4fe08a")
GLOW_DIM = rgba("#237a4c")


def panel(rng, base=STEEL):
    """A bevelled steel plate with corner rivets."""
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, base)
    c.noise(0, 0, 15, 15, rng, 5)
    c.rect(0, 0, 15, 0, STEEL_LIGHT)
    c.rect(0, 0, 0, 15, STEEL_LIGHT)
    c.rect(0, 15, 15, 15, STEEL_DARK)
    c.rect(15, 0, 15, 15, STEEL_DARK)
    c.set(15, 0, base)
    c.set(0, 15, base)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, STEEL_DEEP)
        c.set(x - 1, y - 1, STEEL_LIGHT) if (x, y) == (2, 2) else None
    return c


def make_block():
    rng = random.Random(2603)

    side = panel(rng)
    for y in (5, 7, 9):  # cooling slits
        side.rect(4, y, 11, y, STEEL_DEEP)
        side.rect(4, y + 1, 11, y + 1, STEEL_LIGHT)
    side.save("block/disk_writer_side.png")

    front = panel(rng)
    # Status screen with a few lines of "text".
    front.rect(3, 2, 12, 7, STEEL_DEEP)
    front.rect(4, 3, 11, 6, SCREEN)
    front.rect(5, 4, 8, 4, GLOW)
    front.rect(5, 5, 10, 5, GLOW_DIM)
    front.set(10, 4, GLOW_DIM)
    # Disk slot with a lip, an eject button and a ready light.
    front.rect(3, 10, 11, 11, rgba("#15181f"))
    front.rect(3, 9, 11, 9, STEEL_DARK)
    front.rect(3, 12, 11, 12, STEEL_LIGHT)
    front.rect(5, 10, 9, 10, rgba("#2f62c4"))  # edge of an inserted disk
    front.set(13, 10, GLOW)
    front.set(13, 11, GLOW_DIM)
    front.rect(13, 6, 13, 7, rgba("#d8a13a"))
    front.save("block/disk_writer_front.png")

    top = panel(rng)
    # Recessed tray holding a disk outline, with a write head on an arm.
    top.rect(3, 3, 12, 12, STEEL_DEEP)
    top.rect(4, 4, 12, 12, rgba("#4b5263"))
    top.rect(5, 5, 10, 10, rgba("#2a2f3a"))
    top.rect(6, 6, 9, 9, rgba("#3b4252"))
    top.rect(7, 7, 8, 8, STEEL_LIGHT)  # spindle
    top.rect(11, 5, 11, 9, STEEL_LIGHT)  # arm
    top.set(10, 9, GLOW)  # head
    top.save("block/disk_writer_top.png")

    bottom = panel(rng, rgba("#5a6172"))
    bottom.rect(4, 4, 11, 11, STEEL_DEEP)
    bottom.rect(5, 5, 10, 10, rgba("#4b5263"))
    bottom.save("block/disk_writer_bottom.png")


# ---------------------------------------------------------------- gui


GUI_FILL = rgba("#c6c6c6")
GUI_LIGHT = rgba("#ffffff")
GUI_SHADOW = rgba("#555555")
SLOT_FILL = rgba("#8b8b8b")
SLOT_DARK = rgba("#373737")
WELL = rgba("#1b202b")
WELL_ALT = rgba("#232936")
WELL_LINE = rgba("#343c4f")


def gui_panel(c, x0, y0, w, h):
    """The standard raised container background with rounded corners."""
    x1, y1 = x0 + w - 1, y0 + h - 1
    black = rgba("#000000")
    c.rect(x0 + 2, y0, x1 - 2, y1, black)
    c.rect(x0, y0 + 2, x1, y1 - 2, black)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, black)
    c.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, GUI_FILL)
    # Light top and left bevel.
    c.rect(x0 + 2, y0 + 1, x1 - 3, y0 + 2, GUI_LIGHT)
    c.rect(x0 + 1, y0 + 2, x0 + 2, y1 - 3, GUI_LIGHT)
    c.set(x0 + 3, y0 + 3, GUI_LIGHT)
    # Dark bottom and right bevel.
    c.rect(x0 + 3, y1 - 2, x1 - 2, y1 - 1, GUI_SHADOW)
    c.rect(x1 - 2, y0 + 3, x1 - 1, y1 - 2, GUI_SHADOW)
    c.set(x1 - 3, y1 - 3, GUI_SHADOW)
    c.set(x1 - 2, y0 + 2, GUI_FILL)
    c.set(x0 + 2, y1 - 2, GUI_FILL)


def gui_inset(c, x0, y0, x1, y1, fill):
    """A sunken area: dark edge on the top and left, light edge on the bottom and right."""
    c.rect(x0, y0, x1, y1, fill)
    c.rect(x0, y0, x1 - 1, y0, SLOT_DARK)
    c.rect(x0, y0, x0, y1 - 1, SLOT_DARK)
    c.rect(x0 + 1, y1, x1, y1, GUI_LIGHT)
    c.rect(x1, y0 + 1, x1, y1, GUI_LIGHT)
    c.set(x1, y0, SLOT_FILL)
    c.set(x0, y1, SLOT_FILL)


def gui_slot(c, x, y):
    """An 18x18 item slot whose 16x16 item area starts at (x, y)."""
    gui_inset(c, x - 1, y - 1, x + 16, y + 16, SLOT_FILL)


def arrow(c, x, y, colour):
    """A 22x15 right-pointing arrow."""
    c.rect(x, y + 5, x + 12, y + 9, colour)
    for i in range(8):
        c.rect(x + 13 + i, y + i, x + 13 + i, y + 14 - i, colour)


def make_gui():
    c = Canvas(512, 256)
    gui_panel(c, 0, 0, 276, 222)

    # Catalog list with its scrollbar track.
    gui_inset(c, 7, 35, 187, 128, WELL)
    c.rect(179, 36, 179, 127, WELL_LINE)
    c.rect(180, 36, 186, 127, rgba("#12151d"))

    # Detail panel, with the two slots along its bottom edge.
    gui_inset(c, 189, 35, 268, 128, WELL_ALT)
    c.rect(190, 101, 267, 101, WELL_LINE)
    gui_slot(c, 197, 106)
    gui_slot(c, 245, 106)
    arrow(c, 218, 107, rgba("#4a5266"))
    # Ghost of a blank disk in the input slot, as a hint for what goes there.
    ghost = rgba("#7a7a7a")
    c.rect(199, 108, 210, 119, ghost)
    c.rect(201, 109, 208, 115, SLOT_FILL)
    c.rect(203, 117, 207, 119, SLOT_FILL)
    c.set(210, 108, SLOT_FILL)

    # Player inventory.
    for row in range(3):
        for col in range(9):
            gui_slot(c, 57 + col * 18, 140 + row * 18)
    for col in range(9):
        gui_slot(c, 57 + col * 18, 198)

    # --- sprites beside the panel ---
    # Icon placeholder (32x32) for projects without an icon or still loading.
    c.rect(289, 0, 318, 31, rgba("#2a3040"))
    c.rect(288, 1, 319, 30, rgba("#2a3040"))
    c.rect(296, 7, 311, 24, rgba("#3f4a63"))
    c.rect(299, 9, 308, 16, rgba("#566380"))
    c.rect(301, 20, 306, 24, rgba("#566380"))
    c.set(311, 7, rgba("#2a3040"))
    # Lit arrow, drawn over the dull one when a disk is ready to take.
    arrow(c, 288, 40, rgba("#6fa8ff"))

    c.save("gui/container/disk_writer.png")


if __name__ == "__main__":
    make_items()
    make_block()
    make_gui()
