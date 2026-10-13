package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

/**
 * Bow Spam: Solange du mit dem Bogen Rechtsklick hältst, wird der Pfeil nach der
 * eingestellten Spannzeit automatisch abgeschossen; Minecraft spannt dann sofort neu.
 */
public final class BowSpamModule extends Module {
	/**
	 * Spannzeit in Ticks (20 = voller Schaden). Unter 4 schießt der Bogen nicht: Der Server
	 * zählt einen Tick weniger, und unter Kraft 0.1 entsteht kein Pfeil.
	 */
	private final NumberSetting charge = add(new NumberSetting("charge", 5, 4, 20, 1));

	public BowSpamModule() {
		super("bow_spam", Category.UTILITY);
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (mc().screen != null || !player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
			return;
		}
		if (player.getTicksUsingItem() >= charge.intValue()) {
			mc().gameMode.releaseUsingItem(player);
		}
	}
}
