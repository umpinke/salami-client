package dev.salamiclient.gui.skin.salumeria;

import dev.salamiclient.gui.skin.MenuView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.salamiclient.gui.skin.salumeria.Salumeria.*;

/**
 * Startmenü als Etikett: mittig ein dunkles Schild mit doppelter Linie, oben das Salami-Logo, „Salami“
 * groß in der Antiqua, darunter kursiv der Stand („dieci di sessantanove – 10 von 69 Mods an“) und eine
 * Linie mit roter Raute. Die Einträge stehen wie Gänge einer Speisekarte untereinander: römische Ziffer,
 * Name in Kapitälchen, Punktlinie, kursive Angabe rechts („10 / 69“, „3 online“). Der Eintrag unter der
 * Maus – sonst die Mods, die Enter öffnet – bekommt eine rote Raute. Unten das Kleingedruckte.
 */
final class SalumeriaMenuView implements MenuView {
	private static final int CARD_W = 206;
	private static final int LOGO_SIZE = 22;
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private final SalumeriaSkin skin;

	SalumeriaMenuView(SalumeriaSkin skin) {
		this.skin = skin;
	}

	/** Fassung: mit Logo und Stand, Abstand der Einträge. */
	private record Sizes(boolean logo, boolean status, int pitch) {
		int height(int entries) {
			int h = 9;
			if (logo) {
				h += LOGO_SIZE + 4;
			}
			h += 13; // „Salami“
			if (status) {
				h += 11;
			}
			h += 9 + 6; // Linie, Abstand
			h += entries * pitch;
			h += 3 + 12 + 8; // Linie, Kleingedrucktes
			return h;
		}
	}

	private static final Sizes[] SIZES = {
		new Sizes(true, true, 15),
		new Sizes(false, true, 15),
		new Sizes(false, true, 13),
		new Sizes(false, false, 12),
	};

	/** Alle Plätze: Schild, Zeilen der Einträge (Grundlinien) und ihre Trefferflächen. */
	private record Layout(Sizes sizes, int[] card, float titleBaseline, float statusBaseline, float ruleTop, List<Float> baselines,
		List<int[]> rows, float ruleBottom, float hintBaseline) {}

	/** Maßstab: 1, solange die knappste Fassung passt; sonst wird das ganze Schild verkleinert. */
	private float fit(int w, int h, int entries) {
		Sizes smallest = SIZES[SIZES.length - 1];
		return Math.min(1f, Math.min((h - 8) / (float) smallest.height(entries), (w - 8) / (float) CARD_W));
	}

	private Layout layout(Model model, int w, int h) {
		int n = model.entries().size();
		Sizes sizes = SIZES[SIZES.length - 1];
		for (Sizes candidate : SIZES) {
			if (candidate.height(n) <= h - 12) {
				sizes = candidate;
				break;
			}
		}
		int cardH = sizes.height(n);
		int cardW = Math.min(CARD_W, w - 8);
		int x0 = (w - cardW) / 2;
		int y0 = Math.max(4, (h - cardH) / 2);
		int[] card = {x0, y0, x0 + cardW, y0 + cardH};

		float y = y0 + 9;
		if (sizes.logo()) {
			y += LOGO_SIZE + 4;
		}
		float titleBaseline = y + 12.5f;
		y += 13;
		float statusBaseline = titleBaseline;
		if (sizes.status()) {
			statusBaseline = y + 9f;
			y += 11;
		}
		float ruleTop = y + 5;
		y += 9 + 6;
		List<Float> baselines = new ArrayList<>();
		List<int[]> rows = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			float baseline = y + sizes.pitch() / 2f + 3.5f;
			baselines.add(baseline);
			rows.add(new int[] {x0 + 6, Math.round(y), x0 + cardW - 6, Math.round(y + sizes.pitch())});
			y += sizes.pitch();
		}
		float ruleBottom = y + 3;
		float hintBaseline = ruleBottom + 11;
		return new Layout(sizes, card, titleBaseline, statusBaseline, ruleTop, baselines, rows, ruleBottom, hintBaseline);
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** „dieci di sessantanove – 10 von 69 Mods an“; ohne zwei Zahlen im Stand nur der Stand. */
	static String status(String status) {
		if (status == null) {
			return "";
		}
		Matcher m = NUMBER.matcher(status);
		List<Integer> numbers = new ArrayList<>();
		while (m.find()) {
			try {
				numbers.add(Integer.parseInt(m.group()));
			} catch (NumberFormatException e) {
				return status;
			}
		}
		if (numbers.size() != 2) {
			return status;
		}
		return italian(numbers.get(0)) + " di " + italian(numbers.get(1)) + " – " + status;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		float s = fit(uiWidth, uiHeight, model.entries().size());
		if (s < 1f) {
			g.pose().pushMatrix();
			g.pose().scale(s, s);
			renderScaled(g, font, model, Math.round(uiWidth / s), Math.round(uiHeight / s), Math.round(mouseX / s), Math.round(mouseY / s));
			g.pose().popMatrix();
			return;
		}
		renderScaled(g, font, model, uiWidth, uiHeight, mouseX, mouseY);
	}

