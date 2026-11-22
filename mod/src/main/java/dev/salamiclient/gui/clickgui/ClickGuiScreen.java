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
import dev.salamiclient.gui.ModSettingsScreen;
import dev.salamiclient.gui.ModsScreen;
import dev.salamiclient.gui.SalamiMenuScreen;
import dev.salamiclient.gui.SalamiWindow;
import dev.salamiclient.gui.SettingText;
import dev.salamiclient.gui.TextInput;
import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.Skins;
import dev.salamiclient.gui.skin.TextFit;
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
import dev.salamiclient.setting.TextSetting;
import dev.salamiclient.util.Keys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
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
 *   <li>„Öffnen“ in der Modulzeile: eigenes Menü des Moduls (Freunde, Cosmetics)</li>
 *   <li>Kopfzeile ziehen: Panel verschieben · Rechtsklick: Panel einklappen</li>
 *   <li>Einstellungen: Linksklick ändert, Rechtsklick ändert rückwärts bzw. setzt zurück</li>
 * </ul>
 * Layout wird bei jedem Frame aus dem Modulzustand berechnet ({@link #rows});
 * Zeichnen und Klicken nutzen dieselbe Berechnung, damit beides nie auseinanderläuft.
 */
public final class ClickGuiScreen extends Screen {
	/** Breite eines Panels – genug für die längsten Modulnamen samt Schalter. */
	private static final int PANEL_WIDTH = 150;
	public static final int HEADER_HEIGHT = 18;
	public static final int MODULE_HEIGHT = 15;
	private static final int SETTING_HEIGHT = 13;
	private static final int PADDING = 6;
	private static final int INDENT = 4;
	/** Höhe der Knöpfe in der Leiste oben ({@link #barTop}). */
	public static final int BAR_H = 14;
	/** Abstand zwischen Panels (auch zum Panel darunter, an dem ein Panel endet). */
	private static final int GAP = 8;
	/** Gespeicherte Panel-Positionen aus älteren Versionen (ohne Scrollen, schmalere Panels) werden einmal verworfen. */
	private static final int LAYOUT_VERSION = 3;

	/** Aufgeklappte Module bleiben während der Sitzung aufgeklappt. */
	private static final Set<String> EXPANDED = new HashSet<>();

	private final ModuleManager modules;
	private final ConfigManager config;
	/** Woher die Panel-Ansicht geöffnet wurde ({@code null} = direkt, z. B. in Tests). */
	private final Screen parent;
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
		/** Wie weit der Inhalt nach oben geschoben ist (Mausrad). */
		double scroll;

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
		this(modules, config, null);
	}

	/** Panel-Ansicht als Unteransicht von {@code parent} (Salami-Menü, Mods-Menü): Esc führt dorthin zurück. */
	public ClickGuiScreen(ModuleManager modules, ConfigManager config, Screen parent) {
		super(Component.translatable("salami.clickgui.title"));
		this.modules = modules;
		this.config = config;
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		if (panels.isEmpty()) {
			loadPanels();
		}
		panels.forEach(this::clamp);
		if (headersOverlap()) {
			// gespeicherte Positionen passen nicht in dieses Fenster bzw. diese Menügröße: Standard-Layout
			defaultLayout();
			panels.forEach(this::clamp);
		}
	}

	/** Überdecken sich Kopfzeilen von Panels (dann ist eins davon nicht mehr zu greifen)? */
	private boolean headersOverlap() {
		for (int i = 0; i < panels.size(); i++) {
			Panel a = panels.get(i);
			for (int j = i + 1; j < panels.size(); j++) {
				Panel b = panels.get(j);
				if (a.x < b.x + PANEL_WIDTH && b.x < a.x + PANEL_WIDTH && a.y < b.y + HEADER_HEIGHT && b.y < a.y + HEADER_HEIGHT) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return SalamiMenuScreen.pausesGame();
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	//?}
		SalamiWindow.backdrop(graphics, width, height, uiScale);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	//?}
		TextInput.sync(this, picker != null && picker.isEditingText());
		Skin skin = Skins.current();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		lastMouseX = mx;
		lastMouseY = my;
		Hit hover = (draggedPanel == null && draggedSlider == null) ? hitTest(mx, my) : null;

		graphics.pose().pushMatrix();
		graphics.pose().scale(uiScale, uiScale);
		for (Panel panel : panels) {
			renderPanel(graphics, skin, panel, hover);
		}
		SalamiWindow.hint(graphics, font, I18n.get("salami.clickgui.hint"), uiWidth(), uiHeight());
		boolean overPicker = picker != null && picker.contains(mx, my);
		int bx = overPicker ? -1 : mx;
		topButton(graphics, skin, modsButton(), I18n.get("salami.clickgui.mods"), bx, my, Skin.ButtonKind.PRIMARY);
		topButton(graphics, skin, configsButton(), I18n.get("salami.clickgui.configs"), bx, my, Skin.ButtonKind.SECONDARY);
		topButton(graphics, skin, cosmeticsButton(), I18n.get("salami.clickgui.cosmetics"), bx, my, Skin.ButtonKind.SECONDARY);
		topButton(graphics, skin, minigamesButton(), I18n.get("salami.clickgui.minigames"), bx, my, Skin.ButtonKind.SECONDARY);
		topButton(graphics, skin, hudButton(), I18n.get("salami.clickgui.hud_editor"), bx, my, Skin.ButtonKind.SECONDARY);
		renderScaleControls(graphics, skin, bx, my);
		if (picker != null) {
			picker.render(graphics, font, mx, my);
		}
		graphics.pose().popMatrix();

		Component tooltip = overPicker ? null : tooltipFor(hover);
		int tooltipY = mouseY;
		if (scaleControlAt(mx, my) >= 0) {
			// unter die Knöpfe, damit die Prozentzahl sichtbar bleibt
			tooltip = Component.translatable("salami.clickgui.scale.tooltip");
			tooltipY = Math.round(controlBottom() * uiScale) + 16;
		}
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, tooltipY);
		}
	}

	private void renderPanel(GuiGraphics g, Skin skin, Panel panel, Hit hover) {
		clampScroll(panel);
		List<Row> rows = rows(panel);
		int right = panel.x + PANEL_WIDTH;
		int bottom = bottom(panel, rows);
		Skin.Ink side = skin.ink(Skin.Surface.SIDE);
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);

		// Inhalt auf einer Fensterfläche, darüber die Kopfzeile als eigene Fläche (Theke: Kreideschild)
		if (panel.open) {
			skin.surface(g, Skin.Surface.WINDOW, panel.x + 2, panel.y + HEADER_HEIGHT - 3, PANEL_WIDTH - 4, bottom - panel.y - HEADER_HEIGHT + 3);
		}
		skin.surface(g, Skin.Surface.SIDE, panel.x, panel.y, PANEL_WIDTH, HEADER_HEIGHT);

		int textY = panel.y + 5;
		int markerX = right - PADDING - 5;
		Icons.chevron(g, markerX, panel.y + (panel.open ? 8 : 7), panel.open, side.text());
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
		GuiDraw.text(g, font, count, countX, textY, side.dim(), false);
		TextFit.draw(g, font, I18n.get(panel.category.translationKey()), panel.x + PADDING, textY, countX - 4 - panel.x - PADDING, side.text());

		if (panel.open && rows.isEmpty()) {
			GuiDraw.text(g, font, I18n.get("salami.clickgui.empty"), panel.x + PADDING, panel.y + HEADER_HEIGHT + 4, ink.dim(), false);
		}

		// Zeilen nur im sichtbaren Teil; was darüber hinausgeht, erreicht das Mausrad
		int[] body = body(panel, bottom);
		g.enableScissor(panel.x + 2, body[0], right - 2, body[1]);
		for (Row row : rows) {
			if (row.y() + row.height() <= body[0] || row.y() >= body[1]) {
				continue;
			}
			boolean hovered = hover != null && row.equals(hover.row());
			if (row.isModule()) {
				renderModuleRow(g, skin, panel, row, hovered);
			} else {
				renderSettingRow(g, skin, panel, row, hovered);
			}
		}
		g.disableScissor();

		int max = maxScroll(panel, rows);
		if (max > 0) {
			// Bildlaufleiste am rechten Rand
			int track = body[1] - body[0];
			int content = track + max;
			int thumb = Math.max(8, track * track / content);
			int thumbY = (int) Math.round((track - thumb) * (panel.scroll / max));
			skin.scrollbar(g, right - 6, body[0], 3, track, thumbY, thumb, Skin.UiState.NORMAL);
		}
	}

	private void topButton(GuiGraphics g, Skin skin, int[] b, String label, int mx, int my, Skin.ButtonKind kind) {
		boolean hovered = mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
		skin.button(g, font, b[0], b[1], b[2] - b[0], b[3] - b[1], label, kind, Skin.UiState.of(hovered));
	}

	// ------------------------------------------------------------------ Knopfleiste oben

	/** Oberkante der Knopfleiste (unter der Deko des Hintergrunds); auch für Tests. */
	public static int barTop() {
		return Skins.current().backdropTop() + 3;
	}

	/** Oberkante des Standard-Layouts der Panels; auch für Tests. */
	public static int panelTop() {
		return top() + 2;
	}

	/** Oberkante unter der Knopfleiste. */
	private static int top() {
		return barTop() + BAR_H + 6;
	}

	/**
	 * Passen beide Gruppen der Leiste (links Mods … Minispiele, rechts Größe und HUD-Editor) nicht
	 * nebeneinander (große Oberfläche, schmales Fenster), rutscht die rechte Gruppe eine Reihe tiefer.
	 */
	private boolean twoRows() {
		return minigamesButton()[2] + 8 > scaleControls()[0][0];
	}

	/** Oberkante der rechten Gruppe (Größe, HUD-Editor). */
	private int rightTop() {
		return barTop() + (twoRows() ? BAR_H + 3 : 0);
	}

	/** Platz unten für den Hinweis (ein oder mehr Schilder): Panels enden darüber und scrollen. */
	private int bottomMargin() {
		return Math.max(24, SalamiWindow.hintHeight(font, I18n.get("salami.clickgui.hint"), uiWidth()));
	}

	/** Oberkante der Panels im Standard-Layout (unter einer oder zwei Reihen der Leiste). */
	private int layoutTop() {
		return top() + (twoRows() ? BAR_H + 3 : 0);
	}

	/** Linke Kante des HUD-Editor-Knopfs (ganz rechts in der Leiste). */
	private int hudLeft() {
		return uiWidth() - (font.width(I18n.get("salami.clickgui.hud_editor")) + 12) - 6;
	}

	private int[] hudButton() {
		return new int[] {hudLeft(), rightTop(), uiWidth() - 6, rightTop() + BAR_H};
	}

	private int[] modsButton() {
		int w = font.width(I18n.get("salami.clickgui.mods")) + 16;
		return new int[] {6, barTop(), 6 + w, barTop() + BAR_H};
	}

	private int[] configsButton() {
		int left = modsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.configs")) + 12;
		return new int[] {left, barTop(), left + w, barTop() + BAR_H};
	}

	private int[] cosmeticsButton() {
		int left = configsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.cosmetics")) + 12;
		return new int[] {left, barTop(), left + w, barTop() + BAR_H};
	}

	private int[] minigamesButton() {
		int left = cosmeticsButton()[2] + 4;
		int w = font.width(I18n.get("salami.clickgui.minigames")) + 12;
		return new int[] {left, barTop(), left + w, barTop() + BAR_H};
	}

	// ------------------------------------------------------------------ Größe (− 100 % +)

	private static final int CONTROL_BUTTON = 14;

	private int controlTop() {
		return rightTop();
	}

	private int controlBottom() {
		return rightTop() + BAR_H;
	}

	/** Felder links neben dem HUD-Knopf: [−], [Prozent], [+] jeweils als {x0, x1}. */
	private int[][] scaleControls() {
		int right = hudLeft() - 4;
		int plus = right - CONTROL_BUTTON;
		int label = plus - (font.width("200%") + 8);
		int minus = label - CONTROL_BUTTON;
		return new int[][] {{minus, label}, {label, plus}, {plus, right}};
	}

	/** 0 = kleiner, 1 = Prozentanzeige (zurücksetzen), 2 = größer, -1 = nichts. */
	private int scaleControlAt(double x, double y) {
		if (y < controlTop() || y >= controlBottom()) {
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

	private void renderScaleControls(GuiGraphics g, Skin skin, int mouseX, int mouseY) {
		int[][] controls = scaleControls();
		int hovered = scaleControlAt(mouseX, mouseY);
		for (int i = 0; i < controls.length; i++) {
			int x0 = controls[i][0];
			int x1 = controls[i][1];
			Skin.UiState state = Skin.UiState.of(hovered == i);
			if (i == 1) {
				String percent = Math.round(uiScale * 100) + "%";
				skin.button(g, font, x0 + 1, controlTop(), x1 - x0 - 2, BAR_H, percent, Skin.ButtonKind.SECONDARY, state);
			} else {
				skin.iconButton(g, x0, controlTop(), CONTROL_BUTTON, Skin.ButtonKind.SECONDARY, state);
				Icons.magnifier(g, (x0 + x1) / 2 - 5, controlTop() + 1, i == 2, skin.buttonInk(Skin.ButtonKind.SECONDARY, state));
			}
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

	private void renderModuleRow(GuiGraphics g, Skin skin, Panel panel, Row row, boolean hovered) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		Module module = row.module();
		int left = panel.x + 2;
		int right = panel.x + PANEL_WIDTH - 2;
		boolean on = module.isEnabled();
		boolean overButton = false;
		int nameRight;
		boolean hasSettings = false;
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				hasSettings = true;
				break;
			}
		}
		boolean expanded = EXPANDED.contains(module.id());
		int expanderX = right - PADDING - 3;
		nameRight = expanderX - 4;
		if (module.hasScreen()) {
			int[] b = openButton(panel, row);
			nameRight = b[0] - 3;
			overButton = hovered && lastMouseX >= b[0] && lastMouseX < b[2] && lastMouseY >= b[1] && lastMouseY < b[3];
		}
		skin.row(g, left + 1, row.y(), right - left - 2, row.height(), expanded, Skin.UiState.of(hovered && !overButton));
		int[] size = skin.toggleSize();
		skin.toggle(g, left + 4, row.y() + (row.height() - size[1]) / 2, on, Skin.UiState.of(hovered && !overButton));
		int nameX = left + 4 + size[0] + 4;
		TextFit.draw(g, font, I18n.get(module.translationKey()), nameX, row.y() + 4, nameRight - nameX, ink.text());
		if (module.hasScreen()) {
			int[] b = openButton(panel, row);
			skin.button(g, font, b[0], b[1], b[2] - b[0], b[3] - b[1], I18n.get("salami.clickgui.open"), Skin.ButtonKind.SECONDARY,
				Skin.UiState.of(overButton));
		}
		if (hasSettings) {
			Icons.chevron(g, expanderX, row.y() + (expanded ? 6 : 5), expanded, expanded ? ink.accent() : ink.dim());
		}
	}

	/** „Öffnen“-Knopf links vom Pfeil, nur bei Modulen mit eigenem Menü: {x0, y0, x1, y1}. */
	private int[] openButton(Panel panel, Row row) {
		int right = panel.x + PANEL_WIDTH - 2 - PADDING - 3 - 5;
		int w = font.width(I18n.get("salami.clickgui.open")) + 8;
		return new int[] {right - w, row.y() + 1, right, row.y() + row.height() - 1};
	}

	private void renderSettingRow(GuiGraphics g, Skin skin, Panel panel, Row row, boolean hovered) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		Skin.Ink inputInk = skin.ink(Skin.Surface.INPUT);
		Setting<?> setting = row.setting();
		int left = panel.x + PADDING + INDENT;
		int right = panel.x + PANEL_WIDTH - PADDING;
		int textY = row.y() + 3;
		// Einstellungen eingerückt unter ihrem Modul, mit Führungslinie
		skin.row(g, panel.x + PADDING, row.y(), PANEL_WIDTH - 2 * PADDING + 2, row.height(), false, Skin.UiState.of(hovered));
		g.fill(panel.x + PADDING + 1, row.y(), panel.x + PADDING + 2, row.y() + row.height(), ink.line());
		Skin.UiState state = Skin.UiState.of(hovered);
		boolean chip = false;

		String value;
		int valueColor = ink.text();
		int valueRight = right;

		if (setting instanceof BoolSetting bool) {
			value = "";
			skin.checkbox(g, right - 9, row.y() + 2, 9, bool.isOn(), state);
			valueRight = right - 12;
		} else if (setting instanceof NumberSetting number) {
			value = number.display();
			textY = row.y() + 2;
			skin.slider(g, left, row.y() + row.height() - 4, right - left, 4, (float) number.fraction(),
				draggedSlider == number ? Skin.UiState.PRESSED : state);
		} else if (setting instanceof ModeSetting<?> mode) {
			value = modeLabel(mode);
			valueColor = ink.accent();
		} else if (setting instanceof KeybindSetting keybind) {
			value = listeningKeybind == keybind ? "..." : keybind.keyName();
			chip = true;
			valueColor = listeningKeybind == keybind ? inputInk.accent() : inputInk.text();
		} else if (setting instanceof ColorSetting color) {
			value = "";
			g.fill(right - 10, row.y() + 2, right, row.y() + 11, color.argb());
			GuiDraw.outline(g, right - 10, row.y() + 2, 10, 9, picker != null && picker.setting() == color ? ink.accent() : ink.text());
			valueRight = right - 12;
		} else if (setting instanceof StringListSetting list) {
			value = I18n.get("salami.clickgui.entries", list.get().size());
			valueColor = ink.dim();
		} else if (setting instanceof ActionSetting action) {
			value = I18n.get(action.buttonKey());
			valueColor = ink.accent();
		} else if (setting instanceof TextSetting text) {
			// Text wird im Einstellungsfenster des Moduls bearbeitet (Klick)
			value = text.get().isEmpty() ? I18n.get("salami.clickgui.edit_text") : GuiDraw.ellipsize(font, text.get(), (right - left) / 2);
			valueColor = text.get().isEmpty() ? ink.accent() : ink.text();
		} else {
			value = String.valueOf(setting.get());
		}

		int valueWidth = font.width(value);
		if (chip) {
			// Taste als kleines Feld
			skin.input(g, right - valueWidth - 6, row.y() + 1, valueWidth + 6, row.height() - 2, listeningKeybind == setting);
			valueRight = right - 3;
		}
		TextFit.draw(g, font, label(setting), left, textY, valueRight - valueWidth - left - 4, ink.dim());
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
			return row.module().description();
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

	/** Alle Zeilen eines aufgeklappten Panels, um {@link Panel#scroll} nach oben verschoben (auch unsichtbare). */
	private List<Row> rows(Panel panel) {
		List<Row> rows = new ArrayList<>();
		if (!panel.open) {
			return rows;
		}
		int y = panel.y + HEADER_HEIGHT - (int) Math.round(panel.scroll);
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

	/** Unterkante des ganzen Inhalts, als ob nichts begrenzt wäre (ohne Scrollen). */
	private int naturalBottom(Panel panel, List<Row> rows) {
		if (!panel.open) {
			return panel.y + HEADER_HEIGHT;
		}
		if (rows.isEmpty()) {
			return panel.y + HEADER_HEIGHT + MODULE_HEIGHT;
		}
		Row last = rows.get(rows.size() - 1);
		return last.y() + last.height() + 3 + (int) Math.round(panel.scroll);
	}

	/**
	 * Sichtbare Unterkante: der Inhalt, höchstens bis über die Hinweiszeile und bis kurz vor ein Panel,
	 * das darunter in derselben Spalte liegt – der Rest ist per Mausrad erreichbar.
	 */
	private int bottom(Panel panel, List<Row> rows) {
		int natural = naturalBottom(panel, rows);
		if (!panel.open) {
			return natural;
		}
		int limit = uiHeight() - bottomMargin();
		for (Panel other : panels) {
			boolean sameColumn = other.x < panel.x + PANEL_WIDTH && other.x + PANEL_WIDTH > panel.x;
			if (other != panel && other != draggedPanel && sameColumn && other.y > panel.y + HEADER_HEIGHT) {
				limit = Math.min(limit, other.y - 4);
			}
		}
		// Mindestens zwei Zeilen, auch wenn das Panel darunter sehr dicht folgt
		limit = Math.max(limit, panel.y + HEADER_HEIGHT + 2 * MODULE_HEIGHT + 2);
		if (natural <= limit) {
			return natural;
		}
		// gekürzt: nur ganze Modulzeilen zeigen, keine halb abgeschnittene am Rand
		int whole = Math.max(2, (limit - 2 - panel.y - HEADER_HEIGHT) / MODULE_HEIGHT);
		return panel.y + HEADER_HEIGHT + whole * MODULE_HEIGHT + 2;
	}

	/** Bereich der Zeilen {y0, y1} zwischen Kopfzeile und Rand. */
	private static int[] body(Panel panel, int bottom) {
		return new int[] {panel.y + HEADER_HEIGHT, Math.max(panel.y + HEADER_HEIGHT, bottom - 2)};
	}

	private int maxScroll(Panel panel, List<Row> rows) {
		return Math.max(0, naturalBottom(panel, rows) - bottom(panel, rows));
	}

	private void clampScroll(Panel panel) {
		if (panel.scroll == 0) {
			return;
		}
		int max = maxScroll(panel, rows(panel));
		panel.scroll = Math.max(0, Math.min(panel.scroll, max));
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
			int bottom = bottom(panel, rows);
			int[] body = body(panel, bottom);
			if (mouseY >= body[0] && mouseY < body[1]) {
				for (Row row : rows) {
					if (mouseY >= row.y() && mouseY < row.y() + row.height()) {
						return new Hit(panel, row, false);
					}
				}
			}
			if (mouseY >= panel.y && mouseY < bottom) {
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
		} else if (setting instanceof TextSetting text) {
			if (back) {
				text.reset();
			} else {
				// Texte (AutoText, Ersatzname …) im Einstellungsfenster des Moduls bearbeiten, gleich mit Cursor
				ModSettingsScreen settings = new ModSettingsScreen(modules, row.module(), this);
				settings.editText(text);
				minecraft.setScreen(settings);
			}
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
		// Mausrad über einem Panel: dessen Inhalt scrollen
		Hit hit = scrollY != 0 && draggedPanel == null ? hitTest(mouseX / uiScale, mouseY / uiScale) : null;
		if (hit != null) {
			Panel panel = hit.panel();
			int max = maxScroll(panel, rows(panel));
			if (max > 0) {
				picker = null; // Farbwähler hängt an seiner Zeile
				panel.scroll = Math.max(0, Math.min(max, panel.scroll - scrollY * MODULE_HEIGHT * 2));
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Scrollt ein Panel (auch für Tests); liefert die neue Position in Pixeln. */
	public int scrollPanel(Category category, double pixels) {
		for (Panel panel : panels) {
			if (panel.category == category) {
				int max = maxScroll(panel, rows(panel));
				panel.scroll = Math.max(0, Math.min(max, panel.scroll + pixels));
				return (int) Math.round(panel.scroll);
			}
		}
		return 0;
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
		// Menü-Taste: alles schließen (wie in allen Salami-Bildschirmen); Esc geht eine Ebene zurück
		if (SalamiMenuScreen.closeOnMenuKey(minecraft, event, false)) {
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

	/** Esc: zurück ins Salami- bzw. Mods-Menü; ohne solches dorthin, woher das Salami-Menü kam. */
	@Override
	public void onClose() {
		if (parent != null) {
			minecraft.setScreen(parent);
		} else {
			SalamiMenuScreen.closeAll(minecraft);
		}
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
			if (headersOverlap()) {
				defaultLayout();
				panels.forEach(this::clamp);
			}
		}
	}

	// ------------------------------------------------------------------ Panel-Zustand

	private void bringToFront(Panel panel) {
		panels.remove(panel);
		panels.add(panel);
	}

	/** Panel im Bild halten: Kopfzeile und mindestens eine Zeile über der Hinweiszeile. */
	private void clamp(Panel panel) {
		panel.x = Math.max(0, Math.min(panel.x, uiWidth() - PANEL_WIDTH));
		// nicht über die Knopfleiste und die Deko des Hintergrunds (Theke: Markise)
		int minY = layoutTop() + 2;
		panel.y = Math.max(minY, Math.min(panel.y, uiHeight() - bottomMargin() - HEADER_HEIGHT - MODULE_HEIGHT));
	}

	private void loadPanels() {
		JsonElement saved = config.gui().get("panels");
		JsonObject state = saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : new JsonObject();
		// Positionen aus der Zeit vor dem Scrollen lagen oft außerhalb des Bildes: dann das neue Standard-Layout
		boolean current = isNumber(state.get("layout")) && state.get("layout").getAsInt() >= LAYOUT_VERSION;
		for (Category category : Category.values()) {
			if (modules.byCategory(category).isEmpty()) {
				continue;
			}
			Panel panel = new Panel(category, 0, 0);
			JsonElement entry = state.get(category.id());
			if (entry != null && entry.isJsonObject()) {
				JsonObject obj = entry.getAsJsonObject();
				if (isBoolean(obj.get("open"))) panel.open = obj.get("open").getAsBoolean();
			}
			panels.add(panel);
		}
		defaultLayout();
		if (!current) {
			return;
		}
		for (Panel panel : panels) {
			JsonElement entry = state.get(panel.category.id());
			if (entry != null && entry.isJsonObject()) {
				JsonObject obj = entry.getAsJsonObject();
				// Falsche Typen (z. B. "x": "abc") überspringen; die Position klemmt init() ein
				if (isNumber(obj.get("x"))) panel.x = (int) obj.get("x").getAsDouble();
				if (isNumber(obj.get("y"))) panel.y = (int) obj.get("y").getAsDouble();
			}
		}
	}

	/**
	 * Standard-Layout, das ins Bild passt: alle Panels nebeneinander, wenn genug Platz ist. Sonst in
	 * Spalten – jedes Panel kommt in die bisher kürzeste Spalte, und hohe Panels werden so gekürzt,
	 * dass jede Spalte über der Hinweiszeile endet (der Rest scrollt).
	 */
	private void defaultLayout() {
		// links 10 Pixel Rand, rechts reichen 4
		int columns = Math.max(1, Math.min(panels.size(), (uiWidth() - 14 + GAP) / (PANEL_WIDTH + GAP)));
		List<List<Panel>> stacks = new ArrayList<>();
		int[] used = new int[columns];
		for (int i = 0; i < columns; i++) {
			stacks.add(new ArrayList<>());
		}
		for (Panel panel : panels) {
			int column = 0;
			for (int i = 1; i < columns; i++) {
				if (used[i] < used[column]) {
					column = i;
				}
			}
			stacks.get(column).add(panel);
			used[column] += naturalHeight(panel) + GAP;
		}
		int available = uiHeight() - bottomMargin() - layoutTop();
		for (int column = 0; column < columns; column++) {
			List<Panel> stack = stacks.get(column);
			int[] heights = fitHeights(stack, available - (stack.size() - 1) * GAP);
			int y = layoutTop() + 2;
			for (int i = 0; i < stack.size(); i++) {
				Panel panel = stack.get(i);
				panel.x = 10 + column * (PANEL_WIDTH + GAP);
				panel.y = y;
				panel.scroll = 0;
				y += heights[i] + GAP;
			}
		}
	}

	/** Höhe eines Panels mit allen Zeilen. */
	private int naturalHeight(Panel panel) {
		double scroll = panel.scroll;
		panel.scroll = 0;
		int height = naturalBottom(panel, rows(panel)) - panel.y;
		panel.scroll = scroll;
		return height;
	}

	/**
	 * Höhen für übereinanderliegende Panels, die zusammen in {@code total} passen: kleine Panels behalten
	 * ihre Höhe, die großen teilen sich den Rest (jedes mindestens Kopfzeile und zwei Zeilen).
	 */
	private int[] fitHeights(List<Panel> stack, int total) {
		int[] natural = new int[stack.size()];
		int sum = 0;
		for (int i = 0; i < natural.length; i++) {
			natural[i] = naturalHeight(stack.get(i));
			sum += natural[i];
		}
		if (sum <= total) {
			return natural;
		}
		int minimum = HEADER_HEIGHT + 2 * MODULE_HEIGHT;
		// Obergrenze suchen, bei der alle zusammen hineinpassen
		int cap = total;
		for (int step = 0; step < 64; step++) {
			int used = 0;
			for (int h : natural) {
				used += Math.max(Math.min(h, minimum), Math.min(h, cap));
			}
			if (used <= total || cap <= minimum) {
				break;
			}
			cap -= Math.max(1, (used - total + natural.length - 1) / natural.length);
		}
		int[] result = new int[natural.length];
		for (int i = 0; i < natural.length; i++) {
			result[i] = Math.max(Math.min(natural[i], minimum), Math.min(natural[i], cap));
		}
		return result;
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
		state.addProperty("layout", LAYOUT_VERSION);
		config.gui().add("panels", state);
		config.markDirty();
	}
}
