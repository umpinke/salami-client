package dev.salamiclient.gui.skin.salumeria;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.salamiclient.gui.skin.salumeria.Salumeria.*;

/**
 * Mod-Liste als aufgeschlagene Speisekarte („Carta“) mit doppelter Linie: links das „Register“ – die
 * Kategorien mit römischen Ziffern und kursiven Zählern, die gewählte rot unterstrichen, darunter die
 * Werkzeuge –, rechts die gewählte Kategorie als Überschrift mit kursivem Untertitel und kursiver
 * Suchzeile. Die Einträge stehen in Spalten: rote gefüllte Raute = an, Umriss = aus, Name in der Antiqua,
 * darunter kursiv die Beschreibung – nur ganze Sätze; was nicht hineinpasst, steht im Tooltip.
 */
final class SalumeriaModsView implements ModsView {
	private static final int MAX_W = 456;
	private static final int COL_MIN = 118;
	private static final int COL_GAP = 14;
	private static final int SCROLLBAR = 3;
	private static final Serif.Type DESC = Serif.ITALIC.size(0.85f);
	private static final float DESC_LINE = 8.5f;
	/** Grundlinie des Namens unter der Oberkante der Zelle. */
	private static final float NAME_BASELINE = 10.5f;
	private static final int MAX_LINES = 4;

	private final SalumeriaSkin skin;
	/** Beschreibungen sind teuer zu messen: pro Text, Breite und Fassung nur einmal. */
	private final Map<String, Desc> descriptions = new HashMap<>();

	SalumeriaModsView(SalumeriaSkin skin) {
		this.skin = skin;
	}

	/** Fassungen der linken Seite, von großzügig bis knapp. */
	private record Register(boolean heading, boolean sub, int pitch, boolean tools) {
		int tabsTop() {
			return heading ? (sub ? 40 : 34) : 8;
		}
	}

	private static final Register[] REGISTERS = {
		new Register(true, true, 16, true),
		new Register(true, true, 14, true),
		new Register(true, false, 13, true),
		new Register(true, false, 12, true),
		new Register(false, false, 12, true),
		new Register(false, false, 12, false),
	};

	private record Layout(int[] card, int leftW, Register register, List<int[]> tabs, List<int[]> tools, float titleBaseline,
		float subBaseline, int[] close, int[] search, float ruleY, int[] list, int cols, int colW) {
		float divider() {
			return card[0] + leftW;
		}
	}

