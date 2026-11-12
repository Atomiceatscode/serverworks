"""Generates every JSON resource the mod ships: block states, models, item definitions,
loot tables, recipes, recipe unlocks, tags and the English language file.

Run: python3 art/make_data.py

Keeping these in one script means a new block needs one entry here instead of six
hand-written files that have to agree with each other.
"""
import json
import shutil
from pathlib import Path

from platforms import PLATFORMS, TIERS

RESOURCES = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = RESOURCES / "assets/serverworks"
DATA = RESOURCES / "data"
MOD = "serverworks"

lang = {}
pickaxe = []
tool_tags = {"needs_stone_tool": [], "needs_iron_tool": [], "needs_diamond_tool": []}
written = 0


def write(path, obj):
    global written
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")
    written += 1


def tex(name):
    return f"{MOD}:block/{name}"


# ---------------------------------------------------------------- shared helpers


def block_item(name, model=None):
    write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{model or name}"}})


def drops_itself(name):
    write(DATA / f"{MOD}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "rolls": 1}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def mineable(name, tool="needs_stone_tool"):
    pickaxe.append(f"{MOD}:{name}")
    tool_tags[tool].append(f"{MOD}:{name}")


def unlock(recipe, item, criterion):
    """A hidden advancement that puts the recipe in the recipe book once the player holds `item`."""
    conditions = {"items": [{"items": item}]}
    write(DATA / f"{MOD}/advancement/recipes/misc/{recipe}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            criterion: {"conditions": conditions, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": f"{MOD}:{recipe}"}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_the_recipe", criterion]],
        "rewards": {"recipes": [f"{MOD}:{recipe}"]}})


MANUAL_RECIPES = []

# Tags have no single picture; the manual shows a typical member.
TAG_EXAMPLES = {"#minecraft:leaves": "minecraft:oak_leaves"}


def shaped(name, pattern, key, count=1, unlock_item="minecraft:iron_ingot"):
    grid = []
    for row in pattern + [""] * (3 - len(pattern)):
        for ch in row.ljust(3):
            item = key.get(ch) if ch != " " else None
            grid.append(TAG_EXAMPLES.get(item, item) if item else None)
    MANUAL_RECIPES.append({"result": f"{MOD}:{name}", "count": count, "shape": "shaped", "items": grid})
    result = {"id": f"{MOD}:{name}"}
    if count > 1:
        result["count"] = count
    write(DATA / f"{MOD}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "key": key, "pattern": pattern, "result": result})
    unlock(name, unlock_item, "has_ingredient")


def shapeless(name, ingredients, unlock_item):
    MANUAL_RECIPES.append({"result": f"{MOD}:{name}", "count": 1, "shape": "shapeless",
                           "items": [TAG_EXAMPLES.get(i, i) for i in ingredients]})
    write(DATA / f"{MOD}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
        "result": {"id": f"{MOD}:{name}"}})
    unlock(name, unlock_item, "has_ingredient")


FACINGS = {
    "north": {},
    "east": {"y": 90},
    "south": {"y": 180},
    "west": {"y": 270},
    "up": {"x": 270},
    "down": {"x": 90},
}


def rack_component_parent():
    """Every rack component: a cube whose front sits one pixel back inside a thin bezel."""
    def bar(frm, to, uv_front):
        faces = {"north": {"uv": uv_front, "texture": "#frame", "cullface": "north"}}
        for side in ("east", "west", "up", "down", "south"):
            faces[side] = {"uv": [0, 0, 1, 1], "texture": "#frame"}
        return {"from": frm, "to": to, "faces": faces}

    write(ASSETS / "models/block/rack_component.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [
            {"from": [0, 0, 1], "to": [16, 16, 16], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south"},
                "east": {"uv": [0, 0, 15, 16], "texture": "#side", "cullface": "east"},
                "west": {"uv": [1, 0, 16, 16], "texture": "#side", "cullface": "west"},
                "up": {"uv": [0, 1, 16, 16], "texture": "#side", "cullface": "up"},
                "down": {"uv": [0, 0, 16, 15], "texture": "#side", "cullface": "down"}}},
            bar([0, 15, 0], [16, 16, 1], [0, 0, 16, 1]),
            bar([0, 0, 0], [16, 1, 1], [0, 15, 16, 16]),
            bar([0, 1, 0], [1, 15, 1], [15, 1, 16, 15]),
            bar([15, 1, 0], [16, 15, 1], [0, 1, 1, 15]),
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]}}})


def front_model(model_name, front):
    """A rack component with this front face, using the shared recessed model."""
    write(ASSETS / f"models/block/{model_name}.json", {
        "parent": f"{MOD}:block/rack_component",
        "textures": {"front": tex(front), "side": tex("rack_panel"), "frame": tex("rack_frame")}})


def component(name, display_name, item_formed=False, animated_when_active=True):
    """A six-way rack component: idle, formed, and (when it has one) a running look."""
    front_model(name, f"{name}_front")
    front_model(f"{name}_on", f"{name}_front_on")
    if animated_when_active:
        front_model(f"{name}_active", f"{name}_front_active")
    variants = {}
    for facing, rotation in FACINGS.items():
        for active in ("false", "true"):
            running = animated_when_active and active == "true"
            variants[f"active={active},facing={facing},formed=false"] = {"model": f"{MOD}:block/{name}", **rotation}
            variants[f"active={active},facing={facing},formed=true"] = {
                "model": f"{MOD}:block/{name}_active" if running else f"{MOD}:block/{name}_on", **rotation}
    write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    block_item(name, f"{name}_on" if item_formed else name)
    drops_itself(name)
    mineable(name)
    lang[f"block.{MOD}.{name}"] = display_name


# ---------------------------------------------------------------- M1: disks and the writer


def disk_writer():
    write(ASSETS / "blockstates/disk_writer.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/disk_writer", **rotation}
        for facing, rotation in FACINGS.items() if facing not in ("up", "down")}})
    write(ASSETS / "models/block/disk_writer.json", {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {"top": tex("disk_writer_top"), "bottom": tex("disk_writer_bottom"),
                     "front": tex("disk_writer_front"), "side": tex("disk_writer_side"),
                     "particle": tex("disk_writer_side")}})
    block_item("disk_writer")
    drops_itself("disk_writer")
    mineable("disk_writer")
    shaped("disk_writer", ["IGI", "RDR", "ICI"], {
        "I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone",
        "D": f"{MOD}:blank_disk", "C": "minecraft:copper_ingot"}, unlock_item=f"{MOD}:blank_disk")


def flat_item(name, parent="minecraft:item/generated"):
    write(ASSETS / f"models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{MOD}:item/{name}"}})


