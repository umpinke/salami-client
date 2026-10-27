package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
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
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen eines Moduls aus dem Mods-Menü. Linksklick ändert, Rechtsklick ändert
 * rückwärts bzw. setzt zurück – wie in der ClickGUI. Farben öffnen den {@link ColorPicker}.
 */
public final class ModSettingsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int ROW = 18;
	private static final int PAD = 10;
	private static final int CONTROL_W = 120;

	private final ModuleManager modules;
	private final Module module;
	private final Screen parent;
	private float uiScale = 1f;
	private double scroll;
	private NumberSetting draggedSlider;
	private KeybindSetting listeningKeybind;
	private boolean capturedLastKey;
	/** Gerade bearbeitete Text-Einstellung und der noch nicht übernommene Text. */
	private TextSetting editingText;
	private String editBuffer = "";
	/** Offener Farbwähler oder {@code null}. */
	private ColorPicker picker;

	private record Row(Setting<?> setting, int y) {}

	public ModSettingsScreen(ModuleManager modules, Module module, Screen parent) {
		super(Component.translatable(module.translationKey()));
		this.modules = modules;
		this.module = module;
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** {@code true}, wenn der letzte Tastendruck für eine Tastenbelegung verbraucht wurde. */
	public boolean wasCapturingKey() {
		return capturedLastKey || listeningKeybind != null || editingText != null
			|| (picker != null && picker.isEditingText());
	}

	/** Öffnet den Farbwähler unter der Zeile dieser Farbe (auch für Tests); {@code null}, wenn sie nicht sichtbar ist. */
	public ColorPicker openColorPicker(ColorSetting color) {
		for (Row row : rows()) {
			if (row.setting() == color) {
				int[] r = control(row);
				picker = new ColorPicker(color, r[2], r[3], Math.round(width / uiScale), Math.round(height / uiScale));
				return picker;
			}
		}
		return null;
	}

	/** Offener Farbwähler oder {@code null}. */
	public ColorPicker colorPicker() {
		return picker;
	}

	private void commitText() {
		if (editingText != null) {
			editingText.set(editBuffer);
			editingText = null;
		}
	}

	// ------------------------------------------------------------------ Layout

	private int[] frame() {
		int uiW = Math.round(width / uiScale);
		int uiH = Math.round(height / uiScale);
		int w = Math.min(uiW - 16, 340);
		int h = Math.min(uiH - 16, 300);
		int x0 = (uiW - w) / 2;
		int y0 = (uiH - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] backButton() {
		int[] f = frame();
		int w = font.width(I18n.get("salami.mods.back")) + 12;
		return new int[] {f[0] + 4, f[1] + 5, f[0] + 4 + w, f[1] + 19};
	}

	private int[] toggle() {
		int[] f = frame();
		return new int[] {f[2] - 30, f[1] + 7, f[2] - 8, f[1] + 17};
	}

	private List<FormattedCharSequence> description() {
		if (!Language.getInstance().has(module.descriptionKey())) {
			return List.of();
		}
		int[] f = frame();
		return font.split(Component.translatable(module.descriptionKey()), f[2] - f[0] - 2 * PAD);
	}

	private int[] content() {
		int[] f = frame();
		int top = f[1] + HEADER + 6 + description().size() * 10 + (description().isEmpty() ? 0 : 6);
		return new int[] {f[0] + PAD, top, f[2] - PAD, f[3] - PAD};
	}

	private List<Row> rows() {
		int[] c = content();
		List<Row> rows = new ArrayList<>();
		int y = c[1] - (int) Math.round(scroll);
		for (Setting<?> setting : module.settings()) {
			if (setting.isHidden()) {
				continue;
			}
			rows.add(new Row(setting, y));
			y += ROW;
		}
		return rows;
	}

	private int contentHeight() {
		int count = 0;
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				count++;
			}
		}
		return count * ROW;
	}

	private void clampScroll() {
		int[] c = content();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - (c[3] - c[1]))));
	}

	/** Bedienfeld rechts in einer Zeile {x0, y0, x1, y1}. */
	private int[] control(Row row) {
		int[] c = content();
		return new int[] {c[2] - CONTROL_W, row.y() + 3, c[2], row.y() + ROW - 3};
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xAA000000);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		Component tooltip = null;

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		int[] f = frame();
		g.fill(f[0], f[1], f[2], f[3], theme.panel());
		g.fill(f[0], f[1], f[2], f[1] + HEADER - 2, theme.header());
		GuiDraw.outline(g, f[0], f[1], f[2] - f[0], f[3] - f[1], theme.outline());

		int[] back = backButton();
		boolean backHover = inside(back, mx, my);
		g.fill(back[0], back[1], back[2], back[3], backHover ? theme.accent() : theme.panel());
		GuiDraw.text(g, font, I18n.get("salami.mods.back"), back[0] + 6, back[1] + 3, theme.headerText(), false);

		String name = I18n.get(module.translationKey());
		GuiDraw.centeredText(g, font, name, (f[0] + f[2]) / 2, f[1] + 8, module.isEnabled() ? theme.accent() : theme.headerText());
		drawSwitch(g, theme, toggle(), module.isEnabled());

		int dy = f[1] + HEADER + 6;
		for (FormattedCharSequence line : description()) {
			GuiDraw.text(g, font, line, f[0] + PAD, dy, theme.textDim(), false);
			dy += 10;
		}

		int[] c = content();
		g.enableScissor(c[0], c[1], c[2], c[3]);
		boolean overPicker = picker != null && picker.contains(mx, my);
		for (Row row : rows()) {
			if (row.y() + ROW < c[1] || row.y() > c[3]) {
				continue;
			}
			boolean hovered = !overPicker && mx >= c[0] && mx < c[2] && my >= row.y() && my < row.y() + ROW && my >= c[1] && my < c[3];
			if (hovered) {
				g.fill(c[0] - 4, row.y(), c[2] + 4, row.y() + ROW, theme.hover());
				String desc = SettingText.description(row.setting());
				if (desc != null) {
					tooltip = Component.literal(desc);
				} else if (row.setting() instanceof StringListSetting) {
					tooltip = Component.translatable("salami.clickgui.edit_in_config");
				}
			}
			GuiDraw.text(g, font, font.plainSubstrByWidth(SettingText.label(row.setting()), c[2] - c[0] - CONTROL_W - 8), c[0], row.y() + 5, theme.text(), false);
			renderControl(g, theme, row, mx, my);
		}
		g.disableScissor();
		if (picker != null) {
			picker.render(g, font, theme, mx, my);
		}

		g.pose().popMatrix();
		if (tooltip != null) {
			g.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, mouseY);
		}
	}

	private void renderControl(GuiGraphics g, Theme theme, Row row, int mx, int my) {
		Setting<?> setting = row.setting();
		int[] r = control(row);
		int textY = r[1] + 2;
		if (setting instanceof BoolSetting bool) {
			drawSwitch(g, theme, new int[] {r[2] - 22, r[1] + 1, r[2], r[3] - 1}, bool.isOn());
		} else if (setting instanceof NumberSetting number) {
			String value = number.display();
			int trackRight = r[2] - font.width(value) - 6;
			int trackY = (r[1] + r[3]) / 2;
			g.fill(r[0], trackY, trackRight, trackY + 1, theme.outline());
			int knobX = r[0] + (int) Math.round((trackRight - r[0]) * number.fraction());
			g.fill(r[0], trackY, knobX, trackY + 1, theme.accent());
			g.fill(knobX - 2, trackY - 3, knobX + 2, trackY + 4, theme.accent());
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, theme.text(), false);
		} else if (setting instanceof ModeSetting<?> mode) {
			String value = "< " + SettingText.modeLabel(mode) + " >";
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, theme.accent(), false);
		} else if (setting instanceof KeybindSetting keybind) {
			String value = listeningKeybind == keybind ? "..." : keybind.keyName();
			int w = Math.max(40, font.width(value) + 10);
			g.fill(r[2] - w, r[1], r[2], r[3], listeningKeybind == keybind ? theme.accent() : 0xFF0B0B0E);
			GuiDraw.outline(g, r[2] - w, r[1], w, r[3] - r[1], theme.outline());
			GuiDraw.text(g, font, value, r[2] - w + (w - font.width(value)) / 2, textY, listeningKeybind == keybind ? 0xFF000000 : theme.text(), false);
		} else if (setting instanceof ColorSetting color) {
			String hex = color.isChroma() ? I18n.get("salami.colorpicker.chroma") : ColorSetting.toHex(color.get());
			GuiDraw.text(g, font, hex, r[2] - 24 - font.width(hex), textY, theme.textDim(), false);
			g.fill(r[2] - 20, r[1], r[2], r[3], color.argb());
			GuiDraw.outline(g, r[2] - 20, r[1], 20, r[3] - r[1], picker != null && picker.setting() == color ? theme.accent() : theme.outline());
		} else if (setting instanceof StringListSetting list) {
			String value = I18n.get("salami.clickgui.entries", list.get().size());
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, theme.textDim(), false);
		} else if (setting instanceof ActionSetting) {
			boolean hovered = inside(r, mx, my);
			g.fill(r[0], r[1], r[2], r[3], hovered ? theme.accent() : theme.header());
			String label = I18n.get("salami.mods.open");
			GuiDraw.text(g, font, label, r[0] + (r[2] - r[0] - font.width(label)) / 2, textY, hovered ? 0xFF000000 : theme.headerText(), false);
		} else if (setting instanceof TextSetting text) {
			boolean editing = editingText == text;
			g.fill(r[0], r[1], r[2], r[3], 0xFF0B0B0E);
			GuiDraw.outline(g, r[0], r[1], r[2] - r[0], r[3] - r[1], editing ? theme.accent() : theme.outline());
			String value = editing ? editBuffer : text.get();
			String shown = font.plainSubstrByWidth(value, r[2] - r[0] - 8, editing);
			GuiDraw.text(g, font, shown, r[0] + 3, textY, value.isEmpty() && !editing ? theme.textDim() : theme.text(), false);
			if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = r[0] + 3 + font.width(shown);
				g.fill(cx, r[1] + 2, cx + 1, r[3] - 2, theme.text());
			}
		} else {
			String value = String.valueOf(setting.get());
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, theme.text(), false);
		}
	}

	private void drawSwitch(GuiGraphics g, Theme theme, int[] r, boolean on) {
		g.fill(r[0], r[1], r[2], r[3], on ? theme.accent() : 0xFF3A3A44);
		int size = r[3] - r[1] - 2;
		int knob = on ? r[2] - 1 - size : r[0] + 1;
		g.fill(knob, r[1] + 1, knob + size, r[3] - 1, on ? 0xFF1A1A1F : 0xFFF3F3F3);
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		int button = event.button();
		boolean back = button == InputConstants.MOUSE_BUTTON_RIGHT;
		listeningKeybind = null;
		commitText();
		if (picker != null) {
			if (picker.mouseClicked(font, x, y, button)) {
				return true;
			}
			picker = null; // Klick daneben schließt den Farbwähler
		}

		if (inside(backButton(), x, y)) {
			onClose();
			return true;
		}
		if (inside(toggle(), x, y)) {
			modules.toggle(module);
			return true;
		}
		int[] c = content();
		if (x < c[0] - 4 || x >= c[2] + 4 || y < c[1] || y >= c[3]) {
			return super.mouseClicked(event, doubleClick);
		}
		for (Row row : rows()) {
			if (y < row.y() || y >= row.y() + ROW) {
				continue;
			}
			Setting<?> setting = row.setting();
			if (setting instanceof BoolSetting bool) {
				bool.toggle();
			} else if (setting instanceof NumberSetting number) {
				if (back) {
					number.reset();
				} else {
					draggedSlider = number;
					updateSlider(row, x);
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
					openColorPicker(color);
				}
			} else if (setting instanceof ActionSetting action) {
				action.run();
			} else if (setting instanceof TextSetting text) {
				if (back) {
					text.reset();
				} else {
					editingText = text;
					editBuffer = text.get();
				}
			}
			return true;
		}
		return true;
	}

	private void updateSlider(Row row, double mouseX) {
		int[] r = control(row);
		int trackRight = r[2] - font.width(draggedSlider.display()) - 6;
		draggedSlider.setFraction((mouseX - r[0]) / Math.max(1, trackRight - r[0]));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (picker != null && picker.isDragging()) {
			picker.mouseDragged(event.x() / uiScale, event.y() / uiScale);
			return true;
		}
		if (draggedSlider != null) {
			for (Row row : rows()) {
				if (row.setting() == draggedSlider) {
					updateSlider(row, event.x() / uiScale);
					break;
				}
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (picker != null && picker.isDragging()) {
			picker.mouseReleased();
			return true;
		}
		if (draggedSlider != null) {
			draggedSlider = null;
			float next = GuiScaleModule.clickGuiScale();
			if (next != uiScale) {
				uiScale = next; // z. B. nach dem Ziehen des Größe-Reglers im Modul „Oberfläche“
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker != null && picker.charTyped(event.codepointAsString())) {
			return true;
		}
		if (editingText != null) {
			if (event.isAllowedChatCharacter() && editBuffer.length() < editingText.maxLength()) {
				editBuffer += event.codepointAsString();
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		commitText();
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		picker = null; // Farbwähler hängt an seiner Zeile
		scroll -= scrollY * ROW * 2;
		clampScroll();
		return true;
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
		if (editingText != null) {
			capturedLastKey = true;
			switch (event.key()) {
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> commitText();
				case InputConstants.KEY_ESCAPE -> editingText = null;
				case InputConstants.KEY_BACKSPACE -> {
					if (!editBuffer.isEmpty()) {
						editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
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
		return super.keyPressed(event);
	}
}
