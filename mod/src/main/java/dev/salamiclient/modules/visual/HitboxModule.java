package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

/**
 * Hitbox: zeigt die Hitboxen von Entities (wie F3 + B), ohne die Tastenkombination – mit eigenen
 * Farben für Rahmen, Augenhöhe und Blickrichtung (siehe {@code EntityHitboxDebugRendererMixin}).
 * Wie in Vanilla nicht auf Servern mit „reduzierten Debug-Infos“.
 */
public final class HitboxModule extends Module {
	private static HitboxModule instance;

	private final ColorSetting color = add(new ColorSetting("color", 0xFFFFFFFF));
	private final BoolSetting eyeLine = add(new BoolSetting("eye_line", true));
	private final ColorSetting eyeColor = add(new ColorSetting("eye_color", 0xFFFF0000));
	private final BoolSetting lookLine = add(new BoolSetting("look_line", true));
	private final ColorSetting lookColor = add(new ColorSetting("look_color", 0xFF0000FF));

	/** Ob wir die Anzeige eingeschaltet haben (dann schalten wir sie beim Ausschalten wieder aus). */
	private boolean turnedOn;

	public HitboxModule() {
		super("hitbox", Category.VISUAL);
		instance = this;
		eyeColor.showIf(eyeLine::isOn);
		lookColor.showIf(lookLine::isOn);
	}

	private static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** Rahmen-Farbe (Vanilla: weiß). */
	public static int boxColor(int vanilla) {
		return active() ? instance.color.argb() : vanilla;
	}

	/** Linie auf Augenhöhe (Vanilla: rot); aus = unsichtbar. */
	public static int eyeColor(int vanilla) {
		if (!active()) {
			return vanilla;
		}
		return instance.eyeLine.isOn() ? instance.eyeColor.argb() : 0;
	}

	/** Pfeil in Blickrichtung (Vanilla: blau); aus = unsichtbar. */
	public static int lookColor(int vanilla) {
		if (!active()) {
			return vanilla;
		}
		return instance.lookLine.isOn() ? instance.lookColor.argb() : 0;
	}

	@Override
	protected void onEnable() {
		turnedOn = false;
	}

	@Override
	public void onTick() {
		boolean allowed = !mc().player.isReducedDebugInfo();
		boolean shown = mc().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
		if (allowed && !shown) {
			mc().debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
			turnedOn = true;
		} else if (!allowed && shown && turnedOn) {
			mc().debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
			turnedOn = false;
		}
	}

	@Override
	protected void onDisable() {
		if (turnedOn && mc().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			mc().debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
		}
		turnedOn = false;
	}
}