def items():
    flat_item("blank_disk")
    flat_item("mod_disk")
    flat_item("wrench", "minecraft:item/handheld")
    flat_item("pack_cartridge")
    flat_item("link_card")
    write(ASSETS / "items/link_card.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/link_card"}})
    write(ASSETS / "items/pack_cartridge.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{MOD}:item/pack_cartridge"},
        {"type": "minecraft:special", "base": f"{MOD}:item/pack_cartridge", "model": {"type": f"{MOD}:disk_label"}}]}})
    shapeless("link_card", [f"{MOD}:blank_disk", "minecraft:ender_pearl", "minecraft:copper_ingot"], f"{MOD}:blank_disk")
    for name in ("config_card", "world_drive"):
        flat_item(name)
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})
    shapeless("config_card", [f"{MOD}:blank_disk", "minecraft:paper", "minecraft:comparator"], f"{MOD}:blank_disk")
    shapeless("world_drive", [f"{MOD}:blank_disk", "minecraft:map", "minecraft:grass_block"], f"{MOD}:blank_disk")
    flat_item("operators_manual")
    write(ASSETS / "items/operators_manual.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/operators_manual"}})
    shapeless("operators_manual", ["minecraft:book", f"{MOD}:blank_disk"], f"{MOD}:blank_disk")
    write(ASSETS / "items/blank_disk.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/blank_disk"}})
    write(ASSETS / "items/wrench.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/wrench"}})
    # The disk body is a normal flat model; the special layer draws the project icon on it.
    write(ASSETS / "items/mod_disk.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{MOD}:item/mod_disk"},
        {"type": "minecraft:special", "base": f"{MOD}:item/mod_disk", "model": {"type": f"{MOD}:disk_label"}}]}})
    shaped("blank_disk", ["NPN", "PRP", "NIN"], {
        "N": "minecraft:iron_nugget", "P": "minecraft:paper", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"},
        count=4, unlock_item="minecraft:redstone")
    shaped("wrench", ["I I", " I ", " C "], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot"})


# ---------------------------------------------------------------- M2: the rack


def casings():
    previous = None
    upgrade = {"diamond": "minecraft:diamond", "netherite": "minecraft:netherite_ingot"}
    for tier_id, tier_name, _, _, _, tool in TIERS:
        name = f"{tier_id}_rack_casing"
        write(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
        variants = {"formed=false": {"model": f"{MOD}:block/{name}"},
                    "accent=none,formed=true": {"model": f"{MOD}:block/{name}"}}
        for platform_id, *_ in PLATFORMS:
            lit = f"{name}_{platform_id}"
            write(ASSETS / f"models/block/{lit}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(lit)}})
            variants[f"accent={platform_id},formed=true"] = {"model": f"{MOD}:block/{lit}"}
        write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        block_item(name)
        drops_itself(name)
        mineable(name, tool)
        lang[f"block.{MOD}.{name}"] = f"{tier_name} Rack Casing"
        lang[f"{MOD}.tier.{tier_id}"] = tier_name
        if previous is None:
            shaped(name, ["III", "IRI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone"}, count=8)
        elif tier_id == "diamond":
            # Two diamonds for six casings: about seven diamonds for a small rack.
            shaped(name, ["CUC", "C C", "CUC"], {"C": f"{MOD}:{previous}", "U": upgrade[tier_id]}, count=6,
                   unlock_item=f"{MOD}:{previous}")
        else:
            shaped(name, ["CCC", "CUC", "CCC"], {"C": f"{MOD}:{previous}", "U": upgrade[tier_id]}, count=8,
                   unlock_item=f"{MOD}:{previous}")
        previous = name


def drive_bay(name="drive_bay", display="Drive Bay", tool="needs_stone_tool"):
    """A Drive Bay of any tier. It drops itself with its disks inside, like a shulker box."""
    variants = {}
    for disks in range(9):
        for look in ("", "_on", "_active"):
            front_model(f"{name}_{disks}{look}", f"{name}_front_{disks}{look}")
        for facing, rotation in FACINGS.items():
            for active in ("false", "true"):
                variants[f"active={active},disks={disks},facing={facing},formed=false"] = {
                    "model": f"{MOD}:block/{name}_{disks}", **rotation}
                variants[f"active={active},disks={disks},facing={facing},formed=true"] = {
                    "model": f"{MOD}:block/{name}_{disks}{'_active' if active == 'true' else '_on'}", **rotation}
    write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    block_item(name, f"{name}_0")
    write(DATA / f"{MOD}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}",
                         "modifier": {"type": "minecraft:copy_components", "source": "block_entity",
                                      "include": ["minecraft:custom_name", "minecraft:container"]}}],
            "rolls": 1}],
        "random_sequence": f"{MOD}:blocks/{name}"})
    mineable(name, tool)
    lang[f"block.{MOD}.{name}"] = display


def memory_bank():
    """Shows one lit module per RAM stick it holds."""
    variants = {}
    for sticks in range(5):
        for look in ("", "_on", "_active"):
            front_model(f"memory_bank_{sticks}{look}", f"memory_bank_front_{sticks}{look}")
        for facing, rotation in FACINGS.items():
            for active in ("false", "true"):
                variants[f"active={active},facing={facing},formed=false,sticks={sticks}"] = {
                    "model": f"{MOD}:block/memory_bank_{sticks}", **rotation}
                variants[f"active={active},facing={facing},formed=true,sticks={sticks}"] = {
                    "model": f"{MOD}:block/memory_bank_{sticks}{'_active' if active == 'true' else '_on'}", **rotation}
    write(ASSETS / "blockstates/memory_bank.json", {"variants": variants})
    block_item("memory_bank", "memory_bank_4_on")
    drops_itself("memory_bank")
    mineable("memory_bank")
    lang[f"block.{MOD}.memory_bank"] = "Memory Bank"


def transmute(name, input_item, material, unlock_item):
    """The next tier of a block, made from the last one plus a material, keeping what it holds."""
    MANUAL_RECIPES.append({"result": f"{MOD}:{name}", "count": 1, "shape": "shapeless", "items": [input_item, material]})
    write(DATA / f"{MOD}/recipe/{name}.json", {
        "type": "minecraft:crafting_transmute", "category": "misc", "input": input_item, "material": material,
        "result": {"id": f"{MOD}:{name}"}})
    unlock(name, unlock_item, "has_ingredient")


