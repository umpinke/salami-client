package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.util.CrystalUtil;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Crystal Tweaks: Hältst du keinen End-Kristall (weder Haupt- noch Nebenhand), wird
 * automatisch der erste Kristall der Hotbar ausgewählt – auf Wunsch nur, solange du auf
 * Obsidian, Grundgestein oder einen Kristall zielst.
 */
public final class CrystalTweaksModule extends Module {
	private final BoolSetting onlyAiming = add(new BoolSetting("only_aiming", true));

	public CrystalTweaksModule() {
		super("crystal_tweaks", Category.UTILITY);
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (player.getMainHandItem().is(Items.END_CRYSTAL) || player.getOffhandItem().is(Items.END_CRYSTAL)) {
			return;
		}
		if (onlyAiming.isOn() && !aimingAtCrystalSpot()) {
			return;
		}
		int slot = InventoryUtil.findHotbar(stack -> stack.is(Items.END_CRYSTAL));
		if (slot != InventoryUtil.NOT_FOUND) {
			InventoryUtil.selectHotbar(slot);
		}
	}

	private boolean aimingAtCrystalSpot() {
		HitResult hit = mc().hitResult;
		if (hit instanceof EntityHitResult entityHit) {
			return entityHit.getEntity() instanceof EndCrystal;
		}
		return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
			&& CrystalUtil.isCrystalBase(mc().level.getBlockState(blockHit.getBlockPos()));
	}
}
