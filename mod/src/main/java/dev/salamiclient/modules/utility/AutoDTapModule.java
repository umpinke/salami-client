package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.CrystalUtil;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Auto D-Tap (Double Tap): Nach jedem Kristall, den du zerstörst, setzt das Makro sofort
 * einen neuen auf denselben Block und zerstört auch den – zwei Explosionen kurz
 * hintereinander, z. B. um nach einem Totem-Pop nachzulegen.
 */
public final class AutoDTapModule extends Module implements AttackListener {
	/** Abbruch, falls der Block nach so vielen Ticks nicht frei wird oder kein Kristall erscheint. */
	private static final int TIMEOUT_TICKS = 20;

	private final NumberSetting taps = add(new NumberSetting("taps", 1, 1, 3, 1));
	private final NumberSetting delay = add(new NumberSetting("delay", 0, 0, 5, 1));
	private final BoolSetting autoSwitch = add(new BoolSetting("auto_switch", true));
	private final NumberSetting minHealth = add(new NumberSetting("min_health", 6, 0, 20, 1));

	private BlockPos base;
	private int remaining;
	private int wait;
	private int age;
	private boolean placed;
	private boolean ownAttack;

	public AutoDTapModule() {
		super("auto_dtap", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		base = null;
	}

	@Override
	public void beforeAttack(Entity target) {
		if (ownAttack || !(target instanceof EndCrystal crystal)) {
			return;
		}
		BlockPos below = crystal.blockPosition().below();
		if (!CrystalUtil.isCrystalBase(mc().level.getBlockState(below))) {
			return;
		}
		base = below;
		remaining = taps.intValue();
		restartStep(false);
	}

	private void restartStep(boolean crystalPlaced) {
		placed = crystalPlaced;
		wait = delay.intValue();
		age = 0;
	}

	@Override
	public void onTick() {
		if (base == null) {
			return;
		}
		if (mc().screen != null || ++age > TIMEOUT_TICKS) {
			base = null;
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (!placed) {
			// erst setzen, wenn der alte Kristall wirklich weg ist (Server hat ihn entfernt)
			if (CrystalUtil.canPlaceOn(base) && CrystalUtil.holdCrystal(autoSwitch.isOn())
				&& InteractUtil.useOn(CrystalUtil.topOf(base))) {
				restartStep(true);
			}
			return;
		}
		EndCrystal crystal = CrystalUtil.crystalOn(base);
		if (crystal == null) {
			return; // noch nicht vom Server bestätigt
		}
		if (InventoryUtil.totalHealth() <= minHealth.get()) {
			base = null;
			return;
		}
		ownAttack = true;
		try {
			InteractUtil.attack(crystal);
		} finally {
			ownAttack = false;
		}
		if (--remaining <= 0) {
			base = null;
		} else {
			restartStep(false);
		}
	}
}
