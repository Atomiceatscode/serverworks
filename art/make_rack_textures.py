"""Generates the rack's block and GUI textures.

Run: python3 art/make_rack_textures.py

Rack blocks come in an idle look and a "formed" look that switches on once the block is
part of a working rack. Casings additionally wear the colour of the rack's platform.
"""
import json
import random

from make_textures import (GLOW, GLOW_DIM, GUI_FILL, ROOT, SCREEN, STEEL, STEEL_DARK, STEEL_DEEP, STEEL_LIGHT, WELL,
                           WELL_ALT, Canvas, gui_inset, gui_panel, gui_slot, panel, rgba, shade)
from platforms import PLATFORMS, TIERS

PLATE = rgba("#20242e")
DISK_BLUE = rgba("#2f62c4")
LED_OFF = rgba("#3b4252")
AMBER = rgba("#d8a13a")


def animated(name, frames, frametime, interpolate=False):
    """Stacks equally sized 16x16 canvases into a vertical animation strip."""
    sheet = Canvas(16, 16 * len(frames))
    for i, frame in enumerate(frames):
        for y in range(16):
            for x in range(16):
                sheet.set(x, i * 16 + y, frame.px[x, y])
    sheet.save(name)
    meta = {"animation": {"frametime": frametime}}
    if interpolate:
        meta["animation"]["interpolate"] = True
    (ROOT / (name + ".mcmeta")).write_text(json.dumps(meta, indent=2) + "\n")


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---------------------------------------------------------------- casings


def casing(rng, plate, light, dark, accent=None):
    """A frame of beams around a recessed panel ruled like rack units. Formed casings
    gain a stripe and two status lights in the platform's colour."""
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, plate)
    c.noise(0, 0, 15, 15, rng, 4)
    # Outer bevel.
    c.rect(0, 0, 15, 0, light)
    c.rect(0, 0, 0, 15, light)
    c.rect(0, 15, 15, 15, dark)
    c.rect(15, 0, 15, 15, dark)
    # Recessed panel.
    deep = shade(dark, -22)
    inner = shade(plate, -18)
    c.rect(3, 3, 12, 12, inner)
    c.rect(3, 3, 12, 3, deep)
    c.rect(3, 3, 3, 12, deep)
    c.rect(4, 12, 12, 12, light)
    c.rect(12, 4, 12, 12, light)
    for y in (6, 9):
        c.rect(4, y, 11, y, deep)
    # Bolts at the beam joints.
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, deep)
    if accent is not None:
        c.rect(4, 7, 11, 8, accent)
        c.rect(4, 7, 11, 7, shade(accent, 30))
        c.set(5, 4, accent)
        c.set(7, 4, shade(accent, -40))
        c.set(10, 10, shade(accent, 40))
        c.set(11, 10, accent)
    return c


def make_casings():
    for tier_id, _, plate, light, dark, _ in TIERS:
        rng = random.Random(f"casing-{tier_id}")
        casing(rng, rgba(plate), rgba(light), rgba(dark)).save(f"block/{tier_id}_rack_casing.png")
        for platform_id, _, _, accent, _, _, _ in PLATFORMS:
            rng = random.Random(f"casing-{tier_id}")
            casing(rng, rgba(plate), rgba(light), rgba(dark), rgba(accent)) \
                .save(f"block/{tier_id}_rack_casing_{platform_id}.png")


# ---------------------------------------------------------------- components


def component_base(seed):
    return panel(random.Random(seed))


def make_frame():
    """The thin bezel around every component's recessed front."""
    c = Canvas(16, 16)
    rng = random.Random("frame")
    c.rect(0, 0, 15, 15, STEEL_DARK)
    c.noise(0, 0, 15, 15, rng, 4)
    c.rect(0, 0, 15, 0, STEEL)
    c.rect(0, 15, 15, 15, STEEL_DEEP)
    c.save("block/rack_frame.png")


def make_advancement_background():
    """A tile of rack frame for the advancement tab's background."""
    tile = casing(random.Random("advancement"), rgba("#3d4352"), rgba("#4b5263"), rgba("#2a2f3a"))
    tile.save("gui/advancements/backgrounds/rack.png")


def make_panel():
    side = component_base("rack-panel")
    for y in (5, 7, 9):
        side.rect(4, y, 11, y, STEEL_DEEP)
        side.rect(4, y + 1, 11, y + 1, STEEL_LIGHT)
    side.save("block/rack_panel.png")


