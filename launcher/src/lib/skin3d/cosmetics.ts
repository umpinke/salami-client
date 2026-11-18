/**
 * Cosmetics an der 3D-Figur (skinview3d): Hüte aus dem Hut-Katalog, Emotes als Haltung und das
 * Mini Me. Alles so wie im Spiel (`HatLayer`, `EmotePoses`, `MiniMeLayer` in salami-core), damit der
 * Shop zeigt, was man dort bekommt.
 *
 * Das Spiel rechnet im Modellraum von Minecraft: y nach unten, das Gesicht bei −z. skinview3d hat y
 * nach oben und das Gesicht bei +z – eine halbe Drehung um x. Drehungen um x bleiben dabei gleich,
 * Drehungen um y und z kehren sich um.
 */
import { BoxGeometry, Group, Mesh, MeshBasicMaterial, MeshStandardMaterial, type Material, type Object3D } from "three";
import { SkinObject, type PlayerObject, type SkinViewer } from "skinview3d";
import {
  sampleTrack,
  type CatalogEmote,
  type CatalogHat,
  type EmotePart,
  type HatPart,
} from "../../services/salami-cosmetics-service";

const radians = (degrees: number) => (degrees * Math.PI) / 180;
/** Hüte und Mini Me bewegen sich im Takt des Spiels (20 Ticks pro Sekunde). */
const TICKS_PER_SECOND = 20;

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
  const geometries: BoxGeometry[] = [];
  const materials = new Map<string, Material>();
  const materialOf = (color: string): Material => {
    let material = materials.get(color);
    if (!material) {
      // leuchtende Hüte (Heiligenschein) sind im Spiel immer voll hell
      material = hat.glow ? new MeshBasicMaterial({ color }) : new MeshStandardMaterial({ color });
      materials.set(color, material);
    }
    return material;
  };
  const build = (part: HatPart): Group => {
    const group = new Group();
    if (part.at) group.position.set(...part.at);
    // wie ein Modellteil in Minecraft: erst zum Drehpunkt, dann um z, y, x drehen
    if (part.rot) group.rotation.set(radians(part.rot[0]), radians(part.rot[1]), radians(part.rot[2]), "ZYX");
    for (const [x, y, z, w, h, d] of part.boxes) {
      const geometry = new BoxGeometry(w, h, d);
      geometries.push(geometry);
      const mesh = new Mesh(geometry, materialOf(part.color ?? "#ffffff"));
      mesh.position.set(x + w / 2, y + h / 2, z + d / 2);
      group.add(mesh);
    }
    for (const child of part.parts) group.add(build(child));
    return group;
  };

  const root = new Group();
  root.rotation.x = Math.PI; // Modellraum von Minecraft
  const float = new Group();
  const model = new Group();
  model.scale.setScalar(hat.scale);
  for (const part of hat.parts) model.add(build(part));
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
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => material.dispose());
    },
  };
}

/** Setzt der Figur den Hut auf (an den Kopf, Schulter-Hüte an den Körper). */
export function attachHat(player: PlayerObject, hat: CatalogHat, object: HatObject): void {
  (hat.anchor === "shoulder" ? player.skin : player.skin.head).add(object.root);
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

/** Spielt ein Emote in Dauerschleife: Haltung zur Zeit `seconds` seit dem Start der Vorschau. */
export function playEmote(skin: SkinObject, emote: CatalogEmote, seconds: number): void {
  if (emote.loop) {
    poseEmote(skin, emote, seconds, Math.min(1, seconds / BLEND_IN_SECONDS));
    return;
  }
  const t = seconds % (emote.duration + BLEND_OUT_SECONDS + REPEAT_PAUSE_SECONDS);
  const fadeIn = Math.min(1, t / BLEND_IN_SECONDS);
  const fadeOut = t <= emote.duration ? 1 : Math.max(0, 1 - (t - emote.duration) / BLEND_OUT_SECONDS);
  const weight = Math.min(fadeIn, fadeOut);
  if (weight > 0) poseEmote(skin, emote, t, weight);
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
  /** `body`: die ganze Figur. `head`: Kopf und Schultern, für Hüte. `wide`: mit Platz neben der Figur. */
  focus: "body" | "head" | "wide";
  /** Drehung um die Hochachse (Radiant): positiv holt die rechte Seite der Figur nach vorn, negativ die linke. */
  rotation: number;
}

/** Leicht nach vorn geneigt, damit man die Figur räumlich sieht. */
const TILT = 0.1;

/** Ausschnitt, in dem man das gezeigte Cosmetic gut sieht (ohne Cosmetic: die ganze Figur). */
export function viewOf(shown: { hat?: CatalogHat | null; miniMe?: MiniMePlace | null }): CosmeticsView {
  // Schulter-Hüte schweben links, das Mini Me sitzt rechts oder hängt an der linken Hand
  if (shown.hat) return { focus: "head", rotation: shown.hat.anchor === "shoulder" ? -0.5 : -0.35 };
  if (shown.miniMe) return { focus: "wide", rotation: shown.miniMe === "hand" || shown.miniMe === "follow" ? -0.4 : 0.45 };
  return { focus: "body", rotation: 0.35 };
}

/** Stellt Abstand, Höhe und Drehung der Figur ein. */
export function frameFigure(viewer: SkinViewer, view: CosmeticsView): void {
  // die Kamera schaut auf die Mitte der Figur – für den Kopf rückt die Figur nach unten
  viewer.playerWrapper.position.y = view.focus === "head" ? -13.5 : 0;
  viewer.playerWrapper.rotation.set(TILT, view.rotation, 0);
  viewer.zoom = view.focus === "head" ? 1.35 : view.focus === "wide" ? 0.72 : 0.8;
}
