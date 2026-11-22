package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
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
	private static final int MAX_W = 360;

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
	/** Beim nächsten Zeichnen zu dieser Einstellung scrollen ({@link #editText}). */
	private Setting<?> scrollTo;

	/** Zeile: eine Einstellung oder ({@code setting == null}) die Überschrift vor den Darstellungs-Einstellungen. */
	private record Row(Setting<?> setting, int y) {
		boolean isHeader() {
			return setting == null;
		}
	}

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
		return SalamiMenuScreen.pausesGame();
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

	/** Text-Einstellung gleich bearbeiten (aus der Panel-Ansicht); scrollt beim ersten Zeichnen zu ihr. */
	public void editText(TextSetting text) {
		editingText = text;
		editBuffer = text.get();
		scrollTo = text;
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
		return SalamiWindow.frame(uiW, Math.round(height / uiScale), MAX_W, Math.min(320, naturalHeight(uiW)));
	}

	/** Höhe, bei der alle Einstellungen ohne Scrollen hineinpassen (das Fenster wird nicht größer als nötig). */
	private int naturalHeight(int uiW) {
		int w = Math.min(uiW - 16, MAX_W);
		Component text = module.description();
		int desc = text == null ? 0 : font.split(text, w - 2 * PAD).size();
		int rows = 0;
		boolean header = false;
		boolean any = false;
		for (Setting<?> setting : module.settings()) {
			if (setting.isHidden()) {
				continue;
			}
			if (setting.isAppearance() && !header && any) {
				header = true;
				rows++;
			}
			any = true;
			rows++;
		}
		return Math.max(110, SalamiWindow.HEADER + 5 + desc * 10 + (desc > 0 ? 5 : 0) + rows * ROW + PAD + 2);
	}

	private int[] backButton() {
		return SalamiWindow.backButton(font, frame());
	}

	/** Schalter des Moduls oben rechts samt „An“/„Aus“ davor. */
	private int[] toggle() {
		int[] f = frame();
		int label = Math.max(font.width(I18n.get("salami.on")), font.width(I18n.get("salami.off")));
		int size = SalamiWindow.skin().toggleSize()[0];
		return new int[] {f[2] - PAD - size - 4 - label, f[1] + 6, f[2] - PAD, f[1] + 6 + Math.max(11, SalamiWindow.skin().toggleSize()[1])};
	}

	private List<FormattedCharSequence> description() {
		Component text = module.description();
		if (text == null) {
			return List.of();
		}
		int[] f = frame();
		return font.split(text, f[2] - f[0] - 2 * PAD);
	}

	private int[] content() {
		int[] f = frame();
		int top = SalamiWindow.contentTop(f) + description().size() * 10 + (description().isEmpty() ? 0 : 5);
		return new int[] {f[0] + PAD, top, f[2] - PAD - 6, f[3] - PAD};
	}

	private List<Row> rows() {
		int[] c = content();
		List<Row> rows = new ArrayList<>();
		int y = c[1] - (int) Math.round(scroll);
		boolean header = false;
		boolean any = false;
		for (Setting<?> setting : module.settings()) {
			if (setting.isHidden()) {
				continue;
			}
			// Überschrift „Darstellung“ vor der ersten Darstellungs-Einstellung (nur wenn davor schon etwas steht)
			if (setting.isAppearance() && !header && any) {
				header = true;
				rows.add(new Row(null, y));
				y += ROW;
			}
			any = true;
			rows.add(new Row(setting, y));
			y += ROW;
		}
		return rows;
	}

	private void scrollToSetting(Setting<?> setting) {
		int[] c = content();
		for (Row row : rows()) {
			if (row.setting() == setting && row.y() + ROW > c[3]) {
				scroll += row.y() + ROW - c[3];
				clampScroll();
			}
		}
	}

	private int contentHeight() {
		List<Row> rows = rows();
		return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y() + ROW - rows.get(0).y();
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
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	//?}
		SalamiWindow.backdrop(graphics, width, height, uiScale);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		TextInput.sync(this, (picker != null && picker.isEditingText()) || editingText != null);
		if (scrollTo != null) {
			scrollToSetting(scrollTo);
			scrollTo = null;
		}
		Skin skin = SalamiWindow.skin();
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		Component tooltip = null;

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		int[] f = frame();
		int[] toggle = toggle();
		boolean overPicker = picker != null && picker.contains(mx, my);
		SalamiWindow.draw(g, font, f, I18n.get(module.translationKey()), overPicker ? -1 : mx, my, f[2] - toggle[0]);
		boolean on = module.isEnabled();
		int size = skin.toggleSize()[0];
		Skin.UiState toggleState = Skin.UiState.of(!overPicker && inside(toggle, mx, my));
		skin.toggle(g, toggle[2] - size, toggle[1], on, toggleState);
		String state = I18n.get(on ? "salami.on" : "salami.off");
		GuiDraw.text(g, font, state, toggle[2] - size - 4 - font.width(state), toggle[1] + 2, on ? ink.accent() : ink.dim(), false);

		int dy = SalamiWindow.contentTop(f);
		for (FormattedCharSequence line : description()) {
			GuiDraw.text(g, font, line, f[0] + PAD, dy, ink.dim(), false);
			dy += 10;
		}

		int[] c = content();
		g.enableScissor(c[0] - 4, c[1], c[2] + 4, c[3]);
		for (Row row : rows()) {
			if (row.y() + ROW < c[1] || row.y() > c[3]) {
				continue;
			}
			if (row.isHeader()) {
				String title = I18n.get("salami.mods.appearance");
				skin.heading(g, font, title, c[0], row.y() + 7, ink.accent(), 1f);
				int titleW = skin.headingWidth(font, title, 1f);
				skin.divider(g, c[0] + titleW + 6, row.y() + 11, c[2] - c[0] - titleW - 6, Skin.Surface.WINDOW);
				continue;
			}
			boolean hovered = !overPicker && mx >= c[0] - 4 && mx < c[2] + 4 && my >= row.y() && my < row.y() + ROW && my >= c[1] && my < c[3];
			skin.row(g, c[0] - 4, row.y(), c[2] - c[0] + 8, ROW, editingText == row.setting() || listeningKeybind == row.setting(),
				Skin.UiState.of(hovered));
			if (hovered) {
				String desc = SettingText.description(row.setting());
				if (desc != null) {
					tooltip = Component.literal(desc);
				} else if (row.setting() instanceof StringListSetting) {
					tooltip = Component.translatable("salami.clickgui.edit_in_config");
				}
			}
			TextFit.draw(g, font, SettingText.label(row.setting()), c[0] + 2, row.y() + 5, c[2] - c[0] - CONTROL_W - 10, ink.text());
			renderControl(g, skin, row, overPicker ? -1 : mx, my);
		}
		g.disableScissor();
		renderScrollbar(g, skin, c, mx, my);
		if (picker != null) {
			picker.render(g, font, mx, my);
		}

		g.pose().popMatrix();
		if (tooltip != null) {
			g.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, mouseY);
		}
	}

	private void renderScrollbar(GuiGraphics g, Skin skin, int[] c, int mx, int my) {
		int total = contentHeight();
		int visible = c[3] - c[1];
		if (total <= visible) {
			return;
		}
		int thumb = Math.max(14, visible * visible / total);
		int thumbY = (int) Math.round((visible - thumb) * (scroll / (total - visible)));
		int x = c[2] + 4;
		skin.scrollbar(g, x, c[1], 3, visible, thumbY, thumb, Skin.UiState.of(mx >= x - 1 && mx < x + 4 && my >= c[1] && my < c[3]));
	}

	private void renderControl(GuiGraphics g, Skin skin, Row row, int mx, int my) {
		Setting<?> setting = row.setting();
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		Skin.Ink inputInk = skin.ink(Skin.Surface.INPUT);
		int[] r = control(row);
		int h = r[3] - r[1];
		int textY = r[1] + (h - 8) / 2 + 1;
		boolean hovered = inside(new int[] {r[0], row.y(), r[2], row.y() + ROW}, mx, my);
		Skin.UiState state = Skin.UiState.of(hovered);
		if (setting instanceof BoolSetting bool) {
			int[] size = skin.toggleSize();
			skin.toggle(g, r[2] - size[0], r[1] + (h - size[1]) / 2, bool.isOn(), state);
		} else if (setting instanceof NumberSetting number) {
			String value = number.display();
			int trackRight = r[2] - font.width(value) - 6;
			skin.slider(g, r[0], r[1], trackRight - r[0], h, (float) number.fraction(), draggedSlider == number ? Skin.UiState.PRESSED : state);
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, ink.text(), false);
		} else if (setting instanceof ModeSetting<?> mode) {
			String value = SettingText.modeLabel(mode);
			int w = Math.min(r[2] - r[0], font.width(value) + 22);
			skin.button(g, font, r[2] - w, r[1], w, h, value, Skin.ButtonKind.SECONDARY, state);
			int arrows = skin.buttonInk(Skin.ButtonKind.SECONDARY, state);
			GuiDraw.text(g, font, "<", r[2] - w + 4, textY - 1, GuiDraw.fade(arrows, 0.55F), false);
			GuiDraw.text(g, font, ">", r[2] - 8, textY - 1, GuiDraw.fade(arrows, 0.55F), false);
		} else if (setting instanceof KeybindSetting keybind) {
			boolean listening = listeningKeybind == keybind;
			String value = listening ? "..." : keybind.keyName();
			int w = Math.max(40, font.width(value) + 10);
			skin.input(g, r[2] - w, r[1], w, h, listening);
			GuiDraw.text(g, font, value, r[2] - w + (w - font.width(value)) / 2, textY, listening ? inputInk.accent() : inputInk.text(), false);
		} else if (setting instanceof ColorSetting color) {
			String hex = color.isChroma() ? I18n.get("salami.colorpicker.chroma") : ColorSetting.toHex(color.get());
			GuiDraw.text(g, font, hex, r[2] - 24 - font.width(hex), textY, ink.dim(), false);
			g.fill(r[2] - 20, r[1], r[2], r[3], color.argb());
			GuiDraw.outline(g, r[2] - 20, r[1], 20, h, picker != null && picker.setting() == color ? ink.accent() : hovered ? ink.text() : ink.line());
		} else if (setting instanceof StringListSetting list) {
			String value = I18n.get("salami.clickgui.entries", list.get().size());
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, ink.dim(), false);
		} else if (setting instanceof ActionSetting action) {
			skin.button(g, font, r[0], r[1], r[2] - r[0], h, I18n.get(action.buttonKey()), Skin.ButtonKind.SECONDARY, state);
		} else if (setting instanceof TextSetting text) {
			boolean editing = editingText == text;
			skin.input(g, r[0], r[1], r[2] - r[0], h, editing);
			String value = editing ? editBuffer : text.get();
			String shown = font.plainSubstrByWidth(value, r[2] - r[0] - 8, editing);
			GuiDraw.text(g, font, shown, r[0] + 3, textY, value.isEmpty() && !editing ? inputInk.dim() : inputInk.text(), false);
			if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = r[0] + 3 + font.width(shown);
				g.fill(cx, r[1] + 2, cx + 1, r[3] - 2, inputInk.text());
			}
		} else {
			String value = String.valueOf(setting.get());
			GuiDraw.text(g, font, value, r[2] - font.width(value), textY, ink.text(), false);
		}
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
			if (row.isHeader()) {
				return true;
			}
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
		// Menü-Taste schließt alles – nicht, während eine Taste belegt oder Text getippt wird
		boolean typing = listeningKeybind != null || editingText != null || (picker != null && picker.isEditingText());
		if (SalamiMenuScreen.closeOnMenuKey(minecraft, event, typing)) {
			return true;
		}
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
