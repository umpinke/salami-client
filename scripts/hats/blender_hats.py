"""
Hut-Werkstatt für Blender: Salami-Hüte aus Würfeln bauen und als Einträge für
``server/capes/hats.json`` ausgeben. Läuft in Blender (ab 5.1), z. B. über die Blender-Anbindung von
Claude oder im Reiter „Scripting“:

    import importlib.util, sys
    spec = importlib.util.spec_from_file_location("blender_hats", r"…\\scripts\\hats\\blender_hats.py")
    hats = importlib.util.module_from_spec(spec); spec.loader.exec_module(hats)
    hats.write_hats_json(r"…\\server\\capes\\hats.json")      # alle Hüte der Szene in die Liste schreiben

So ist die Szene (``hats.blend``) aufgebaut:

- 1 Blender-Einheit = 1 Pixel der Spielerfigur. z zeigt nach oben, die Figur schaut nach −y
  (Vorderansicht: Numpad 1). Der Ursprung ist der Hals: der Kopf reicht von z 0 bis 8 und von −4 bis 4
  in x und y. +x ist die linke Seite des Spielers. Die Sammlung „Referenz“ zeigt die Figur und wird
  nicht ausgegeben.
- Jeder Hut ist eine Sammlung „hat:<id>“. Namen und Optionen stehen als eigene Eigenschaften an der
  Sammlung: ``names`` (JSON, mindestens ``en_us``), ``anchor`` (``head`` oder ``shoulder``), ``scale``,
  ``glow``, ``bob``, ``sway``.
- Jeder Würfel der Sammlung ist ein Würfel des Hutes. Seine Farbe ist der Name seines Materials
  (``#rrggbb``). Größe ändern: im Objektmodus skalieren (S) – ausgegeben wird die sichtbare Größe.
- Ein Empty ist ein Drehpunkt: Würfel und weitere Empties, die an ihm hängen, drehen sich mit ihm
  (höchstens drei Ebenen tief, wie in ``hats.json``). Ein gedrehter Würfel ohne Empty dreht sich um
  seine Mitte.
- Schulter-Hüte (``anchor = shoulder``) baut man dort, wo sie schweben: über der linken Schulter
  (x 6,5, z 1 ist ihr Ankerpunkt).

Grenzen wie im Spiel: 64 Würfel und 32 Teile je Hut, Maße bis 32 Pixel, 64 Hüte insgesamt.
"""

import json
import math
import re

import bpy
from mathutils import Euler, Matrix, Vector

PREFIX = "hat:"
REFERENCE = "Referenz"
COLOR = re.compile(r"#[0-9a-fA-F]{6}")
ID = re.compile(r"[a-z0-9_]{1,24}")
#: Ankerpunkt der Schulter-Hüte in Blender-Koordinaten (im Spiel: 6,5 Pixel neben der Mitte, 1 über der Schulter).
SHOULDER_ANCHOR = Vector((6.5, 0.0, 1.0))
#: Blender (x, y nach hinten, z nach oben) → Modellraum von Minecraft (x, y nach unten, z nach hinten).
TO_GAME = Matrix(((1, 0, 0), (0, 0, -1), (0, 1, 0)))
MAX_BOXES = 64
MAX_PARTS = 32
MAX_DEPTH = 3
MAX_PIXELS = 32.0


# ------------------------------------------------------------------ bauen


def _linear(channel):
    """sRGB-Wert (0..1) → linearer Wert, wie ihn Blender für Materialfarben erwartet."""
    return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4


