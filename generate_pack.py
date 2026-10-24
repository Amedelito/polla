#!/usr/bin/env python3
"""
Genera el resource pack de YTCustom (texturas 16x16 originales + modelos 3D en JSON).
Solo usa la biblioteca estándar. Uso:  python3 tools/generate_pack.py
Salida: ./resourcepack/  (y ./dist/YTCustom-ResourcePack.zip con ZIP determinista)
"""
import json, math, os, random, struct, zlib, zipfile, hashlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACK = os.path.join(ROOT, "resourcepack")
NS = "ytcustom"
PACK_FORMAT = 34  # Minecraft 1.21 - 1.21.1

# ---------------------------------------------------------------- PNG
def write_png(path, w, h, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)

class Canvas:
    def __init__(self, w=16, h=16, bg=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[bg for _ in range(w)] for _ in range(h)]
    def set(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c if len(c) == 4 else (c[0], c[1], c[2], 255)
    def line(self, x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
        for i in range(n + 1):
            t = i / n
            self.set(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)
    def save(self, rel):
        write_png(os.path.join(PACK, "assets", NS, "textures", rel), self.w, self.h, self.px)

def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

OUT = hexc("0b0712")
# ---------------------------------------------------------------- Texturas de objetos
def tex_coin():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.2:
                if d > 6.0: col = hexc("5a3d08")
                elif d > 5.0: col = hexc("a97a10")
                else: col = mix(hexc("f2c14e"), hexc("9a6f12"), (x + y) / 30)
                c.set(x, y, col)
    for (x, y) in [(4, 4), (5, 3), (4, 5)]: c.set(x, y, hexc("fff3b0"))
    for (x, y) in [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9), (8, 9)]:
        c.set(x, y, hexc("8a2be2"))
    for (x, y) in [(7, 6), (7, 7), (6, 6)]: c.set(x, y, hexc("d9a7ff"))
    c.set(8, 8, hexc("3a0b66"))
    c.save("item/hollow_coin.png")

def tex_pluma():
    c = Canvas()
    N = 40
    for i in range(N + 1):
        t = i / N
        sx, sy = 2 + 11.5 * t, 14 - 12 * t
        w = math.sin(math.pi * min(1, t * 1.05)) * 3.2
        for k in range(int(-w * 2), int(w * 2) + 1):
            s = k / 2
            if abs(s) > w: continue
            px, py = sx + s * 0.7, sy + s * 0.7
            col = mix(hexc("2a1560"), hexc("00e5ff"), t * 0.8 + (0.2 if s > 0 else 0))
            if abs(s) > w - 0.6: col = mix(col, hexc("ffffff"), 0.35)
            c.set(px, py, col)
    c.line(1.5, 14.5, 13.5, 1.5, hexc("e8fbff"))
    c.line(1, 15, 3, 13, hexc("8a2be2"))
    c.save("item/pluma_impulso.png")

def tex_heart():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            u, v = (x - 7.5) / 6.2, -(y - 7.0) / 6.2
            if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0:
                t = (x + y) / 30
                c.set(x, y, mix(hexc("c1121f"), hexc("3a0b66"), t))
    for y in range(16):
        for x in range(16):
            if c.px[y][x][3] and any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or c.px[y + dy][x + dx][3] == 0
                                       for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]):
                c.px[y][x] = (*OUT, 255)
    for (x, y) in [(4, 4), (5, 4), (4, 5)]: c.set(x, y, hexc("ff9ea3"))
    c.line(7, 6, 8, 10, hexc("0b0712")); c.set(8, 7, hexc("00e5ff"))
    c.save("item/hollow_heart.png")

def tex_blade():
    c = Canvas()
    c.line(3, 12, 13, 2, hexc("2e2e3d")); c.line(4, 12, 13, 3, hexc("4a4a60"))
    c.line(2.5, 11.5, 12.5, 1.5, hexc("00e5ff")); c.set(13, 2, hexc("ffffff"))
    c.line(2, 10, 6, 14, hexc("d4a017")); c.line(3, 10.5, 5.5, 13, hexc("8a6a0c"))
    c.line(1, 15, 3, 13, hexc("5a3d08")); c.set(1, 15, hexc("8a2be2"))
    c.line(5, 9, 8, 6, hexc("8a2be2"))
    c.save("item/hollow_blade.png")

