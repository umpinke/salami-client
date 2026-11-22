package dev.salamiclient.gui.skin.theke;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

import static dev.salamiclient.gui.skin.theke.Theke.*;

/**
 * Mod-Liste an der Theke: links die Kreidetafel „Sorte:“ mit den Kategorien (gewählte rot) und unten
 * den Werkzeugen, rechts das Papier „Zutatenliste · Optik“ mit Suchzettel, „Esc“ und den Einträgen in
 * Spalten (Stempel, Name, Punktlinie, Zahnrad). Unten ein Notizzettel mit der vollen Beschreibung des
 * Eintrags unter der Maus (bzw. des zuletzt gezeigten).
 */
final class ThekeModsView implements ModsView {
	private static final int MARGIN = 5;
	private static final int SIDE_W = 100;
	private static final int SIDE_W_NARROW = 94;
	private static final int PAD = 8;
	private static final int ROW_H = 13;
	private static final int TAB_H = 14;
	private static final int TOOL_H = 12;
	private static final int NOTE_H = 44;
	private static final int COL_MIN = 135;
	private static final int COL_GAP = 10;
	private static final int SCROLLBAR = 4;
	private static final int SEARCH_MIN = 96;

	private final ThekeSkin skin;
	/** Name des zuletzt gezeigten Eintrags – bleibt im Notizzettel, bis die Maus einen anderen trifft. */
	private String noteName;

	ThekeModsView(ThekeSkin skin) {
		this.skin = skin;
	}

	private record Layout(int[] side, int[] paper, boolean heading, List<int[]> tabs, List<int[]> tools, int toolsTop, String title, float titleScale,
		int titleY, int[] search, int[] close, int ruleY, int[] list, int[] note, int cols, int colW) {}