def material(color):
    """Material in dieser Farbe (``#rrggbb``); der Name ist die Farbe und steht so später in hats.json."""
    color = color.lower()
    if not COLOR.fullmatch(color):
        raise ValueError(f"Farbe {color!r} ist kein #rrggbb")
    found = bpy.data.materials.get(color)
    if found:
        return found
    rgb = [int(color[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    made = bpy.data.materials.new(color)
    made.diffuse_color = (*rgb, 1.0)
    made.use_nodes = True
    shader = made.node_tree.nodes.get("Principled BSDF")
    if shader:
        shader.inputs["Base Color"].default_value = (*[_linear(c) for c in rgb], 1.0)
        shader.inputs["Roughness"].default_value = 1.0
    return made


def collection(name):
    """Sammlung mit diesem Namen, neu und leer."""
    old = bpy.data.collections.get(name)
    if old:
        for obj in list(old.objects):
            bpy.data.objects.remove(obj, do_unlink=True)
        bpy.data.collections.remove(old)
    made = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(made)
    return made


def hat(hat_id, names, anchor="head", scale=1.0, glow=False, bob=0.0, sway=0.0):
    """Legt die Sammlung eines Hutes an (eine alte mit demselben Namen wird ersetzt)."""
    if not ID.fullmatch(hat_id) or hat_id == "none":
        raise ValueError(f"id {hat_id!r} ungültig (a-z, 0-9, _, höchstens 24 Zeichen, nicht „none“)")
    made = collection(PREFIX + hat_id)
    made["names"] = json.dumps(names, ensure_ascii=False)
    made["anchor"] = anchor
    made["scale"] = float(scale)
    made["glow"] = bool(glow)
    made["bob"] = float(bob)
    made["sway"] = float(sway)
    return made


def box(into, color, low, high, parent=None, name=None):
    """
    Würfel von Ecke ``low`` bis Ecke ``high`` (x, y, z in Blender-Koordinaten; unter einem Drehpunkt
    ``parent`` in dessen Koordinaten).
    """
    low, high = Vector(low), Vector(high)
    size = high - low
    if min(size) <= 0:
        raise ValueError(f"Würfel {low[:]}..{high[:]} hat keine Größe")
    half = size / 2
    corners = [(x * half.x, y * half.y, z * half.z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    mesh = bpy.data.meshes.new(name or "Würfel")
    mesh.from_pydata(corners, [], faces)
    mesh.update()
    mesh.materials.append(material(color))
    obj = bpy.data.objects.new(name or "Würfel", mesh)
    obj.location = (low + high) / 2
    obj.parent = parent
    into.objects.link(obj)
    return obj


def pivot(into, at, rotation=(0.0, 0.0, 0.0), parent=None, name=None):
    """
    Drehpunkt bei ``at``, gedreht um ``rotation`` Grad (x, y, z in Blender: +x kippt nach vorn,
    +y kippt nach links vom Spieler, +z dreht die linke Seite nach hinten).
    """
    obj = bpy.data.objects.new(name or "Drehpunkt", None)
    obj.empty_display_type = "ARROWS"
    obj.empty_display_size = 1.0
    obj.location = at
    obj.rotation_euler = Euler([math.radians(a) for a in rotation], "XYZ")
    obj.parent = parent
    into.objects.link(obj)
    return obj


def setup_scene():
    """Leere Szene mit Figur, Kamera und Licht – für eine neue hats.blend."""
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for old in list(bpy.data.collections):
        bpy.data.collections.remove(old)
    scene = bpy.context.scene
    scene.unit_settings.system = "NONE"

    figure = collection(REFERENCE)
    skin, shirt, trousers, shoes, hair, eyes = "#c68e6a", "#18a5a5", "#3a3f9e", "#55555c", "#3b2616", "#3d2f8f"
    box(figure, skin, (-4, -4, 0), (4, 4, 8), name="Kopf")
    box(figure, hair, (-4.02, -4.02, 6), (4.02, 4.02, 8.02), name="Haare")
    box(figure, hair, (-4.02, -1, 2), (4.02, 4.02, 6), name="Haare hinten")
    box(figure, "#ffffff", (-3, -4.03, 3), (-1, -4, 4), name="Auge rechts")
    box(figure, "#ffffff", (1, -4.03, 3), (3, -4, 4), name="Auge links")
    box(figure, eyes, (-2, -4.05, 3), (-1, -4, 4), name="Pupille rechts")
    box(figure, eyes, (1, -4.05, 3), (2, -4, 4), name="Pupille links")
    box(figure, shirt, (-4, -2, -12), (4, 2, 0), name="Körper")
    box(figure, shirt, (4, -2, -4), (8, 2, 0), name="Arm links")
    box(figure, skin, (4, -2, -12), (8, 2, -4), name="Hand links")
    box(figure, shirt, (-8, -2, -4), (-4, 2, 0), name="Arm rechts")
    box(figure, skin, (-8, -2, -12), (-4, 2, -4), name="Hand rechts")
    box(figure, trousers, (-4, -2, -22), (4, 2, -12), name="Beine")
    box(figure, shoes, (-4, -2, -24), (4, 2, -22), name="Schuhe")
    for obj in figure.objects:
        obj.hide_select = True

    camera = bpy.data.objects.new("Kamera", bpy.data.cameras.new("Kamera"))
    camera.data.lens = 85
    scene.collection.objects.link(camera)
    scene.camera = camera
    sun = bpy.data.objects.new("Sonne", bpy.data.lights.new("Sonne", "SUN"))
    sun.data.energy = 3.0
    sun.rotation_euler = Euler((math.radians(50), math.radians(10), math.radians(-30)), "XYZ")
    scene.collection.objects.link(sun)

    world = scene.world or bpy.data.worlds.new("Welt")
    scene.world = world
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    if background:
        background.inputs[0].default_value = (0.11, 0.12, 0.15, 1.0)
        background.inputs[1].default_value = 1.6
    scene.view_settings.view_transform = "Standard"
    scene.render.resolution_x = 560
    scene.render.resolution_y = 560
    scene.render.film_transparent = False
    look_at("head")


def look_at(target="head", turn=-30.0, tilt=18.0, distance=None):
    """
    Stellt die Kamera auf den Kopf (``head``), die ganze Figur (``body``) oder einen Punkt. ``turn``:
    Grad um die Hochachse (negativ = von links vorn gesehen), ``tilt``: Grad von oben.
    """
    camera = bpy.context.scene.camera
    if target == "head":
        center, default = Vector((0, 0, 8.5)), 95.0
    elif target == "body":
        center, default = Vector((0, 0, -4)), 190.0
    else:
        center, default = Vector(target), 95.0
    distance = distance or default
    direction = Vector((0, -1, 0))
    direction.rotate(Euler((math.radians(-tilt), 0, 0), "XYZ"))
    direction.rotate(Euler((0, 0, math.radians(turn)), "XYZ"))
    camera.location = center + direction * distance
    camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()


def show_only(hat_id=None):
    """Zeigt in Ansicht und Rendering nur diesen Hut (``None``: alle)."""
    for coll in bpy.data.collections:
        if coll.name.startswith(PREFIX):
            hidden = hat_id is not None and coll.name != PREFIX + hat_id
            coll.hide_render = hidden
            coll.hide_viewport = hidden


def render(path, hat_id=None, **view):
    """Rendert die Figur mit einem Hut (oder allen) als PNG nach ``path``."""
    show_only(hat_id)
    if view:
        look_at(**view)
    scene = bpy.context.scene
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    show_only(None)


# ------------------------------------------------------------------ ausgeben


def _number(value):
    """Zahl für hats.json: auf Tausendstel gerundet, ohne überflüssige Nullen."""
    value = round(float(value), 3)
    return int(value) if value == int(value) else value


def _game_point(point):
    return [_number(c) for c in (TO_GAME @ Vector(point))]


def _game_rotation(rotation):
    """Drehung eines Objekts → Grad um x, y, z, wie das Spiel sie anwendet (erst x, dann y, dann z)."""
    turned = TO_GAME @ rotation.to_matrix() @ TO_GAME.transposed()
    return [_number(math.degrees(a)) for a in turned.to_euler("XYZ")]


def _is_turned(obj):
    return any(abs(a) > 1e-5 for a in obj.matrix_basis.to_euler("XYZ"))


def _local_box(obj, origin):
    """Würfel eines Mesh-Objekts als [x, y, z, Breite, Höhe, Tiefe] im Modellraum des Spiels, relativ zu ``origin``."""
    scale = obj.matrix_basis.to_scale()
    corners = [Vector((c[0] * scale.x, c[1] * scale.y, c[2] * scale.z)) for c in obj.bound_box]
    low = Vector([min(c[i] for c in corners) for i in range(3)]) + origin
    high = Vector([max(c[i] for c in corners) for i in range(3)]) + origin
    size = high - low
    values = [low.x, -high.z, low.y, size.x, size.z, size.y]
    if min(values[3:]) < 0.05 or max(abs(v) for v in values) > MAX_PIXELS:
        raise ValueError(f"{obj.name}: Würfel zu klein (< 0,05) oder zu weit weg/zu groß (> {MAX_PIXELS:g})")
    return [_number(v) for v in values]


def _color_of(obj):
    name = obj.material_slots[0].material.name if obj.material_slots and obj.material_slots[0].material else ""
    color = name[:7].lower()
    if not COLOR.fullmatch(color):
        raise ValueError(f"{obj.name}: das Material muss wie seine Farbe heißen (#rrggbb), nicht {name!r}")
    return color


def _parts(children, offset):
    """Teile aus den Objekten einer Ebene: Würfel nach Farbe gesammelt, Drehpunkte als eigene Teile."""
    by_color = {}
    parts = []
    turned = []
    for obj in sorted(children, key=lambda o: o.name):
        if obj.type == "MESH" and not _is_turned(obj):
            by_color.setdefault(_color_of(obj), []).append(_local_box(obj, obj.matrix_basis.translation + offset))
        elif obj.type in {"MESH", "EMPTY"}:
            turned.append(obj)
    for color, boxes in by_color.items():
        parts.append({"color": color, "boxes": boxes})
    for obj in turned:
        part = {"at": _game_point(obj.matrix_basis.translation + offset)}
        rotation = _game_rotation(obj.matrix_basis.to_euler("XYZ"))
        if any(rotation):
            part["rot"] = rotation
        if obj.type == "MESH":
            # gedrehter Würfel ohne Drehpunkt: dreht sich um seine Mitte
            part["color"] = _color_of(obj)
            part["boxes"] = [_local_box(obj, Vector((0, 0, 0)))]
        else:
            inner = _parts(obj.children, Vector((0, 0, 0)))
            plain = [p for p in inner if set(p) == {"color", "boxes"}]
            if len(plain) == 1:
                # nur eine Farbe direkt am Drehpunkt: die Würfel gehören dem Drehpunkt selbst
                part.update(plain[0])
                inner = [p for p in inner if p is not plain[0]]
            if inner:
                part["parts"] = inner
            if "boxes" not in part and "parts" not in part:
                raise ValueError(f"{obj.name}: Drehpunkt ohne Würfel")
        parts.append(part)
    return parts


def _count(parts, depth, count):
    """Zählt Teile und Würfel so, wie das Spiel es beim Einlesen tut, und prüft die Tiefe."""
    if parts and depth > MAX_DEPTH:
        raise ValueError(f"Drehpunkte höchstens {MAX_DEPTH} Ebenen tief")
    for part in parts:
        count["parts"] += 1
        count["boxes"] += len(part.get("boxes", ()))
        _count(part.get("parts", ()), depth + 1, count)


def export_hat(coll):
    """Ein Hut (Sammlung „hat:<id>“) als Eintrag für hats.json."""
    hat_id = coll.name[len(PREFIX) :]
    if not ID.fullmatch(hat_id) or hat_id == "none":
        raise ValueError(f"{coll.name}: id ungültig")
    names = json.loads(coll.get("names", "{}"))
    if not names.get("en_us"):
        raise ValueError(f"{coll.name}: names braucht mindestens en_us")
    anchor = coll.get("anchor", "head")
    if anchor not in ("head", "shoulder"):
        raise ValueError(f"{coll.name}: anchor muss head oder shoulder sein")
    entry = {"id": hat_id, "names": names}
    if anchor == "shoulder":
        entry["anchor"] = "shoulder"
    if abs(coll.get("scale", 1.0) - 1.0) > 1e-6:
        entry["scale"] = _number(coll["scale"])
    if coll.get("glow"):
        entry["glow"] = True
    for key in ("bob", "sway"):
        if coll.get(key):
            entry[key] = _number(coll[key])
    count = {"parts": 0, "boxes": 0}
    offset = -SHOULDER_ANCHOR if anchor == "shoulder" else Vector((0, 0, 0))
    try:
        entry["parts"] = _parts([o for o in coll.objects if o.parent is None], offset)
        _count(entry["parts"], 1, count)
    except ValueError as error:
        raise ValueError(f"{coll.name}: {error}") from None
    if count["boxes"] == 0:
        raise ValueError(f"{coll.name}: kein Würfel")
    if count["boxes"] > MAX_BOXES or count["parts"] > MAX_PARTS:
        raise ValueError(f"{coll.name}: {count['boxes']} Würfel, {count['parts']} Teile – erlaubt sind {MAX_BOXES} und {MAX_PARTS}")
    return entry


def export_hats():
    """Alle Hüte der Szene, in der Reihenfolge ihrer Namen."""
    return [export_hat(c) for c in sorted(bpy.data.collections, key=lambda c: c.name) if c.name.startswith(PREFIX)]


# ------------------------------------------------------------------ hats.json schreiben


def _inline(value):
    """JSON in einer Zeile, im Stil von hats.json: Objekte mit Leerzeichen innen, Listen ohne."""
    if isinstance(value, dict):
        return "{ " + ", ".join(f"{json.dumps(k)}: {_inline(v)}" for k, v in value.items()) + " }"
    if isinstance(value, list):
        return "[" + ", ".join(_inline(v) for v in value) + "]"
    return json.dumps(value, ensure_ascii=False)


def _format_part(part, indent):
    pad = " " * indent
    one_line = pad + _inline(part)
    if set(part) <= {"color", "boxes"} and len(one_line) <= 150:
        return one_line
    lines = [pad + "{"]
    keys = [k for k in ("at", "rot", "color", "boxes", "parts") if k in part]
    for i, key in enumerate(keys):
        comma = "," if i < len(keys) - 1 else ""
        if key == "parts":
            inner = ",\n".join(_format_part(p, indent + 4) for p in part["parts"])
            lines.append(f'{pad}  "parts": [\n{inner}\n{pad}  ]{comma}')
        else:
            lines.append(f"{pad}  {json.dumps(key)}: {_inline(part[key])}{comma}")
    lines.append(pad + "}")
    return "\n".join(lines)


def format_hat(entry, indent=4):
    """Eintrag als Text, so eingerückt und umbrochen wie die übrigen in hats.json."""
    pad = " " * indent
    lines = [pad + "{"]
    for key in ("id", "names", "anchor", "scale", "glow", "bob", "sway"):
        if key in entry:
            lines.append(f"{pad}  {json.dumps(key)}: {_inline(entry[key])},")
    parts = ",\n".join(_format_part(p, indent + 4) for p in entry["parts"])
    lines.append(f'{pad}  "parts": [\n{parts}\n{pad}  ]')
    lines.append(pad + "}")
    return "\n".join(lines)


def _entry_span(text, hat_id):
    """Anfang und Ende des Eintrags mit dieser id im Text von hats.json (oder ``None``)."""
    found = re.search(r'\{\s*"id":\s*"' + re.escape(hat_id) + '"', text)
    if not found:
        return None
    start = text.rfind("\n", 0, found.start()) + 1
    depth = 0
    for i in range(found.start(), len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise ValueError(f"Eintrag {hat_id} in hats.json nicht zu Ende")


def write_hats_json(path, only=None):
    """
    Schreibt die Hüte der Szene nach ``path`` (server/capes/hats.json): vorhandene Einträge mit derselben
    id werden ersetzt, neue hinten angehängt, alles andere bleibt, wie es ist. ``only``: nur diese ids,
    neue in dieser Reihenfolge (so stehen sie dann auch im Menü). Liefert die geschriebenen ids.
    """
    with open(path, encoding="utf-8", newline="") as handle:
        text = handle.read()
    entries = export_hats()
    if only is not None:
        order = {hat_id: i for i, hat_id in enumerate(only)}
        entries = sorted((e for e in entries if e["id"] in order), key=lambda e: order[e["id"]])
    written = []
    for entry in entries:
        block = format_hat(entry)
        span = _entry_span(text, entry["id"])
        if span:
            text = text[: span[0]] + block + text[span[1] :]
        else:
            end = text.rstrip().rfind("]")
            last = text.rfind("}", 0, end)
            text = text[: last + 1] + ",\n" + block + text[last + 1 :]
        written.append(entry["id"])
    catalog = json.loads(text)
    ids = [h["id"] for h in catalog["hats"]]
    if len(ids) != len(set(ids)) or len(ids) > 64:
        raise ValueError("hats.json: doppelte id oder mehr als 64 Hüte")
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)
    return written


def _self_check():
    """Blender wendet „XYZ“ so an wie das Spiel seine Drehungen: erst x, dann y, dann z."""
    a, b, c = 0.3, -0.5, 0.9
    expected = Matrix.Rotation(c, 3, "Z") @ Matrix.Rotation(b, 3, "Y") @ Matrix.Rotation(a, 3, "X")
    got = Euler((a, b, c), "XYZ").to_matrix()
    assert all(abs(expected[i][j] - got[i][j]) < 1e-6 for i in range(3) for j in range(3)), "Euler-Reihenfolge anders als erwartet"


_self_check()
