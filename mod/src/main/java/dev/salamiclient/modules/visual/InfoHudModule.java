package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/** Info HUD: FPS, Ping, Koordinaten und Blickrichtung – Zeilen einzeln abschaltbar. */
public final class InfoHudModule extends HudModule {
	private final BoolSetting fps = add(new BoolSetting("fps", true));
	private final BoolSetting ping = add(new BoolSetting("ping", true));
	private final BoolSetting coords = add(new BoolSetting("coords", true));
	private final BoolSetting direction = add(new BoolSetting("direction", false));

	/** Zeilen samt Breiten – einmal pro Tick gebaut, pro Frame nur gezeichnet. */
	private record Line(String label, String value, int valueX) {}

	private final List<Line> lines = new ArrayList<>();
	private int width = 40;

	public InfoHudModule() {
		super("info_hud", Category.HUD, false, 0.005, 0.11); // unter dem Watermark
	}

	@Override
	public void onTick() {
		lines.clear();
		LocalPlayer player = mc().player;
		if (fps.isOn()) {
			add("FPS", String.valueOf(mc().getFps()));
		}
		if (ping.isOn() && mc().getConnection() != null) {
			PlayerInfo info = mc().getConnection().getPlayerInfo(player.getUUID());
			add("Ping", info == null ? "-" : info.getLatency() + " ms");
		}
		if (coords.isOn() && !StreamerModeModule.hidesCoords()) {
			add("XYZ", player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ());
		}
		if (direction.isOn()) {
			add("Dir", player.getDirection().getName());
		}
		int max = 40;
		for (Line line : lines) {
			max = Math.max(max, line.valueX() + mc().font.width(line.value()));
		}
		width = max;
	}

	private void add(String name, String value) {
		String label = name + ": ";
		lines.add(new Line(label, value, mc().font.width(label)));
	}

	@Override
	public boolean hasContent() {
		return !lines.isEmpty();
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return Math.max(10, lines.size() * 10);
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		int y = 0;
		for (Line line : lines) {
			GuiDraw.text(graphics, mc().font, line.label(), 0, y, accentColor(), shadow());
			GuiDraw.text(graphics, mc().font, line.value(), line.valueX(), y, textColor(), shadow());
			y += 10;
		}
	}
}