def core_face(state, pulse=0.0):
    """state: off, on (formed, idle) or active (server running; pulse 0..1 brightens the ring)."""
    c = component_base("core")
    lit = state != "off"
    c.rect(2, 2, 13, 13, STEEL_DEEP)
    c.rect(3, 3, 12, 12, SCREEN if lit else rgba("#14171d"))
    if state == "active":
        ring = mix(rgba("#2f8fb0"), rgba("#9ff4ff"), pulse)
        core = mix(rgba("#bdf6ff"), rgba("#ffffff"), pulse)
    elif lit:
        ring = rgba("#3f7f95")
        core = rgba("#8fcfe0")
    else:
        ring = rgba("#39414f")
        core = rgba("#4a5263")
    for x, y in ((6, 4), (9, 4), (5, 5), (10, 5), (4, 6), (11, 6), (4, 7), (11, 7), (4, 8), (11, 8),
                 (5, 9), (10, 9), (6, 10), (7, 11), (8, 11), (9, 10), (5, 10), (10, 10), (6, 11), (9, 11)):
        c.set(x, y, ring)
    c.rect(7, 4, 8, 8, core)
    if lit:
        c.set(3, 12, GLOW if state == "active" else GLOW_DIM)
        c.set(12, 12, rgba("#5fe0ff") if state == "active" else rgba("#2f6f80"))
    return c


def make_server_core():
    core_face("off").save("block/server_core_front.png")
    core_face("on").save("block/server_core_front_on.png")
    pulses = [0.0, 0.2, 0.45, 0.75, 1.0, 0.75, 0.45, 0.2]
    animated("block/server_core_front_active.png", [core_face("active", p) for p in pulses], 3, True)


STONE = rgba("#7d828c")
STONE_DARK = rgba("#4f535c")


def glyph_colour(ch, accent, second):
    """The colour a glyph character stands for; see platforms.py."""
    white = rgba("#ffffff")
    black = rgba("#000000")
    return {
        "#": accent, "+": mix(accent, white, 0.4), "-": mix(accent, black, 0.35),
        "o": second, "~": mix(second, white, 0.4), ",": mix(second, black, 0.35),
        "*": rgba("#f4f1ea"), "=": STONE, "_": STONE_DARK,
    }[ch]


def module_face(platform_id, accent, second, glyph, look):
    """look: off, on, or a brightness 0..1 for the running animation."""
    c = component_base(f"module-{platform_id}")
    on = look != "off"
    glow = look if isinstance(look, float) else 0.0
    frame = accent if on else mix(accent, STEEL_DEEP, 0.6)
    c.rect(1, 1, 14, 14, mix(frame, rgba("#ffffff"), glow * 0.3) if on else frame)
    c.rect(2, 2, 13, 13, PLATE if on else rgba("#191c24"))
    for gy, line in enumerate(glyph):
        for gx, ch in enumerate(line):
            if ch == ".":
                continue
            colour = glyph_colour(ch, accent, second)
            if not on:
                colour = mix(colour, PLATE, 0.45)
            else:
                colour = mix(colour, rgba("#ffffff"), glow * 0.25)
            c.set(3 + gx, 3 + gy, colour)
    return c


def make_platform_modules():
    for platform_id, _, _, accent_hex, second_hex, _, glyph in PLATFORMS:
        accent = rgba(accent_hex)
        second = rgba(second_hex)
        module_face(platform_id, accent, second, glyph, "off").save(f"block/{platform_id}_platform_module_front.png")
        module_face(platform_id, accent, second, glyph, "on").save(f"block/{platform_id}_platform_module_front_on.png")
        frames = [module_face(platform_id, accent, second, glyph, g) for g in (0.0, 0.35, 0.7, 1.0, 0.7, 0.35)]
        animated(f"block/{platform_id}_platform_module_front_active.png", frames, 4, True)


def bay_face(disks, state, frame=0):
    c = component_base("bay")
    c.rect(1, 1, 14, 14, STEEL_DEEP)
    blink = random.Random(f"bay-{disks}-{frame}")
    for slot in range(8):
        col, row = slot % 2, slot // 2
        x = 2 + col * 6
        y = 2 + row * 3
        c.rect(x, y, x + 5, y + 1, rgba("#11141a"))
        c.rect(x, y + 2, x + 5, y + 2, STEEL_DARK)
        if slot < disks:
            c.rect(x, y, x + 3, y + 1, DISK_BLUE)
            c.rect(x, y, x + 3, y, shade(DISK_BLUE, 40))
            if state == "active":
                led = GLOW if blink.random() < 0.6 else rgba("#ffd25a")
            else:
                led = GLOW_DIM if state == "on" else rgba("#1d4a32")
            c.set(x + 5, y, led)
        else:
            c.set(x + 5, y, LED_OFF)
    return c


def make_drive_bay():
    for disks in range(9):
        bay_face(disks, "off").save(f"block/drive_bay_front_{disks}.png")
        bay_face(disks, "on").save(f"block/drive_bay_front_{disks}_on.png")
        animated(f"block/drive_bay_front_{disks}_active.png", [bay_face(disks, "active", f) for f in range(4)], 3)


