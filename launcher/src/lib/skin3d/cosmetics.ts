/**
 * Cosmetics an der 3D-Figur (skinview3d): Hüte und Buddys aus ihren Katalogen, Emotes als Haltung –
 * samt dem, was sie mitbringen (Requisiten, Verwandlung) – und das Mini Me. Alles so wie im Spiel
 * (`HatLayer`, `BuddyLayer`, `BuddyFollowers`, `EmotePoses`, `EmotePropLayer`, `EmoteEffects`,
 * `MiniMeLayer` in salami-core), damit der Shop zeigt, was man dort bekommt.
 *
 * Das Spiel rechnet im Modellraum von Minecraft: y nach unten, das Gesicht bei −z. skinview3d hat y
 * nach oben und das Gesicht bei +z – eine halbe Drehung um x. Drehungen um x bleiben dabei gleich,
 * Drehungen um y und z kehren sich um.
 */
import { Box3, BoxGeometry, Group, Mesh, MeshStandardMaterial, type Material, type Object3D } from "three";
import { SkinObject, type PlayerObject, type SkinViewer } from "skinview3d";
import {
  sampleTrack,
  type CatalogBuddy,
  type CatalogEmote,
  type CatalogHat,
  type EmoteLimb,
  type EmotePart,
  type EmoteTrack,
  type HatPart,
} from "../../services/salami-cosmetics-service";

const radians = (degrees: number) => (degrees * Math.PI) / 180;
/** Hüte, Buddys und Mini Me bewegen sich im Takt des Spiels (20 Ticks pro Sekunde). */
const TICKS_PER_SECOND = 20;

// ------------------------------------------------------------------ Würfel

/**
 * Baut Teile aus einem Katalog: farbige Würfel in Spieler-Pixeln, Teile mit Drehpunkt und Drehung.
 * Was `build` liefert, steht im Modellraum von Minecraft – die Gruppe darüber dreht ihn in den der Figur
 * ({@link minecraftSpace}).
 */
class BoxBuilder {
  private readonly geometries: BoxGeometry[] = [];
  private readonly materials = new Map<string, Material>();

  private materialOf(color: string): Material {
    let material = this.materials.get(color);
    if (!material) {
      // auch leuchtende Modelle (Heiligenschein): im Spiel sind sie nur im Dunkeln heller als andere, ihre
      // Flächen sind schattiert wie bei jedem Modell – ganz ohne Licht wäre ein dicker Ring ein flacher Fleck
      material = new MeshStandardMaterial({ color });
      this.materials.set(color, material);
    }
    return material;
  }

  /** `bare`: ohne das eigene `at` und `rot` des Teils – es liegt dann um seinen Drehpunkt. */
  build(part: HatPart, bare = false): Group {
    const group = new Group();
    if (part.at && !bare) group.position.set(...part.at);
    // wie ein Modellteil in Minecraft: erst zum Drehpunkt, dann um z, y, x drehen
    if (part.rot && !bare) group.rotation.set(radians(part.rot[0]), radians(part.rot[1]), radians(part.rot[2]), "ZYX");
    for (const [x, y, z, w, h, d] of part.boxes) {
      const geometry = new BoxGeometry(w, h, d);
      this.geometries.push(geometry);
      const mesh = new Mesh(geometry, this.materialOf(part.color ?? "#ffffff"));
      mesh.position.set(x + w / 2, y + h / 2, z + d / 2);
      group.add(mesh);
    }
    for (const child of part.parts) group.add(this.build(child));
    return group;
  }

  dispose(): void {
    this.geometries.forEach((geometry) => geometry.dispose());
    this.materials.forEach((material) => material.dispose());
  }
}

/** Gruppe, in der man im Modellraum von Minecraft rechnet (y nach unten, vorn bei −z). */
function minecraftSpace(): Group {
  const root = new Group();
  root.rotation.x = Math.PI;
  return root;
}

// ------------------------------------------------------------------ Hüte

/** Abstand von der Körpermitte: über der Außenkante der Schulter, frei vom Kopf. */
const SHOULDER_X = 6.5;
/** So weit schwebt ein Schulter-Hut über der Schulter. */
const SHOULDER_FLOAT = 1;

export interface HatObject {
  /** Hängt am Kopf oder (Schulter-Hüte) am Körper der Figur. */
  root: Group;
  /** Schweben und Pendeln zur Zeit `seconds`. */
  animate(seconds: number): void;
  dispose(): void;
}

