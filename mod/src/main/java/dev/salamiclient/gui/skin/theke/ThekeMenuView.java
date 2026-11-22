package dev.salamiclient.gui.skin.theke;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

import static dev.salamiclient.gui.skin.theke.Theke.*;

/**
 * Startmenü an der Theke: unter der Markise hängt das Kreideschild „Salami“ mit dem Stand, darunter
 * die große Papierkarte zu den Mods (Stempel, „Öffnen ⏎“) und zwei Schienen mit je drei Kreidetafeln
 * (HUD, Configs, Panel-Ansicht · Freunde, Cosmetics, Minispiele). Unten die Holztheke mit dem
 * Tastenhinweis auf einem Preisschild.
 */
final class ThekeMenuView implements MenuView {
	private static final int CARD_W = 252;
	private static final int GAP = 9;
	private static final int COUNTER_H = 24;
	private static final int PER_ROW = 3;

	private final ThekeSkin skin;

	ThekeMenuView(ThekeSkin skin) {
		this.skin = skin;
	}

	/** Maße der vollen und der knappen Fassung. */
	private record Sizes(int sign, int signGap, int card, int railGap, int hang, int board, boolean compact) {
		int rowHeight() {
			return railGap + 3 + hang + board;
		}

		int block(int rows) {
			return sign + signGap + card + rows * rowHeight();
		}
	}

	private static final Sizes FULL = new Sizes(38, 5, 40, 7, 5, 38, false);
	private static final Sizes COMPACT = new Sizes(26, 4, 36, 4, 3, 32, true);

	/**
	 * Alle Plätze: Schild, Karte, „Öffnen“-Knopf, Schienen und Tafeln (Reihenfolge wie die Einträge ohne
	 * Mods), Oberkante der Theke ({@code counterTop == height}: keine Theke, nur das Schild).
	 */
	private record Layout(Sizes sizes, int[] sign, int[] card, int[] open, List<Integer> rails, int railX0, int railX1,
		List<int[]> boards, int counterTop) {}

	/**
	 * Maßstab, mit dem das Menü gezeichnet wird: 1, solange die knappe Fassung samt Theke passt; auf sehr
	 * kleinen Flächen (große Oberfläche im Modul „Oberfläche“) wird alles darunter verkleinert.
	 */
	private float fit(int w, int h) {
		int top = skin.backdropTop() + 3;
		int need = COMPACT.block(2) + 4 + COUNTER_H;
		return Math.min(1f, Math.min((h - top) / (float) need, w / (float) (CARD_W + 30)));
	}

	private Layout layout(Font font, Model model, int w, int h) {
		return layout(font, model, w, h, skin.backdropTop() + 3);
	}

	private Layout layout(Font font, Model model, int w, int h, int top) {
		int others = Math.max(0, model.entries().size() - 1);
		int rows = Math.max(1, (others + PER_ROW - 1) / PER_ROW);
		Sizes sizes = FULL;
		int counter = COUNTER_H;
		int available = h - counter - 4 - top;
		if (available < FULL.block(rows)) {
			sizes = COMPACT;
			if (available < COMPACT.block(rows)) {
				counter = 0;
				available = h - skin.tagHeight() - 6 - top;
			}
		}
		int extra = Math.max(0, available - sizes.block(rows));
		int cardW = Math.min(CARD_W, w - 24);
		int cardX = (w - cardW) / 2;

		String status = model.status() == null ? "" : model.status();
		int signW = Math.max(84, Math.max(headingWidth(font, model.title(), 2) + 24, sizes.compact() ? 0 : font.width(status) + 20));
		int signY = top + extra / 3;
		int[] sign = {(w - signW) / 2, signY, (w + signW) / 2, signY + sizes.sign()};

		int cardY = sign[3] + sizes.signGap() + extra / 3;
		int[] card = {cardX, cardY, cardX + cardW, cardY + sizes.card()};
		int openW = font.width(openLabel()) + 21;
		int openY = cardY + (sizes.card() - 15) / 2;
		int[] open = {card[2] - 10 - openW, openY, card[2] - 10, openY + 15};

		int boardW = (cardW - (PER_ROW - 1) * GAP) / PER_ROW;
		List<Integer> rails = new ArrayList<>();
		List<int[]> boards = new ArrayList<>();
		int y = card[3];
		for (int r = 0; r < rows; r++) {
			int railY = y + sizes.railGap();
			rails.add(railY);
			int boardY = railY + 3 + sizes.hang();
			for (int c = 0; c < PER_ROW && r * PER_ROW + c < others; c++) {
				int x = cardX + c * (boardW + GAP);
				// letzte Tafel schließt bündig mit der Karte ab
				int right = c == PER_ROW - 1 ? card[2] : x + boardW;
				boards.add(new int[] {x, boardY, right, boardY + sizes.board()});
			}
			y = boardY + sizes.board();
		}
		return new Layout(sizes, sign, card, open, rails, cardX - 7, card[2] + 7, boards, h - counter);
	}