def memory_sticks():
    sizes = [1, 2, 4, 8, 16]
    extra = {2: "minecraft:gold_ingot", 4: "minecraft:diamond", 8: "minecraft:emerald", 16: "minecraft:netherite_scrap"}
    for gb in sizes:
        name = f"memory_stick_{gb}gb"
        flat_item(name)
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})
        lang[f"item.{MOD}.{name}"] = f"RAM Stick ({gb} GB)"
        if gb == 1:
            shapeless(name, ["minecraft:iron_ingot", "minecraft:redstone", "minecraft:redstone", "minecraft:gold_nugget"],
                      "minecraft:redstone")
        else:
            smaller = f"{MOD}:memory_stick_{gb // 2}gb"
            shapeless(name, [smaller, smaller, extra[gb]], smaller)
    lang[f"item.{MOD}.memory_stick.adds"] = "Adds %s GB to a rack's server"
    lang[f"item.{MOD}.memory_stick.use"] = "Put it in a rack's Memory Bank"


def gateway():
    """Six-way, and its formed look depends on whether the rack's server is running."""
    front_model("gateway", "gateway_front")
    for look in ("on", "open", "restricted"):
        front_model(f"gateway_{look}", f"gateway_front_{look}")
    variants = {}
    for facing, rotation in FACINGS.items():
        for portal, look in (("closed", "gateway_on"), ("open", "gateway_open"), ("restricted", "gateway_restricted")):
            for active in ("false", "true"):
                variants[f"active={active},facing={facing},formed=false,portal={portal}"] = {
                    "model": f"{MOD}:block/gateway", **rotation}
                variants[f"active={active},facing={facing},formed=true,portal={portal}"] = {
                    "model": f"{MOD}:block/{look}", **rotation}
    write(ASSETS / "blockstates/gateway.json", {"variants": variants})
    block_item("gateway", "gateway_open")
    drops_itself("gateway")
    mineable("gateway")
    lang[f"block.{MOD}.gateway"] = "Gateway"


def rack_components():
    casing = f"{MOD}:iron_rack_casing"
    component("server_core", "Server Core", item_formed=True)
    shaped("server_core", ["COC", "RDR", "CPC"], {
        "C": casing, "O": "minecraft:observer", "R": "minecraft:repeater", "D": "minecraft:diamond",
        "P": "minecraft:comparator"}, unlock_item=casing)

    drive_bay()
    shaped("drive_bay", ["IBI", "BCB", "IRI"], {
        "I": "minecraft:iron_ingot", "B": f"{MOD}:blank_disk", "C": casing, "R": "minecraft:redstone"},
        unlock_item=casing)

    transmute("advanced_drive_bay", f"{MOD}:drive_bay", "minecraft:diamond", f"{MOD}:drive_bay")
    drive_bay("advanced_drive_bay", "Advanced Drive Bay", "needs_iron_tool")
    transmute("elite_drive_bay", f"{MOD}:advanced_drive_bay", "minecraft:netherite_ingot", f"{MOD}:advanced_drive_bay")
    drive_bay("elite_drive_bay", "Elite Drive Bay", "needs_diamond_tool")

    memory_bank()
    memory_sticks()
    shaped("memory_bank", ["GQG", "QCQ", "GRG"], {
        "G": "minecraft:gold_ingot", "Q": "minecraft:quartz", "C": casing, "R": "minecraft:redstone"},
        unlock_item=casing)

    component("vent", "Vent")
    shaped("vent", [" B ", "BCB", " B "], {"B": "minecraft:iron_bars", "C": casing}, unlock_item=casing)

    gateway()
    shaped("gateway", ["OEO", "ECE", "OEO"], {"O": "minecraft:obsidian", "E": "minecraft:ender_pearl", "C": casing},
           unlock_item=casing)

    component("status_display", "Status Display", animated_when_active=False)
    shaped("status_display", ["GGG", "GCG", "LRL"], {
        "G": "minecraft:glass_pane", "C": casing, "L": "minecraft:glowstone_dust", "R": "minecraft:redstone"},
        unlock_item=casing)

    for platform_id, display, group, _, _, ingredient, _ in PLATFORMS:
        name = f"{platform_id}_platform_module"
        component(name, f"{display} Platform Module", item_formed=True)
        shapeless(name, [casing, f"{MOD}:blank_disk", "minecraft:redstone", ingredient], casing)
        lang[f"{MOD}.platform.{platform_id}"] = display


# ---------------------------------------------------------------- text


