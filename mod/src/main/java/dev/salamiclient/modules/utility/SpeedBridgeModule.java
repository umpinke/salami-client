package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Speed Bridge: Schleicht automatisch, sobald unter dir kein Block mehr ist – beim
 * Rückwärts-Bauen fällst du nicht von der Kante, ohne selbst Shift zu halten.
 *
 * <p>Die Taste wird nicht wirklich gedrückt: {@code KeyboardInputMixin} fragt
 * {@link #filterMovement} beim Einlesen der Bewegung.
 */
public final class SpeedBridgeModule extends Module {
	private static SpeedBridgeModule instance;

	public SpeedBridgeModule() {
		super("speed_bridge", Category.UTILITY);
		instance = this;
	}

	/** Für {@code KeyboardInputMixin}: an der Kante Schleichen dazunehmen. */
	public static boolean filterMovement(KeyMapping key, boolean down) {
		if (down || instance == null || !instance.isEnabled()) {
			return down;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (key != mc.options.keyShift || player == null || mc.level == null) {
			return down;
		}
		return player.onGround()
			&& !player.getAbilities().flying
			&& mc.level.getBlockState(player.blockPosition().below()).isAir();
	}
}
