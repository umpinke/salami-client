package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.CrystalUtil;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Auto Crystal: setzt und zerstört End-Kristalle dort, wo du hinschaust.
 *
 * <ul>
 *   <li>Blick auf Obsidian/Grundgestein mit freiem Platz darüber → Kristall setzen.</li>
 *   <li>Blick auf einen Kristall → zerstören (nicht, wenn dein Leben unter der Grenze liegt).</li>
 * </ul>
 * Auslöser: automatisch oder nur bei gehaltener Benutzen-Taste.
 */
public final class AutoCrystalModule extends Module {
	public enum Trigger { AUTO, HOLD_USE }

	private final ModeSetting<Trigger> trigger = add(new ModeSetting<>("trigger", Trigger.AUTO));
	private final NumberSetting placeDelay = add(new NumberSetting("place_delay", 2, 0, 10, 1));
	private final NumberSetting breakDelay = add(new NumberSetting("break_delay", 2, 0, 10, 1));
	private final BoolSetting autoSwitch = add(new BoolSetting("auto_switch", false));
	private final NumberSetting minHealth = add(new NumberSetting("min_health", 6, 0, 20, 1));

	private int placeCooldown;
	private int breakCooldown;

	public AutoCrystalModule() {
		super("auto_crystal", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		placeCooldown = 0;
		breakCooldown = 0;
	}

	@Override
	public void onTick() {
		if (placeCooldown > 0) placeCooldown--;
		if (breakCooldown > 0) breakCooldown--;
		if (mc().screen != null) {
			return;
		}
		if (trigger.get() == Trigger.HOLD_USE && !mc().options.keyUse.isDown()) {
			return;
		}

		HitResult hit = mc().hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal) {
			if (breakCooldown == 0 && InventoryUtil.totalHealth() > minHealth.get()) {
				InteractUtil.attack(crystal);
				breakCooldown = breakDelay.intValue();
			}
			return;
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && placeCooldown == 0) {
			if (CrystalUtil.canPlaceOn(blockHit.getBlockPos()) && CrystalUtil.holdCrystal(autoSwitch.isOn())) {
				if (InteractUtil.useOn(blockHit)) {
					placeCooldown = placeDelay.intValue();
				}
			}
		}
	}
}