def language():
    lang.update({
        "itemGroup.serverworks.main": "Serverworks",
        "block.serverworks.disk_writer": "Disk Writer",
        "item.serverworks.blank_disk": "Blank Disk",
        "item.serverworks.mod_disk": "Mod Disk",
        "item.serverworks.wrench": "Rack Wrench",
        "item.serverworks.mod_disk.unwritten": "Nothing written",
        "item.serverworks.mod_disk.by": "%1$s by %2$s",
        "item.serverworks.mod_disk.runs_on": "Runs on: %s",
        "item.serverworks.mod_disk.client_required": "Players must install this too",
        "item.serverworks.mod_disk.server_only": "Players can join without installing it",
        "container.serverworks.disk_writer": "Disk Writer",
        "container.serverworks.drive_bay": "Drive Bay",
        "container.serverworks.memory_bank": "Memory Bank",
        "serverworks.memory_bank.total": "+%s GB",
        "serverworks.rack.problem.too_large_for_casing": "Too big for this casing: iron racks go up to 5 blocks a side, diamond 7, netherite 9.",
        "container.serverworks.server_core": "Server Core",

        "serverworks.project_type.mod": "Mod",
        "serverworks.project_type.plugin": "Plugin",
        "serverworks.project_type.datapack": "Data Pack",
        "serverworks.project_type.modpack": "Modpack",
        "serverworks.project_type.resourcepack": "Resource Pack",
        "serverworks.project_type.shader": "Shader",
        "serverworks.project_type.other": "Project",

        "serverworks.platform_group.vanilla": "Vanilla",
        "serverworks.platform_group.mod": "Mod platform",
        "serverworks.platform_group.plugin": "Plugin platform",
        "serverworks.platform_group.proxy": "Proxy platform",

        "serverworks.disk_writer.search": "Search or @author",
        "serverworks.disk_writer.type.all": "All",
        "serverworks.disk_writer.sort.relevance": "Relevant",
        "serverworks.disk_writer.sort.downloads": "Popular",
        "serverworks.disk_writer.sort.follows": "Followed",
        "serverworks.disk_writer.sort.newest": "Newest",
        "serverworks.disk_writer.sort.updated": "Updated",
        "serverworks.disk_writer.loading": "Searching the catalog...",
        "serverworks.disk_writer.no_results": "Nothing matches that search.",
        "serverworks.disk_writer.error.unavailable": "Can't reach the catalog. Check your connection and try again.",
        "serverworks.disk_writer.error.rate_limited": "Too many searches. Wait a moment and try again.",
        "serverworks.disk_writer.pick": "Pick a project to write onto a disk",
        "serverworks.disk_writer.count": "%s results",
        "serverworks.disk_writer.powered_by": "Modrinth catalog",
        "serverworks.disk_writer.downloads": "↓ %s",
        "serverworks.disk_writer.status.checking": "Checking the catalog...",
        "serverworks.disk_writer.status.ready": "Ready to write",
        "serverworks.disk_writer.status.need_disk": "Insert a blank disk",
        "serverworks.disk_writer.outcome.ready": "Ready to write",
        "serverworks.disk_writer.outcome.not_found": "No longer in the catalog",
        "serverworks.disk_writer.outcome.rate_limited": "Slow down a little",
        "serverworks.disk_writer.outcome.unavailable": "Server can't reach the catalog",
        "serverworks.disk_writer.outcome.refused": "Server refused that project",

        "serverworks.rack.problem.none": "Rack formed.",
        "serverworks.rack.problem.too_small": "A rack must be at least 3 blocks along every side.",
        "serverworks.rack.problem.too_large": "Too big, or touching other rack blocks. Keep it within 7 blocks a side.",
        "serverworks.rack.problem.missing_block": "The frame has a gap.",
        "serverworks.rack.problem.missing_block.at": "Missing a rack block at %s, %s, %s.",
        "serverworks.rack.problem.edge_not_casing": "Edges and corners must be casing.",
        "serverworks.rack.problem.edge_not_casing.at": "Edges and corners must be casing: replace %s, %s, %s.",
        "serverworks.rack.problem.not_hollow": "The inside of the rack must be empty.",
        "serverworks.rack.problem.not_hollow.at": "The inside must be empty: clear %s, %s, %s.",
        "serverworks.rack.problem.multiple_cores": "A rack takes only one Server Core.",
        "serverworks.rack.problem.multiple_cores.at": "Only one Server Core per rack: remove %s, %s, %s.",
        "serverworks.rack.problem.no_platform": "Add a Platform Module to choose the server software.",
        "serverworks.rack.problem.multiple_platforms": "A rack takes only one Platform Module.",
        "serverworks.rack.problem.multiple_platforms.at": "Only one Platform Module per rack: remove %s, %s, %s.",
        "serverworks.rack.problem.no_drive_bay": "Add a Drive Bay to hold disks.",
        "serverworks.rack.problem.no_memory": "Add a Memory Bank.",
        "serverworks.rack.problem.no_vent": "Add a Vent to cool the rack.",
        "serverworks.rack.problem.not_enough_vents": "Add another Vent: one for every 4 Drive Bays and Memory Banks.",

        "serverworks.wrench.no_core": "No Server Core within reach of this block.",
        "serverworks.wrench.formed": "Rack formed: %s x %s x %s, running %s.",

        "serverworks.server_core.reading": "Reading the rack...",
        "serverworks.server_core.formed": "Rack formed",
        "serverworks.server_core.not_formed": "Rack not formed",
        "serverworks.server_core.summary": "%1$s (%2$s)",
        "serverworks.server_core.help": "Build a hollow box of casing, %s to %s blocks a side. Add a Platform Module, Drive Bay, Memory Bank and Vent. A Rack Wrench shows what is missing.",
        "serverworks.server_core.frame": "Frame",
        "serverworks.server_core.size": "Size",
        "serverworks.server_core.vents": "Vents",
        "serverworks.server_core.vents.value": "%s of %s",
        "serverworks.server_core.memory": "Memory",
        "serverworks.server_core.memory.value": "%s GB",
        "serverworks.server_core.frame.value": "%s (to %s)",
        "serverworks.server_core.players": "Players",
        "serverworks.server_core.players.value": "up to %s",
        "serverworks.server_core.bays": "Bays",
        "serverworks.server_core.disks": "Disks loaded: %s of %s",
        "serverworks.server_core.incompatible": "%s won't run here",
        "serverworks.server_core.no_disks": "Put Mod Disks in a Drive Bay to load them.",
        "serverworks.server_core.disk_incompatible": "Won't run on %s",
        "serverworks.server_core.start": "Start Server",
        "serverworks.server_core.stop": "Stop Server",
        "serverworks.server_core.start.not_allowed": "Only an operator can start or stop rack servers.",
        "serverworks.server_core.start.not_formed": "Finish building the rack first.",
        "serverworks.server_core.start.unsupported": "This platform can't be launched.",
        "serverworks.server_core.tab.rack": "Rack",
        "serverworks.server_core.tab.console": "Console",
        "serverworks.server_core.join_local": "Join at localhost:%s",
        "serverworks.server_core.join_port": "Listening on port %s",
        "serverworks.server_core.console.empty": "Nothing here yet. Start the server to see its output.",
        "serverworks.server_core.console.operators": "Only operators can see a rack's console.",
        "serverworks.server_core.console.hint": "Type a command",
        "serverworks.server_core.console.hint.tooltip": "Type a server command and press Enter, without the /",
        "serverworks.server_core.console.not_running": "Server not running",
        "serverworks.server_core.console.not_running.tooltip": "Start the server to send it commands",
        "serverworks.server_core.console.newer": "%s newer",

        "serverworks.instance.state.idle": "Ready",
        "serverworks.instance.state.resolving": "Preparing...",
        "serverworks.instance.state.downloading": "Downloading %s%%",
        "serverworks.instance.state.installing": "Installing...",
        "serverworks.instance.state.starting": "Starting...",
        "serverworks.instance.state.running": "Running",
        "serverworks.instance.state.stopping": "Stopping...",
        "serverworks.instance.state.stopped": "Stopped",
        "serverworks.instance.state.crashed": "Crashed",
        "serverworks.instance.state.failed": "Couldn't start",
        "serverworks.instance.failure.none": "",
        "serverworks.instance.failure.eula": "The Minecraft EULA must be accepted first.",
        "serverworks.instance.failure.download": "A download failed. Check the connection and try again.",
        "serverworks.instance.failure.install": "Couldn't set up the server's files.",
        "serverworks.instance.failure.launch": "Couldn't launch the server. Hover for details.",
        "serverworks.instance.failure.content": "A disk couldn't be installed. Hover for details.",
        "serverworks.instance.failure.unsupported": "This platform can't be launched yet.",
        "serverworks.instance.failure.exited": "The server shut down on its own. See the console.",

        "serverworks.instance.state.sleeping": "Asleep",
        "serverworks.server_core.sleeping": "Nobody was on, so it went to sleep. Use the Gateway or Start to wake it.",
        "serverworks.server_core.join_players": "%1$s \u00b7 %2$s/%3$s online",
        "serverworks.server_core.gateway_restricted": "%1$s \u00b7 Players need: %2$s",
        "serverworks.server_core.pack": "Client Pack",
        "serverworks.server_core.pack.tooltip": "Save a modpack of this server's mods, to import into the Modrinth App or Prism Launcher.",
        "serverworks.server_core.pack.unavailable": "Start the server once to build its client pack.",
        "serverworks.notice.no_pack": "No client pack yet. Start the server once first.",
        "serverworks.pack.saved": "Saved %s to serverworks/client-packs",
        "serverworks.pack.failed": "Couldn't save the client pack.",

        "serverworks.trip.leaving": "Heading to the rack server",
        "serverworks.trip.returning": "Heading back",
        "serverworks.trip.return": "Return to %s",
        "serverworks.gateway.destination": "%s rack server",
        "serverworks.gateway.waking": "Waking the server up. Try the Gateway again in a moment.",
        "serverworks.gateway.starting": "The server is still starting. Try again in a moment.",
        "serverworks.gateway.closed": "The server isn't running. Start it from the Server Core.",
        "serverworks.gateway.restricted": "Players need these mods to join: %s. Save the Client Pack from the Server Core.",

        "serverworks.display.online": "Online",
        "serverworks.display.online_mods": "Online \u00b7 mods needed",
        "serverworks.display.players": "%s/%s players",
        "serverworks.display.local": "localhost:%s",
        "serverworks.display.port": "Port %s",
        "serverworks.display.sleeping": "Asleep",
        "serverworks.display.wake": "Use the Gateway",
        "serverworks.display.offline": "Offline",
        "serverworks.display.address": "%s:%s",
        "serverworks.jade.not_formed": "Not part of a formed rack",
        "serverworks.showcase.built": "Built the Serverworks showcase. Walk south to explore it; disks arrive in a few seconds.",
        "config.jade.plugin_serverworks.rack": "Serverworks racks",
        "serverworks.jade.platform": "%s rack",
        "serverworks.jade.disks": "Disks: %s/%s",
        "serverworks.config.title": "Serverworks Settings",
        "serverworks.config.subtitle": "For rack servers run by this copy of the game. Port changes apply after a restart.",
        "serverworks.config.idle": "Sleep after (minutes)",
        "serverworks.config.idle.hint": "Stop an empty server after this many minutes. It wakes when someone uses its Gateway. 0 keeps servers running.",
        "serverworks.config.max_running": "Racks running at once",
        "serverworks.config.max_running.hint": "How many rack servers may run at the same time. Each one uses its rack's memory.",
        "serverworks.config.first_port": "First port",
        "serverworks.config.first_port.hint": "Rack servers use ports from here up to the last port.",
        "serverworks.config.last_port": "Last port",
        "serverworks.config.last_port.hint": "The highest port a rack server may use.",
        "serverworks.config.operators_only": "Only operators start racks",
        "serverworks.config.operators_only.hint": "Starting a rack runs downloaded code on this computer. When on, only you (in singleplayer) or operators (on a server) can start or stop racks.",
        "serverworks.config.public_address": "Public address",
        "serverworks.config.public_address.hint": "On a dedicated server, the address players use to reach it. Shown with each rack's port on Status Displays.",

        "item.serverworks.pack_cartridge": "Pack Cartridge",
        "item.serverworks.link_card": "Link Card",
        "item.serverworks.link_card.linked": "Link Card (%s rack)",
        "item.serverworks.link_card.blank": "Sneak-use on a rack's Server Core to link it",
        "item.serverworks.link_card.at": "Rack at %s, %s, %s",
        "item.serverworks.link_card.use": "Put it in a proxy rack's Drive Bay",
        "serverworks.link.not_formed": "That rack isn't formed yet.",
        "serverworks.link.proxy": "Link Cards point a proxy at a server rack, not at another proxy.",
        "serverworks.link.linked": "Linked to the %s rack at %s, %s, %s",
        "serverworks.server_core.disks_links": "Plugins: %s \u00b7 Linked racks: %s",
        "serverworks.platform.waterfall.discontinued": "Waterfall is discontinued and can't connect players on this version. Use Velocity or BungeeCord.",
        "serverworks.notice.no_links": "Put Link Cards for the racks behind this proxy in its Drive Bay.",
        "serverworks.notice.link_unloaded": "A linked rack is too far away to be loaded.",
        "serverworks.notice.link_invalid": "A Link Card points at a rack that isn't formed, or at another proxy.",
        "serverworks.notice.restart_linked": "Stop the linked racks first. The proxy starts them itself.",
        "serverworks.notice.one_cartridge": "A rack can hold only one Pack Cartridge.",
        "serverworks.gateway.behind_proxy": "This server is behind a proxy. Use the proxy rack's Gateway.",

        "serverworks.eula.title": "Minecraft EULA",
        "serverworks.eula.body": "Running a Minecraft server means agreeing to the Minecraft End User License Agreement. Your agreement is recorded once for every rack on this computer.",
        "serverworks.eula.read": "Read it",
        "serverworks.eula.agree": "I agree",
        "serverworks.eula.cancel": "Cancel",

        "serverworks.notice.not_allowed": "Only an operator can do that.",
        "serverworks.notice.eula_save_failed": "Couldn't save your EULA agreement.",
        "serverworks.notice.eula_required": "Agree to the Minecraft EULA first.",
        "serverworks.notice.not_formed": "The rack isn't formed.",
        "serverworks.notice.platform_unsupported": "This platform can't be launched yet.",
        "serverworks.notice.incompatible_disks": "Remove the disks that won't run on this platform first.",
        "serverworks.notice.too_many": "Three rack servers are already running.",
        "serverworks.notice.no_port": "No free port left for another server.",
        "serverworks.notice.not_running": "The server isn't running.",
    })
    write(ASSETS / "lang/en_us.json", dict(sorted(lang.items())))


