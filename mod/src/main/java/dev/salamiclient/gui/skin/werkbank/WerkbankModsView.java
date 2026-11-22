package dev.salamiclient.gui.skin.werkbank;

import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.TextFit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.salamiclient.gui.skin.werkbank.Werkbank.*;

/**
 * Mod-Liste als Kreativinventar: oben die Reiter mit Items (Kategorien links, Werkzeuge rechts), im
 * Panel der Titel „Optik · 4 von 34 an“ und das schwarze Suchfeld, darunter die Einträge in einer
 * vertieften Fläche – je ein Slot mit Item, Name und Stein-Knopf „An“/„Aus“ (an: grün, aus: Item
 * ausgegraut) – und rechts der Rollbalken des Kreativinventars. Beschreibung und „Rechtsklick:
 * Einstellungen“ zeigt der lila Item-Tooltip.
 */
final class WerkbankModsView implements ModsView {
	private static final int MARGIN = 6;
	private static final int MAX_W = 500;
	private static final int MAX_ROWS = 11;
	private static final int PAD = 7;
	/** Oberkante der Liste im Panel (unter Titel und Suchfeld). */
	private static final int LIST_TOP = 19;
	private static final int ROW_H = 22;
	private static final int GAP = 2;
	private static final int COL_MIN = 150;
	private static final int TRACK_W = 14;
	private static final int SEARCH_H = 12;
	private static final int BUTTON_H = 14;

	private final WerkbankSkin skin;

	WerkbankModsView(WerkbankSkin skin) {
		this.skin = skin;
	}

	/**
	 * @param panel   Panel {x0, y0, x1, y1}
	 * @param tabs    Reiter der Kategorien
	 * @param tools   Reiter der Werkzeuge (leer, wenn sie nicht neben die Kategorien passen)
	 * @param list    vertiefte Fläche der Einträge
	 * @param rows    Innenbereich, in dem die Einträge scrollen
	 * @param track   Schiene des Rollbalkens
	 */
	private record Layout(int[] panel, List<int[]> tabs, List<int[]> tools, int[] search, int[] list, int[] rows, int[] track,
		int cols, int colW) {}

