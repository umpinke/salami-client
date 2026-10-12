package dev.salamiclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/** Block-/Entity-Interaktionen wie ein normaler Rechts-/Linksklick (inkl. Schwung-Animation). */
public final class InteractUtil {
	private InteractUtil() {}

	/** Rechtsklick mit der Haupthand auf einen Block. */
	public static boolean useOn(BlockHitResult hit) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.gameMode == null || player == null) {
			return false;
		}
		InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		if (result instanceof InteractionResult.Success) {
			player.swing(InteractionHand.MAIN_HAND);
			return true;
		}
		return false;
	}

	/** Linksklick auf ein Entity. */
	public static void attack(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode == null || mc.player == null) {
			return;
		}
		mc.gameMode.attack(mc.player, target);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
