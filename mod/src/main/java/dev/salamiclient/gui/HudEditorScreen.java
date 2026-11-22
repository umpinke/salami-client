package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.Skins;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * HUD-Editor (Overlay-System). Die HUD-Elemente zeichnen sich weiter selbst über das HUD; der Editor
 * legt Rahmen darüber und speichert Position (Anteil der Bildschirmgröße) und Größe.
 *
 * <ul>
 *   <li>Ziehen: verschieben – rastet an Bildschirmrändern, Bildschirmmitte und anderen Elementen ein
 *       (Hilfslinien; Umschalt gedrückt = frei)</li>
 *   <li>Ecke unten rechts ziehen oder Mausrad: Größe des Elements</li>
 *   <li>Rechtsklick: Einstellungen des Elements · Pfeiltasten: verschieben · R: zurücksetzen ·
 *       Entf: ausblenden</li>
 *   <li>Leiste in der Mitte: HUD-Größe aller Elemente, HUD-Stil, Einrasten, Liste aller Elemente
 *       zum Ein- und Ausschalten, Einstellungen „Oberfläche“</li>
 * </ul>
 */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private static final int HANDLE = 6;
	private static final int BAR_H = 16;
	private static final int ROW_H = 14;
	private static final int LIST_W = 160;

	/** Elementliste bleibt während der Sitzung offen/zu. */
	private static boolean listOpen;

	private final Screen parent;
	private HudModule selected;
	private HudModule dragging;
	/** Menü-Taste gedrückt, ohne dass seitdem etwas anderes passiert ist (siehe keyPressed). */
	private boolean closeOnRelease;
	private int dragOffsetX;
	private int dragOffsetY;
	private HudModule resizing;
	private double resizeStartScale;
	private int resizeStartWidth;
	/** Hilfslinien beim Einrasten ({@code Integer.MIN_VALUE} = keine). */
	private int guideX = Integer.MIN_VALUE;
	private int guideY = Integer.MIN_VALUE;
	private double listScroll;
	/** Zuletzt gewählte Höhe der Leiste ({@link #toolbarTop}); {@code MIN_VALUE} = noch keine. */
	private int toolbarTop = Integer.MIN_VALUE;

	private enum Tool {
		SMALLER,
		SIZE,
		BIGGER,
		STYLE,
		SNAP,
		ELEMENTS,
		SETTINGS,
		DONE
	}

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("salami.hud_editor.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return SalamiMenuScreen.pausesGame();
	}

	/** Liste aller HUD-Elemente auf- oder zuklappen (Knopf „Elemente“; auch für Tests). */
	public static void showElementList(boolean open) {
		listOpen = open;
	}

	private static ModuleManager modules() {
		return SalamiClient.get().modules();
	}

	/** Eingeschaltete HUD-Elemente (zuletzt gezeichnet = liegt oben). */
	private static List<HudModule> elements() {
		List<HudModule> result = new ArrayList<>();
		for (Module module : modules().all()) {
			if (module instanceof HudModule hud && hud.isEnabled()) {
				result.add(hud);
			}
		}
		return result;
	}

	/** Alle HUD-Elemente für die Liste, nach Namen sortiert. */
	private static List<HudModule> allElements() {
		List<HudModule> result = new ArrayList<>();
		for (Module module : modules().all()) {
			if (module instanceof HudModule hud) {
				result.add(hud);
			}
		}
		result.sort(Comparator.comparing(hud -> I18n.get(hud.translationKey()).toLowerCase(Locale.ROOT)));
		return result;
	}

	private record Box(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		boolean onHandle(double mx, double my) {
			return mx >= x + w - HANDLE && mx < x + w + 2 && my >= y + h - HANDLE && my < y + h + 2;
		}
	}

	private Box box(HudModule hud) {
		float s = hud.effectiveScale();
		return new Box(hud.screenX(width), hud.screenY(height), Math.max(8, Math.round(hud.boxWidth() * s)),
			Math.max(8, Math.round(hud.boxHeight() * s)));
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Leiste

	private String toolLabel(Tool tool) {
		GuiScaleModule gui = GuiScaleModule.get();
		return switch (tool) {
			case SMALLER -> "-";
			case BIGGER -> "+";
			case SIZE -> I18n.get("salami.hud_editor.size", Math.round(GuiScaleModule.hudScale() * 100) + "%");
			case STYLE -> I18n.get("salami.hud_editor.style", I18n.get("salami.module.gui_scale.setting.hud_style."
				+ (gui == null || !gui.isEnabled() ? GuiScaleModule.HudLook.CLEAN : gui.hudLook()).name().toLowerCase(Locale.ROOT)));
			case SNAP -> I18n.get(GuiScaleModule.hudSnap() ? "salami.hud_editor.snap_on" : "salami.hud_editor.snap_off");
			case ELEMENTS -> I18n.get("salami.hud_editor.elements");
			case SETTINGS -> I18n.get("salami.hud_editor.settings");
			case DONE -> I18n.get("salami.hud_editor.done");
		};
	}

	/**
	 * Knöpfe der Leiste: {x0, y0, x1, y1} in Bildschirmkoordinaten, Reihenfolge wie {@link Tool}. Passt
	 * die Leiste nicht in die Fensterbreite, bricht sie in weitere Reihen um (die Größen-Knöpfe bleiben
	 * zusammen); jede Reihe ist mittig.
	 */
	private int[][] toolbar() {
		return toolbar(toolbarTop());
	}

	private int[][] toolbar(int top) {
		Tool[] tools = Tool.values();
		int[] widths = new int[tools.length];
		for (int i = 0; i < tools.length; i++) {
			boolean narrow = tools[i] == Tool.SMALLER || tools[i] == Tool.BIGGER;
			widths[i] = narrow ? BAR_H : font.width(toolLabel(tools[i])) + 12;
		}
		// Reihen bilden: {erster Knopf, Breite}
		int maxWidth = Math.max(BAR_H * 4, width - 16 - 8);
		List<int[]> rows = new ArrayList<>();
		int start = 0;
		int rowWidth = 0;
		for (int i = 0; i < tools.length; i++) {
			// „-“, „HUD 100 %“ und „+“ gehören zusammen
			if (i > 0 && (tools[i] == Tool.SIZE || tools[i] == Tool.BIGGER)) {
				rowWidth += gapAfter(tools[i - 1]) + widths[i];
				continue;
			}
			int unit = widths[i];
			if (tools[i] == Tool.SMALLER) {
				unit += gapAfter(Tool.SMALLER) + widths[i + 1] + gapAfter(Tool.SIZE) + widths[i + 2];
			}
			if (i > start && rowWidth + 4 + unit > maxWidth) {
				rows.add(new int[] {start, rowWidth});
				start = i;
				rowWidth = 0;
			}
			rowWidth += (i > start ? gapAfter(tools[i - 1]) : 0) + widths[i];
		}
		rows.add(new int[] {start, rowWidth});

		int[][] rects = new int[tools.length][];
		for (int r = 0; r < rows.size(); r++) {
			int from = rows.get(r)[0];
			int to = r + 1 < rows.size() ? rows.get(r + 1)[0] : tools.length;
			int x = Math.max(8, (width - rows.get(r)[1]) / 2);
			int y = top + r * (BAR_H + 4);
			for (int i = from; i < to; i++) {
				if (i > from) {
					x += gapAfter(tools[i - 1]);
				}
				rects[i] = new int[] {x, y, x + widths[i], y + BAR_H};
				x += widths[i];
			}
		}
		return rects;
	}

	/** Größen-Knöpfe hängen zusammen, sonst kleine Lücken. */
	private static int gapAfter(Tool tool) {
		return tool == Tool.SMALLER || tool == Tool.SIZE ? 1 : 4;
	}

	/** Rahmen um alle Knöpfe {x0, y0, x1, y1}. */
	private static int[] frameOf(int[][] rects) {
		int[] frame = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (int[] r : rects) {
			frame[0] = Math.min(frame[0], r[0] - 4);
			frame[1] = Math.min(frame[1], r[1] - 4);
			frame[2] = Math.max(frame[2], r[2] + 4);
			frame[3] = Math.max(frame[3], r[3] + 4);
		}
		return frame;
	}

	private int[] toolbarFrame() {
		return frameOf(toolbar());
	}

	/** Oberkante der Knöpfe; bleibt stehen, solange ein Element gezogen oder vergrößert wird. */
	private int toolbarTop() {
		if (toolbarTop == Integer.MIN_VALUE || (dragging == null && resizing == null)) {
			toolbarTop = chooseToolbarTop();
		}
		return toolbarTop;
	}

	/**
	 * Platz für die Leiste, der keine eingeschalteten HUD-Elemente verdeckt: knapp unter dem Fadenkreuz,
	 * knapp darüber, oben oder unten über dem Hinweis – der erste freie Platz, sonst der mit der kleinsten
	 * Überdeckung.
	 */
	private int chooseToolbarTop() {
		int[] probe = frameOf(toolbar(0));
		int rowsHeight = probe[3] - probe[1] - 8; // Höhe der Knopfreihen ohne Rahmen
		int[] candidates = {
			height / 2 + 14,
			height / 2 - 14 - rowsHeight,
			8,
			height - hintHeight() - 10 - rowsHeight,
		};
		int best = candidates[0];
		long bestOverlap = Long.MAX_VALUE;
		for (int top : candidates) {
			int[] frame = frameOf(toolbar(top));
			if (frame[1] < 0 || frame[3] > height) {
				continue;
			}
			long overlap = 0;
			for (HudModule hud : elements()) {
				Box b = box(hud);
				int w = Math.min(frame[2], b.x() + b.w() + 2) - Math.max(frame[0], b.x() - 2);
				int h = Math.min(frame[3], b.y() + b.h() + 2) - Math.max(frame[1], b.y() - 2);
				if (w > 0 && h > 0) {
					overlap += (long) w * h;
				}
			}
			if (overlap == 0) {
				return top;
			}
			if (overlap < bestOverlap) {
				bestOverlap = overlap;
				best = top;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ Elementliste

	/** Liste über oder unter der Leiste – dort, wo mehr Platz ist. */
	private int[] listFrame() {
		int[] bar = toolbarFrame();
		int wanted = allElements().size() * ROW_H + 8;
		int above = bar[1] - 4 - 4;
		int below = height - bar[3] - 4 - hintHeight() - 6;
		int x = (width - LIST_W) / 2;
		if (above >= below) {
			int h = Math.min(wanted, Math.max(ROW_H * 3, above));
			return new int[] {x, bar[1] - 4 - h, x + LIST_W, bar[1] - 4};
		}
		int h = Math.min(wanted, Math.max(ROW_H * 3, below));
		return new int[] {x, bar[3] + 4, x + LIST_W, bar[3] + 4 + h};
	}

	// ------------------------------------------------------------------ Hinweis unten

	/** Hinweis in Zeilen, die in die Fensterbreite passen (Teile an „·“ umbrechen). */
	private List<String> hintLines() {
		String hint = I18n.get(elements().isEmpty() ? "salami.hud_editor.empty" : "salami.hud_editor.hint");
		String[] parts = hint.split("\\s+·\\s+");
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		int max = width - 24;
		for (String part : parts) {
			String candidate = line.isEmpty() ? part : line + "  ·  " + part;
			if (!line.isEmpty() && font.width(candidate) > max) {
				lines.add(line.toString());
				line = new StringBuilder(part);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private int hintHeight() {
		return hintLines().size() * 10 + 4;
	}

	private int listContentHeight() {
		return allElements().size() * ROW_H;
	}

	private void clampListScroll() {
		int[] f = listFrame();
		int visible = f[3] - f[1] - 8;
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContentHeight() - visible)));
	}

	/** Zahnrad-Feld einer Listenzeile. */
	private static int[] gearRect(int[] f, int rowY) {
		return new int[] {f[2] - 19, rowY, f[2] - 4, rowY + ROW_H};
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Kein Hintergrund des Looks: hier muss man das Spiel und das HUD sehen
		if (minecraft.level == null) {
			// ohne Welt (Titelbildschirm, Mod Menu): Panorama dahinter wie in Minecrafts eigenen Menüs
			//? if >=26.1 {
			/*super.extractBackground(graphics, mouseX, mouseY, partialTick);
			*///?} else
			super.renderBackground(graphics, mouseX, mouseY, partialTick);
		}
		graphics.fill(0, 0, width, height, 0x38000000);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	//?}
		Skin skin = Skins.current();
		int accent = skin.ink(Skin.Surface.WINDOW).accent();
		boolean overUi = overToolbarOrList(mouseX, mouseY);

		// Mittellinien zur Orientierung, nur angedeutet
		graphics.fill(width / 2, 0, width / 2 + 1, height, 0x10FFFFFF);
		graphics.fill(0, height / 2, width, height / 2 + 1, 0x10FFFFFF);

		HudModule hover = overUi ? null : elementAt(mouseX, mouseY);
		for (HudModule hud : elements()) {
			Box b = box(hud);
			boolean active = hud == dragging || hud == resizing || hud == hover || hud == selected;
			if (!hud.hasContent()) {
				GuiDraw.rounded(graphics, b.x(), b.y(), b.w(), b.h(), 2, 0x30FFFFFF);
			}
			GuiDraw.outline(graphics, b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, active ? accent : 0x50FFFFFF);
			if (hud == selected || hud == hover || hud == resizing) {
				// Griff zum Vergrößern
				int hx = b.x() + b.w() - HANDLE + 2;
				int hy = b.y() + b.h() - HANDLE + 2;
				graphics.fill(hx - 1, hy - 1, hx + HANDLE - 1, hy + HANDLE - 1, 0xFFFFFFFF);
				graphics.fill(hx, hy, hx + HANDLE - 2, hy + HANDLE - 2, accent);
			}
		}

		if (guideX != Integer.MIN_VALUE) {
			graphics.fill(guideX, 0, guideX + 1, height, GuiDraw.fade(accent, 0.8F));
		}
		if (guideY != Integer.MIN_VALUE) {
			graphics.fill(0, guideY, width, guideY + 1, GuiDraw.fade(accent, 0.8F));
		}

		// Name und Größe am aktiven Element (nur eins, sonst überlappen die Beschriftungen)
		HudModule labeled = dragging != null ? dragging : resizing != null ? resizing : hover != null ? hover : selected;
		if (labeled != null) {
			renderTag(graphics, skin, labeled);
		}

		renderToolbar(graphics, skin, mouseX, mouseY);
		if (listOpen) {
			renderList(graphics, skin, mouseX, mouseY);
		}

		List<String> hint = hintLines();
		int hintWidth = 0;
		for (String line : hint) {
			hintWidth = Math.max(hintWidth, font.width(line) + 12);
		}
		int hintTop = height - 3 - hintHeight();
		skin.surface(graphics, Skin.Surface.POPUP, (width - hintWidth) / 2, hintTop, hintWidth, hintHeight());
		Skin.Ink popup = skin.ink(Skin.Surface.POPUP);
		for (int i = 0; i < hint.size(); i++) {
			String line = hint.get(i);
			GuiDraw.text(graphics, font, line, (width - font.width(line)) / 2, hintTop + 3 + i * 10, popup.dim(), false);
		}
	}

	private void renderTag(GuiGraphics g, Skin skin, HudModule hud) {
		Box b = box(hud);
		String text = I18n.get(hud.translationKey()) + "  " + Math.round(hud.scale.get() * 100) + "%";
		int w = skin.tagWidth(font, text);
		int h = skin.tagHeight();
		int tagY = b.y() >= h + 3 ? b.y() - h - 2 : b.y() + b.h() + 3;
		int tagX = Math.max(2, Math.min(b.x(), width - w - 2));
		skin.tag(g, font, tagX, tagY, text);
	}

	private void renderToolbar(GuiGraphics g, Skin skin, int mouseX, int mouseY) {
		int[] frame = toolbarFrame();
		skin.surface(g, Skin.Surface.POPUP, frame[0], frame[1], frame[2] - frame[0], frame[3] - frame[1]);
		int[][] rects = toolbar();
		Tool[] tools = Tool.values();
		for (int i = 0; i < tools.length; i++) {
			int[] r = rects[i];
			Tool tool = tools[i];
			boolean primary = tool == Tool.DONE || (tool == Tool.ELEMENTS && listOpen);
			skin.button(g, font, r[0], r[1], r[2] - r[0], r[3] - r[1], toolLabel(tool), primary ? Skin.ButtonKind.PRIMARY : Skin.ButtonKind.SECONDARY,
				Skin.UiState.of(inside(r, mouseX, mouseY)));
		}
	}

	private void renderList(GuiGraphics g, Skin skin, int mouseX, int mouseY) {
		clampListScroll();
		Skin.Ink ink = skin.ink(Skin.Surface.POPUP);
		int[] f = listFrame();
		skin.surface(g, Skin.Surface.POPUP, f[0], f[1], f[2] - f[0], f[3] - f[1]);
		g.enableScissor(f[0], f[1] + 4, f[2], f[3] - 4);
		int y = f[1] + 4 - (int) Math.round(listScroll);
		int[] size = skin.toggleSize();
		for (HudModule hud : allElements()) {
			if (y + ROW_H >= f[1] && y <= f[3]) {
				boolean hovered = mouseX >= f[0] && mouseX < f[2] && mouseY >= y && mouseY < y + ROW_H
					&& mouseY >= f[1] + 4 && mouseY < f[3] - 4;
				int[] gear = gearRect(f, y);
				boolean gearHover = hovered && inside(gear, mouseX, mouseY);
				skin.row(g, f[0] + 3, y, f[2] - f[0] - 6, ROW_H, false, Skin.UiState.of(hovered && !gearHover));
				boolean on = hud.isEnabled();
				skin.toggle(g, f[0] + 7, y + (ROW_H - size[1]) / 2, on, Skin.UiState.of(hovered && !gearHover));
				int nameX = f[0] + 7 + size[0] + 5;
				TextFit.draw(g, font, I18n.get(hud.translationKey()), nameX, y + 3, gear[0] - 3 - nameX, on ? ink.text() : ink.dim());
				Icons.gear(g, gear[0] + 4, y + (ROW_H - 7) / 2, gearHover ? ink.accent() : ink.dim());
			}
			y += ROW_H;
		}
		g.disableScissor();
	}

	// ------------------------------------------------------------------ Treffer

	private boolean overToolbarOrList(double x, double y) {
		return inside(toolbarFrame(), x, y) || (listOpen && inside(listFrame(), x, y));
	}

	/** Oberstes Element unter der Maus (Griff zählt mit). */
	private HudModule elementAt(double x, double y) {
		List<HudModule> list = elements();
		for (int i = list.size() - 1; i >= 0; i--) {
			Box b = box(list.get(i));
			if (b.contains(x, y) || b.onHandle(x, y)) {
				return list.get(i);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		closeOnRelease = false; // Umschalt war ein Modifikator, kein Schließen
		double x = event.x();
		double y = event.y();
		int button = event.button();
		if (inside(toolbarFrame(), x, y)) {
			int[][] rects = toolbar();
			for (int i = 0; i < rects.length; i++) {
				if (inside(rects[i], x, y)) {
					runTool(Tool.values()[i], button);
					return true;
				}
			}
			return true;
		}
		if (listOpen && inside(listFrame(), x, y)) {
			clickList(x, y, button);
			return true;
		}
		// Griff des gewählten Elements hat Vorrang (liegt oft über einem Nachbarn)
		if (selected != null && selected.isEnabled() && box(selected).onHandle(x, y) && button == InputConstants.MOUSE_BUTTON_LEFT) {
			startResize(selected);
			return true;
		}
		HudModule hud = elementAt(x, y);
		if (hud == null) {
			selected = null;
			return super.mouseClicked(event, doubleClick);
		}
		selected = hud;
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			minecraft.setScreen(new ModSettingsScreen(modules(), hud, this));
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			Box b = box(hud);
			if (b.onHandle(x, y)) {
				startResize(hud);
			} else {
				dragging = hud;
				dragOffsetX = (int) x - b.x();
				dragOffsetY = (int) y - b.y();
			}
		}
		return true;
	}

	private void startResize(HudModule hud) {
		resizing = hud;
		resizeStartScale = hud.scale.get();
		resizeStartWidth = Math.max(1, box(hud).w());
	}

	private void runTool(Tool tool, int button) {
		GuiScaleModule gui = GuiScaleModule.get();
		boolean back = button == InputConstants.MOUSE_BUTTON_RIGHT;
		switch (tool) {
			case SMALLER -> stepHud(-1);
			case BIGGER -> stepHud(1);
			case SIZE -> stepHud(0); // Klick auf die Prozentzahl: zurück auf 100 %
			case STYLE -> {
				if (gui != null) {
					enable(gui);
					gui.cycleHudLook(back ? -1 : 1);
				}
			}
			case SNAP -> {
				if (gui != null) {
					enable(gui);
					gui.toggleHudSnap();
				}
			}
			case ELEMENTS -> {
				listOpen = !listOpen;
				listScroll = 0;
			}
			case SETTINGS -> {
				if (gui != null) {
					minecraft.setScreen(new ModSettingsScreen(modules(), gui, this));
				}
			}
			case DONE -> onClose();
		}
	}

	/** HUD-Größe aller Elemente ändern ({@code steps} × 10 %, 0 = 100 %); schaltet „Oberfläche“ bei Bedarf ein. */
	private void stepHud(int steps) {
		GuiScaleModule gui = GuiScaleModule.get();
		if (gui != null) {
			enable(gui);
			gui.stepHud(steps);
		}
	}

	private static void enable(Module module) {
		if (!module.isEnabled()) {
			modules().setEnabled(module, true);
		}
	}

	private void clickList(double x, double y, int button) {
		int[] f = listFrame();
		if (y < f[1] + 4 || y >= f[3] - 4) {
			return;
		}
		int index = (int) Math.floor((y - (f[1] + 4) + listScroll) / ROW_H);
		List<HudModule> all = allElements();
		if (index < 0 || index >= all.size()) {
			return;
		}
		HudModule hud = all.get(index);
		int rowY = f[1] + 4 + index * ROW_H - (int) Math.round(listScroll);
		if (button == InputConstants.MOUSE_BUTTON_RIGHT || inside(gearRect(f, rowY), x, y)) {
			minecraft.setScreen(new ModSettingsScreen(modules(), hud, this));
			return;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			modules().toggle(hud);
			selected = hud.isEnabled() ? hud : null;
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		closeOnRelease = false; // Umschalt war ein Modifikator, kein Schließen
		if (resizing != null) {
			Box b = box(resizing);
			double wanted = Math.max(8, event.x() - b.x());
			resizing.scale.set(resizeStartScale * wanted / resizeStartWidth);
			return true;
		}
		if (dragging != null) {
			Box b = box(dragging);
			int nx = (int) event.x() - dragOffsetX;
			int ny = (int) event.y() - dragOffsetY;
			guideX = Integer.MIN_VALUE;
			guideY = Integer.MIN_VALUE;
			if (GuiScaleModule.hudSnap() && !minecraft.hasShiftDown()) {
				int[] sx = snap(nx, b.w(), width, true);
				int[] sy = snap(ny, b.h(), height, false);
				nx = sx[0];
				guideX = sx[1];
				ny = sy[0];
				guideY = sy[1];
			}
			nx = Math.max(0, Math.min(width - b.w(), nx));
			ny = Math.max(0, Math.min(height - b.h(), ny));
			dragging.moveTo(nx, ny, width, height);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	/**
	 * Rastet eine Kante oder die Mitte des gezogenen Elements an Bildschirmrand, Bildschirmmitte
	 * oder Kanten/Mitten anderer Elemente ein. Liefert {neue Position, Hilfslinie oder MIN_VALUE}.
	 */
	private int[] snap(int pos, int size, int screen, boolean horizontal) {
		List<Integer> targets = new ArrayList<>(List.of(0, screen / 2, screen));
		for (HudModule other : elements()) {
			if (other == dragging || !other.hasContent()) {
				continue;
			}
			Box o = box(other);
			int start = horizontal ? o.x() : o.y();
			int length = horizontal ? o.w() : o.h();
			targets.add(start);
			targets.add(start + length / 2);
			targets.add(start + length);
		}
		int[] edges = {pos, pos + size / 2, pos + size};
		int best = SNAP + 1;
		int shift = 0;
		int guide = Integer.MIN_VALUE;
		for (int target : targets) {
			for (int edge : edges) {
				int d = target - edge;
				if (Math.abs(d) < best) {
					best = Math.abs(d);
					shift = d;
					guide = target;
				}
			}
		}
		if (best > SNAP) {
			return new int[] {pos, Integer.MIN_VALUE};
		}
		return new int[] {pos + shift, Math.min(guide, screen - 1)};
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		guideX = Integer.MIN_VALUE;
		guideY = Integer.MIN_VALUE;
		if (dragging != null || resizing != null) {
			dragging = null;
			resizing = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		closeOnRelease = false; // Umschalt war ein Modifikator, kein Schließen
		if (scrollY == 0) {
			return false;
		}
		if (listOpen && inside(listFrame(), mouseX, mouseY)) {
			listScroll -= scrollY * ROW_H * 2;
			clampListScroll();
			return true;
		}
		if (inside(toolbarFrame(), mouseX, mouseY)) {
			stepHud(scrollY > 0 ? 1 : -1);
			return true;
		}
		HudModule hud = elementAt(mouseX, mouseY);
		if (hud == null) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		selected = hud;
		NumberSetting scale = hud.scale;
		double step = minecraft.hasShiftDown() ? 0.25 : 0.05;
		scale.set(scale.get() + (scrollY > 0 ? step : -step));
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (closeOnRelease && SalamiClient.get().clickGuiKey().matches(event)) {
			closeOnRelease = false;
			SalamiMenuScreen.closeAll(minecraft);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Die Menü-Taste (Standard Rechte Umschalttaste) ist hier auch Umschalt fürs Ziehen ohne Einrasten
		// und für größere Schritte: schließen erst beim Loslassen und nur, wenn dazwischen nichts passiert ist
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			closeOnRelease = dragging == null && resizing == null;
			return true;
		}
		closeOnRelease = false;
		int key = event.key();
		if (selected != null && selected.isEnabled()) {
			int step = minecraft.hasShiftDown() ? 10 : 1;
			int dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
			int dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
			if (dx != 0 || dy != 0) {
				Box b = box(selected);
				int nx = Math.max(0, Math.min(width - b.w(), b.x() + dx));
				int ny = Math.max(0, Math.min(height - b.h(), b.y() + dy));
				selected.moveTo(nx, ny, width, height);
				return true;
			}
			if (key == InputConstants.KEY_R) {
				selected.resetLayout();
				return true;
			}
			if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
				modules().setEnabled(selected, false);
				selected = null;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