	private Layout layout(Font font, Model model, int w, int h) {
		int margin = h < 200 || w < 330 ? 4 : 10;
		int cardW = Math.min(w - 2 * margin, MAX_W);
		int x0 = (w - cardW) / 2;
		int y0 = margin;
		int y1 = h - margin;
		int[] card = {x0, y0, x0 + cardW, y1};
		int leftW = cardW < 340 ? 90 : 106;

		// linke Seite: Werkzeuge unten, Register darüber – bei wenig Platz enger, dann ohne Überschrift, ohne Werkzeuge
		int n = Math.max(1, model.tabs().size());
		Register register = REGISTERS[REGISTERS.length - 1];
		for (Register candidate : REGISTERS) {
			int toolsTop = candidate.tools() ? y1 - 6 - model.tools().size() * 12 - 4 : y1 - 4;
			if (y0 + candidate.tabsTop() + n * candidate.pitch() <= toolsTop) {
				register = candidate;
				break;
			}
		}
		List<int[]> tabs = new ArrayList<>();
		for (int i = 0; i < model.tabs().size(); i++) {
			int top = y0 + register.tabsTop() + i * register.pitch();
			tabs.add(new int[] {x0 + 6, top, x0 + leftW - 6, top + register.pitch()});
		}
		List<int[]> tools = new ArrayList<>();
		if (register.tools()) {
			int count = model.tools().size();
			for (int i = 0; i < count; i++) {
				int top = y1 - 6 - (count - i) * 12;
				tools.add(new int[] {x0 + 8, top, x0 + leftW - 6, top + 12});
			}
		}

		// rechte Seite: Überschrift und „Esc“, Untertitel und Suche, Linie, Einträge
		int rx0 = x0 + leftW + 13;
		int rx1 = card[2] - 12;
		float titleBaseline = y0 + 22;
		float subBaseline = y0 + 32;
		String esc = model.closeLabel();
		int escW = (int) Math.ceil(Serif.ITALIC.width(font, esc));
		int[] close = {rx1 - escW - 4, Math.round(titleBaseline) - 10, rx1 + 3, Math.round(titleBaseline) + 4};
		int searchW = Math.min(104, Math.round((rx1 - rx0) * 0.45f));
		int[] search = {rx1 - searchW, Math.round(subBaseline) - 9, rx1, Math.round(subBaseline) + 4};
		float ruleY = y0 + 38;
		int listTop = Math.round(ruleY) + 3;
		int bottom = Math.max(listTop + 20, y1 - 6);
		int[] list = {rx0, listTop, rx1 - SCROLLBAR - 4, bottom};
		int listW = list[2] - list[0];
		int cols = Math.max(1, Math.min(3, (listW + COL_GAP) / (COL_MIN + COL_GAP)));
		int colW = (listW - (cols - 1) * COL_GAP) / cols;
		return new Layout(card, leftW, register, tabs, tools, titleBaseline, subBaseline, close, search, ruleY, list, cols, colW);
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Zelle des Eintrags {@code index} {x0, y0, x1, y1} (mit Scrollstand). */
	private static int[] cell(Layout l, Model model, int[] tops, int index) {
		int col = index % l.cols();
		int row = index / l.cols();
		int x = l.list()[0] + col * (l.colW() + COL_GAP);
		int scroll = (int) Math.round(model.scroll());
		return new int[] {x, l.list()[1] + tops[row] - scroll, x + l.colW(), l.list()[1] + tops[row + 1] - scroll};
	}

	/** Zahnrad rechts neben dem Namen (Höhe der ersten Zeile). */
	private static int[] gearRect(int[] cell) {
		return new int[] {cell[2] - 11, cell[1], cell[2], cell[1] + 16};
	}

	private static int itemAt(Layout l, Model model, int[] tops, double x, double y) {
		if (!inside(l.list(), x, y)) {
			return -1;
		}
		int col = (int) ((x - l.list()[0]) / (l.colW() + COL_GAP));
		if (col >= l.cols() || x - l.list()[0] - col * (l.colW() + COL_GAP) >= l.colW()) {
			return -1;
		}
		double yy = y - l.list()[1] + model.scroll();
		for (int row = 0; row + 1 < tops.length; row++) {
			if (yy >= tops[row] && yy < tops[row + 1]) {
				int index = row * l.cols() + col;
				return index < model.items().size() ? index : -1;
			}
		}
		return -1;
	}

	private static String activeLabel(Model model) {
		return model.activeTab() >= 0 && model.activeTab() < model.tabs().size() ? model.tabs().get(model.activeTab()).label() : model.title();
	}

	private static String subtitle(Model model) {
		if (!model.query().isEmpty()) {
			return I18n.get("salami.salumeria.mods.results", model.items().size());
		}
		if (model.activeTab() < 0 || model.activeTab() >= model.tabs().size()) {
			return "";
		}
		Tab tab = model.tabs().get(model.activeTab());
		return tab.enabled() < 0 ? I18n.get("salami.salumeria.mods.bundled", tab.total())
			: I18n.get("salami.salumeria.mods.subtitle", tab.enabled(), tab.total());
	}

	// ------------------------------------------------------------------ Beschreibung

	/**
	 * Was von einer Beschreibung unter dem Namen steht: so viele ganze Sätze, wie in zwei Zeilen passen (ein langer
	 * erster Satz bis zu {@link #MAX_LINES}); {@code cut}: nicht alles – dann steht der ganze Text im Tooltip.
	 */
	private record Desc(List<String> lines, boolean cut) {}

	private static float textWidth(Layout l, Item item) {
		return l.colW() - 11 - (item.hasSettings() ? 12 : 2);
	}

	private Desc describe(Font font, Item item, float width) {
		String text = item.description() == null ? "" : item.description().getString().trim();
		if (text.isEmpty()) {
			return new Desc(List.of(), false);
		}
		String key = text + '\u0000' + Math.round(width) + '\u0000' + Serif.variant(Serif.layoutRatio() * DESC.scale());
		Desc cached = descriptions.get(key);
		if (cached != null) {
			return cached;
		}
		if (descriptions.size() > 600) {
			descriptions.clear();
		}
		Desc desc = describe(font, text, width);
		descriptions.put(key, desc);
		return desc;
	}

	private static Desc describe(Font font, String text, float width) {
		List<String> sentences = Serif.sentences(text);
		// so viele ganze Sätze, wie in zwei Zeilen passen …
		for (int k = sentences.size(); k >= 1; k--) {
			List<String> lines = wrapped(font, String.join(" ", sentences.subList(0, k)), width, 2);
			if (lines != null) {
				return new Desc(lines, k < sentences.size());
			}
		}
		// … ein langer erster Satz darf drei oder vier Zeilen haben
		for (int max = 3; max <= MAX_LINES; max++) {
			List<String> lines = wrapped(font, sentences.isEmpty() ? text : sentences.get(0), width, max);
			if (lines != null) {
				return new Desc(lines, sentences.size() > 1);
			}
		}
		// nichts halb zeigen: alles im Tooltip
		return new Desc(List.of(), true);
	}

	/** Zeilen von {@code text} in {@code width}, oder {@code null}, wenn es mehr als {@code maxLines} wären. */
	private static List<String> wrapped(Font font, String text, float width, int maxLines) {
		List<String> lines = Serif.wrap(font, DESC, text, width);
		if (lines.size() > maxLines) {
			return null;
		}
		for (String line : lines) {
			if (DESC.width(font, line) > width) {
				return null;
			}
		}
		return lines;
	}

	/** Höhe einer Zelle mit {@code lines} Zeilen Beschreibung. */
	private static int cellHeight(int lines) {
		return lines == 0 ? 17 : Math.round(NAME_BASELINE + 1 + lines * DESC_LINE + 4f);
	}

	/**
	 * Oberkanten der Reihen (relativ zur Liste, ohne Scrollstand); {@code tops[rows]} ist die Gesamthöhe. Eine Reihe
	 * ist so hoch wie ihr längster Eintrag.
	 */
	private int[] rowTops(Font font, Model model, Layout l) {
		int n = model.items().size();
		int rows = (n + l.cols() - 1) / l.cols();
		int[] tops = new int[rows + 1];
		for (int r = 0; r < rows; r++) {
			int h = cellHeight(0);
			for (int c = 0; c < l.cols(); c++) {
				int i = r * l.cols() + c;
				if (i < n) {
					Item item = model.items().get(i);
					h = Math.max(h, cellHeight(describe(font, item, textWidth(l, item)).lines().size()));
				}
			}
			tops[r + 1] = tops[r] + h;
		}
		return tops;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		int[] c = l.card();
		skin.surface(g, Skin.Surface.WINDOW, c[0], c[1], c[2] - c[0], c[3] - c[1]);
		vline(g, l.divider(), c[1] + 8, c[3] - 8, RULE_SOFT);
		renderRegister(g, font, model, l, mouseX, mouseY);
		renderPage(g, font, model, l, mouseX, mouseY);
	}

	private void renderRegister(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] c = l.card();
		float x = c[0] + 12;
		float right = l.divider() - 10;
		Register r = l.register();
		if (r.heading()) {
			Serif.fit(font, Serif.ROMAN.size(1.3f), I18n.get("salami.salumeria.mods.register"), right - x).draw(g, font, x, c[1] + 22, CREAM);
			if (r.sub()) {
				Serif.fit(font, Serif.ITALIC.size(0.9f), I18n.get("salami.salumeria.mods.register_sub"), right - x)
					.draw(g, font, x, c[1] + 31, CREAM_SOFT);
			}
		}
		for (int i = 0; i < l.tabs().size(); i++) {
			int[] t = l.tabs().get(i);
			Tab tab = model.tabs().get(i);
			boolean active = i == model.activeTab();
			boolean hover = !active && inside(t, mx, my);
			float baseline = t[1] + r.pitch() / 2f + 3.5f;
			if (hover) {
				g.fill(t[0], t[1] + 1, t[2], t[3] - 1, 0x0EF3E7D6);
			}
			Serif.ITALIC.draw(g, font, roman(i + 1), x, baseline, active ? RED_TEXT : CREAM_SOFT);
			String count = tab.count();
			float countW = Serif.ITALIC.width(font, count);
			Serif.ITALIC.size(0.95f).drawRight(g, font, count, right, baseline, active ? CREAM_DIM : CREAM_SOFT);
			float labelX = x + (l.leftW() < 100 ? 14 : 16);
			Serif.fit(font, Serif.CAPS, tab.label(), right - countW - 4 - labelX).draw(g, font, labelX, baseline,
				active || hover ? CREAM : 0xFFE6D8C5);
			if (active) {
				g.fill(Math.round(x - 1), Math.round(baseline + 3), Math.round(right + 1), Math.round(baseline + 4), RED);
			}
		}
		for (int i = 0; i < l.tools().size(); i++) {
			int[] t = l.tools().get(i);
			boolean hover = inside(t, mx, my);
			float baseline = t[1] + 9;
			Serif.Fitted label = Serif.fit(font, Serif.ITALIC, model.tools().get(i).label() + " ›", t[2] - t[0] - 6);
			label.draw(g, font, t[0] + 4, baseline, hover ? CREAM : CREAM_DIM);
			if (hover) {
				diamond(g, t[0] + 0.5f, baseline - 2.5f, 1.5f, RED_HI, true);
			}
		}
	}

