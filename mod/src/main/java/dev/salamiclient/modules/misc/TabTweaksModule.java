package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Tab Tweaks: Ping in der Tab-Liste als Zahl (farbig) statt Balken. */
public final class TabTweaksModule extends Module {
	private static TabTweaksModule instance;

	public TabTweaksModule() {
		super("tab_tweaks", Category.MISC);
		instance = this;
	}

	/** Für {@code PlayerTabOverlayMixin}: {@code true} = Zahl gezeichnet, Vanilla-Balken weglassen. */
	public static boolean renderPing(GuiGraphics graphics, int width, int x, int y, PlayerInfo info) {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		int ping = info.getLatency();
		String text = ping < 0 ? "?" : String.valueOf(Math.min(ping, 9999));
		int color = ping < 0 ? 0xFFAAAAAA : ping < 80 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : ping < 300 ? 0xFFFFAA00 : 0xFFFF5555;
		var font = Minecraft.getInstance().font;
		float scale = 0.75f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + width - 1 - font.width(text) * scale, y + 1);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
		return true;
	}
}
