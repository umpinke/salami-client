package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * Info HUD: FPS, Ping, Koordinaten, Blickrichtung und Biom – Zeilen einzeln abschaltbar,
 * Werte links- oder rechtsbündig (rechtsbündig passt an den rechten Bildschirmrand).
 */
public final class InfoHudModule extends HudModule {
	public enum Align {
		LEFT,
		RIGHT
	}

	private final BoolSetting fps = add(new BoolSetting("fps", true));
	private final BoolSetting ping = add(new BoolSetting("ping", true));
	private final BoolSetting coords = add(new BoolSetting("coords", true));
	private final BoolSetting direction = add(new BoolSetting("direction", false));
	private final BoolSetting biome = add(new BoolSetting("biome", false));
	private final ModeSetting<Align> align = add(new ModeSetting<>("align", Align.LEFT));

	private static final int LINE = 10;

	/** Zeilen samt Breiten – einmal pro Tick gebaut, pro Frame nur gezeichnet. */
	private record Line(String label, String value, int labelWidth, int valueWidth) {}

	private final List<Line> lines = new ArrayList<>();
	private int labelColumn;
	private int width = 40;

	public InfoHudModule() {
		super("info_hud", Category.HUD, false, 0.005, 0.11); // unter dem Watermark
	}

	@Override
	public void onTick() {
		lines.clear();
		LocalPlayer player = mc().player;
		if (fps.isOn()) {
			add(label("fps"), String.valueOf(mc().getFps()));
		}
		if (ping.isOn() && mc().getConnection() != null) {
			PlayerInfo info = mc().getConnection().getPlayerInfo(player.getUUID());
			add(label("ping"), info == null ? "-" : info.getLatency() + " ms");
		}
		if (coords.isOn() && !StreamerModeModule.hidesCoords()) {
			add(label("coords"), player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ());
		}
		if (direction.isOn()) {
			// Himmelsrichtung samt Achse wie bei F3 („Norden (−Z)“)
			add(label("direction"), I18n.get("salami.module.info_hud.direction." + player.getDirection().getName()));
		}
		if (biome.isOn() && mc().level != null) {
			String name = mc().level.getBiome(player.blockPosition()).unwrapKey()
				.map(InfoHudModule::biomeName)
				.orElse("-");
			add(label("biome"), name);
		}
		// Bezeichnungen in einer Spalte, Werte dahinter
		labelColumn = 0;
		int values = 0;
		for (Line line : lines) {
			labelColumn = Math.max(labelColumn, line.labelWidth());
			values = Math.max(values, line.valueWidth());
		}
		width = Math.max(30, labelColumn + 6 + values - 1);
	}

	/**
	 * Übersetzter Biom-Name aus dem Schlüssel ({@code ResourceKey[minecraft:worldgen/biome / minecraft:plains]}).
	 * Über {@code toString}, weil die Methode für die ID zwischen den Minecraft-Versionen anders heißt.
	 */
	private static String biomeName(Object key) {
		String text = key.toString();
		int start = text.lastIndexOf(" / ");
		String id = (start >= 0 ? text.substring(start + 3) : text).replace("]", "").trim();
		String translationKey = "biome." + id.replace(':', '.');
		return Language.getInstance().has(translationKey) ? I18n.get(translationKey) : id.substring(id.indexOf(':') + 1).replace('_', ' ');
	}

	/** Bezeichnung einer Zeile aus der Sprachdatei ({@code salami.module.info_hud.label.<id>}). */
	private static String label(String id) {
		return I18n.get("salami.module.info_hud.label." + id);
	}

	private void add(String name, String value) {
		lines.add(new Line(name, value, mc().font.width(name), mc().font.width(value)));
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
		return Math.max(8, lines.size() * LINE - 2);
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		int y = 0;
		boolean right = align.get() == Align.RIGHT;
		for (Line line : lines) {
			GuiDraw.text(graphics, mc().font, line.label(), 0, y, labelColor(), shadow());
			int valueX = right ? width - line.valueWidth() + 1 : labelColumn + 6;
			GuiDraw.text(graphics, mc().font, line.value(), valueX, y, textColor(), shadow());
			y += LINE;
		}
	}
}
