package dev.salamiclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** End-Kristall-Helfer für Auto Crystal und Auto D-Tap. */
public final class CrystalUtil {
	private CrystalUtil() {}

	/** Gleiche Bedingungen wie EndCrystalItem: Obsidian/Grundgestein, Luft darüber, kein Entity im Weg. */
	public static boolean canPlaceOn(BlockPos base) {
		ClientLevel level = Minecraft.getInstance().level;
		if (!isCrystalBase(level.getBlockState(base))) {
			return false;
		}
		BlockPos above = base.above();
		if (!level.getBlockState(above).isAir()) {
			return false;
		}
		AABB space = new AABB(above).expandTowards(0, 1, 0);
		for (Entity entity : level.getEntities((Entity) null, space)) {
			if (entity.isAlive()) {
				return false;
			}
		}
		return true;
	}

	public static boolean isCrystalBase(BlockState state) {
		return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
	}

	/** Kristall in die Haupthand – optional aus der Hotbar wechseln. */
	public static boolean holdCrystal(boolean autoSwitch) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player.getMainHandItem().is(Items.END_CRYSTAL)) {
			return true;
		}
		if (!autoSwitch) {
			return false;
		}
		int slot = InventoryUtil.findHotbar(stack -> stack.is(Items.END_CRYSTAL));
		if (slot == InventoryUtil.NOT_FOUND) {
			return false;
		}
		InventoryUtil.selectHotbar(slot);
		return true;
	}

	/** Klick auf die Oberseite des Blocks (dort entsteht der Kristall). */
	public static BlockHitResult topOf(BlockPos base) {
		return new BlockHitResult(Vec3.atCenterOf(base).add(0, 0.5, 0), Direction.UP, base, false);
	}

	/** Lebender Kristall, der auf diesem Block steht, sonst {@code null}. */
	public static EndCrystal crystalOn(BlockPos base) {
		AABB space = new AABB(base.above()).expandTowards(0, 1, 0);
		for (EndCrystal crystal : Minecraft.getInstance().level.getEntitiesOfClass(EndCrystal.class, space)) {
			if (crystal.isAlive()) {
				return crystal;
			}
		}
		return null;
	}
}
