package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

/**
 * Hitbox: zeigt die Hitboxen von Entities (wie F3 + B), ohne die Tastenkombination.
 * Wie in Vanilla nicht auf Servern mit „reduzierten Debug-Infos“.
 */
public final class HitboxModule extends Module {
	/** Ob wir die Anzeige eingeschaltet haben (dann schalten wir sie beim Ausschalten wieder aus). */
	private boolean turnedOn;

	public HitboxModule() {
		super("hitbox", Category.VISUAL);
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