	private Layout layout(Font font, Model model, int w, int h) {
		int top = skin.backdropTop() + 4;
		int bottom = h - MARGIN;
		int total = Math.min(w - 2 * MARGIN, 640);
		int left = (w - total) / 2;
		int sideW = total < 440 ? SIDE_W_NARROW : SIDE_W;
		int[] side = {left, top, left + sideW, bottom};
		int[] paper = {side[2] + 5, top, left + total, bottom};

		// Seitenleiste: Werkzeuge unten, Kategorien darüber. Bei wenig Platz erst engere Zeilen, dann ohne
		// „Sorte:“, zuletzt ohne Werkzeuge (die gibt es auch im Startmenü)
		int n = Math.max(1, model.tabs().size());
		int toolsTop = bottom - 7 - model.tools().size() * TOOL_H;
		boolean heading = true;
		boolean showTools = true;
		int tabsTop = top + 24;
		int tabH = TAB_H;
		if (toolsTop - 9 - tabsTop < n * TAB_H) {
			tabH = 12;
			if (toolsTop - 9 - tabsTop < n * tabH) {
				heading = false;
				tabsTop = top + 6;
				if (toolsTop - 9 - tabsTop < n * tabH) {
					showTools = false;
					toolsTop = bottom - 4;
				}
			}
		}
		List<int[]> tools = new ArrayList<>();
		for (int i = 0; showTools && i < model.tools().size(); i++) {
			int y = toolsTop + i * TOOL_H;
			tools.add(new int[] {side[0] + 4, y, side[2] - 4, y + TOOL_H});
		}
		List<int[]> tabs = new ArrayList<>();
		for (int i = 0; i < model.tabs().size(); i++) {
			int y = tabsTop + i * tabH;
			tabs.add(new int[] {side[0] + 4, y, side[2] - 4, y + tabH});
		}

		// Papier: Titel, Suchzettel und „Esc“ in einer Reihe, wenn es passt
		int px0 = paper[0] + PAD;
		int px1 = paper[2] - PAD;
		String active = model.activeTab() >= 0 && model.activeTab() < model.tabs().size() ? model.tabs().get(model.activeTab()).label() : "";
		String title = I18n.get("salami.theke.mods.title", active);
		int escW = font.width(model.closeLabel()) + 9;
		int[] close = {px1 - escW, top + 8, px1, top + 20};
		float scale = 1.5f;
		int searchX0 = px0 + headingWidth(font, title, scale) + 12;
		if (close[0] - 8 - searchX0 < SEARCH_MIN) {
			scale = 1f;
			searchX0 = px0 + headingWidth(font, title, scale) + 12;
		}
		int titleY = scale > 1 ? top + 8 : top + 10;
		int[] search;
		int ruleY;
		if (close[0] - 8 - searchX0 >= SEARCH_MIN) {
			search = new int[] {searchX0, top + 8, close[0] - 8, top + 21};
			ruleY = top + 26;
		} else {
			search = new int[] {px0, top + 24, px1, top + 37};
			ruleY = top + 41;
		}
		// auf kleinen Flächen ein flacherer Notizzettel (der Text wird dann kleiner), damit Einträge Platz haben
		int noteH = bottom - top < 190 ? 32 : NOTE_H;
		int[] note = {px0, bottom - PAD - noteH, px1, bottom - PAD};
		// nur ganze Zeilen: der Rest geht in den Abstand zum Notizzettel
		int listTop = ruleY + 5;
		int listH = Math.max(ROW_H, (note[1] - 4 - listTop) / ROW_H * ROW_H);
		int[] list = {px0, listTop, px1 - SCROLLBAR - 2, listTop + listH};
		int listW = list[2] - list[0];
		int cols = Math.max(1, Math.min(3, (listW + COL_GAP) / (COL_MIN + COL_GAP)));
		int colW = (listW - (cols - 1) * COL_GAP) / cols;
		return new Layout(side, paper, heading, tabs, tools, toolsTop, title, scale, titleY, search, close, ruleY, list, note, cols, colW);
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Zelle des Eintrags {@code index} {x0, y0, x1, y1} (mit Scrollstand). */
	private static int[] cell(Layout l, Model model, int index) {
		int col = index % l.cols();
		int row = index / l.cols();
		int x = l.list()[0] + col * (l.colW() + COL_GAP);
		int y = l.list()[1] + row * ROW_H - (int) Math.round(model.scroll());
		return new int[] {x, y, x + l.colW(), y + ROW_H};
	}

	private static int[] gearRect(int[] cell) {
		return new int[] {cell[2] - 12, cell[1], cell[2], cell[3]};
	}

	/** Eintrag unter (x, y) in der Liste oder -1. */
	private static int itemAt(Layout l, Model model, double x, double y) {
		if (!inside(l.list(), x, y)) {
			return -1;
		}
		int col = (int) ((x - l.list()[0]) / (l.colW() + COL_GAP));
		if (col >= l.cols() || x - l.list()[0] - col * (l.colW() + COL_GAP) >= l.colW()) {
			return -1;
		}
		int row = (int) Math.floor((y - l.list()[1] + model.scroll()) / ROW_H);
		int index = row * l.cols() + col;
		return row >= 0 && index < model.items().size() ? index : -1;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		renderSide(g, font, model, l, mouseX, mouseY);
		renderPaper(g, font, model, l, mouseX, mouseY);
	}

	private void renderSide(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] s = l.side();
		skin.surface(g, Skin.Surface.SIDE, s[0], s[1], s[2] - s[0], s[3] - s[1]);
		if (l.heading()) {
			heading(g, font, I18n.get("salami.theke.mods.kinds"), s[0] + 10, s[1] + 10, CHALK_TEXT, 1);
		}
		for (int i = 0; i < l.tabs().size(); i++) {
			int[] r = l.tabs().get(i);
			Tab tab = model.tabs().get(i);
			skin.tab(g, font, r[0], r[1], r[2] - r[0], r[3] - r[1], tab.label(), tab.count(), i == model.activeTab(), true,
				Skin.UiState.of(inside(r, mx, my)));
		}
		if (!l.tools().isEmpty()) {
			skin.divider(g, s[0] + 8, l.toolsTop() - 5, s[2] - s[0] - 16, Skin.Surface.SIDE);
		}
		for (int i = 0; i < l.tools().size(); i++) {
			int[] r = l.tools().get(i);
			boolean hover = inside(r, mx, my);
			int color = hover ? CHALK_YELLOW : CHALK_TEXT;
			int iconX = r[0] + 5;
			int iconY = r[1] + 2;
			toolIcon(g, model.tools().get(i).kind(), iconX, iconY, color);
			TextFit.draw(g, font, model.tools().get(i).label(), r[0] + 15, r[1] + 2, r[2] - r[0] - 18, color);
		}
	}

