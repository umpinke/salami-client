package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tap Reset: Nach einem Sprint-Treffer wird der Sprint kurz unterbrochen – per W-Tap
 * (Vorwärts kurz loslassen) oder S-Tap (kurz Rückwärts). Danach sprintest du neu an, und
 * der nächste Treffer bekommt wieder den vollen Sprint-Rückstoß.
 *
 * <p>Die Tasten werden nicht wirklich gedrückt: {@code KeyboardInputMixin} fragt
 * {@link #filterMovement} beim Einlesen der Bewegung.
 */
public final class TapResetModule extends Module implements AttackListener {
	public enum Mode { W_TAP, S_TAP }

	private static TapResetModule instance;

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.S_TAP));
	/** Wie viele Ticks die Bewegung angepasst wird. */
	private final NumberSetting duration = add(new NumberSetting("duration", 2, 1, 10, 1));

	private int ticksLeft;

	public TapResetModule() {
		super("tap_reset", Category.UTILITY);
		instance = this;
	}

	@Override
	protected void onDisable() {
		ticksLeft = 0;
	}

	@Override
	public void beforeAttack(Entity target) {
		if (target instanceof LivingEntity && mc().player.isSprinting()) {
			ticksLeft = duration.intValue();
		}
	}

	/** Für {@code KeyboardInputMixin}: Bewegungstaste während eines Taps anpassen. */
	public static boolean filterMovement(KeyMapping key, boolean down) {
		TapResetModule module = instance;
		if (module == null || !module.isEnabled() || module.ticksLeft <= 0) {
			return down;
		}
		Options options = Minecraft.getInstance().options;
		if (key == options.keyUp && module.mode.get() == Mode.W_TAP) {
			return false;
		}
		if (key == options.keyDown && module.mode.get() == Mode.S_TAP) {
			return true;
		}
		if (key == options.keySprint) {
			// Sprint ist die letzte Taste, die KeyboardInput pro Tick abfragt
			module.ticksLeft--;
		}
		return down;
	}
}