def memory_face(state, chase=-1):
    c = component_base("memory")
    c.rect(1, 1, 14, 14, STEEL_DEEP)
    on = state != "off"
    board = rgba("#2f8f5a") if on else rgba("#2a5a41")
    chip = rgba("#15181f")
    gold = rgba("#e6c04a") if on else rgba("#8f7a37")
    for i in range(4):
        x = 2 + i * 3
        c.rect(x, 2, x + 2, 12, board)
        c.rect(x, 2, x, 12, shade(board, 26))
        for y in (3, 6, 9):
            c.rect(x + 1, y, x + 2, y + 1, chip)
        c.rect(x, 13, x + 2, 13, gold)
        if state == "active":
            c.set(x + 1, 2, rgba("#b6ffcf") if i == chase else GLOW)
        elif on:
            c.set(x + 1, 2, GLOW_DIM)
    return c


def make_memory_bank():
    memory_face("off").save("block/memory_bank_front.png")
    memory_face("on").save("block/memory_bank_front_on.png")
    animated("block/memory_bank_front_active.png", [memory_face("active", i) for i in range(4)], 4)


FAN_FRAMES = [
    # Blade pixels relative to the hub centre between (7,7) and (8,8), four rotation steps.
    [(0, -4), (0, -3), (1, -3), (4, 1), (3, 1), (3, 2), (1, 5), (1, 4), (0, 4), (-3, 0), (-2, 0), (-2, -1)],
    [(2, -4), (2, -3), (3, -2), (5, 3), (4, 3), (3, 4), (-1, 5), (-1, 4), (-2, 3), (-3, -2), (-2, -2), (-2, -3)],
    [(4, -2), (3, -2), (3, -1), (2, 5), (2, 4), (1, 4), (-3, 3), (-2, 3), (-2, 2), (-1, -4), (-1, -3), (0, -3)],
    [(5, 0), (4, 0), (4, 1), (0, 5), (0, 4), (-1, 4), (-3, 1), (-2, 1), (-2, 0), (1, -4), (1, -3), (2, -3)],
]


def vent_frame(c, oy, frame, on):
    """A round grille with a five-blade fan. Frames step the blades by a quarter of the blade spacing."""
    import math
    base = component_base("vent")
    for y in range(16):
        for x in range(16):
            c.set(x, oy + y, base.px[x, y])
    well = rgba("#0e1116")
    rim = STEEL_DEEP
    blade = rgba("#7f9cc4") if on else rgba("#4d5a70")
    blade_edge = shade(blade, -45)
    hub = STEEL_LIGHT if on else STEEL
    spacing = 2 * math.pi / 5
    turn = frame * spacing / 4
    for y in range(1, 15):
        for x in range(1, 15):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 6.6:
                continue
            if r > 5.8:
                c.set(x, oy + y, rim)
                continue
            if r < 1.6:
                c.set(x, oy + y, hub)
                continue
            # Blades are wedges that sweep slightly with the radius, like a real fan.
            angle = (math.atan2(dy, dx) - turn + r * 0.12) % spacing
            if angle < 0.62:
                c.set(x, oy + y, blade if angle > 0.16 else blade_edge)
            else:
                c.set(x, oy + y, well)
    # Two guard bars across the opening.
    for x in (4, 11):
        for y in range(3, 13):
            if math.hypot(x - 7.5, y - 7.5) < 5.8:
                c.set(x, oy + y, STEEL_DARK)
    if on:
        c.set(13, oy + 13, GLOW)