	/** Kleine Symbole (7 × 7) der Werkzeuge. */
	private static void toolIcon(GuiGraphics g, MenuView.Kind kind, int x, int y, int color) {
		switch (kind) {
			case HUD -> Icons.pencil(g, x, y, color);
			case CONFIGS -> {
				g.fill(x, y + 1, x + 7, y + 2, color);
				g.fill(x, y + 3, x + 7, y + 4, color);
				g.fill(x, y + 5, x + 7, y + 6, color);
			}
			case PANELS -> {
				g.fill(x, y, x + 3, y + 7, color);
				g.fill(x + 4, y, x + 7, y + 5, color);
			}
			default -> g.fill(x + 2, y + 2, x + 5, y + 5, color);
		}
	}

	private void renderPaper(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] p = l.paper();
		skin.surface(g, Skin.Surface.WINDOW, p[0], p[1], p[2] - p[0], p[3] - p[1]);
		int px0 = p[0] + PAD;
		int px1 = p[2] - PAD;
		heading(g, font, l.title(), px0, l.titleY(), PEPPER, l.titleScale());

		renderSearch(g, font, model, l.search());

		int[] c = l.close();
		boolean closeHover = inside(c, mx, my);
		box(g, c[0], c[1], c[2] - c[0], c[3] - c[1], closeHover ? RED : PEPPER);
		GuiDraw.text(g, font, model.closeLabel(), c[0] + 5, c[1] + 2, CREAM, false);

		g.fill(px0, l.ruleY(), px1, l.ruleY() + 1, PEPPER);

		int[] list = l.list();
		int hovered = itemAt(l, model, mx, my);
		g.enableScissor(list[0] - 2, list[1], list[2] + 2, list[3]);
		for (int i = 0; i < model.items().size(); i++) {
			int[] cell = cell(l, model, i);
			if (cell[3] <= list[1] || cell[1] >= list[3]) {
				continue;
			}
			renderItem(g, font, model.items().get(i), cell, i == hovered, i == hovered && inside(gearRect(cell), mx, my));
		}
		g.disableScissor();
		if (model.items().isEmpty()) {
			TextFit.drawCentered(g, font, model.emptyText(), (list[0] + list[2]) / 2, list[1] + 20, list[2] - list[0], INK);
		}
		renderScrollbar(g, font, model, l, mx, my);

