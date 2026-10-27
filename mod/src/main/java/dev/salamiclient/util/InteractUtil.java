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
			swing(player);
			return true;
		}
		return false;
	}

	/** Rechtsklick mit der Haupthand in die Luft (Enderperle, Windkugel, Erfahrungsflasche …). */
	public static boolean use() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.gameMode == null || player == null) {
			return false;
		}
		if (mc.gameMode.useItem(player, InteractionHand.MAIN_HAND) instanceof InteractionResult.Success success) {
			if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
				swing(player);
			}
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
		swing(mc.player);
	}

	/** Schwung-Animation der Haupthand (ab 26.3 mit Animationsart). */
	public static void swing(LocalPlayer player) {
		//? if >=26.3 {
		/*player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		*///?} else
		player.swing(InteractionHand.MAIN_HAND);
	}
}
