package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * TNT Timer: zeigt über gezündetem TNT die Zeit bis zur Explosion. Der Name wird nur auf deinem
 * Client gesetzt (der Server sendet für TNT keinen Namen), andere Spieler sehen nichts.
 */
public final class TntTimerModule extends Module {
	private static final double RANGE = 48.0;

	/** Beschriftetes TNT → zuletzt angezeigter Wert (in Zehntelsekunden). */
	private final Map<PrimedTnt, Integer> labelled = new HashMap<>();

	public TntTimerModule() {
		super("tnt_timer", Category.VISUAL);
	}

	@Override
	public void onTick() {
		var player = mc().player;
		labelled.keySet().removeIf(tnt -> tnt.isRemoved());
		for (PrimedTnt tnt : mc().level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(RANGE))) {
			int fuse = Math.max(0, tnt.getFuse());
			int tenths = fuse / 2; // 1 Tick = 0,05 s
			Integer shown = labelled.put(tnt, tenths);
			if (shown != null && shown == tenths) {
				continue;
			}
			ChatFormatting color = fuse > 40 ? ChatFormatting.GREEN : fuse > 20 ? ChatFormatting.GOLD : ChatFormatting.RED;
			tnt.setCustomName(Component.literal(tenths / 10 + "." + tenths % 10 + "s").withStyle(color));
			tnt.setCustomNameVisible(true);
		}
	}

	@Override
	protected void onDisable() {
		for (PrimedTnt tnt : labelled.keySet()) {
			tnt.setCustomName(null);
			tnt.setCustomNameVisible(false);
		}
		labelled.clear();
	}
}