/** Baut einen Hut aus dem Katalog: farbige Würfel in Spieler-Pixeln, Teile mit Drehpunkt und Drehung. */
export function buildHat(hat: CatalogHat): HatObject {
  const builder = new BoxBuilder();
  const root = minecraftSpace();
  const float = new Group();
  const model = new Group();
  model.scale.setScalar(hat.scale);
  for (const part of hat.parts) model.add(builder.build(part));
  float.add(model);
  root.add(float);

  const onShoulder = hat.anchor === "shoulder";
  const animate = (seconds: number) => {
    const ticks = seconds * TICKS_PER_SECOND;
    const bob = Math.sin(ticks * 0.1) * hat.bob;
    float.position.set(onShoulder ? SHOULDER_X : 0, (onShoulder ? -SHOULDER_FLOAT : 0) - bob, 0);
    float.rotation.y = Math.sin(ticks * 0.07) * radians(hat.sway);
  };
  animate(0);

  return {
    root,
    animate,
    dispose() {
      root.removeFromParent();
      builder.dispose();
    },
  };
}

/** Setzt der Figur den Hut auf (an den Kopf, Schulter-Hüte an den Körper). */
export function attachHat(player: PlayerObject, hat: CatalogHat, object: HatObject): void {
  (hat.anchor === "shoulder" ? player.skin : player.skin.head).add(object.root);
}

// ------------------------------------------------------------------ Buddys

export const BUDDY_PLACES = ["hand", "shoulder", "head", "follow"] as const;
/** Wo der Buddy ist – wie `BuddyModule.Place` im Spiel. */
export type BuddyPlace = (typeof BUDDY_PLACES)[number];

/** Am Boden ist ein Buddy so viel größer als über der Schulter (`BuddyFollowers.SCALE`). */
const BUDDY_FOLLOW_SCALE = 1.6;
/** So hoch hüpft ein hinterherlaufender Buddy bei jedem Schritt: 0,14 Blöcke. */
const BUDDY_HOP = 0.14 * 16;

export interface BuddyObject {
  animate(seconds: number): void;
  dispose(): void;
}

/**
 * Gibt der Figur einen Buddy mit: er hängt unter der linken Hand, schwebt über der linken Schulter,
 * steht auf dem Kopf oder hüpft neben ihr her. Sein Nullpunkt liegt unten in der Mitte.
 */
export function attachBuddy(player: PlayerObject, buddy: CatalogBuddy, place: BuddyPlace): BuddyObject {
  const builder = new BoxBuilder();
  const root = minecraftSpace();
  const float = new Group();
  const model = new Group();
  model.scale.setScalar(buddy.scale * (place === "follow" ? BUDDY_FOLLOW_SCALE : 1));
  for (const part of buddy.parts) model.add(builder.build(part));
  // höchster Punkt des Modells (oben ist negativ) – gemessen, solange es noch nirgends hängt
  const top = new Box3().setFromObject(model).min.y;
  float.add(model);
  root.add(float);

  const skin = player.skin;
  switch (place) {
    case "shoulder":
      skin.add(root);
      break;
    case "head":
      // steht oben auf dem Kopf und dreht sich mit ihm
      skin.head.add(root);
      float.position.set(0, -8, 0);
      break;
    case "hand":
      // hängt an seinem Scheitel unter dem Handende (10 Pixel unter der Schulter)
      skin.leftArm.add(root);
      float.position.set(1, 10.3, 0);
      model.position.y = -top;
      break;
    case "follow":
      skin.add(root);
      break;
  }

  const animate = (seconds: number) => {
    const ticks = seconds * TICKS_PER_SECOND;
    if (place === "shoulder") {
      // sanftes Schweben wie ein Schulter-Hut
      const bob = Math.sin(ticks * 0.1) * buddy.bob;
      float.position.set(SHOULDER_X, -SHOULDER_FLOAT - bob, 0);
      float.rotation.y = Math.sin(ticks * 0.07) * radians(buddy.sway);
    } else if (place === "hand") {
      float.rotation.z = Math.sin(ticks * 0.1) * 0.12;
    } else if (place === "follow") {
      // hüpft schräg hinter der Figur her, die Füße auf dem Boden (im Modell bei y 24); jeder Schritt ein
      // Hüpfer, dazu ein leichtes Wackeln
      const step = ticks * 0.5;
      float.position.set(11, 24 - Math.abs(Math.sin(step)) * BUDDY_HOP, 4);
      float.rotation.z = Math.sin(step) * 0.14;
    }
  };
  animate(0);

  return {
    animate,
    dispose() {
      root.removeFromParent();
      builder.dispose();
    },
  };
}