def tex_pickaxe():
    c = Canvas()
    c.line(2, 14, 10, 6, hexc("4a2f12")); c.line(3, 14, 11, 6, hexc("2b1a08"))
    for (x0, y0, x1, y1) in [(3, 6, 6, 3), (6, 3, 10, 3), (10, 3, 13, 6), (13, 6, 13, 10)]:
        c.line(x0, y0, x1, y1, hexc("2e2e3d"))
        c.line(x0, y0 - 1, x1, y1 - 1, hexc("4a4a60"))
    c.line(4, 5, 7, 2, hexc("00e5ff")); c.line(7, 2, 11, 2, hexc("8a2be2"))
    c.save("item/hollow_pickaxe.png")

def tex_ingot():
    c = Canvas()
    for y in range(5, 11):
        off = (10 - y) * 0.9
        for x in range(int(2 + off), int(12 + off)):
            t = (x - 2 - off) / 10
            col = mix(hexc("2a0845"), hexc("8a2be2"), t) if y > 6 else mix(hexc("5b2a9a"), hexc("00e5ff"), t)
            if y == 10 or x == int(2 + off): col = mix(col, (0, 0, 0), 0.4)
            c.set(x, y, col)
    for (x, y) in [(5, 6), (6, 6), (7, 6)]: c.set(x, y, hexc("d9fbff"))
    c.save("item/hollowite_ingot.png")

def tex_ore():
    rnd = random.Random(1337)
    c = Canvas(16, 16, (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-10, 10)
            c.set(x, y, (max(0, 40 + n), max(0, 40 + n), max(0, 52 + n)))
    for (cx, cy) in [(3, 3), (10, 4), (6, 10), (12, 12), (2, 12)]:
        for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)]:
            if rnd.random() < 0.85:
                c.set(cx + dx, cy + dy, mix(hexc("8a2be2"), hexc("00e5ff"), rnd.random()))
        c.set(cx, cy, hexc("e6fdff"))
    c.save("block/hollowite_ore.png")

# ---------------------------------------------------------------- Paleta de colores para modelos 3D
PALETTE = ["0a0a0f", "1c1c26", "2e2e3d", "4a4a60", "d8d0c0", "8a2be2", "c77dff", "00e5ff",
           "c1121f", "ff3b3b", "d4a017", "2a0845", "ffffff", "ff7a00", "5a3d08", "9ad8e0"]
C = {n: i for i, n in enumerate(["black", "char", "steel", "steelL", "bone", "purple", "violet", "cyan",
                                  "crim", "red", "gold", "dpurple", "white", "orange", "brown", "ice"])}

