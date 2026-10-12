package dev.salamiclient.gui.clickgui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.gui.HudEditorScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.setting.Setting;
import dev.salamiclient.setting.StringListSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ClickGUI: ein verschiebbares Panel pro Kategorie.
 *
 * <ul>
 *   <li>Linksklick auf Modul: an/aus · Rechtsklick: Einstellungen auf-/zuklappen</li>
 *   <li>Kopfzeile ziehen: Panel verschieben · Rechtsklick: Panel einklappen</li>
 *   <li>Einstellungen: Linksklick ändert, Rechtsklick ändert rückwärts bzw. setzt zurück</li>
 * </ul>
 * Layout wird bei jedem Frame aus dem Modulzustand berechnet ({@link #rows});
 * Zeichnen und Klicken nutzen dieselbe Berechnung, damit beides nie auseinanderläuft.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_WIDTH = 124;
	private static final int HEADER_HEIGHT = 15;
	private static final int MODULE_HEIGHT = 14;
	private static final int SETTING_HEIGHT = 12;
	private static final int PADDING = 4;
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
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x88000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		Theme theme = SalamiClient.get().theme();
		Hit hover = (draggedPanel == null && draggedSlider == null) ? hitTest(mouseX, mouseY) : null;

		for (Panel panel : panels) {
			renderPanel(graphics, panel, theme, hover);
		}

		graphics.drawCenteredString(font, I18n.get("salami.clickgui.hint"), width / 2, height - 12, theme.textDim());
		renderHudButton(graphics, theme, mouseX, mouseY);

		Component tooltip = tooltipFor(hover);
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, mouseY);
		}
	}

	private void renderPanel(GuiGraphics g, Panel panel, Theme theme, Hit hover) {
		List<Row> rows = rows(panel);
		int right = panel.x + PANEL_WIDTH;
		int bottom = bottom(panel, rows);

		g.fill(panel.x, panel.y, right, bottom, theme.panel());
		g.fill(panel.x, panel.y, right, panel.y + HEADER_HEIGHT, theme.header());

		g.drawString(font, I18n.get(panel.category.translationKey()), panel.x + PADDING, panel.y + 4, theme.headerText(), false);
		String marker = panel.open ? "-" : "+";
		int markerX = right - PADDING - font.width(marker);
		g.drawString(font, marker, markerX, panel.y + 4, theme.headerText(), false);
		if (panel.category.isRestricted()) {
			g.drawString(font, "!", markerX - 8, panel.y + 4, theme.warning(), false);
		}

		if (panel.open && rows.isEmpty()) {
			g.drawString(font, I18n.get("salami.clickgui.empty"), panel.x + PADDING, panel.y + HEADER_HEIGHT + 3, theme.textDim(), false);
		}

		for (Row row : rows) {
			boolean hovered = hover != null && row.equals(hover.row());
			if (row.isModule()) {
				renderModuleRow(g, panel, row, theme, hovered);
			} else {
				renderSettingRow(g, panel, row, theme, hovered);
			}
		}

		g.renderOutline(panel.x, panel.y, PANEL_WIDTH, bottom - panel.y, theme.outline());
	}

	// ------------------------------------------------------------------ HUD-Editor-Knopf

	private int[] hudButton() {
		int w = font.width(I18n.get("salami.clickgui.hud_editor")) + 12;
		return new int[] {width - w - 6, 4, width - 6, 18};
	}

	private void renderHudButton(GuiGraphics g, Theme theme, int mouseX, int mouseY) {
		int[] b = hudButton();
		boolean hovered = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
		g.fill(b[0], b[1], b[2], b[3], hovered ? theme.header() : theme.panel());
		g.renderOutline(b[0], b[1], b[2] - b[0], b[3] - b[1], theme.outline());
		g.drawString(font, I18n.get("salami.clickgui.hud_editor"), b[0] + 6, b[1] + 3, theme.headerText(), false);
	}

	private void renderModuleRow(GuiGraphics g, Panel panel, Row row, Theme theme, boolean hovered) {
		Module module = row.module();
		int right = panel.x + PANEL_WIDTH;
		if (module.isEnabled()) {
			g.fill(panel.x, row.y(), right, row.y() + row.height(), theme.enabledRow());
		}
		if (hovered) {
			g.fill(panel.x, row.y(), right, row.y() + row.height(), theme.hover());
		}
		String expander = EXPANDED.contains(module.id()) ? "v" : ">";
		int expanderX = right - PADDING - font.width(expander);
		String name = font.plainSubstrByWidth(I18n.get(module.translationKey()), expanderX - panel.x - PADDING - 2);
		g.drawString(font, name, panel.x + PADDING, row.y() + 3, module.isEnabled() ? theme.accent() : theme.text(), false);
		g.drawString(font, expander, expanderX, row.y() + 3, theme.textDim(), false);
	}

	private void renderSettingRow(GuiGraphics g, Panel panel, Row row, Theme theme, boolean hovered) {
		Setting<?> setting = row.setting();
		int left = panel.x + PADDING + INDENT;
		int right = panel.x + PANEL_WIDTH - PADDING;
		int textY = row.y() + 2;
		if (hovered) {
			g.fill(panel.x, row.y(), panel.x + PANEL_WIDTH, row.y() + row.height(), theme.hover());
		}
		// dezente Einrückungslinie
		g.fill(panel.x + PADDING, row.y(), panel.x + PADDING + 1, row.y() + row.height(), theme.outline());

		String value;
		int valueColor = theme.text();
		int valueRight = right;

		if (setting instanceof BoolSetting bool) {
			value = I18n.get(bool.isOn() ? "salami.on" : "salami.off");
			valueColor = bool.isOn() ? theme.accent() : theme.textDim();
		} else if (setting instanceof NumberSetting number) {
			value = number.display();
			int trackY = row.y() + row.height() - 2;
			g.fill(left, trackY, right, trackY + 1, theme.outline());
			g.fill(left, trackY, left + (int) Math.round((right - left) * number.fraction()), trackY + 1, theme.accent());
		} else if (setting instanceof ModeSetting<?> mode) {
			value = modeLabel(mode);
		} else if (setting instanceof KeybindSetting keybind) {
			value = listeningKeybind == keybind ? "..." : keybind.keyName();
			if (listeningKeybind == keybind) {
				valueColor = theme.accent();
			}
		} else if (setting instanceof ColorSetting color) {
			value = "";
			g.fill(right - 9, row.y() + 2, right, row.y() + 10, color.argb());
			g.renderOutline(right - 9, row.y() + 2, 9, 8, theme.outline());
			valueRight = right - 12;
		} else if (setting instanceof StringListSetting list) {
			value = I18n.get("salami.clickgui.entries", list.get().size());
		} else {
			value = String.valueOf(setting.get());
		}

		int valueWidth = font.width(value);
		String label = font.plainSubstrByWidth(label(setting), valueRight - valueWidth - left - 4);
		g.drawString(font, label, left, textY, theme.textDim(), false);
		if (!value.isEmpty()) {
			g.drawString(font, value, valueRight - valueWidth, textY, valueColor, false);
		}
	}

	private Component tooltipFor(Hit hit) {
		if (hit == null) {
			return null;
		}
		if (hit.header() && hit.panel().category.isRestricted()) {
			return Component.translatable("salami.category.utility.warning");
		}
		if (hit.row() == null) {
			return null;
		}
		Row row = hit.row();
		if (row.isModule()) {
			String key = row.module().descriptionKey();
			return I18n.exists(key) ? Component.translatable(key) : null;
		}
		String key = row.setting().translationKey() + ".desc";
		if (I18n.exists(key)) {
			return Component.translatable(key);
		}
		return row.setting() instanceof StringListSetting ? Component.translatable("salami.clickgui.edit_in_config") : null;
	}

	/** Anzeigename des gewählten Modus: modulspezifisch, sonst generisch, sonst Enum-Name. */
	private static String modeLabel(ModeSetting<?> mode) {
		String specific = mode.valueTranslationKey();
		if (I18n.exists(specific)) {
			return I18n.get(specific);
		}
		String generic = "salami.setting." + mode.id() + "." + mode.get().name().toLowerCase(java.util.Locale.ROOT);
		return I18n.exists(generic) ? I18n.get(generic) : mode.get().name();
	}

	/** Modulspezifischer Name, sonst generischer ("salami.setting.keybind"). */
	private static String label(Setting<?> setting) {
		String specific = setting.translationKey();
		if (I18n.exists(specific)) {
			return I18n.get(specific);
		}
		String generic = "salami.setting." + setting.id();
		return I18n.exists(generic) ? I18n.get(generic) : setting.id();
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
		return last.y() + last.height() + 2;
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
		listeningKeybind = null;
		int[] b = hudButton();
		if (event.x() >= b[0] && event.x() < b[2] && event.y() >= b[1] && event.y() < b[3]) {
			minecraft.setScreen(new HudEditorScreen(this));
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
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				draggedPanel = panel;
				dragOffsetX = event.x() - panel.x;
				dragOffsetY = event.y() - panel.y;
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				modules.toggle(row.module());
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				String id = row.module().id();
				if (!EXPANDED.remove(id)) {
					EXPANDED.add(id);
				}
			}
			return true;
		}
		clickSetting(row.setting(), panel, event.x(), button);
		return true;
	}

	private void clickSetting(Setting<?> setting, Panel panel, double mouseX, int button) {
		boolean back = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
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
			color.cyclePalette(back ? -1 : 1);
		}
	}

	private void updateSlider(double mouseX) {
		draggedSlider.setFraction((mouseX - sliderLeft) / sliderWidth);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
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
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggedPanel != null) {
			draggedPanel = null;
			savePanels();
			return true;
		}
		if (draggedSlider != null) {
			draggedSlider = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	/** {@code true}, wenn der letzte Tastendruck für eine Tastenbelegung verbraucht wurde. */
	public boolean wasCapturingKey() {
		return capturedLastKey;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		capturedLastKey = listeningKeybind != null;
		if (listeningKeybind != null) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
				listeningKeybind.set(KeybindSetting.NONE);
			} else if (key != GLFW.GLFW_KEY_ESCAPE) {
				listeningKeybind.set(key);
			}
			listeningKeybind = null;
			return true;
		}
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		savePanels();
		super.removed();
	}

	// ------------------------------------------------------------------ Panel-Zustand

	private void bringToFront(Panel panel) {
		panels.remove(panel);
		panels.add(panel);
	}

	private void clamp(Panel panel) {
		panel.x = Math.max(0, Math.min(panel.x, width - PANEL_WIDTH));
		panel.y = Math.max(0, Math.min(panel.y, height - HEADER_HEIGHT));
	}

	private void loadPanels() {
		JsonElement saved = config.gui().get("panels");
		JsonObject state = saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : new JsonObject();
		// Standard-Layout: nebeneinander, bei schmalem Fenster in die nächste Reihe umbrechen
		int gap = 8;
		int x = 10;
		int y = 24;
		int rowHeight = 0;
		for (Category category : Category.values()) {
			if (x > 10 && x + PANEL_WIDTH > width - 10) {
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
				if (obj.has("x")) panel.x = obj.get("x").getAsInt();
				if (obj.has("y")) panel.y = obj.get("y").getAsInt();
				if (obj.has("open")) panel.open = obj.get("open").getAsBoolean();
			}
			panels.add(panel);
		}
	}

	private void savePanels() {
		JsonObject state = new JsonObject();
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