SOUNDS = {
    "rack_hum": "Server rack hums",
    "rack_boot": "Server starts up",
    "rack_power_down": "Server powers down",
    "rack_alarm": "Server alarm",
    "drive_insert": "Disk inserted",
    "drive_eject": "Disk ejected",
    "drive_seek": "Drive reads",
    "disk_write": "Disk written",
    "gateway_open": "Gateway opens",
    "gateway_travel": "Gateway whooshes",
}


def sounds():
    write(ASSETS / "sounds.json", {name: {"subtitle": f"subtitles.{MOD}.{name}", "sounds": [f"{MOD}:{name}"]}
                                   for name in SOUNDS})
    for name, subtitle in SOUNDS.items():
        lang[f"subtitles.{MOD}.{name}"] = subtitle


ADVANCEMENTS = [
    # name, parent, icon, frame, trigger (None = granted by the mod), title, description
    ("root", None, "server_core", "task", ("inventory_changed", ["disk_writer", "blank_disk", "operators_manual"]),
     "Serverworks", "Build a server you can walk into"),
    ("write_disk", "root", "mod_disk", "task", ("inventory_changed", ["mod_disk"]),
     "Burned to Disk", "Write a project from the catalog onto a Mod Disk"),
    ("cartridge", "write_disk", "pack_cartridge", "task", ("inventory_changed", ["pack_cartridge"]),
     "The Whole Pack", "Write a modpack onto a Pack Cartridge"),
    ("build_rack", "write_disk", "iron_rack_casing", "task", None,
     "Rack and Roll", "Build a server rack that forms"),
    ("start_server", "build_rack", "server_core", "task", None,
     "It's Alive", "Start a server from a rack"),
    ("go_through", "start_server", "gateway", "goal", None,
     "Through the Gateway", "Walk into your own server"),
    ("proxy", "go_through", "link_card", "goal", None,
     "Network Engineer", "Start a proxy rack with the racks behind it"),
    ("netherite_rack", "build_rack", "netherite_rack_casing", "challenge", None,
     "Enterprise Grade", "Build a rack framed entirely in netherite"),
]