	private void renderPage(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] list = l.list();
		float x0 = list[0];
		float x1 = l.card()[2] - 12;
		int[] close = l.close();
		boolean closeHover = inside(close, mx, my);
		Serif.ITALIC.drawRight(g, font, model.closeLabel(), x1, l.titleBaseline(), closeHover ? CREAM : CREAM_FAINT);
		if (closeHover) {
			diamond(g, close[0] - 1, l.titleBaseline() - 2.5f, 1.5f, RED_HI, true);
		}
		Serif.fit(font, Serif.ROMAN.size(1.55f), activeLabel(model), close[0] - 8 - x0).draw(g, font, x0, l.titleBaseline(), CREAM);

		int[] search = l.search();
		Serif.fit(font, Serif.ITALIC.size(0.95f), subtitle(model), search[0] - 10 - x0).draw(g, font, x0, l.subBaseline(), CREAM_SOFT);
		renderSearch(g, font, model, search, l.subBaseline());
		hline(g, x0, x1, l.ruleY(), RULE_SOFT);

		int[] tops = rowTops(font, model, l);
		int hovered = itemAt(l, model, tops, mx, my);
		g.enableScissor(list[0] - 3, list[1], list[2] + 2, list[3]);
		for (int i = 0; i < model.items().size(); i++) {
			int[] cell = cell(l, model, tops, i);
			if (cell[3] <= list[1] || cell[1] >= list[3]) {
				continue;
			}
			Item item = model.items().get(i);
			renderItem(g, font, item, cell, textWidth(l, item), i == hovered, i == hovered && inside(gearRect(cell), mx, my));
		}
		g.disableScissor();
		// angeschnittene Reihen oben und unten weich ausblenden, wie eine Karte, die weitergeht
		int total = tops[tops.length - 1];
		if (model.scroll() > 0.5) {
			g.fillGradient(list[0] - 3, list[1], list[2] + 2, list[1] + 8, BG, BG & 0x00FFFFFF);
		}
		if (total - model.scroll() > list[3] - list[1] + 0.5) {
			g.fillGradient(list[0] - 3, list[3] - 10, list[2] + 2, list[3], BG & 0x00FFFFFF, BG);
		}
		if (model.items().isEmpty()) {
			Serif.fit(font, Serif.ITALIC, model.emptyText(), list[2] - list[0]).drawCentered(g, font, (list[0] + list[2]) / 2f, list[1] + 24,
				CREAM_DIM);
		}
		renderScrollbar(g, model, l, tops, mx, my);
	}

	private void renderSearch(GuiGraphics g, Font font, Model model, int[] r, float baseline) {
		boolean typed = !model.query().isEmpty();
		Serif.Type type = Serif.ITALIC.size(0.95f);
		float room = r[2] - r[0] - 3;
		if (typed) {
			String shown = model.query();
			while (!shown.isEmpty() && type.width(font, shown) > room - 2) {
				shown = shown.substring(1);
			}
			type.draw(g, font, shown, r[0], baseline, CREAM);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				float cx = r[0] + type.width(font, shown) + 1;
				g.fill(Math.round(cx), Math.round(baseline - 7), Math.round(cx) + 1, Math.round(baseline + 2), CREAM);
			}
			g.fill(r[0], Math.round(baseline + 3), r[2], Math.round(baseline + 4), RED);
		} else {
			Serif.fit(font, type, model.searchHint(), room).draw(g, font, r[0], baseline, CREAM_FAINT);
			hline(g, r[0], r[2], baseline + 3, RULE_SOFT);
		}
	}

	private void renderItem(GuiGraphics g, Font font, Item item, int[] cell, float textW, boolean hover, boolean gearHover) {
		int x = cell[0];
		int y = cell[1];
		if (hover) {
			g.fill(x - 3, y, cell[2] + 1, cell[3] - 1, 0x0EF3E7D6);
		}
		Desc desc = describe(font, item, textW);
		float nameBaseline = y + NAME_BASELINE;
		float textX = x + 10;

		if (item.toggleable()) {
			SalumeriaSkin.toggleDiamond(g, x + 3.5f, nameBaseline - 3f, 2.75f, item.on(), hover && !gearHover, false);
		} else {
			diamond(g, x + 3.5f, nameBaseline - 3f, 1.5f, CREAM_FAINT, true);
		}
		float tagW = item.tag() == null ? 0 : DESC.width(font, item.tag()) + 5;
		Serif.Fitted name = Serif.fit(font, Serif.ROMAN, item.name(), textW - tagW);
		boolean bright = item.on() || hover;
		name.draw(g, font, textX, nameBaseline, bright ? CREAM : 0xFFD9CAB6);
		if (item.tag() != null) {
			DESC.draw(g, font, item.tag(), textX + name.width() + 5, nameBaseline, RED_TEXT);
		}
		for (int i = 0; i < desc.lines().size(); i++) {
			DESC.draw(g, font, desc.lines().get(i), textX, nameBaseline + 1f + (i + 1) * DESC_LINE, hover ? CREAM : CREAM_DIM);
		}
		if (item.hasSettings()) {
			int color = gearHover ? RED_TEXT : hover ? CREAM_DIM : 0x55BFAE9B;
			Icons.gear(g, cell[2] - 8, Math.round(nameBaseline - 6.5f), color);
		}
		hline(g, x, cell[2], cell[3] - 0.5f, RULE_FAINT);
	}

	private void renderScrollbar(GuiGraphics g, Model model, Layout l, int[] tops, int mx, int my) {
		int[] list = l.list();
		int visible = list[3] - list[1];
		int total = tops[tops.length - 1];
		if (total <= visible) {
			return;
		}
		int thumb = Math.max(14, visible * visible / total);
		int thumbY = (int) Math.round((visible - thumb) * (model.scroll() / (total - visible)));
		int x = list[2] + 4;
		skin.scrollbar(g, x, list[1], SCROLLBAR, visible, thumbY, thumb,
			Skin.UiState.of(mx >= x - 1 && mx < x + SCROLLBAR + 1 && my >= list[1] && my < list[3]));
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
		int[] tops = rowTops(font, model, l);
		int index = itemAt(l, model, tops, x, y);
		if (index >= 0) {
			Item item = model.items().get(index);
			if (item.hasSettings() && inside(gearRect(cell(l, model, tops, index)), x, y)) {
				return new Hit.SettingsHit(index);
			}
			return new Hit.ItemHit(index);
		}
		return Hit.NONE;
	}

	@Override
	public List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		int[] tops = rowTops(font, model, l);
		int index = itemAt(l, model, tops, mouseX, mouseY);
		if (index >= 0) {
			Item item = model.items().get(index);
			if (item.hasSettings() && inside(gearRect(cell(l, model, tops, index)), mouseX, mouseY)) {
				return List.of(Component.translatable("salami.mods.settings"));
			}
			if (describe(font, item, textWidth(l, item)).cut()) {
				return List.of(Component.literal(item.name()), item.description().copy().withStyle(s -> s.withColor(CREAM_DIM & 0xFFFFFF)));
			}
			return null;
		}
		if (inside(l.close(), mouseX, mouseY)) {
			return List.of(Component.translatable("gui.back"));
		}
		return null;
	}

	@Override
	public int[] scrollArea(Model model, int uiWidth, int uiHeight) {
		return layout(Minecraft.getInstance().font, model, uiWidth, uiHeight).list().clone();
	}

	@Override
	public int contentHeight(Font font, Model model, int uiWidth, int uiHeight) {
		int[] tops = rowTops(font, model, layout(font, model, uiWidth, uiHeight));
		return tops[tops.length - 1];
	}
}
