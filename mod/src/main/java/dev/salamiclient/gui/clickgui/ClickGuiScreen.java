package dev.salamiclient.gui.clickgui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.gui.ColorPicker;
import dev.salamiclient.gui.ConfigsScreen;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.HudEditorScreen;
import dev.salamiclient.gui.MinigamesScreen;
import dev.salamiclient.gui.ModsScreen;
import dev.salamiclient.gui.SettingText;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.ActionSetting;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.setting.Setting;
import dev.salamiclient.setting.StringListSetting;
import dev.salamiclient.theme.Theme;
import dev.salamiclient.util.Keys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ClickGUI: ein verschiebbares Panel pro Kategorie.
 *
 * <ul>
 *   <li>Linksklick auf Modul: an/aus · Rechtsklick: Einstellungen auf-/zuklappen</li>
 *   <li>„Öffnen“ in der Modulzeile: eigenes Menü des Moduls (Freunde, Cosmetics, Wegpunkte)</li>
 *   <li>Kopfzeile ziehen: Panel verschieben · Rechtsklick: Panel einklappen</li>
 *   <li>Einstellungen: Linksklick ändert, Rechtsklick ändert rückwärts bzw. setzt zurück</li>
 * </ul>
 * Layout wird bei jedem Frame aus dem Modulzustand berechnet ({@link #rows});
 * Zeichnen und Klicken nutzen dieselbe Berechnung, damit beides nie auseinanderläuft.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_WIDTH = 132;
	private static final int HEADER_HEIGHT = 18;
	private static final int MODULE_HEIGHT = 15;
	private static final int SETTING_HEIGHT = 13;
	private static final int PADDING = 6;
	private static final int INDENT = 4;

	/** Aufgeklappte Module bleiben während der Sitzung aufgeklappt. */
	private static final Set<String> EXPANDED = new HashSet<>();

	private final ModuleManager modules;
	private final ConfigManager config;
	private final List<Panel> panels = new ArrayList<>();

	private Panel draggedPanel;
	private double dragOffsetX;
	private double dragOffsetY;
	private NumberSetting draggedSlider;
	private int sliderLeft;
	private int sliderWidth;
	private KeybindSetting listeningKeybind;
	private boolean capturedLastKey;
	/** Maus in GUI-Koordinaten (für den Hover des „Öffnen“-Knopfs). */
	private int lastMouseX;
	private int lastMouseY;

	private static final class Panel {
		final Category category;
		int x;
		int y;
		boolean open = true;

		Panel(Category category, int x, int y) {
			this.category = category;
			this.x = x;
			this.y = y;
		}
	}

	/** Sichtbare Zeile: ein Modul ({@code setting == null}) oder eine seiner Einstellungen. */
	private record Row(Module module, Setting<?> setting, int y, int height) {
		boolean isModule() {
			return setting == null;
		}
	}

	/** Ergebnis eines Treffertests: Panel + Kopfzeile oder Zeile (oder leere Fläche). */
	private record Hit(Panel panel, Row row, boolean header) {}

	public ClickGuiScreen(ModuleManager modules, ConfigManager config) {
		super(Component.translatable("salami.clickgui.title"));
		this.modules = modules;
		this.config = config;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		if (panels.isEmpty()) {
			loadPanels();
		}
		panels.forEach(this::clamp);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xA60A0809);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	//?}
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		lastMouseX = mx;
		lastMouseY = my;
		Hit hover = (draggedPanel == null && draggedSlider == null) ? hitTest(mx, my) : null;

		graphics.pose().pushMatrix();
		graphics.pose().scale(uiScale, uiScale);
		for (Panel panel : panels) {
			renderPanel(graphics, panel, theme, hover);
		}
		String hint = I18n.get("salami.clickgui.hint");
		int hintWidth = font.width(hint) + 16;
		GuiDraw.roundRect(graphics, (uiWidth() - hintWidth) / 2, uiHeight() - 18, hintWidth, 14, 0xB0000000);
		GuiDraw.centeredText(graphics, font, hint, uiWidth() / 2, uiHeight() - 15, theme.textDim());
		renderHudButton(graphics, theme, mx, my);
		renderModsButton(graphics, theme, mx, my);
		renderConfigsButton(graphics, theme, mx, my);
		renderCosmeticsButton(graphics, theme, mx, my);
		renderMinigamesButton(graphics, theme, mx, my);
		renderScaleControls(graphics, theme, mx, my);
		if (picker != null) {
			picker.render(graphics, font, theme, mx, my);
		}
		graphics.pose().popMatrix();

		Component tooltip = picker != null && picker.contains(mx, my) ? null : tooltipFor(hover);
		int tooltipY = mouseY;
		if (scaleControlAt(mx, my) >= 0) {
			// unter die Knöpfe, damit die Prozentzahl sichtbar bleibt
			tooltip = Component.translatable("salami.clickgui.scale.tooltip");
			tooltipY = Math.round(CONTROL_BOTTOM * uiScale) + 16;
		}
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, tooltipY);
		}
	}

	private void renderPanel(GuiGraphics g, Panel panel, Theme theme, Hit hover) {
		List<Row> rows = rows(panel);
		int right = panel.x + PANEL_WIDTH;
		int bottom = bottom(panel, rows);
		int height = bottom - panel.y;

		// Schatten, Rahmen, Fläche – jeweils mit abgeschrägten Ecken
		GuiDraw.roundRect(g, panel.x + 2, panel.y + 3, PANEL_WIDTH, height, 0x50000000);
		GuiDraw.roundRect(g, panel.x, panel.y, PANEL_WIDTH, height, theme.outline());
		GuiDraw.roundRect(g, panel.x + 1, panel.y + 1, PANEL_WIDTH - 2, height - 2, theme.panel() | 0xFF000000);
		if (panel.open) {
			g.fill(panel.x + 2, panel.y + 1, right - 2, panel.y + 2, theme.header());
			g.fill(panel.x + 1, panel.y + 2, right - 1, panel.y + HEADER_HEIGHT, theme.header());
			g.fill(panel.x + 1, panel.y + HEADER_HEIGHT - 1, right - 1, panel.y + HEADER_HEIGHT, theme.accent());
		} else {
			GuiDraw.roundRect(g, panel.x + 1, panel.y + 1, PANEL_WIDTH - 2, HEADER_HEIGHT - 2, theme.header());
		}

		int textY = panel.y + 5;
		GuiDraw.text(g, font, I18n.get(panel.category.translationKey()), panel.x + PADDING, textY, theme.headerText(), false);
		int markerX = right - PADDING - 5;
		drawChevron(g, markerX, panel.y + (panel.open ? 8 : 7), panel.open, theme.headerText());

		// „3/12“: eingeschaltete von allen Modulen der Kategorie
		int total = 0;
		int enabled = 0;
		for (Module module : modules.byCategory(panel.category)) {
			total++;
			if (module.isEnabled()) {
				enabled++;
			}
		}
		String count = enabled + "/" + total;
		int countX = markerX - 5 - font.width(count);
		GuiDraw.text(g, font, count, countX, textY, (theme.headerText() & 0x00FFFFFF) | 0x99000000, false);

		if (panel.open && rows.isEmpty()) {
			GuiDraw.text(g, font, I18n.get("salami.clickgui.empty"), panel.x + PADDING, panel.y + HEADER_HEIGHT + 4, theme.textDim(), false);
		}

		for (Row row : rows) {
			boolean hovered = hover != null && row.equals(hover.row());
			if (row.isModule()) {
				renderModuleRow(g, panel, row, theme, hovered);
			} else {
				renderSettingRow(g, panel, row, theme, hovered);
			}
		}
	}

	/** Kleiner Pfeil aus Pixeln: nach unten (aufgeklappt) oder nach rechts (zugeklappt). */
	private static void drawChevron(GuiGraphics g, int x, int y, boolean down, int color) {
		if (down) {
			g.fill(x, y, x + 5, y + 1, color);
			g.fill(x + 1, y + 1, x + 4, y + 2, color);
			g.fill(x + 2, y + 2, x + 3, y + 3, color);
		} else {
			g.fill(x + 1, y, x + 2, y + 5, color);
			g.fill(x + 2, y + 1, x + 3, y + 4, color);
			g.fill(x + 3, y + 2, x + 4, y + 3, color);
		}
	}

	/** Knopf der oberen Leiste; {@code primary} ist vollflächig in der Akzentfarbe. */
	private void renderTopButton(GuiGraphics g, Theme theme, int[] b, String label, int textInset, boolean hovered, boolean primary) {
		int width = b[2] - b[0];
		int height = b[3] - b[1];
		int fill = primary ? theme.accent() : hovered ? theme.header() : theme.panel() | 0xFF000000;
		GuiDraw.roundRect(g, b[0], b[1], width, height, primary || hovered ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, b[0] + 1, b[1] + 1, width - 2, height - 2, fill);
		if (primary && hovered) {
			GuiDraw.roundRect(g, b[0] + 1, b[1] + 1, width - 2, height - 2, 0x30FFFFFF);
		}
		GuiDraw.text(g, font, label, b[0] + textInset, b[1] + 3, primary ? GuiDraw.textOn(theme.accent()) : theme.headerText(), false);
	}

	// ------------------------------------------------------------------ HUD-Editor-Knopf

	private int[] hudButton() {
		int w = font.width(I18n.get("salami.clickgui.hud_editor")) + 12;
		return new int[] {uiWidth() - w - 6, 4, uiWidth() - 6, 18};
	}

	private void renderHudButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = hudButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		renderTopButton(g, theme, b, I18n.get("salami.clickgui.hud_editor"), 6, hovered, false);
	}

	// ------------------------------------------------------------------ Mods-Knopf (oben links)

	private int[] modsButton() {
		int w = font.width(I18n.get("salami.clickgui.mods")) + 16;
		return new int[] {6, 4, 6 + w, 18};
	}

	private void renderModsButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = modsButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		renderTopButton(g, theme, b, I18n.get("salami.clickgui.mods"), 8, hovered, true);
	}

	// ------------------------------------------------------------------ Configs-Knopf (neben Mods)

	private int[] configsButton() {
		int left = modsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.configs")) + 12;
		return new int[] {left, 4, left + w, 18};
	}

	private void renderConfigsButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = configsButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		renderTopButton(g, theme, b, I18n.get("salami.clickgui.configs"), 6, hovered, false);
	}

	// ------------------------------------------------------------------ Cosmetics-Knopf (neben Configs)

	private int[] cosmeticsButton() {
		int left = configsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.cosmetics")) + 12;
		return new int[] {left, 4, left + w, 18};
	}

	private void renderCosmeticsButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = cosmeticsButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		renderTopButton(g, theme, b, I18n.get("salami.clickgui.cosmetics"), 6, hovered, false);
	}

	// ------------------------------------------------------------------ Minispiele-Knopf (neben Cosmetics)

	private int[] minigamesButton() {
		int left = cosmeticsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.minigames")) + 12;
		return new int[] {left, 4, left + w, 18};
	}

	private void renderMinigamesButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = minigamesButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		renderTopButton(g, theme, b, I18n.get("salami.clickgui.minigames"), 6, hovered, false);
	}

	// ------------------------------------------------------------------ Größe (− 100 % +)

	private static final int CONTROL_TOP = 4;
	private static final int CONTROL_BOTTOM = 18;
	private static final int CONTROL_BUTTON = 14;

	/** Felder links neben dem HUD-Knopf: [−], [Prozent], [+] jeweils als {x0, x1}. */
	private int[][] scaleControls() {
		int right = hudButton()[0] - 4;
		int plus = right - CONTROL_BUTTON;
		int label = plus - (font.width("200%") + 8);
		int minus = label - CONTROL_BUTTON;
		return new int[][] {{minus, label}, {label, plus}, {plus, right}};
	}

	/** 0 = kleiner, 1 = Prozentanzeige (zurücksetzen), 2 = größer, -1 = nichts. */
	private int scaleControlAt(double x, double y) {
		if (y < CONTROL_TOP || y >= CONTROL_BOTTOM) {
			return -1;
		}
		int[][] controls = scaleControls();
		for (int i = 0; i < controls.length; i++) {
			if (x >= controls[i][0] && x < controls[i][1]) {
				return i;
			}
		}
		return -1;
	}

	private void renderScaleControls(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[][] controls = scaleControls();
		int hovered = scaleControlAt(mouseX, mouseY);
		for (int i = 0; i < controls.length; i++) {
			int x0 = controls[i][0];
			int x1 = controls[i][1];
			g.fill(x0, CONTROL_TOP, x1, CONTROL_BOTTOM, theme.outline());
			g.fill(x0 + (i == 0 ? 1 : 0), CONTROL_TOP + 1, x1 - (i == 2 ? 1 : 0), CONTROL_BOTTOM - 1, hovered == i ? theme.header() : theme.panel() | 0xFF000000);
			if (i == 1) {
				String percent = Math.round(uiScale * 100) + "%";
				GuiDraw.text(g, font, percent, (x0 + x1 - font.width(percent)) / 2, CONTROL_TOP + 3, theme.headerText(), false);
			} else {
				drawMagnifier(g, (x0 + x1) / 2 - 5, CONTROL_TOP + 2, i == 2, theme.headerText());
			}
		}
	}

	/** Pixel-Lupe (10x10) mit − oder + im Glas; (x, y) ist die linke obere Ecke. */
	private static void drawMagnifier(GuiGraphics g, int x, int y, boolean plus, int color) {
		// Glas: Ring 7x7 ohne Ecken
		g.fill(x + 1, y, x + 6, y + 1, color);
		g.fill(x + 1, y + 6, x + 6, y + 7, color);
		g.fill(x, y + 1, x + 1, y + 6, color);
		g.fill(x + 6, y + 1, x + 7, y + 6, color);
		// Griff schräg nach rechts unten
		g.fill(x + 6, y + 6, x + 8, y + 8, color);
		g.fill(x + 8, y + 8, x + 10, y + 10, color);
		// − bzw. + im Glas
		g.fill(x + 2, y + 3, x + 5, y + 4, color);
		if (plus) {
			g.fill(x + 3, y + 2, x + 4, y + 5, color);
		}
	}

	/** Größe ändern ({@code steps} × 10 %, 0 = 100 %); schaltet das Modul „Oberfläche“ bei Bedarf ein. */
	private void stepScale(int steps) {
		GuiScaleModule module = GuiScaleModule.get();
		if (module == null) {
			return;
		}
		if (!module.isEnabled()) {
			modules.setEnabled(module, true);
		}
		module.stepClickGui(steps);
		rescale();
	}

	private void renderModuleRow(GuiGraphics g, Panel panel, Row row, Theme theme, boolean hovered) {
		Module module = row.module();
		int left = panel.x + 1;
		int right = panel.x + PANEL_WIDTH - 1;
		boolean on = module.isEnabled();
		if (hovered) {
			GuiDraw.rounded(g, left + 2, row.y() + 1, right - left - 4, row.height() - 2, 3, theme.hover());
		}
		if (on) {
			// schmaler Akzent-Strich statt eingefärbter Zeile
			GuiDraw.rounded(g, left + 2, row.y() + 3, 2, row.height() - 6, 1, theme.accent());
		}
		boolean hasSettings = false;
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				hasSettings = true;
				break;
			}
		}
		boolean expanded = EXPANDED.contains(module.id());
		int expanderX = right - PADDING - 4;
		int nameRight = expanderX - 4;
		if (module.hasScreen()) {
			int[] b = openButton(panel, row);
			nameRight = b[0] - 3;
			boolean overButton = hovered && lastMouseX >= b[0] && lastMouseX < b[2] && lastMouseY >= b[1] && lastMouseY < b[3];
			GuiDraw.roundRect(g, b[0], b[1], b[2] - b[0], b[3] - b[1], overButton ? theme.accent() : theme.outline());
			GuiDraw.roundRect(g, b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2, overButton ? theme.accent() : theme.panel() | 0xFF000000);
			GuiDraw.text(g, font, I18n.get("salami.clickgui.open"), b[0] + 4, b[1] + 2,
				overButton ? GuiDraw.textOn(theme.accent()) : theme.headerText(), false);
		}
		String name = font.plainSubstrByWidth(I18n.get(module.translationKey()), nameRight - panel.x - PADDING);
		GuiDraw.text(g, font, name, panel.x + PADDING + (on ? 2 : 0), row.y() + 4, on ? theme.headerText() : theme.text(), false);
		if (hasSettings) {
			drawChevron(g, expanderX, row.y() + (expanded ? 6 : 5), expanded, expanded ? theme.accent() : theme.textDim());
		}
	}

	/** „Öffnen“-Knopf links vom Pfeil, nur bei Modulen mit eigenem Menü: {x0, y0, x1, y1}. */
	private int[] openButton(Panel panel, Row row) {
		int right = panel.x + PANEL_WIDTH - 1 - PADDING - 4 - 5;
		int w = font.width(I18n.get("salami.clickgui.open")) + 8;
		return new int[] {right - w, row.y() + 1, right, row.y() + row.height() - 1};
	}

	private void renderSettingRow(GuiGraphics g, Panel panel, Row row, Theme theme, boolean hovered) {
		Setting<?> setting = row.setting();
		int left = panel.x + PADDING + INDENT;
		int right = panel.x + PANEL_WIDTH - PADDING;
		int textY = row.y() + 3;
		// Einstellungen liegen auf einer dunkleren Fläche unter ihrem Modul
		g.fill(panel.x + 1, row.y(), panel.x + PANEL_WIDTH - 1, row.y() + row.height(), 0x38000000);
		if (hovered) {
			g.fill(panel.x + 1, row.y(), panel.x + PANEL_WIDTH - 1, row.y() + row.height(), theme.hover());
		}
		g.fill(panel.x + PADDING, row.y(), panel.x + PADDING + 1, row.y() + row.height(), theme.outline());
		boolean chip = false;

		String value;
		int valueColor = theme.text();
		int valueRight = right;

		if (setting instanceof BoolSetting bool) {
			// Schalter statt „An/Aus“
			value = "";
			int trackY = row.y() + 4;
			GuiDraw.roundRect(g, right - 12, trackY, 12, 6, bool.isOn() ? theme.accent() : theme.outline());
			int knobX = bool.isOn() ? right - 5 : right - 11;
			g.fill(knobX, trackY + 1, knobX + 4, trackY + 5, bool.isOn() ? GuiDraw.textOn(theme.accent()) : theme.textDim());
			valueRight = right - 15;
		} else if (setting instanceof NumberSetting number) {
			value = number.display();
			int trackY = row.y() + row.height() - 2;
			int knob = left + (int) Math.round((right - left) * number.fraction());
			g.fill(left, trackY, right, trackY + 1, theme.outline());
			g.fill(left, trackY, knob, trackY + 1, theme.accent());
			g.fill(Math.max(left, knob - 1), trackY - 1, Math.min(right, knob + 1), trackY + 2, theme.headerText());
		} else if (setting instanceof ModeSetting<?> mode) {
			value = modeLabel(mode);
		} else if (setting instanceof KeybindSetting keybind) {
			value = listeningKeybind == keybind ? "..." : keybind.keyName();
			chip = true;
			if (listeningKeybind == keybind) {
				valueColor = theme.accent();
			}
		} else if (setting instanceof ColorSetting color) {
			value = "";
			GuiDraw.roundRect(g, right - 10, row.y() + 2, 10, 9, picker != null && picker.setting() == color ? theme.accent() : theme.outline());
			g.fill(right - 9, row.y() + 3, right - 1, row.y() + 10, color.argb());
			valueRight = right - 12;
		} else if (setting instanceof StringListSetting list) {
			value = I18n.get("salami.clickgui.entries", list.get().size());
		} else if (setting instanceof ActionSetting) {
			value = I18n.get("salami.mods.open");
			valueColor = theme.accent();
		} else {
			value = String.valueOf(setting.get());
		}

		int valueWidth = font.width(value);
		if (chip) {
			// Taste als kleines Feld
			GuiDraw.roundRect(g, right - valueWidth - 6, row.y() + 1, valueWidth + 6, row.height() - 2, theme.hover());
			valueRight = right - 3;
		}
		String label = font.plainSubstrByWidth(label(setting), valueRight - valueWidth - left - 4);
		GuiDraw.text(g, font, label, left, textY, theme.textDim(), false);
		if (!value.isEmpty()) {
			GuiDraw.text(g, font, value, valueRight - valueWidth, textY, valueColor, false);
		}
	}

	private Component tooltipFor(Hit hit) {
		if (hit == null) {
			return null;
		}
		if (hit.row() == null) {
			return null;
		}
		Row row = hit.row();
		if (row.isModule()) {
			String key = row.module().descriptionKey();
			return Language.getInstance().has(key) ? Component.translatable(key) : null;
		}
		String desc = SettingText.description(row.setting());
		if (desc != null) {
			return Component.literal(desc);
		}
		return row.setting() instanceof StringListSetting ? Component.translatable("salami.clickgui.edit_in_config") : null;
	}

	private static String modeLabel(ModeSetting<?> mode) {
		return SettingText.modeLabel(mode);
	}

	private static String label(Setting<?> setting) {
		return SettingText.label(setting);
	}

	// ------------------------------------------------------------------ Layout

	private List<Row> rows(Panel panel) {
		List<Row> rows = new ArrayList<>();
		if (!panel.open) {
			return rows;
		}
		int y = panel.y + HEADER_HEIGHT;
		for (Module module : modules.byCategory(panel.category)) {
			rows.add(new Row(module, null, y, MODULE_HEIGHT));
			y += MODULE_HEIGHT;
			if (EXPANDED.contains(module.id())) {
				for (Setting<?> setting : module.settings()) {
					if (setting.isHidden()) {
						continue;
					}
					rows.add(new Row(module, setting, y, SETTING_HEIGHT));
					y += SETTING_HEIGHT;
				}
			}
		}
		return rows;
	}

	private int bottom(Panel panel, List<Row> rows) {
		if (!panel.open) {
			return panel.y + HEADER_HEIGHT;
		}
		if (rows.isEmpty()) {
			return panel.y + HEADER_HEIGHT + MODULE_HEIGHT;
		}
		Row last = rows.get(rows.size() - 1);
		return last.y() + last.height() + 3;
	}

	/** Oberstes Panel zuerst (zuletzt gezeichnet = liegt oben). */
	private Hit hitTest(double mouseX, double mouseY) {
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel panel = panels.get(i);
			if (mouseX < panel.x || mouseX >= panel.x + PANEL_WIDTH) {
				continue;
			}
			if (mouseY >= panel.y && mouseY < panel.y + HEADER_HEIGHT) {
				return new Hit(panel, null, true);
			}
			List<Row> rows = rows(panel);
			for (Row row : rows) {
				if (mouseY >= row.y() && mouseY < row.y() + row.height()) {
					return new Hit(panel, row, false);
				}
			}
			if (mouseY >= panel.y && mouseY < bottom(panel, rows)) {
				return new Hit(panel, null, false);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		event = scaled(event);
		listeningKeybind = null;
		if (picker != null) {
			if (picker.mouseClicked(font, event.x(), event.y(), event.button())) {
				return true;
			}
			picker = null; // Klick daneben schließt den Farbwähler
		}
		int control = scaleControlAt(event.x(), event.y());
		if (control >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			stepScale(control - 1); // − = -1, Prozent = zurücksetzen, + = +1
			return true;
		}
		int[] b = hudButton();
		if (event.x() >= b[0] && event.x() < b[2] && event.y() >= b[1] && event.y() < b[3]) {
			minecraft.setScreen(new HudEditorScreen(this));
			return true;
		}
		int[] m = modsButton();
		if (event.x() >= m[0] && event.x() < m[2] && event.y() >= m[1] && event.y() < m[3]) {
			minecraft.setScreen(new ModsScreen(modules, this));
			return true;
		}
		int[] c = configsButton();
		if (event.x() >= c[0] && event.x() < c[2] && event.y() >= c[1] && event.y() < c[3]) {
			minecraft.setScreen(new ConfigsScreen(this));
			return true;
		}
		int[] cos = cosmeticsButton();
		if (event.x() >= cos[0] && event.x() < cos[2] && event.y() >= cos[1] && event.y() < cos[3]) {
			minecraft.setScreen(new CosmeticsScreen(this));
			return true;
		}
		int[] games = minigamesButton();
		if (event.x() >= games[0] && event.x() < games[2] && event.y() >= games[1] && event.y() < games[3]) {
			MinigamesScreen.open(this);
			return true;
		}
		Hit hit = hitTest(event.x(), event.y());
		if (hit == null) {
			return super.mouseClicked(event, doubleClick);
		}
		Panel panel = hit.panel();
		bringToFront(panel);
		int button = event.button();

		if (hit.header()) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				draggedPanel = panel;
				dragOffsetX = event.x() - panel.x;
				dragOffsetY = event.y() - panel.y;
			} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				panel.open = !panel.open;
				savePanels();
			}
			return true;
		}

		Row row = hit.row();
		if (row == null) {
			return true;
		}
		if (row.isModule()) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT && row.module().hasScreen()) {
				int[] open = openButton(panel, row);
				if (event.x() >= open[0] && event.x() < open[2]) {
					row.module().openScreen();
					return true;
				}
			}
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				modules.toggle(row.module());
			} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				String id = row.module().id();
				if (!EXPANDED.remove(id)) {
					EXPANDED.add(id);
				}
			}
			return true;
		}
		clickSetting(row, panel, event.x(), button);
		return true;
	}

	private void clickSetting(Row row, Panel panel, double mouseX, int button) {
		Setting<?> setting = row.setting();
		boolean back = button == InputConstants.MOUSE_BUTTON_RIGHT;
		if (setting instanceof BoolSetting bool) {
			bool.toggle();
		} else if (setting instanceof NumberSetting number) {
			if (back) {
				number.reset();
			} else {
				draggedSlider = number;
				sliderLeft = panel.x + PADDING + INDENT;
				sliderWidth = PANEL_WIDTH - 2 * PADDING - INDENT;
				updateSlider(mouseX);
			}
		} else if (setting instanceof ModeSetting<?> mode) {
			mode.cycle(back ? -1 : 1);
		} else if (setting instanceof KeybindSetting keybind) {
			if (back) {
				keybind.set(KeybindSetting.NONE);
			} else {
				listeningKeybind = keybind;
			}
		} else if (setting instanceof ColorSetting color) {
			if (back) {
				color.resetAll();
			} else {
				int right = panel.x + PANEL_WIDTH - PADDING;
				picker = new ColorPicker(color, right, row.y() + row.height(), uiWidth(), uiHeight());
			}
		} else if (setting instanceof ActionSetting action && !back) {
			action.run();
		}
	}

	private void updateSlider(double mouseX) {
		draggedSlider.setFraction((mouseX - sliderLeft) / sliderWidth);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		event = scaled(event);
		if (picker != null && picker.isDragging()) {
			picker.mouseDragged(event.x(), event.y());
			return true;
		}
		if (draggedPanel != null) {
			draggedPanel.x = (int) (event.x() - dragOffsetX);
			draggedPanel.y = (int) (event.y() - dragOffsetY);
			clamp(draggedPanel);
			return true;
		}
		if (draggedSlider != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (minecraft.hasControlDown() && scrollY != 0) {
			stepScale(scrollY > 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (picker != null && picker.isDragging()) {
			picker.mouseReleased();
			return true;
		}
		if (draggedPanel != null) {
			draggedPanel = null;
			savePanels();
			return true;
		}
		if (draggedSlider != null) {
			draggedSlider = null;
			rescale(); // z. B. nach dem Ziehen des ClickGUI-Größe-Reglers
			return true;
		}
		return super.mouseReleased(event);
	}

	/** {@code true}, wenn der letzte Tastendruck für eine Tastenbelegung verbraucht wurde. */
	public boolean wasCapturingKey() {
		return capturedLastKey;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker != null && picker.charTyped(event.codepointAsString())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (picker != null) {
			capturedLastKey = picker.isEditingText();
			if (picker.keyPressed(event.key())) {
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE) {
				picker = null;
				return true;
			}
		}
		capturedLastKey = listeningKeybind != null;
		if (listeningKeybind != null) {
			int key = event.key();
			if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
				listeningKeybind.set(KeybindSetting.NONE);
			} else if (key != InputConstants.KEY_ESCAPE) {
				listeningKeybind.set(key);
			}
			listeningKeybind = null;
			return true;
		}
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			onClose();
			return true;
		}
		if (event.hasControlDown() || minecraft.hasControlDown()) {
			int steps = switch (event.key()) {
				// „+“/„-“ liegen auf deutschen Tastaturen auf ] und /
				case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD, InputConstants.KEY_RBRACKET -> 1;
				case InputConstants.KEY_MINUS, Keys.KEYPAD_SUBTRACT, InputConstants.KEY_SLASH -> -1;
				case InputConstants.KEY_0, InputConstants.KEY_NUMPAD0 -> 0;
				default -> Integer.MIN_VALUE;
			};
			if (steps != Integer.MIN_VALUE) {
				stepScale(steps);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		savePanels();
		super.removed();
	}

	// ------------------------------------------------------------------ Skalierung

	/** ClickGUI-Größe aus dem Modul „Oberfläche“; Layout und Maus rechnen in skalierten Koordinaten. */
	private float uiScale = 1f;
	/** Offener Farbwähler oder {@code null}. */
	private ColorPicker picker;

	private int uiWidth() {
		return Math.round(width / uiScale);
	}

	private int uiHeight() {
		return Math.round(height / uiScale);
	}

	private MouseButtonEvent scaled(MouseButtonEvent event) {
		return uiScale == 1f ? event : new MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, event.buttonInfo());
	}

	private void rescale() {
		float next = GuiScaleModule.clickGuiScale();
		if (next != uiScale) {
			uiScale = next;
			panels.forEach(this::clamp);
		}
	}

	// ------------------------------------------------------------------ Panel-Zustand

	private void bringToFront(Panel panel) {
		panels.remove(panel);
		panels.add(panel);
	}

	private void clamp(Panel panel) {
		panel.x = Math.max(0, Math.min(panel.x, uiWidth() - PANEL_WIDTH));
		panel.y = Math.max(0, Math.min(panel.y, uiHeight() - HEADER_HEIGHT));
	}

	private void loadPanels() {
		JsonElement saved = config.gui().get("panels");
		JsonObject state = saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : new JsonObject();
		// Standard-Layout: nebeneinander, bei schmalem Fenster in die nächste Reihe umbrechen
		int gap = 8;
		int x = 10;
		int y = 26;
		int rowHeight = 0;
		for (Category category : Category.values()) {
			if (modules.byCategory(category).isEmpty()) {
				continue;
			}
			if (x > 10 && x + PANEL_WIDTH > uiWidth() - 10) {
				x = 10;
				y += rowHeight + gap;
				rowHeight = 0;
			}
			Panel panel = new Panel(category, x, y);
			rowHeight = Math.max(rowHeight, bottom(panel, rows(panel)) - panel.y);
			x += PANEL_WIDTH + gap;

			JsonElement entry = state.get(category.id());
			if (entry != null && entry.isJsonObject()) {
				JsonObject obj = entry.getAsJsonObject();
				// Falsche Typen (z. B. "x": "abc") überspringen; die Position klemmt init() ein
				if (isNumber(obj.get("x"))) panel.x = (int) obj.get("x").getAsDouble();
				if (isNumber(obj.get("y"))) panel.y = (int) obj.get("y").getAsDouble();
				if (isBoolean(obj.get("open"))) panel.open = obj.get("open").getAsBoolean();
			}
			panels.add(panel);
		}
	}

	private static boolean isNumber(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
	}

	private static boolean isBoolean(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
	}

	private void savePanels() {
		// Ausgeblendete Panels (Kampf-Module aus) behalten ihre gespeicherte Position
		JsonElement saved = config.gui().get("panels");
		JsonObject state = saved != null && saved.isJsonObject() ? saved.getAsJsonObject().deepCopy() : new JsonObject();
		for (Panel panel : panels) {
			JsonObject obj = new JsonObject();
			obj.addProperty("x", panel.x);
			obj.addProperty("y", panel.y);
			obj.addProperty("open", panel.open);
			state.add(panel.category.id(), obj);
		}
		config.gui().add("panels", state);
		config.markDirty();
	}
}