def advancements():
    for name, parent, icon, frame, trigger, title, description in ADVANCEMENTS:
        if trigger is None:
            criteria = {"done": {"trigger": "minecraft:impossible"}}
        else:
            kind, items = trigger
            criteria = {"has_item": {"trigger": f"minecraft:{kind}",
                                     "conditions": {"items": [{"items": [f"{MOD}:{i}" for i in items]}]}}}
        display = {
            "icon": {"id": f"{MOD}:{icon}"},
            "title": {"translate": f"advancements.{MOD}.{name}.title"},
            "description": {"translate": f"advancements.{MOD}.{name}.description"},
            "frame": frame,
            "show_toast": parent is not None,
            "announce_to_chat": parent is not None,
        }
        advancement = {"criteria": criteria, "display": display, "requirements": [list(criteria)]}
        if parent is None:
            display["background"] = f"{MOD}:gui/advancements/backgrounds/rack"
        else:
            advancement["parent"] = f"{MOD}:main/{parent}"
        write(DATA / f"{MOD}/advancement/main/{name}.json", advancement)
        lang[f"advancements.{MOD}.{name}.title"] = title
        lang[f"advancements.{MOD}.{name}.description"] = description


MANUAL = {
    "welcome": ("Welcome",
                "Serverworks turns Minecraft servers into machines you build. A server rack holds the software, the "
                "mods and plugins you load into it, and a Gateway you can walk through to play on it.\n"
                "Every rack runs a real server on this computer, downloaded from the official sources and from "
                "Modrinth, the catalog all the disks come from."),
    "disks": ("Disks and the Disk Writer",
              "Put a Blank Disk in a Disk Writer, search the catalog, click a project or drag it onto the right-hand "
              "panel, and take the written disk. Type @name or click an author for one author's projects; the star "
              "keeps favourites; collection:ID lists a Modrinth collection.\n"
              "A modpack makes a Pack Cartridge. A disk takes the newest version that suits the rack, unless you "
              "pin one under the project's name."),
    "building": ("Building a Rack",
                 "A rack is a hollow box of Rack Casing, at least 3 blocks along each side. Edges and corners must "
                 "be casing; components go on the faces; the inside stays empty.\n"
                 "Stronger casing builds bigger racks, with room for more bays: iron up to 5 blocks a side, diamond "
                 "7, netherite 9. The weakest casing counts. A Rack Wrench outlines anything still missing."),
    "parts": ("Rack Components",
              "Server Core: the controls. One per rack.\n"
              "Platform Module: picks the server software. One per rack.\n"
              "Drive Bay: 8 disks; Advanced 16; Elite 32, crafted from the tier below with its disks kept.\n"
              "Memory Bank (optional): holds four RAM Sticks of 1 to 16 GB. Servers get 1 GB plus the sticks, up "
              "to 32 GB, and 10 players per GB, up to 100.\n"
              "Vent: one for every four Drive Bays and Memory Banks.\n"
              "Gateway and Status Display are optional: the way in, and a screen showing who is on."),
    "platforms": ("Platforms",
                  "Vanilla runs data packs. Fabric, Quilt, NeoForge and Forge run mods. Paper, Purpur, Folia, Spigot, "
                  "CraftBukkit and Sponge run plugins. Velocity and BungeeCord are proxies that join several racks "
                  "into one network.\n"
                  "Disks that cannot run on the rack's platform are tinted red in the Server Core. Spigot and "
                  "CraftBukkit are compiled the first time they are used, which takes several minutes."),
    "starting": ("Starting a Server",
                 "Open the Server Core and press Start. The first time, you will be asked to agree to the Minecraft "
                 "EULA for this computer.\n"
                 "The core shows downloads, then the address to join. The Console tab shows the server's output and "
                 "takes commands. In singleplayer only you can start racks; on a server, only operators can."),
    "joining": ("Joining Your Server",
                "Use the Gateway while the server runs: your world is saved and you arrive on the rack's server. "
                "Press B there (change it in Controls), or press Escape and choose Return, to go back. On a "
                "dedicated server you travel through the server's own port, so no other ports need opening.\n"
                "If a disk needs to be installed by players too, the Gateway stays amber and names it. Save the "
                "Client Pack from the Server Core and import it into the Modrinth App or Prism Launcher."),
    "proxies": ("Proxies and Link Cards",
                "Sneak-use a Link Card on a rack's Server Core to point it at that rack. Put Link Cards in a "
                "Velocity or BungeeCord rack's Drive Bay.\n"
                "Starting the proxy starts every linked rack, reachable only through the proxy. Players arrive on "
                "the first linked rack and can move between them with the proxy's /server command."),
    "modpacks": ("Pack Cartridges",
                 "A rack can hold one Pack Cartridge alongside its disks. The modpack must be for this Minecraft "
                 "version and the rack's platform.\n"
                 "Its server-side mods and settings are installed when the server starts. Players need the whole "
                 "pack, which the Client Pack button saves for you."),
    "cards": ("Config Cards and World Drives",
              "Use a Config Card to choose a server's difficulty, game mode, PvP, keep inventory and more, then put "
              "it in a Drive Bay. It applies the next time the rack starts.\n"
              "A World Drive holds a world: name it, give it a seed and type, or copy one of your singleplayer "
              "worlds onto it. In a Drive Bay, the rack plays its world; swap drives to switch worlds."),
    "versions": ("Versions and Updates",
                 "Under a project's name in the Disk Writer, choose a version to pin the disk to it. Unpinned disks "
                 "always get the newest version that suits the rack.\n"
                 "When newer versions are out, the Server Core marks those disks with a green arrow. Update moves "
                 "pinned disks to the newest version and restarts a running server to install everything."),
    "redstone": ("Redstone",
                 "A comparator reading the Server Core gives 0 while the server is off, 1 when it runs with nobody "
                 "on, and up to 15 as it fills.\n"
                 "Turn on redstone control with the button next to Start: then a redstone signal starts the rack and "
                 "losing it stops the rack. Only the owner or operators can turn it on."),
    "backups": ("Backups",
                "The Server Core's Backups tab saves a copy of the rack's world, even while it runs, and lists the "
                "copies kept. Restore puts one back while the server is stopped; the world it replaces is kept too.\n"
                "Automatic backups can be turned on in the settings, with how many to keep."),
    "limits": ("Sleep and Limits",
               "A server with nobody on it for ten minutes goes to sleep. Using its Gateway wakes it up.\n"
               "Up to six rack servers can run at once on one computer. Each keeps its world in the serverworks "
               "folder of your game directory, so stopping a rack never loses anything."),
    "trouble": ("Troubleshooting",
                "Rack not forming? Use the Rack Wrench: blue outlines need a block, orange ones are in the wrong "
                "place, red ones must be cleared from inside.\n"
                "Server failed? The Console tab shows why. Hovering over the error in the Server Core shows the "
                "details. A disk with no version for your platform or Minecraft version is the usual cause."),
}