		if (hovered >= 0) {
			noteName = model.items().get(hovered).name();
		}
		renderNote(g, font, model, l.note());
	}

	private void renderSearch(GuiGraphics g, Font font, Model model, int[] r) {
		boolean typed = !model.query().isEmpty();
		Icons.pencil(g, r[0], r[1] + 2, typed ? RED : INK);
		int textX = r[0] + 11;
		int room = r[2] - textX - 2;
		if (typed) {
			String shown = font.plainSubstrByWidth(model.query(), room - 2, true);
			GuiDraw.text(g, font, shown, textX, r[1] + 2, PEPPER, false);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = textX + font.width(shown) + 1;
				g.fill(cx, r[1] + 1, cx + 1, r[1] + 11, PEPPER);
			}
			g.fill(r[0], r[3] - 1, r[2], r[3], RED);
		} else {
			TextFit.draw(g, font, model.searchHint(), textX, r[1] + 2, room, INK_LIGHT);
			dotted(g, r[0], r[2], r[3] - 1, INK, 2);
		}
	}

	private void renderItem(GuiGraphics g, Font font, Item item, int[] cell, boolean hover, boolean gearHover) {
		int x = cell[0];
		int y = cell[1];
		int w = cell[2] - cell[0];
		skin.row(g, x - 3, y, w + 5, ROW_H, false, Skin.UiState.of(hover && !gearHover));
		if (item.toggleable()) {
			skin.toggle(g, x + 1, y + 1, item.on(), Skin.UiState.of(hover && !gearHover));
		} else {
			Icons.parcel(g, x + 3, y + 3, hover ? RED : INK_LIGHT);
		}
		int nameX = x + 15;
		int gearX = cell[2] - 9;
		int nameW = TextFit.draw(g, font, item.name(), nameX, y + 3, gearX - 3 - nameX, PEPPER);
		int leaderX0 = nameX + nameW + 3;
		int leaderX1 = gearX - 3;
		if (leaderX1 - leaderX0 > 4) {
			dotted(g, leaderX0 + (leaderX0 % 2), leaderX1, y + 9, INK_LIGHT, 2);
		}
		if (item.hasSettings()) {
			Icons.gear(g, gearX, y + 3, gearHover ? RED : INK);
		}
	}

	private void renderScrollbar(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] list = l.list();
		int visible = list[3] - list[1];
		int total = contentHeight(font, model, 0, 0, l);
		if (total <= visible) {
			return;
		}
		int thumb = Math.max(14, visible * visible / total);
		int thumbY = (int) Math.round((visible - thumb) * (model.scroll() / (total - visible)));
		int x = list[2] + 2;
		skin.scrollbar(g, x, list[1], SCROLLBAR, visible, thumbY, thumb,
			Skin.UiState.of(mx >= x && mx < x + SCROLLBAR && my >= list[1] && my < list[3]));
	}

	private Item noteItem(Model model) {
		if (noteName == null) {
			return null;
		}
		for (Item item : model.items()) {
			if (item.name().equals(noteName)) {
				return item;
			}
		}
		return null;
	}

	/** Text des Notizzettels: fetter Name, danach die Beschreibung – oder der Hinweis, wenn nichts gewählt ist. */
	private static Component noteText(Item item) {
		if (item == null) {
			return Component.translatable("salami.mods.hint").withStyle(Style.EMPTY.withColor(INK & 0xFFFFFF));
		}
		MutableComponent text = Component.literal(item.name()).withStyle(Style.EMPTY.withBold(true).withColor(PEPPER & 0xFFFFFF));
		boolean hasDescription = item.description() != null && !item.description().getString().isEmpty();
		if (item.tag() != null) {
			text.append(Component.literal("  " + item.tag()).withStyle(Style.EMPTY.withColor(RED & 0xFFFFFF)));
		}
		if (hasDescription) {
			text.append(Component.literal("  ").append(item.description().copy()).withStyle(Style.EMPTY.withColor(INK & 0xFFFFFF)));
		}
		return text;
	}

	private void renderNote(GuiGraphics g, Font font, Model model, int[] note) {
		skin.surface(g, Skin.Surface.CARD, note[0], note[1], note[2] - note[0], note[3] - note[1]);
		TextFit.Wrapped wrapped = TextFit.wrapBlock(font, noteText(noteItem(model)), note[2] - note[0] - 12, note[3] - note[1] - 9);
		int y = note[1] + (note[3] - note[1] - wrapped.height()) / 2 + 1;
		wrapped.draw(g, font, note[0] + 6, y, INK);
	}

	// ------------------------------------------------------------------ Treffer, Tooltip, Scrollen

	@Override
	public Hit hit(Font font, Model model, int uiWidth, int uiHeight, double x, double y) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		if (inside(l.close(), x, y)) {
			return new Hit.CloseHit();
		}
		if (inside(l.search(), x, y)) {
			return new Hit.SearchHit();
		}
		for (int i = 0; i < l.tabs().size(); i++) {
			if (inside(l.tabs().get(i), x, y)) {
				return new Hit.TabHit(i);
			}
		}
		for (int i = 0; i < l.tools().size(); i++) {
			if (inside(l.tools().get(i), x, y)) {
				return new Hit.ToolHit(i);
			}
		}
		int index = itemAt(l, model, x, y);
		if (index >= 0) {
			Item item = model.items().get(index);
			if (item.hasSettings() && inside(gearRect(cell(l, model, index)), x, y)) {
				return new Hit.SettingsHit(index);
			}
			return new Hit.ItemHit(index);
		}
		return Hit.NONE;
	}

	@Override
	public List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		int index = itemAt(l, model, mouseX, mouseY);
		if (index >= 0) {
			Item item = model.items().get(index);
			if (item.hasSettings() && inside(gearRect(cell(l, model, index)), mouseX, mouseY)) {
				return List.of(Component.translatable("salami.mods.settings"));
			}
			return null;
		}
		int[] note = l.note();
		if (inside(note, mouseX, mouseY)) {
			Component text = noteText(noteItem(model));
			if (TextFit.wrapBlock(font, text, note[2] - note[0] - 12, note[3] - note[1] - 9).cut()) {
				return List.of(text);
			}
		}
		if (inside(l.close(), mouseX, mouseY)) {
			return List.of(Component.translatable("gui.back"));
		}
		return null;
	}

	@Override
	public int[] scrollArea(Model model, int uiWidth, int uiHeight) {
		return layout(net.minecraft.client.Minecraft.getInstance().font, model, uiWidth, uiHeight).list().clone();
	}

	@Override
	public int contentHeight(Font font, Model model, int uiWidth, int uiHeight) {
		return contentHeight(font, model, uiWidth, uiHeight, layout(font, model, uiWidth, uiHeight));
	}

	private static int contentHeight(Font font, Model model, int uiWidth, int uiHeight, Layout l) {
		int rows = (model.items().size() + l.cols() - 1) / l.cols();
		return rows * ROW_H;
	}
}