	private void renderScaled(GuiGraphics g, Font font, Model model, int w, int h, int mx, int my) {
		Layout l = layout(model, w, h);
		int[] c = l.card();
		int cw = c[2] - c[0];
		skin.surface(g, dev.salamiclient.gui.skin.Skin.Surface.WINDOW, c[0], c[1], cw, c[3] - c[1]);
		float cx = (c[0] + c[2]) / 2f;
		float inner = cw - 28;

		if (l.sizes().logo()) {
			sprite(g, LOGO, Math.round(cx - LOGO_SIZE / 2f), c[1] + 9, LOGO_SIZE, LOGO_SIZE);
		}
		Serif.Type title = Serif.ROMAN.size(1.85f);
		Serif.fit(font, title, model.title(), inner).drawCentered(g, font, cx, l.titleBaseline(), CREAM);
		if (l.sizes().status()) {
			Serif.Type italic = Serif.ITALIC.size(0.95f);
			String status = status(model.status());
			if (italic.width(font, status) > inner && model.status() != null) {
				status = model.status();
			}
			Serif.fit(font, italic, status, inner).drawCentered(g, font, cx, l.statusBaseline(), CREAM_DIM);
		}
		ruleWithDiamond(g, c[0] + 14, c[2] - 14, l.ruleTop(), RULE_SOFT, RED_HI);

		int hovered = -1;
		for (int i = 0; i < l.rows().size(); i++) {
			if (inside(l.rows().get(i), mx, my)) {
				hovered = i;
			}
		}
		// ohne Maus: die Mods (Enter öffnet sie)
		int marked = hovered >= 0 ? hovered : 0;
		for (int i = 0; i < model.entries().size(); i++) {
			renderEntry(g, font, model.entries().get(i), i, l.baselines().get(i), c, i == marked, i == hovered);
		}

		ruleWithDiamond(g, c[0] + 14, c[2] - 14, l.ruleBottom(), RULE_SOFT, RED_HI);
		if (model.hint() != null) {
			Serif.Type small = Serif.ITALIC.size(0.9f);
			Serif.fit(font, small, model.hint(), inner).drawCentered(g, font, cx, l.hintBaseline(), CREAM_FAINT);
		}
	}

	private void renderEntry(GuiGraphics g, Font font, Entry entry, int index, float baseline, int[] c, boolean marked, boolean hovered) {
		float left = c[0] + 14;
		float right = c[2] - 14;
		if (hovered) {
			g.fill(c[0] + 6, Math.round(baseline - 10.5f), c[2] - 6, Math.round(baseline + 4.5f), 0x0EF3E7D6);
		}
		if (marked) {
			diamond(g, left + 1.5f, baseline - 2.5f, 1.75f, RED_HI, true);
		}
		// römische Ziffer rechtsbündig in ihrer Spalte
		Serif.Type numeral = Serif.ITALIC;
		String roman = roman(index + 1);
		numeral.drawRight(g, font, roman, left + 24, baseline, marked ? RED_TEXT : CREAM_SOFT);

		float labelX = left + 31;
		String detail = entry.detail();
		Serif.Type detailType = Serif.ITALIC;
		float detailW = detail == null ? 0 : detailType.width(font, detail);
		Serif.Fitted label = Serif.fit(font, Serif.CAPS, entry.label(), right - labelX - (detail == null ? 6 : detailW + 10));
		label.draw(g, font, labelX, baseline, marked || hovered ? CREAM : 0xFFE6D8C5);
		float labelEnd = labelX + label.width();
		if (entry.badge() > 0) {
			String text = entry.badge() > 99 ? "99+" : String.valueOf(entry.badge());
			int bw = Math.max(9, font.width(text) + 5);
			skin.badge(g, font, Math.round(labelEnd + 4 + bw), Math.round(baseline - 8.5f), text);
			labelEnd += 4 + bw;
		}
		float leaderEnd = detail == null ? right : right - detailW - 5;
		leader(g, labelEnd + 5, leaderEnd, baseline - 0.5f, marked || hovered ? 0x99F3E7D6 : RULE_SOFT);
		if (detail != null) {
			detailType.drawRight(g, font, detail, right, baseline, marked ? CREAM : CREAM_DIM);
		}
	}

	// ------------------------------------------------------------------ Treffer

	@Override
	public Entry hit(Model model, int uiWidth, int uiHeight, double x, double y) {
		float s = fit(uiWidth, uiHeight, model.entries().size());
		int w = uiWidth;
		int h = uiHeight;
		if (s < 1f) {
			w = Math.round(uiWidth / s);
			h = Math.round(uiHeight / s);
			x /= s;
			y /= s;
		}
		Layout l = layout(model, w, h);
		for (int i = 0; i < l.rows().size(); i++) {
			if (inside(l.rows().get(i), x, y)) {
				return model.entries().get(i);
			}
		}
		return null;
	}
}