	private static String openLabel() {
		return I18n.get("salami.mods.open");
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		float s = fit(uiWidth, uiHeight);
		if (s < 1f) {
			// verkleinert unter der Markise: oben bleibt die echte Höhe der Markise frei
			int top = skin.backdropTop() + 3;
			g.pose().pushMatrix();
			g.pose().translate(0, top);
			g.pose().scale(s, s);
			renderScaled(g, font, model, Math.round(uiWidth / s), Math.round((uiHeight - top) / s), Math.round(mouseX / s),
				Math.round((mouseY - top) / s), 0);
			g.pose().popMatrix();
			return;
		}
		renderScaled(g, font, model, uiWidth, uiHeight, mouseX, mouseY, skin.backdropTop() + 3);
	}

	private void renderScaled(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY, int top) {
		Layout l = layout(font, model, uiWidth, uiHeight, top);
		renderSign(g, font, model, l);
		renderCard(g, font, model, l, mouseX, mouseY);

		for (int railY : l.rails()) {
			rail(g, l.railX0(), l.railX1(), railY);
		}
		List<Entry> others = model.entries().subList(Math.min(1, model.entries().size()), model.entries().size());
		for (int i = 0; i < others.size() && i < l.boards().size(); i++) {
			int[] b = l.boards().get(i);
			int railY = l.rails().get(i / PER_ROW);
			renderBoard(g, font, others.get(i), b, railY, l.sizes(), inside(b, mouseX, mouseY));
		}

		renderCounter(g, font, model, l, uiWidth, uiHeight);
	}

	private void renderSign(GuiGraphics g, Font font, Model model, Layout l) {
		int[] s = l.sign();
		int w = s[2] - s[0];
		int stringTop = Math.min(AWNING_H - 2, s[1] - 1);
		string(g, s[0] + 12, stringTop, s[1] + 2);
		string(g, s[2] - 13, stringTop, s[1] + 2);
		board(g, s[0], s[1], w, s[3] - s[1]);
		int titleW = headingWidth(font, model.title(), 2);
		heading(g, font, model.title(), s[0] + (w - titleW) / 2f, s[1] + 5, CHALK_TEXT, 2);
		if (!l.sizes().compact() && model.status() != null) {
			TextFit.drawCentered(g, font, model.status(), (s[0] + s[2]) / 2, s[1] + 24, w - 12, CHALK_DIM);
		}
	}

	private void renderCard(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		int[] c = l.card();
		boolean hover = inside(c, mx, my);
		int w = c[2] - c[0];
		int h = c[3] - c[1];
		paper(g, c[0], c[1], w, h, SHEET, true);
		if (hover) {
			frame(g, c[0], c[1], w, h, RED_HI);
		}
		int stamp = 24;
		sprite(g, hover ? STAMP_PLAY_HOVER : STAMP_PLAY, c[0] + 9, c[1] + (h - stamp) / 2, stamp, stamp);

		Entry mods = model.entry(Kind.MODS);
		String label = mods != null ? mods.label() : "Mods";
		int textX = c[0] + 9 + stamp + 9;
		boolean compact = l.sizes().compact();
		heading(g, font, label, textX, c[1] + (compact ? 4 : 6), PEPPER, 2);
		int[] open = l.open();
		TextFit.draw(g, font, I18n.get("salami.theke.menu.mods_sub"), textX, c[1] + (compact ? 23 : 26), open[0] - 8 - textX, INK);

		Skin.UiState state = Skin.UiState.of(hover);
		skin.button(g, font, open[0], open[1], open[2] - open[0], open[3] - open[1], "", Skin.ButtonKind.PRIMARY, state);
		int ink = skin.buttonInk(Skin.ButtonKind.PRIMARY, state);
		String text = openLabel();
		GuiDraw.text(g, font, text, open[0] + 7, open[1] + 3, ink, false);
		Icons.enter(g, open[0] + 7 + font.width(text) + 4, open[1] + 4, ink);
	}