def manual_text():
    lang["item.serverworks.operators_manual"] = "Operator's Manual"
    lang["item.serverworks.config_card"] = "Config Card"
    lang["item.serverworks.config_card.summary"] = "%s · %s"
    lang["item.serverworks.config_card.hardcore"] = "Hardcore"
    lang["item.serverworks.config_card.use"] = "Use to edit; put it in a Drive Bay to apply it at the next start"
    lang["serverworks.config_card.gamemode"] = "Game Mode"
    lang["serverworks.config_card.hardcore"] = "Hardcore"
    lang["serverworks.config_card.hardcore.tooltip"] = "Only takes effect when the rack's world is first made."
    lang["serverworks.config_card.pvp"] = "PvP"
    lang["serverworks.config_card.view"] = "View"
    lang["serverworks.config_card.simulation"] = "Simulation"
    lang["serverworks.config_card.keep_inventory"] = "Keep Inventory"
    lang["serverworks.config_card.allow_flight"] = "Allow Flight"
    lang["serverworks.config_card.allow_flight.tooltip"] = "Stops the server kicking players for flying, for mods that let them fly."
    lang["serverworks.config_card.spawn_protection"] = "Spawn Protection"
    lang["serverworks.config_card.spawn_protection.tooltip"] = "Blocks around spawn that only operators can change."
    lang["serverworks.config_card.motd"] = "Message of the day"
    lang["serverworks.config_card.motd.hint"] = "Shown in the server list"
    lang["item.serverworks.world_drive"] = "World Drive"
    lang["item.serverworks.world_drive.named"] = "World Drive: %s"
    lang["item.serverworks.world_drive.blank"] = "Use to set up its world"
    lang["item.serverworks.world_drive.seed"] = "Seed: %s"
    lang["item.serverworks.world_drive.copy"] = "Starts as a copy of %s"
    lang["item.serverworks.world_drive.use"] = "Put it in a Drive Bay to play its world on that rack"
    lang["serverworks.world_drive.name"] = "World name"
    lang["serverworks.world_drive.seed"] = "Seed"
    lang["serverworks.world_drive.seed.hint"] = "Leave empty for a random world"
    lang["serverworks.world_drive.type"] = "World Type"
    lang["serverworks.world_drive.type.normal"] = "Default"
    lang["serverworks.world_drive.type.flat"] = "Superflat"
    lang["serverworks.world_drive.type.large_biomes"] = "Large Biomes"
    lang["serverworks.world_drive.type.amplified"] = "Amplified"
    lang["serverworks.world_drive.save"] = "Start from"
    lang["serverworks.world_drive.save.none"] = "A new world"
    lang["serverworks.world_drive.save.tooltip"] = "Copy one of your singleplayer worlds onto the drive. It is copied the first time the drive's world is played; your save is never changed."
    lang["serverworks.world_drive.existing"] = "Seed, type and saved world only matter before the world is first played."
    lang["serverworks.manual.heading"] = "Operator's Manual"
    lang["serverworks.manual.subheading"] = "Everything about building and running racks"
    lang["serverworks.manual.contents"] = "Contents"
    lang["serverworks.manual.page"] = "%s / %s"
    lang["serverworks.manual.recipes.short"] = "Recipes"
    lang["serverworks.manual.recipes.title"] = "Recipes"
    lang["serverworks.manual.recipes.modules"] = "Platform Modules"
    lang["serverworks.manual.recipes.modules.body"] = "its platform's item:"
    lang["serverworks.manual.recipes.shapeless"] = "Any order"
    lang["serverworks.config.direct_ports"] = "Players reach racks"
    lang["serverworks.config.direct_ports.gateway"] = "Through this server"
    lang["serverworks.config.direct_ports.direct"] = "At their own ports"
    lang["serverworks.config.direct_ports.hint"] = "On a dedicated server: through this server's own port (nothing else to open), or directly at each rack's port, which must then be reachable. Restart racks to apply."
    lang["serverworks.display.gateway"] = "Join by the Gateway"
    lang["serverworks.server_core.join_gateway"] = "Join through the Gateway"
    lang["serverworks.recipe_viewer.mod_disk"] = "Written in a Disk Writer: put in a Blank Disk, search Modrinth, pick a project and take the disk. It stands for that project on any rack."
    lang["serverworks.recipe_viewer.pack_cartridge"] = "Written in a Disk Writer from a Blank Disk by picking a modpack. A rack installs the pack's server side."
    lang["serverworks.recipe_viewer.config_card"] = "Use it to choose a server's difficulty, game mode, PvP and more, then put it in a rack's Drive Bay."
    lang["serverworks.recipe_viewer.world_drive"] = "Use it to name a world, give it a seed or copy a saved world. In a rack's Drive Bay, the rack plays its world."
    lang["serverworks.disk_writer.favourites"] = "Show your favourites. Type collection:ID or paste a Modrinth collection link to list a collection."
    lang["serverworks.disk_writer.favourites.empty"] = "No favourites yet. Click the star on a project to keep it here."
    lang["serverworks.disk_writer.star"] = "Add to favourites"
    lang["serverworks.disk_writer.unstar"] = "Remove from favourites"
    lang["serverworks.display.stats"] = "%s GB \u00b7 up %s"
    lang["serverworks.display.uptime"] = "Up %s"
    lang["serverworks.server_core.tab.backups"] = "Backups"
    lang["serverworks.server_core.backup_now"] = "Back Up Now"
    lang["serverworks.server_core.restore"] = "Restore"
    lang["serverworks.server_core.restore.tooltip"] = "Put this world back. The world it replaces is kept in the backups folder, under before-restore."
    lang["serverworks.server_core.restore.stop_first"] = "Stop the server to restore a backup."
    lang["serverworks.server_core.backups.heading"] = "Backups: %s"
    lang["serverworks.server_core.backups.busy"] = "Working..."
    lang["serverworks.server_core.backups.empty"] = "No backups yet. Back Up Now saves a copy of this rack's world."
    lang["serverworks.server_core.backups.operators"] = "Only the rack's owner or operators can see backups."
    lang["serverworks.notice.backed_up"] = "Backup saved."
    lang["serverworks.notice.backup_failed"] = "The backup failed. See the game log."
    lang["serverworks.notice.restored"] = "Backup restored. Start the server to play it."
    lang["serverworks.notice.restore_failed"] = "The restore failed. See the game log."
    lang["serverworks.notice.stop_first"] = "Stop the server first."
    lang["serverworks.notice.no_world"] = "This rack has no world yet. Start it once first."
    lang["serverworks.config.ports"] = "Ports"
    lang["serverworks.config.ports.hint"] = "The first and last port rack servers may use. Restart the game to apply."
    lang["serverworks.config.backups"] = "Backups"
    lang["serverworks.config.backups.hint"] = "Minutes between automatic backups of each running rack (0 for none), and how many to keep."
    lang["serverworks.server_core.update"] = "Update (%s)"
    lang["serverworks.server_core.update.tooltip"] = "Newer versions are out. Pinned disks are moved to the newest version; the others get it the next time the server starts."
    lang["serverworks.server_core.update.restart"] = "Newer versions are out. Pinned disks are moved to the newest version and the server restarts to install every update."
    lang["serverworks.server_core.update.line"] = "Update: %s \u2192 %s"
    lang["serverworks.notice.updating"] = "Updating disks..."
    lang["serverworks.disk_writer.versions.link"] = "Newest version"
    lang["serverworks.disk_writer.versions.pinned"] = "Pinned: %s"
    lang["serverworks.disk_writer.versions.tooltip"] = "Choose a version to lock this disk to, or keep the newest compatible one."
    lang["serverworks.disk_writer.versions.newest"] = "Newest compatible version"
    lang["serverworks.disk_writer.versions.newest.detail"] = "Recommended · chosen when the rack starts"
    lang["serverworks.disk_writer.versions.none"] = "No versions of this project for Minecraft %s."
    lang["serverworks.disk_writer.versions.release"] = "Release"
    lang["serverworks.disk_writer.versions.beta"] = "Beta"
    lang["serverworks.disk_writer.versions.alpha"] = "Alpha"
    lang["item.serverworks.mod_disk.pinned"] = "Pinned to version %s"
    lang["serverworks.server_core.redstone.off"] = "Redstone control: off. Click to let a redstone signal start this rack, and losing it stop the rack. A comparator always reads how full the server is."
    lang["serverworks.server_core.redstone.on"] = "Redstone control: on. A redstone signal starts this rack and losing it stops the rack. Click to turn off."
    lang["serverworks.disk_writer.by_author"] = "Click to see everything by %s"
    lang["serverworks.disk_writer.error.no_author"] = "No Modrinth author has that name."
    lang["serverworks.trip.arrived"] = "You are on the rack's server. To go back to %s, press %s, or press Esc and choose Return."
    lang["key.serverworks.return"] = "Return from Rack Server"
    lang["key.category.serverworks.serverworks"] = "Serverworks"
    lang["serverworks.showcase.welcome"] = "Welcome to the Serverworks Showcase. Walk south and follow the numbered signs; the chests at the starter station hold supplies. Disks arrive in a few seconds."
    lang["serverworks.create_world.showcase"] = "Serverworks Showcase"
    lang["serverworks.create_world.showcase.info"] = "Builds a tour of every Serverworks block, with example racks ready to start. Turns on Creative, Superflat and commands. The tour is built when you first join."
    lang["serverworks.manual.recipes.disks"] = "Mod Disks and Pack Cartridges are not crafted: write them in the Disk Writer from a Blank Disk."
    shorts = {"welcome": "Welcome", "disks": "Disks", "building": "Building", "parts": "Components",
              "platforms": "Platforms", "starting": "Starting", "joining": "Joining", "proxies": "Proxies",
              "modpacks": "Modpacks", "limits": "Limits", "trouble": "Help", "cards": "Cards", "versions": "Versions",
              "redstone": "Redstone", "backups": "Backups"}
    for key, (title, body) in MANUAL.items():
        lang[f"serverworks.manual.{key}.title"] = title
        lang[f"serverworks.manual.{key}.short"] = shorts[key]
        lang[f"serverworks.manual.{key}.body"] = body