// ------------------------------------------------------------------ Emotes

const BLEND_IN_SECONDS = 0.18;
const BLEND_OUT_SECONDS = 0.22;
/** Pause in der Vorschau, bevor ein Emote von vorn beginnt. */
const REPEAT_PAUSE_SECONDS = 0.7;

const partOf = (skin: SkinObject, part: EmotePart): Object3D => {
  switch (part) {
    case "head":
      return skin.head;
    case "right_arm":
      return skin.rightArm;
    case "left_arm":
      return skin.leftArm;
    case "right_leg":
      return skin.rightLeg;
    case "left_leg":
      return skin.leftLeg;
  }
};

/** Ruhige Haltung mit leicht pendelnden Armen, wie ein stehender Spieler in Minecraft. */
export function poseIdle(skin: SkinObject, seconds: number): void {
  skin.resetJoints();
  const ticks = seconds * TICKS_PER_SECOND;
  const out = Math.cos(ticks * 0.09) * 0.05 + 0.05;
  const swing = Math.sin(ticks * 0.067) * 0.05;
  skin.rightArm.rotation.set(swing, 0, -out);
  skin.leftArm.rotation.set(-swing, 0, out);
}

/**
 * Legt die Haltung eines Emotes über die aktuelle: `t` Sekunden seit Beginn, `weight` (0..1) blendet
 * weich über, damit Anfang und Ende nicht springen.
 */
export function poseEmote(skin: SkinObject, emote: CatalogEmote, t: number, weight: number): void {
  for (const track of emote.tracks) {
    const [x, y, z] = sampleTrack(track, t);
    const rotation = partOf(skin, track.part).rotation;
    rotation.set(
      rotation.x + (x - rotation.x) * weight,
      rotation.y + (-y - rotation.y) * weight,
      rotation.z + (-z - rotation.z) * weight,
      "ZYX",
    );
  }
}

/** Wo ein Emote in der Dauerschleife der Vorschau gerade ist: `t` Sekunden seit seinem Beginn, `weight` blendet ein und aus. */
export function emoteClock(emote: CatalogEmote, seconds: number): { t: number; weight: number } {
  if (emote.loop) return { t: seconds, weight: Math.min(1, seconds / BLEND_IN_SECONDS) };
  const t = seconds % (emote.duration + BLEND_OUT_SECONDS + REPEAT_PAUSE_SECONDS);
  const fadeIn = Math.min(1, t / BLEND_IN_SECONDS);
  const fadeOut = t <= emote.duration ? 1 : Math.max(0, 1 - (t - emote.duration) / BLEND_OUT_SECONDS);
  return { t, weight: Math.min(fadeIn, fadeOut) };
}

/** Spielt ein Emote in Dauerschleife: Haltung zur Zeit `seconds` seit dem Start der Vorschau. */
export function playEmote(skin: SkinObject, emote: CatalogEmote, seconds: number): void {
  const { t, weight } = emoteClock(emote, seconds);
  if (weight > 0) poseEmote(skin, emote, t, weight);
}

/** Was ein Emote außer der Haltung zeigt: Requisiten an Körperteilen und die Figur einer Verwandlung. */
export interface EmoteExtrasObject {
  /** `t` Sekunden seit Beginn des Emotes, `weight` wie bei {@link poseEmote}; 0 = das Emote läuft nicht. */
  update(t: number, weight: number): void;
  dispose(): void;
}

/** Körperteil, an dem eine Requisite hängt; der Rumpf dreht sich nicht, sein Gelenk ist der Hals. */
const limbOf = (skin: SkinObject, limb: EmoteLimb): Object3D => (limb === "body" ? skin : partOf(skin, limb));

/**
 * Hängt an die Figur, was das Emote mitbringt – oder liefert `null`, wenn es nichts mitbringt. Was
 * geworfen wird, liegt bis zum Wurf als Requisite in der Hand; den Flug zeigt nur das Spiel.
 */