def make_vent():
    still = Canvas(16, 16)
    vent_frame(still, 0, 0, False)
    still.save("block/vent_front.png")
    idle = Canvas(16, 16)
    vent_frame(idle, 0, 0, True)
    for y in range(16):
        for x in range(16):
            if idle.px[x, y] == GLOW:
                idle.set(x, y, GLOW_DIM)
    idle.save("block/vent_front_on.png")
    spinning = Canvas(16, 64)
    for frame in range(4):
        vent_frame(spinning, frame * 16, frame, True)
    spinning.save("block/vent_front_active.png")
    (ROOT / "block/vent_front_active.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 1}}, indent=2) + "\n")
    stale = ROOT / "block/vent_front_on.png.mcmeta"
    if stale.exists():
        stale.unlink()


def gateway_frame(c, oy, swirl, rng):
    """One 16x16 Gateway face at vertical offset oy. swirl is a list of colours, or None for a dark arch."""
    base = component_base("gateway")
    for y in range(16):
        for x in range(16):
            c.set(x, oy + y, base.px[x, y])
    lit = swirl is not None
    c.rect(2, oy + 2, 13, oy + 15, rgba("#2b2140") if lit else STEEL_DEEP)
    c.rect(3, oy + 3, 12, oy + 15, rgba("#0d0a14"))
    if lit:
        for y in range(4, 16):
            for x in range(4, 12):
                c.set(x, oy + y, rng.choice(swirl) if rng.random() < 0.5 else shade(swirl[0], -60))
    else:
        c.rect(4, oy + 4, 11, oy + 15, rgba("#15181f"))
    c.rect(7, oy + 1, 8, oy + 2, AMBER)


def make_gateway():
    """Idle (unformed), closed (formed, server off), open (animated) and restricted (animated, amber)."""
    idle = Canvas(16, 16)
    gateway_frame(idle, 0, None, random.Random(0))
    for x in (7, 8):
        for y in (1, 2):
            idle.set(x, y, STEEL_DARK)
    idle.save("block/gateway_front.png")

    closed = Canvas(16, 16)
    gateway_frame(closed, 0, None, random.Random(0))
    closed.save("block/gateway_front_on.png")

    for name, colours in (("open", [rgba("#7a4fd0"), rgba("#b08af0"), rgba("#4fd0e0")]),
                          ("restricted", [rgba("#d8902a"), rgba("#ffcf6a"), rgba("#a8602a")])):
        sheet = Canvas(16, 16 * 6)
        rng = random.Random(f"gateway-{name}")
        for frame in range(6):
            gateway_frame(sheet, frame * 16, colours, rng)
        sheet.save(f"block/gateway_front_{name}.png")
        (ROOT / f"block/gateway_front_{name}.png.mcmeta").write_text(
            json.dumps({"animation": {"frametime": 3, "interpolate": True}}, indent=2) + "\n")


def make_status_display():
    """A blank screen. When formed it glows faintly; the text itself is drawn in game."""
    for on in (False, True):
        c = component_base("display")
        c.rect(0, 0, 15, 13, STEEL_DEEP)
        c.rect(1, 1, 14, 12, rgba("#0b1612") if on else rgba("#14171d"))
        if on:
            for y in range(2, 12, 2):
                c.rect(1, y, 14, y, rgba("#0e1b16"))
            c.rect(1, 1, 14, 1, rgba("#16302a"))
            c.set(14, 13, GLOW)
        c.rect(5, 14, 10, 14, STEEL_DARK)
        c.save("block/status_display_front_on.png" if on else "block/status_display_front.png")


def make_wrench():
    c = Canvas(16, 16)
    metal = rgba("#aeb7c6")
    dark = rgba("#5e667a")
    light = rgba("#dfe5f0")
    grip = rgba("#c9562e")
    # Handle along the diagonal, open jaw at the top right.
    for i in range(9):
        x, y = 2 + i, 13 - i
        c.set(x, y, metal)
        c.set(x + 1, y, dark)
        c.set(x, y - 1, light)
    for i in range(4):
        x, y = 2 + i, 13 - i
        c.set(x, y, grip)
        c.set(x + 1, y, shade(grip, -40))
        c.set(x, y - 1, shade(grip, 40))
    for x, y in ((10, 2), (11, 2), (12, 3), (13, 4), (13, 5), (12, 6), (11, 6), (10, 5), (9, 3), (9, 2), (12, 2),
                 (13, 3), (10, 1), (11, 1)):
        c.set(x, y, metal)
    for x, y in ((11, 3), (11, 4), (12, 4), (10, 3), (10, 4)):
        c.set(x, y, (0, 0, 0, 0))
    c.set(13, 6, dark)
    c.set(12, 7, dark)
    c.set(11, 7, dark)
    c.save("item/wrench.png")


# ---------------------------------------------------------------- gui


def make_guis():
    core = Canvas(256, 256)
    gui_panel(core, 0, 0, 230, 188)
    gui_inset(core, 7, 17, 222, 54, WELL)
    gui_inset(core, 7, 107, 224, 162, WELL)
    for row in range(3):
        for col in range(12):
            x = 8 + col * 18
            y = 108 + row * 18
            core.rect(x, y, x + 15, y + 15, WELL_ALT)
    core.save("gui/container/server_core.png")

    bay = Canvas(256, 256)
    gui_panel(bay, 0, 0, 176, 154)
    for i in range(8):
        gui_slot(bay, 53 + (i % 4) * 18, 22 + (i // 4) * 18)
    for row in range(3):
        for col in range(9):
            gui_slot(bay, 8 + col * 18, 72 + row * 18)
    for col in range(9):
        gui_slot(bay, 8 + col * 18, 130)
    bay.save("gui/container/drive_bay.png")


if __name__ == "__main__":
    make_casings()
    make_panel()
    make_frame()
    make_advancement_background()
    make_server_core()
    make_platform_modules()
    make_drive_bay()
    make_memory_bank()
    make_vent()
    make_gateway()
    make_status_display()
    make_wrench()
    make_guis()