def tex_palette():
    cv = Canvas(16, 16, (0, 0, 0, 255))
    for i in range(64):
        col = hexc(PALETTE[i % len(PALETTE)])
        cx, cy = (i % 8) * 2, (i // 8) * 2
        for dx in range(2):
            for dy in range(2):
                cv.set(cx + dx, cy + dy, col)
    cv.save("item/palette.png")

# ---------------------------------------------------------------- Modelos
def box(x1, y1, z1, x2, y2, z2, color):
    i = C[color]
    u, v = (i % 8) * 2, (i // 8) * 2
    face = {"uv": [u, v, u + 2, v + 2], "texture": "#0"}
    return {"from": [x1, y1, z1], "to": [x2, y2, z2],
            "faces": {k: dict(face) for k in ("north", "south", "east", "west", "up", "down")}}

def mirror(b, cx=8.0):
    """Refleja un cubo respecto al plano x=cx."""
    f, t = b["from"], b["to"]
    nb = json.loads(json.dumps(b))
    nb["from"][0], nb["to"][0] = 2 * cx - t[0], 2 * cx - f[0]
    return nb

def model(elements):
    return {"textures": {"0": f"{NS}:item/palette", "particle": f"{NS}:item/palette"}, "elements": elements}

def king(phase):
    eye = {1: "cyan", 2: "red", 3: "white"}[phase]
    core = {1: "purple", 2: "violet", 3: "red"}[phase]
    armor = {1: "steel", 2: "steel", 3: "char"}[phase]
    trim = {1: "gold", 2: "violet", 3: "crim"}[phase]
    els = []
    half = []  # elementos del lado derecho (se reflejan al izquierdo)
    half += [box(5, 0, 6, 7.6, 10, 10, armor), box(4.6, 0, 5.4, 7.8, 2.6, 10.6, "char"),
             box(4.8, 6, 5.7, 7.8, 9, 6.0, "steelL"), box(3.7, 20, 5.3, 6.2, 23.4, 10.7, armor),
             box(4.2, 23.4, 7.2, 5.2, 26.4 + phase * 1.2, 8.2, "steelL"), box(2.4, 10, 6.4, 4.6, 20, 9.6, "char"),
             box(2.0, 7.6, 6.0, 4.8, 12, 10.0, armor), box(2.6, 5.4, 6.6, 3.2, 8, 7.4, "bone"),
             box(3.6, 5.4, 6.6, 4.2, 8, 7.4, "bone"), box(2.2, 20.4, 5.1, 3.2, 21.2, 10.9, trim),
             box(6.7, 23.4, 9.85, 7.7, 24.4, 10.05, eye)]
    els += half + [mirror(b) for b in half]
    els += [box(5, 9, 6, 11, 12, 10, armor), box(5, 11, 5.8, 11, 12, 10.2, trim),
            box(4.5, 12, 6, 11.5, 22, 10, "char"), box(6.5, 14, 10.0, 9.5, 19, 10.3, core),
            box(7.4, 15.2, 10.3, 8.6, 17.8, 10.45, "white"),
            box(5.5, 19.6, 10.0, 10.5, 20.2, 10.25, "bone"), box(5.5, 12.4, 10.0, 10.5, 13.0, 10.25, "bone"),
            box(6, 22, 6.2, 10, 26, 9.8, "bone"), box(6.5, 22, 9.0, 9.5, 22.9, 9.9, "black"),
            box(6.9, 23.0, 9.95, 7.5, 23.6, 10.0, "black"),
            box(5.7, 25.4, 5.9, 10.3, 27, 10.1, trim), box(7.5, 26, 10.1, 8.5, 26.8, 10.35, core),
            box(7.4, 27, 7.4, 8.6, 31.5, 8.6, trim), box(6, 27, 7.5, 6.9, 30, 8.3, trim),
            box(9.1, 27, 7.5, 10, 30, 8.3, trim), box(7.5, 27, 6, 8.5, 30, 6.9, trim), box(7.5, 27, 9.1, 8.5, 30, 10, trim),
            box(4, 6, 5, 12, 22, 5.6, "dpurple"), box(5, 3, 5.2, 11, 6, 5.7, "dpurple"),
            box(7.4, 22, 5.6, 8.6, 23, 6.0, core)]
    if phase >= 2:
        els += [box(3.0, 26.0, 7.0, 4.0, 31.0, 8.0, "crim" if phase == 3 else "violet"),
                box(12.0, 26.0, 7.0, 13.0, 31.0, 8.0, "crim" if phase == 3 else "violet"),
                box(5.2, 27, 7.4, 6.0, 33.0, 8.4, core), box(10.0, 27, 7.4, 10.8, 33.0, 8.4, core),
                box(4.0, 12, 10.0, 12.0, 12.8, 10.2, core)]
    if phase == 3:
        for side in (1, -1):
            cx = 8 + side * 4.5
            for k, (dx, y0, y1, w) in enumerate([(2.0, 16, 30, 0.8), (5.0, 14, 27, 0.8), (8.0, 12, 23, 0.8), (11.0, 11, 19, 0.8)]):
                x0 = 8 + side * dx
                x1 = x0 + side * 2.2
                lo, hi = (x0, x1) if x0 < x1 else (x1, x0)
                els.append(box(lo, y0, 4.2 - k * 0.15, hi, y1, 5.0 - k * 0.15, "crim" if k % 2 == 0 else "dpurple"))
        els += [box(6.0, 27.2, 6.0, 7.2, 34.0, 7.2, "orange"), box(8.8, 27.2, 6.0, 10.0, 34.0, 7.2, "orange"),
                box(5.5, 21.0, 10.0, 10.5, 22.0, 10.4, "orange")]
    return model(els)

def halo(color):
    els = [box(1, 8, 1, 15, 8.6, 2, color), box(1, 8, 14, 15, 8.6, 15, color),
           box(1, 8, 2, 2, 8.6, 14, color), box(14, 8, 2, 15, 8.6, 14, color)]
    for (x, z) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        els.append(box(x, 8.6, z, x + 1, 12.5, z + 1, "white" if color != "cyan" else "ice"))
    return model(els)

def wraith():
    els = [box(5, 3, 5, 11, 13, 11, "dpurple"), box(6, 0, 6, 10, 3, 10, "black"), box(7, -2, 7, 9, 0, 9, "black"),
           box(5.5, 13, 5.5, 10.5, 17, 10.5, "steel"), box(6, 14.5, 10.5, 7.3, 15.8, 10.7, "cyan"),
           box(8.7, 14.5, 10.5, 10, 15.8, 10.7, "cyan"), box(6.5, 12.4, 10.5, 9.5, 13.2, 10.6, "black"),
           box(2.5, 7, 6.5, 5, 10, 9.5, "char"), box(11, 7, 6.5, 13.5, 10, 9.5, "char"),
           box(11, 9, 7.6, 17, 14, 8.4, "violet"), box(-1, 9, 7.6, 5, 14, 8.4, "violet"),
           box(6.5, 17, 7.5, 9.5, 20, 8.5, "purple")]
    return model(els)

def jdump(rel, data):
    path = os.path.join(PACK, "assets", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)

def override_item(base, parent, layer0, overrides):
    jdump(f"minecraft/models/item/{base}.json", {
        "parent": parent, "textures": {"layer0": layer0} if layer0 else {},
        "overrides": [{"predicate": {"custom_model_data": cmd}, "model": m} for cmd, m in overrides]})

def build():
    for d in (os.path.join(PACK, "assets")):
        pass
    tex_coin(); tex_pluma(); tex_heart(); tex_blade(); tex_pickaxe(); tex_ingot(); tex_ore(); tex_palette()

    # modelos de objetos 2D
    def flat(name, parent="minecraft:item/generated"):
        jdump(f"{NS}/models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
    for n in ("hollow_coin", "pluma_impulso", "hollow_heart", "hollowite_ingot"): flat(n)
    for n in ("hollow_blade", "hollow_pickaxe"): flat(n, "minecraft:item/handheld")

    # bloque personalizado
    jdump(f"{NS}/models/block/hollowite_ore.json", {"parent": "minecraft:block/cube_all",
                                                      "textures": {"all": f"{NS}:block/hollowite_ore"}})
    jdump(f"{NS}/models/item/hollowite_ore.json", {"parent": f"{NS}:block/hollowite_ore"})

    # modelos 3D de entidades
    for p in (1, 2, 3): jdump(f"{NS}/models/item/hollow_king_p{p}.json", king(p))
    jdump(f"{NS}/models/item/king_halo_p1.json", halo("cyan"))
    jdump(f"{NS}/models/item/king_halo_p2.json", halo("violet"))
    jdump(f"{NS}/models/item/king_halo_p3.json", halo("crim"))
    jdump(f"{NS}/models/item/hollow_wraith.json", wraith())

    # overrides sobre los objetos base de vanilla (CustomModelData)
    override_item("gold_nugget", "minecraft:item/generated", "minecraft:item/gold_nugget", [(2001, f"{NS}:item/hollow_coin")])
    override_item("feather", "minecraft:item/generated", "minecraft:item/feather", [(2002, f"{NS}:item/pluma_impulso")])
    override_item("nether_star", "minecraft:item/generated", "minecraft:item/nether_star", [(2003, f"{NS}:item/hollow_heart")])
    override_item("netherite_sword", "minecraft:item/handheld", "minecraft:item/netherite_sword", [(2004, f"{NS}:item/hollow_blade")])
    override_item("netherite_pickaxe", "minecraft:item/handheld", "minecraft:item/netherite_pickaxe", [(2005, f"{NS}:item/hollow_pickaxe")])
    override_item("netherite_scrap", "minecraft:item/generated", "minecraft:item/netherite_scrap", [(2006, f"{NS}:item/hollowite_ingot")])
    jdump("minecraft/models/item/note_block.json", {"parent": "minecraft:block/note_block",
          "overrides": [{"predicate": {"custom_model_data": 2007}, "model": f"{NS}:item/hollowite_ore"}]})
    override_item("echo_shard", "minecraft:item/generated", "minecraft:item/echo_shard", [
        (3001, f"{NS}:item/hollow_king_p1"), (3002, f"{NS}:item/hollow_king_p2"), (3003, f"{NS}:item/hollow_king_p3"),
        (3011, f"{NS}:item/king_halo_p1"), (3012, f"{NS}:item/king_halo_p2"), (3013, f"{NS}:item/king_halo_p3"),
        (3020, f"{NS}:item/hollow_wraith")])

    # blockstate de note_block: reemplaza TODAS las variantes (vanilla + la mena)
    instruments = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
                   "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton",
                   "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"]
    variants = {}
    for ins in instruments:
        for note in range(25):
            for pw in ("true", "false"):
                key = f"instrument={ins},note={note},powered={pw}"
                variants[key] = {"model": f"{NS}:block/hollowite_ore"} if (ins == "bit" and note == 7) \
                    else {"model": "minecraft:block/note_block"}
    jdump("minecraft/blockstates/note_block.json", {"variants": variants})

    # pack.mcmeta + icono
    with open(os.path.join(PACK, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": PACK_FORMAT, "description": "§5YTCustom §8· §7The Hollow King"}}, f, indent=1, ensure_ascii=False)
    ic = Canvas(64, 64, (10, 7, 18, 255))
    for y in range(64):
        for x in range(64):
            ic.set(x, y, mix(hexc("0b0712"), hexc("2a0845"), y / 63))
    for x in range(14, 50):
        ic.line(x, 44, x, 44, hexc("d4a017"))
    for (cx, h) in [(14, 20), (23, 12), (32, 24), (41, 12), (49, 20)]:
        for y in range(44 - h, 44): ic.set(cx, y, hexc("d4a017")); ic.set(cx + 1, y, hexc("a97a10"))
    for y in range(44, 50):
        for x in range(14, 51): ic.set(x, y, hexc("8a6a0c"))
    for (x, y) in [(32, 30), (23, 36), (41, 36)]: ic.set(x, y, hexc("00e5ff")); ic.set(x + 1, y, hexc("8a2be2"))
    write_png(os.path.join(PACK, "pack.png"), 64, 64, ic.px)

def make_zip():
    os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
    out = os.path.join(ROOT, "dist", "YTCustom-ResourcePack.zip")
    files = ["pack.mcmeta", "pack.png"]
    for dp, _, fn in os.walk(os.path.join(PACK, "assets")):
        for f in fn: files.append(os.path.relpath(os.path.join(dp, f), PACK).replace(os.sep, "/"))
    files = sorted(set(files), key=lambda s: (s not in ("pack.mcmeta", "pack.png"), s))
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for rel in files:
            zi = zipfile.ZipInfo(rel, (1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, open(os.path.join(PACK, rel), "rb").read())
    sha1 = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print("ZIP:", out, "\nSHA-1:", sha1)
    return sha1

if __name__ == "__main__":
    build()
    make_zip()