export function attachEmoteExtras(player: PlayerObject, emote: CatalogEmote): EmoteExtrasObject | null {
  if (emote.props.length === 0 && !emote.costume) return null;
  const builder = new BoxBuilder();
  const roots: Group[] = [];
  const updates: ((t: number, weight: number) => void)[] = [];
  const skin = player.skin;

  for (const prop of emote.props) {
    const root = minecraftSpace();
    const holder = new Group();
    holder.position.set(...prop.at);
    holder.rotation.set(radians(prop.rot[0]), radians(prop.rot[1]), radians(prop.rot[2]), "ZYX");
    for (const part of prop.parts) holder.add(builder.build(part));
    root.add(holder);
    limbOf(skin, prop.on).add(root);
    roots.push(root);
    updates.push((t, weight) => {
      holder.visible = weight > 0 && t >= prop.from && (prop.to === null || t < prop.to);
      // die Requisite wächst mit dem Einblenden des Emotes
      holder.scale.setScalar(Math.max(prop.scale * weight, 1e-4));
    });
  }

  const costume = emote.costume;
  if (costume) {
    // steht anstelle der Figur: der Nullpunkt des Modells ist ihr Hals, der Boden liegt bei y 24
    const root = minecraftSpace();
    root.position.copy(skin.position);
    const feet = new Group();
    feet.position.set(0, 24, 0);
    const figure = new Group();
    feet.add(figure);
    root.add(feet);
    const pieces: { pivot: Group; track: EmoteTrack | undefined }[] = [];
    for (const part of costume.parts) {
      if (!part.follow) {
        figure.add(builder.build(part));
        continue;
      }
      // dreht sich um sein `at` wie das Körperteil: erst die Bewegung, dann die eigene Grundhaltung
      const pivot = new Group();
      if (part.at) pivot.position.set(...part.at);
      const own = new Group();
      if (part.rot) own.rotation.set(radians(part.rot[0]), radians(part.rot[1]), radians(part.rot[2]), "ZYX");
      own.add(builder.build(part, true));
      pivot.add(own);
      figure.add(pivot);
      pieces.push({ pivot, track: costume.tracks.find((track) => track.part === part.follow) });
    }
    player.add(root);
    roots.push(root);
    updates.push((t, weight) => {
      const shown = weight > 0;
      root.visible = shown;
      skin.visible = !shown;
      // wächst beim Verwandeln und schrumpft am Ende wieder, wie im Spiel
      feet.scale.setScalar(costume.scale * (0.35 + 0.65 * weight));
      const hop = costume.hop > 0 ? Math.abs(Math.sin((Math.PI * t) / costume.hopCycle)) * costume.hop * weight : 0;
      figure.position.set(0, -24 - hop, 0);
      for (const { pivot, track } of pieces) {
        const [x, y, z] = track ? sampleTrack(track, t) : [0, 0, 0];
        pivot.rotation.set(x * weight, y * weight, z * weight, "ZYX");
      }
    });
  }

  const update = (t: number, weight: number) => updates.forEach((apply) => apply(t, weight));
  update(0, 0);
  return {
    update,
    dispose() {
      roots.forEach((root) => root.removeFromParent());
      if (costume) skin.visible = true;
      builder.dispose();
    },
  };
}

// ------------------------------------------------------------------ Mini Me

export const MINI_ME_PLACES = ["hand", "shoulder", "head", "follow"] as const;
/** Wo das Mini Me ist – wie `MiniMeModule.Place` im Spiel. */
export type MiniMePlace = (typeof MINI_ME_PLACES)[number];

const MINI_SCALE = 0.28;

export interface MiniMeObject {
  /** Übernimmt Skin und Armform der großen Figur (nach jedem Laden des Skins aufrufen). */
  syncSkin(player: PlayerObject): void;
  animate(seconds: number): void;
  dispose(): void;
}

