package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.SlotSwapper;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Auto Mace: Fällst du auf ein anvisiertes Ziel, wird automatisch zum Streitkolben
 * gewechselt und im Fall zugeschlagen (Smash-Angriff, Schaden wächst mit der Fallhöhe).
 * Ein Schlag pro Fall; danach zurück zur vorherigen Waffe.
 */
public final class AutoMaceModule extends Module {
	public enum Prefer { DENSITY, BREACH, ANY }

	private final NumberSetting minFall = add(new NumberSetting("min_fall", 2.0, 1.5, 20, 0.5));
	private final ModeSetting<Prefer> prefer = add(new ModeSetting<>("prefer", Prefer.DENSITY));
	private final NumberSetting swapBack = add(new NumberSetting("swap_back", 3, 0, 10, 1));

	private final SlotSwapper swapper = new SlotSwapper();
	private boolean struckThisFall;

	public AutoMaceModule() {
		super("auto_mace", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		swapper.reset();
		struckThisFall = false;
	}

	@Override
	public void onTick() {
		swapper.tick();
		LocalPlayer player = mc().player;
		if (player.onGround()) {
			struckThisFall = false;
			return;
		}
		if (struckThisFall || mc().screen != null || player.isFallFlying() || player.fallDistance < minFall.get()) {
			return;
		}
		if (!(mc().hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		ResourceKey<Enchantment> preferred = switch (prefer.get()) {
			case DENSITY -> Enchantments.DENSITY;
			case BREACH -> Enchantments.BREACH;
			case ANY -> null;
		};
		int slot = WeaponUtil.bestHotbarSlot(stack -> stack.is(Items.MACE), preferred);
		if (slot == InventoryUtil.NOT_FOUND) {
			return;
		}
		swapper.swapTo(slot, swapBack.intValue());
		InteractUtil.attack(target);
		struckThisFall = true;
	}
}