	private void renderBoard(GuiGraphics g, Font font, Entry entry, int[] b, int railY, Sizes sizes, boolean hover) {
		int lift = hover ? 1 : 0;
		int w = b[2] - b[0];
		int h = b[3] - b[1];
		int y = b[1] - lift;
		string(g, b[0] + 9, railY + 2, y + 2);
		string(g, b[2] - 10, railY + 2, y + 2);
		board(g, b[0], y, w, h);
		if (hover) {
			int t = boardFrame(w, h);
			frame(g, b[0] + t + 1, y + t + 1, w - 2 * t - 2, h - 2 * t - 2, 0x40F2C46D);
		}
		List<String> lines = TextFit.wrap(font, entry.label(), w - 12, 2);
		int textColor = hover ? CHALK_YELLOW : CHALK_TEXT;
		int cx = b[0] + w / 2;
		boolean icon = !(sizes.compact() && lines.size() > 1);
		if (icon) {
			int iconY = sizes.compact() ? y + 5 : lines.size() > 1 ? y + 4 : y + 7;
			Icons.draw(g, entry.kind(), cx - Icons.BIG / 2, iconY, CHALK_YELLOW);
		}
		int textY;
		if (!icon) {
			textY = y + 7;
		} else if (sizes.compact()) {
			textY = y + 19;
		} else {
			textY = lines.size() > 1 ? y + 17 : y + 22;
		}
		for (int i = 0; i < lines.size(); i++) {
			TextFit.drawCentered(g, font, lines.get(i), cx, textY + i * 9, w - 10, textColor);
		}
		if (entry.badge() > 0) {
			skin.badge(g, font, b[2] + 3, y - 4, entry.badge() > 99 ? "99+" : String.valueOf(entry.badge()));
		}
	}

	private void renderCounter(GuiGraphics g, Font font, Model model, Layout l, int w, int h) {
		String hint = model.hint() == null ? "" : model.hint();
		int tagW = tagWidth(font, hint);
		if (l.counterTop() >= h) {
			if (!hint.isEmpty()) {
				skin.tag(g, font, (w - tagW) / 2, h - TAG_H - 4, hint);
			}
			return;
		}
		int top = l.counterTop();
		g.fill(0, top - 3, w, top, 0x50000000);
		wood(g, 0, top, w, h - top);
		g.fill(0, top, w, top + 3, WOOD_LIGHT);
		g.fill(0, top, w, top + 1, 0xFF94664A);
		g.fill(0, top + 3, w, top + 4, WOOD_DARK);
		if (!hint.isEmpty()) {
			skin.tag(g, font, (w - tagW) / 2, top + 4 + (h - top - 4 - TAG_H) / 2, hint);
		}
	}

	// ------------------------------------------------------------------ Treffer

	@Override
	public Entry hit(Model model, int uiWidth, int uiHeight, double x, double y) {
		Font font = Minecraft.getInstance().font;
		float s = fit(uiWidth, uiHeight);
		Layout l;
		if (s < 1f) {
			int top = skin.backdropTop() + 3;
			l = layout(font, model, Math.round(uiWidth / s), Math.round((uiHeight - top) / s), 0);
			x = x / s;
			y = (y - top) / s;
		} else {
			l = layout(font, model, uiWidth, uiHeight);
		}
		if (inside(l.card(), x, y)) {
			return model.entry(Kind.MODS);
		}
		List<Entry> entries = model.entries();
		for (int i = 0; i + 1 < entries.size() && i < l.boards().size(); i++) {
			if (inside(l.boards().get(i), x, y)) {
				return entries.get(i + 1);
			}
		}
		return null;
	}
}
