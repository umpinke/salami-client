package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Info HUD: FPS, Ping, Koordinaten und Blickrichtung – Zeilen einzeln abschaltbar. */
public final class InfoHudModule extends HudModule {
	private final BoolSetting fps = add(new BoolSetting("fps", true));
	private final BoolSetting ping = add(new BoolSetting("ping", true));
	private final BoolSetting coords = add(new BoolSetting("coords", true));
	private final BoolSetting direction = add(new BoolSetting("direction", false));

	private final List<String[]> lines = new ArrayList<>();

	public InfoHudModule() {
		super("info_hud", Category.HUD, false, 0.005, 0.11); // unter dem Watermark
	}

	@Override
	public void onTick() {
		lines.clear();
		LocalPlayer player = mc().player;
		if (fps.isOn()) {
			lines.add(new String[] {"FPS", String.valueOf(mc().getFps())});
		}
		if (ping.isOn() && mc().getConnection() != null) {
			PlayerInfo info = mc().getConnection().getPlayerInfo(player.getUUID());
			lines.add(new String[] {"Ping", info == null ? "-" : info.getLatency() + " ms"});
		}
		if (coords.isOn()) {
			lines.add(new String[] {"XYZ", String.format(Locale.ROOT, "%d %d %d", player.getBlockX(), player.getBlockY(), player.getBlockZ())});
		}
		if (direction.isOn()) {
			lines.add(new String[] {"Dir", player.getDirection().getName()});
		}
	}

	@Override
	public boolean hasContent() {
		return !lines.isEmpty();
	}

	@Override
	public int width() {
		int max = 40;
		for (String[] line : lines) {
			max = Math.max(max, mc().font.width(line[0] + ": " + line[1]));
		}
		return max;
	}

	@Override
	public int height() {
		return Math.max(10, lines.size() * 10);
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		int y = 0;
		for (String[] line : lines) {
			String label = line[0] + ": ";
			graphics.drawString(mc().font, label, 0, y, theme.accent(), true);
			graphics.drawString(mc().font, line[1], mc().font.width(label), y, theme.text(), true);
			y += 10;
		}
	}
}