	private Layout layout(Font font, Model model, int w, int h) {
		int panelW = Math.min(w - 2 * MARGIN, MAX_W);
		// ganze Zeilen: so viele, wie unter die Reiter passen
		int avail = h - 2 * MARGIN - TAB_RISE;
		int fixed = LIST_TOP + 4 + PAD;
		int rowCount = Math.max(1, Math.min(MAX_ROWS, (avail - fixed + GAP) / (ROW_H + GAP)));
		int rowsH = rowCount * (ROW_H + GAP) - GAP;
		int panelH = fixed + rowsH;
		int x0 = (w - panelW) / 2;
		int top = Math.max(MARGIN, (h - TAB_RISE - panelH) / 2);
		int y0 = top + TAB_RISE;
		int[] panel = {x0, y0, x0 + panelW, y0 + panelH};

		List<int[]> tabs = new ArrayList<>();
		for (int i = 0; i < model.tabs().size(); i++) {
			int x = x0 + i * TAB_STEP;
			tabs.add(new int[] {x, y0 - TAB_RISE, x + TAB_W, y0});
		}
		List<int[]> tools = new ArrayList<>();
		int toolsLeft = x0 + panelW - model.tools().size() * TAB_STEP + 1;
		int tabsRight = x0 + model.tabs().size() * TAB_STEP;
		if (toolsLeft >= tabsRight + 4) {
			for (int i = 0; i < model.tools().size(); i++) {
				int x = toolsLeft + i * TAB_STEP;
				tools.add(new int[] {x, y0 - TAB_RISE, x + TAB_W, y0});
			}
		}

		int searchW = Math.max(70, Math.min(120, panelW / 4));
		int right = panel[2] - PAD;
		int[] search = {right - searchW, y0 + 4, right, y0 + 4 + SEARCH_H};

		int[] track = {right - TRACK_W, y0 + LIST_TOP, right, y0 + LIST_TOP + rowsH + 4};
		int[] list = {x0 + PAD, y0 + LIST_TOP, track[0] - 2, track[3]};
		int[] rows = {list[0] + 2, list[1] + 2, list[2] - 2, list[3] - 2};
		int innerW = rows[2] - rows[0];
		int cols = Math.max(1, Math.min(3, (innerW + GAP) / (COL_MIN + GAP)));
		int colW = (innerW - (cols - 1) * GAP) / cols;
		return new Layout(panel, tabs, tools, search, list, rows, track, cols, colW);
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Zelle des Eintrags {@code index} (mit Scrollstand). */
	private static int[] cell(Layout l, Model model, int index) {
		int col = index % l.cols();
		int row = index / l.cols();
		int x = l.rows()[0] + col * (l.colW() + GAP);
		int y = l.rows()[1] + row * (ROW_H + GAP) - (int) Math.round(model.scroll());
		return new int[] {x, y, x + l.colW(), y + ROW_H};
	}

	private static int itemAt(Layout l, Model model, double x, double y) {
		if (!inside(l.rows(), x, y)) {
			return -1;
		}
		int col = (int) ((x - l.rows()[0]) / (l.colW() + GAP));
		if (col >= l.cols() || x - l.rows()[0] - col * (l.colW() + GAP) >= l.colW()) {
			return -1;
		}
		double rel = y - l.rows()[1] + model.scroll();
		int row = (int) Math.floor(rel / (ROW_H + GAP));
		if (row < 0 || rel - row * (ROW_H + GAP) >= ROW_H) {
			return -1;
		}
		int index = row * l.cols() + col;
		return index < model.items().size() ? index : -1;
	}

	private static String onLabel() {
		return I18n.get("salami.on");
	}

	private static String offLabel() {
		return I18n.get("salami.off");
	}

	/** Breite des Knopfs eines Eintrags („An“/„Aus“ bzw. „Öffnen“ bei mitgelieferten Mods). */
	private static int buttonWidth(Font font, Item item) {
		if (!item.toggleable()) {
			return font.width(I18n.get("salami.mods.open")) + 10;
		}
		return Math.max(font.width(onLabel()), font.width(offLabel())) + 10;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		int[] p = l.panel();
		int active = model.activeTab();

		// Reiter hinter dem Panel, der gewählte davor (wie im Kreativinventar)
		for (int i = 0; i < l.tabs().size(); i++) {
			if (i != active) {
				renderTab(g, l.tabs().get(i), false, i == 0 ? 1 : 2, WerkbankItems.tab(model.tabs().get(i).id()));
			}
		}
		for (int i = 0; i < l.tools().size(); i++) {
			renderTab(g, l.tools().get(i), false, i == l.tools().size() - 1 ? 7 : 2, WerkbankItems.kind(model.tools().get(i).kind()));
		}
		dropShadow(g, p[0], p[1], p[2] - p[0], p[3] - p[1]);
		panel(g, p[0], p[1], p[2] - p[0], p[3] - p[1]);
		if (active >= 0 && active < l.tabs().size()) {
			renderTab(g, l.tabs().get(active), true, active == 0 ? 1 : 2, WerkbankItems.tab(model.tabs().get(active).id()));
		}

		// Titel „Optik · 4 von 34 an“ und Suchfeld
		int[] s = l.search();
		TextFit.draw(g, font, title(model), p[0] + PAD + 1, p[1] + 6, s[0] - 6 - (p[0] + PAD + 1), TITLE);
		renderSearch(g, font, model, s);

		// Einträge in der vertieften Fläche
		int[] list = l.list();
		inset(g, list[0], list[1], list[2] - list[0], list[3] - list[1], SLOT);
		int[] rows = l.rows();
		int hovered = itemAt(l, model, mouseX, mouseY);
		g.enableScissor(rows[0], rows[1], rows[2], rows[3]);
		for (int i = 0; i < model.items().size(); i++) {
			int[] c = cell(l, model, i);
			if (c[3] <= rows[1] || c[1] >= rows[3]) {
				continue;
			}
			renderItem(g, font, model.items().get(i), c, i == hovered);
		}
		g.disableScissor();
		if (model.items().isEmpty()) {
			TextFit.drawCentered(g, font, model.emptyText(), (rows[0] + rows[2]) / 2, rows[1] + 8, rows[2] - rows[0] - 8, WHITE, true);
		}
		renderScrollbar(g, font, model, l);
	}

	private static String title(Model model) {
		int active = model.activeTab();
		if (active < 0 || active >= model.tabs().size()) {
			return model.title();
		}
		Tab tab = model.tabs().get(active);
		if (tab.enabled() < 0) {
			return I18n.get("salami.werkbank.mods.title_count", tab.label(), tab.total());
		}
		return I18n.get("salami.werkbank.mods.title", tab.label(), tab.enabled(), tab.total());
	}

	private static void renderTab(GuiGraphics g, int[] r, boolean selected, int position, net.minecraft.world.item.Item icon) {
		sprite(g, tabSprite(selected, position), r[0], r[1], TAB_W, TAB_H);
		WerkbankItems.draw(g, icon, r[0] + 5, r[1] + 9);
	}

	private static void renderSearch(GuiGraphics g, Font font, Model model, int[] r) {
		boolean typed = !model.query().isEmpty();
		sprite(g, typed ? TEXT_FIELD_HIGHLIGHTED : TEXT_FIELD, r[0], r[1], r[2] - r[0], r[3] - r[1]);
		int textX = r[0] + 4;
		int textY = r[1] + 2;
		int room = r[2] - textX - 3;
		if (typed) {
			String shown = font.plainSubstrByWidth(model.query(), room - 6, true);
			text(g, font, shown, textX, textY, FIELD_TEXT, true);
			if ((System.currentTimeMillis() / 300) % 2 == 0) {
				text(g, font, "_", textX + font.width(shown), textY, FIELD_TEXT, true);
			}
		} else {
			TextFit.draw(g, font, model.searchHint(), textX, textY, room, FIELD_HINT, TextFit.MIN_SCALE, true);
		}
	}

	private void renderItem(GuiGraphics g, Font font, Item item, int[] c, boolean hover) {
		int x = c[0];
		int y = c[1];
		int w = c[2] - c[0];
		raised(g, x, y, w, ROW_H, hover ? 0xFFD8D8D8 : PANEL);
		slot(g, x + 2, y + 2, 18);
		int itemX = x + 3;
		int itemY = y + 3;
		WerkbankItems.draw(g, WerkbankItems.module(item.id()), itemX, itemY);
		boolean off = item.toggleable() && !item.on();
		if (off) {
			// ausgeschaltet: Item ausgegraut wie hinter Glas
			g.fill(itemX, itemY, itemX + 16, itemY + 16, 0x808B8B8B);
		}

		int buttonW = buttonWidth(font, item);
		int bx = c[2] - 3 - buttonW;
		int by = y + (ROW_H - BUTTON_H) / 2;
		String label;
		int tint;
		int textColor;
		if (!item.toggleable()) {
			label = I18n.get("salami.mods.open");
			tint = 0xFFFFFFFF;
			textColor = BUTTON_TEXT;
		} else if (item.on()) {
			label = onLabel();
			tint = TINT_GREEN;
			textColor = BUTTON_TEXT;
		} else {
			label = offLabel();
			tint = 0xFFFFFFFF;
			textColor = BUTTON_TEXT_OFF;
		}
		stone(g, bx, by, buttonW, BUTTON_H, tint, hover, false);
		TextFit.drawCentered(g, font, label, bx + buttonW / 2, by + 3, buttonW - 4, textColor, true);

		int nameX = x + 24;
		TextFit.draw(g, font, item.name(), nameX, y + 7, bx - 4 - nameX, off ? 0xFF686868 : TITLE);
	}

	private void renderScrollbar(GuiGraphics g, Font font, Model model, Layout l) {
		int[] t = l.track();
		inset(g, t[0], t[1], t[2] - t[0], t[3] - t[1], SLOT);
		int visible = l.rows()[3] - l.rows()[1];
		int total = contentHeight(model, l);
		int travel = t[3] - t[1] - 2 - 15;
		if (total <= visible) {
			sprite(g, SCROLLER_DISABLED, t[0] + 1, t[1] + 1, 12, 15);
			return;
		}
		int offset = (int) Math.round(travel * Math.max(0, Math.min(1, model.scroll() / (total - visible))));
		sprite(g, SCROLLER, t[0] + 1, t[1] + 1 + offset, 12, 15);
	}

	// ------------------------------------------------------------------ Treffer, Tooltip, Scrollen

	@Override
	public Hit hit(Font font, Model model, int uiWidth, int uiHeight, double x, double y) {
		Layout l = layout(font, model, uiWidth, uiHeight);
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
		if (inside(l.search(), x, y)) {
			return new Hit.SearchHit();
		}
		int index = itemAt(l, model, x, y);
		if (index >= 0) {
			return new Hit.ItemHit(index);
		}
		return Hit.NONE;
	}

	@Override
	public List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model, uiWidth, uiHeight);
		for (int i = 0; i < l.tabs().size(); i++) {
			if (inside(l.tabs().get(i), mouseX, mouseY)) {
				Tab tab = model.tabs().get(i);
				String count = tab.enabled() < 0 ? String.valueOf(tab.total())
					: I18n.get("salami.werkbank.tab_count", tab.enabled(), tab.total());
				return List.of(Component.literal(tab.label()), Component.literal(count).withStyle(ChatFormatting.GRAY));
			}
		}
		for (int i = 0; i < l.tools().size(); i++) {
			if (inside(l.tools().get(i), mouseX, mouseY)) {
				return List.of(Component.literal(model.tools().get(i).label()),
					Component.translatable("salami.werkbank.click_open").withStyle(ChatFormatting.BLUE));
			}
		}
		int index = itemAt(l, model, mouseX, mouseY);
		if (index < 0) {
			return null;
		}
		Item item = model.items().get(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(item.name()).withStyle(ChatFormatting.YELLOW));
		if (item.tag() != null) {
			lines.add(Component.literal(item.tag()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (item.description() != null && !item.description().getString().isEmpty()) {
			lines.add(item.description().copy().withStyle(ChatFormatting.GRAY));
		}
		if (item.toggleable()) {
			lines.add(Component.translatable("salami.werkbank.click_toggle").withStyle(ChatFormatting.BLUE));
			if (item.hasSettings()) {
				lines.add(Component.translatable("salami.werkbank.right_click_settings").withStyle(ChatFormatting.BLUE));
			}
		} else {
			lines.add(Component.translatable("salami.werkbank.click_settings").withStyle(ChatFormatting.BLUE));
		}
		return lines;
	}

	@Override
	public int[] scrollArea(Model model, int uiWidth, int uiHeight) {
		return layout(Minecraft.getInstance().font, model, uiWidth, uiHeight).rows().clone();
	}

	@Override
	public int contentHeight(Font font, Model model, int uiWidth, int uiHeight) {
		return contentHeight(model, layout(font, model, uiWidth, uiHeight));
	}

	private static int contentHeight(Model model, Layout l) {
		int rows = (model.items().size() + l.cols() - 1) / l.cols();
		return Math.max(0, rows * (ROW_H + GAP) - GAP);
	}
}
