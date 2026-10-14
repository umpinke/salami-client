package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TNT Timer: zeigt über gezündetem TNT die Zeit bis zur Explosion. Der Name wird nur auf deinem
 * Client gesetzt (der Server sendet für TNT keinen Namen), andere Spieler sehen nichts.
 */
public final class TntTimerModule extends Module {
	private static final double RANGE = 48.0;

	private final List<PrimedTnt> labelled = new ArrayList<>();

	public TntTimerModule() {
		super("tnt_timer", Category.VISUAL);
	}

	@Override
	public void onTick() {
		var player = mc().player;
		labelled.removeIf(tnt -> tnt.isRemoved());
		for (PrimedTnt tnt : mc().level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(RANGE))) {
			int fuse = Math.max(0, tnt.getFuse());
			ChatFormatting color = fuse > 40 ? ChatFormatting.GREEN : fuse > 20 ? ChatFormatting.GOLD : ChatFormatting.RED;
			tnt.setCustomName(Component.literal(String.format(Locale.ROOT, "%.1fs", fuse / 20.0)).withStyle(color));
			tnt.setCustomNameVisible(true);
			if (!labelled.contains(tnt)) {
				labelled.add(tnt);
			}
		}
	}

	@Override
	protected void onDisable() {
		for (PrimedTnt tnt : labelled) {
			tnt.setCustomName(null);
			tnt.setCustomNameVisible(false);
		}
		labelled.clear();
	}
}