/** Hängt ein kleines Abbild der Figur an sie: an die Hand, auf Schulter oder Kopf, oder es läuft nebenher. */
export function attachMiniMe(player: PlayerObject, place: MiniMePlace): MiniMeObject {
  const mini = new SkinObject();
  mini.scale.setScalar(MINI_SCALE);
  const skin = player.skin;
  // Position des Halses der kleinen Figur; ihr Rumpf ist 12 Pixel hoch, die Beine noch einmal 12
  switch (place) {
    case "hand":
      // hängt mit erhobenem Arm an der linken Hand (10,5 Pixel unter der Schulter)
      skin.leftArm.add(mini);
      mini.position.set(1 + 6 * MINI_SCALE, -(10.5 + 7.5 * MINI_SCALE), 0);
      break;
    case "shoulder":
      // sitzt auf der rechten Schulter, die Hüfte auf Schulterhöhe
      skin.add(mini);
      mini.position.set(-6.8, 0.2 + 12 * MINI_SCALE, 0);
      break;
    case "head":
      skin.head.add(mini);
      mini.position.set(0, 8.6 + 12 * MINI_SCALE, 0);
      break;
    case "follow":
      // läuft neben der Figur her, die Füße auf dem Boden
      skin.add(mini);
      mini.position.set(10, -24 + 24 * MINI_SCALE, 3);
      break;
  }

  const animate = (seconds: number) => {
    const ticks = seconds * TICKS_PER_SECOND;
    mini.resetJoints();
    if (place === "hand") {
      const dangle = Math.sin(ticks * 0.1) * 0.18;
      mini.rightArm.rotation.x = Math.PI;
      mini.leftArm.rotation.z = 0.25 + Math.sin(ticks * 0.07) * 0.05;
      mini.rightLeg.rotation.x = dangle;
      mini.leftLeg.rotation.x = -dangle;
    } else if (place === "follow") {
      const swing = Math.sin(ticks * 0.5) * 0.5;
      mini.rightLeg.rotation.x = swing * 1.4;
      mini.leftLeg.rotation.x = -swing * 1.4;
      mini.rightArm.rotation.x = -swing;
      mini.leftArm.rotation.x = swing;
    } else {
      // sitzt und baumelt mit den Beinen
      mini.rightLeg.rotation.set(-1.41 + Math.sin(ticks * 0.12) * 0.12, -0.31, 0, "ZYX");
      mini.leftLeg.rotation.set(-1.41 + Math.sin(ticks * 0.12 + 1.6) * 0.12, 0.31, 0, "ZYX");
      mini.rightArm.rotation.x = -0.63;
      mini.leftArm.rotation.x = -0.63;
    }
  };
  animate(0);

  return {
    syncSkin(source) {
      mini.modelType = source.skin.modelType;
      mini.map = source.skin.map;
    },
    animate,
    dispose() {
      mini.removeFromParent();
      // Geometrie und Material gehören der kleinen Figur, das Skin-Bild der großen
      mini.traverse((object) => {
        if (!(object instanceof Mesh)) return;
        object.geometry.dispose();
        (Array.isArray(object.material) ? object.material : [object.material]).forEach((material: Material) => material.dispose());
      });
    },
  };
}

// ------------------------------------------------------------------ Bildausschnitt

/** Wie die Figur im Bild steht. */
export interface CosmeticsView {
  /**
   * `body`: die ganze Figur. `head`: Kopf und Schultern, für Hüte. `top`: Kopf mit Platz darüber, für
   * das, was auf ihm steht. `wide`: mit Platz neben der Figur.
   */
  focus: "body" | "head" | "top" | "wide";
  /** Drehung um die Hochachse (Radiant): positiv holt die rechte Seite der Figur nach vorn, negativ die linke. */
  rotation: number;
}

/** Leicht nach vorn geneigt, damit man die Figur räumlich sieht. */
const TILT = 0.1;

/** Ausschnitt, in dem man das gezeigte Cosmetic gut sieht (ohne Cosmetic: die ganze Figur). */
export function viewOf(shown: {
  hat?: CatalogHat | null;
  buddy?: BuddyPlace | null;
  emote?: CatalogEmote | null;
  miniMe?: MiniMePlace | null;
}): CosmeticsView {
  // Schulter-Hüte schweben links, das Mini Me sitzt rechts oder hängt an der linken Hand
  if (shown.hat) return { focus: "head", rotation: shown.hat.anchor === "shoulder" ? -0.5 : -0.35 };
  // Buddys sind immer links: über der Schulter, unter der Hand oder schräg hinter der Figur
  if (shown.buddy === "shoulder") return { focus: "head", rotation: -0.5 };
  if (shown.buddy === "head") return { focus: "top", rotation: -0.35 };
  if (shown.buddy === "hand") return { focus: "body", rotation: -0.55 };
  if (shown.buddy) return { focus: "wide", rotation: -0.4 };
  if (shown.miniMe) return { focus: "wide", rotation: shown.miniMe === "hand" || shown.miniMe === "follow" ? -0.4 : 0.45 };
  // eine Verwandlung ist länger als die Figur breit: etwas mehr von der Seite
  if (shown.emote?.costume) return { focus: "wide", rotation: 0.6 };
  return { focus: "body", rotation: 0.35 };
}

/** Stellt Abstand, Höhe und Drehung der Figur ein. */
export function frameFigure(viewer: SkinViewer, view: CosmeticsView): void {
  // die Kamera schaut auf die Mitte der Figur – für den Kopf rückt die Figur nach unten
  viewer.playerWrapper.position.y = view.focus === "head" ? -13.5 : view.focus === "top" ? -16 : 0;
  viewer.playerWrapper.rotation.set(TILT, view.rotation, 0);
  viewer.zoom = view.focus === "head" ? 1.35 : view.focus === "top" ? 1.1 : view.focus === "wide" ? 0.72 : 0.8;
}