def manual_recipes():
    """Every recipe, for the Operator's Manual's recipe pages."""
    order = ["blank_disk", "disk_writer", "operators_manual", "wrench", "iron_rack_casing", "diamond_rack_casing",
             "netherite_rack_casing", "server_core", "drive_bay", "advanced_drive_bay", "elite_drive_bay", "memory_bank",
             "memory_stick_1gb", "memory_stick_2gb", "memory_stick_4gb", "memory_stick_8gb", "memory_stick_16gb",
             "vent", "gateway",
             "status_display", "link_card"]

    def rank(recipe):
        name = recipe["result"].split(":")[1]
        return order.index(name) if name in order else len(order)
    write(ASSETS / "manual_recipes.json", {"recipes": sorted(MANUAL_RECIPES, key=rank)})


def tags():
    write(DATA / "minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": sorted(pickaxe)})
    for tag, values in tool_tags.items():
        if values:
            write(DATA / f"minecraft/tags/block/{tag}.json", {"replace": False, "values": sorted(values)})


def clean():
    """Removes everything this script owns, so renamed or deleted blocks leave nothing behind."""
    for folder in (ASSETS / "blockstates", ASSETS / "models", ASSETS / "items", ASSETS / "lang", ASSETS / "sounds.json",
                   ASSETS / "manual_recipes.json",
                   DATA / MOD, DATA / "minecraft/tags"):
        if folder.is_file():
            folder.unlink()
        elif folder.exists():
            shutil.rmtree(folder)


if __name__ == "__main__":
    clean()
    disk_writer()
    items()
    casings()
    rack_component_parent()
    rack_components()
    sounds()
    advancements()
    manual_recipes()
    manual_text()
    language()
    tags()
    print(f"wrote {written} files")
