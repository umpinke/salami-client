package dev.salamiclient.gui.skin.werkbank;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static dev.salamiclient.gui.skin.werkbank.Werkbank.*;

/**
 * Startmenü als Inventar: ein Panel „Salami“ mit dem Stand, links das Logo im schwarzen Vorschaukasten
 * (wie die Spielerfigur im Inventar), rechts große Slots mit Items für Mods, HUD, Configs, Freunde,
 * Cosmetics und Minispiele, unten die Schnellschalter als Hotbar (grün = an, Taste als Stapelzahl).
 * Darunter Stein-Knöpfe „Panel-Ansicht“ und „Fertig“. Unter der Maus der lila Item-Tooltip.
 */
final class WerkbankMenuView implements MenuView {
	private static final int PAD = 7;
	private static final int TOP = 17;
	private static final int LOGO = 64;
	private static final int PREVIEW_W = LOGO + 6;
	private static final int BIG = 26;
	private static final int LABEL_H = 11;
	private static final int ROW_GAP = 3;
	private static final int COLUMNS = 3;
	private static final int BUTTON_H = 20;
	private static final int QUICK_SLOTS = 9;
	private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "icon.png");
	private static final int LOGO_TEXTURE_SIZE = 128;

	private final WerkbankSkin skin;

	WerkbankMenuView(WerkbankSkin skin) {
		this.skin = skin;
	}

	/**
	 * Plätze relativ zur linken oberen Ecke des Panels: Panel {w, h}, Vorschau, Slots der Einträge (ohne
	 * Panel-Ansicht), Hotbar, Knöpfe; {@code total} = Höhe samt Knöpfen.
	 */
	private record Layout(int width, int height, int total, int[] preview, List<Entry> grid, List<int[]> slots, int cellW,
		int quickLabelY, int quickX, List<int[]> quick, int[] panels, int[] done) {}

	/** Wo und wie groß das Menü gezeichnet wird (verkleinert, wenn es nicht passt). */
	private record Placement(float x, float y, float scale) {
		double localX(double x) {
			return (x - this.x) / scale;
		}

		double localY(double y) {
			return (y - this.y) / scale;
		}
	}

	private static List<Entry> gridEntries(Model model) {
		List<Entry> list = new ArrayList<>();
		for (Entry entry : model.entries()) {
			if (entry.kind() != Kind.PANELS) {
				list.add(entry);
			}
		}
		return list;
	}

	private Layout layout(Font font, Model model) {
		List<Entry> grid = gridEntries(model);
		int cellW = 44;
		for (Entry entry : grid) {
			cellW = Math.max(cellW, font.width(entry.label()) + 6);
		}
		cellW = Math.min(cellW, 60);
		// Hotbar (9 Slots) und obere Hälfte gleich breit: die Slots wachsen bis 26, die Zellen füllen den Rest
		boolean hotbar = !model.quick().isEmpty();
		int upperW = PREVIEW_W + 6 + COLUMNS * cellW;
		int quickSize = Math.max(18, Math.min(BIG, (upperW + QUICK_SLOTS - 1) / QUICK_SLOTS));
		int contentW = hotbar ? Math.max(upperW, QUICK_SLOTS * quickSize) : upperW;
		cellW = (contentW - PREVIEW_W - 6) / COLUMNS;
		int width = 2 * PAD + contentW;
		int rows = Math.max(1, (grid.size() + COLUMNS - 1) / COLUMNS);
		int gridH = rows * (BIG + LABEL_H) + (rows - 1) * ROW_GAP;
		int gridX = PAD + PREVIEW_W + 6;
		int[] preview = {PAD, TOP, PAD + PREVIEW_W, TOP + Math.max(gridH, LOGO + 6)};

		List<int[]> slots = new ArrayList<>();
		for (int i = 0; i < grid.size(); i++) {
			int col = i % COLUMNS;
			int row = i / COLUMNS;
			int x = gridX + col * cellW + (cellW - BIG) / 2;
			int y = TOP + row * (BIG + LABEL_H + ROW_GAP);
			slots.add(new int[] {x, y, x + BIG, y + BIG});
		}

		int y = preview[3];
		List<int[]> quick = new ArrayList<>();
		int quickLabelY = -1;
		int quickX = PAD;
		if (hotbar) {
			// immer 9 Slots wie die Hotbar; ohne Schalter bleiben sie leer
			quickX = PAD + (contentW - QUICK_SLOTS * quickSize) / 2;
			quickLabelY = y + 5;
			int top = quickLabelY + 11;
			for (int i = 0; i < QUICK_SLOTS; i++) {
				int x = quickX + i * quickSize;
				quick.add(new int[] {x, top, x + quickSize, top + quickSize});
			}
			y = top + quickSize;
		}
		int height = y + PAD;

		int buttonsY = height + 4;
		boolean hasPanels = model.entry(Kind.PANELS) != null;
		int[] panels = null;
		int[] done;
		if (hasPanels) {
			int half = (width - 4) / 2;
			panels = new int[] {0, buttonsY, half, buttonsY + BUTTON_H};
			done = new int[] {width - half, buttonsY, width, buttonsY + BUTTON_H};
		} else {
			done = new int[] {0, buttonsY, width, buttonsY + BUTTON_H};
		}
		return new Layout(width, height, buttonsY + BUTTON_H, preview, grid, slots, cellW, quickLabelY, quickX, quick, panels, done);
	}

	private static Placement place(Layout l, int uiWidth, int uiHeight) {
		float scale = Math.min(1f, Math.min((uiHeight - 8) / (float) l.total(), (uiWidth - 8) / (float) l.width()));
		float x = (uiWidth - l.width() * scale) / 2f;
		float y = (uiHeight - l.total() * scale) / 2f;
		if (scale >= 1f) {
			// ganze Pixel, damit Kanten und Schrift scharf bleiben
			x = Math.round(x);
			y = Math.round(y);
		}
		return new Placement(x, y, scale);
	}

	private static boolean inside(int[] r, double x, double y) {
		return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model);
		Placement p = place(l, uiWidth, uiHeight);
		int mx = (int) Math.floor(p.localX(mouseX));
		int my = (int) Math.floor(p.localY(mouseY));
		g.pose().pushMatrix();
		g.pose().translate(p.x(), p.y());
		if (p.scale() < 1f) {
			g.pose().scale(p.scale(), p.scale());
		}
		renderPanel(g, font, model, l, mx, my);
		g.pose().popMatrix();
	}

	private void renderPanel(GuiGraphics g, Font font, Model model, Layout l, int mx, int my) {
		dropShadow(g, 0, 0, l.width(), l.height());
		panel(g, 0, 0, l.width(), l.height());

		// Titel wie ein Container, rechts der Stand (wenn er passt)
		text(g, font, model.title(), PAD + 1, 6, TITLE, false);
		String status = model.status() == null ? "" : model.status();
		int statusX = l.width() - PAD - 1 - font.width(status);
		if (statusX > PAD + 1 + font.width(model.title()) + 8) {
			text(g, font, status, statusX, 6, DIM, false);
		}

		renderPreview(g, l.preview());

		for (int i = 0; i < l.grid().size(); i++) {
			renderEntry(g, font, l.grid().get(i), l.slots().get(i), l.cellW(), inside(l.slots().get(i), mx, my));
		}

		if (!l.quick().isEmpty()) {
			text(g, font, I18n.get("salami.werkbank.quick"), l.quickX() + 1, l.quickLabelY(), TITLE, false);
			for (int i = 0; i < l.quick().size(); i++) {
				int[] s = l.quick().get(i);
				if (i < model.quick().size()) {
					renderQuick(g, font, model.quick().get(i), s, inside(s, mx, my));
				} else {
					slot(g, s[0], s[1], s[2] - s[0]);
				}
			}
		}

		if (l.panels() != null) {
			Entry panels = model.entry(Kind.PANELS);
			renderButton(g, font, l.panels(), panels.label(), mx, my);
		}
		renderButton(g, font, l.done(), I18n.get("gui.done"), mx, my);
	}

	/** Schwarzer Kasten wie die Spielerfigur im Inventar, darin das Salami-Logo. */
	private static void renderPreview(GuiGraphics g, int[] r) {
		int w = r[2] - r[0];
		int h = r[3] - r[1];
		inset(g, r[0], r[1], w, h, BLACK);
		int x = r[0] + (w - LOGO) / 2;
		int y = r[1] + (h - LOGO) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 0f, 0f, LOGO, LOGO, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE,
			LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
	}

	private void renderEntry(GuiGraphics g, Font font, Entry entry, int[] s, int cellW, boolean hover) {
		slot(g, s[0], s[1], BIG);
		int itemX = s[0] + (BIG - 16) / 2;
		int itemY = s[1] + (BIG - 16) / 2;
		if (hover) {
			slotHoverBack(g, s[0], s[1], BIG);
		}
		WerkbankItems.draw(g, WerkbankItems.kind(entry.kind()), itemX, itemY);
		if (hover) {
			slotHoverFront(g, s[0], s[1], BIG);
		}
		if (entry.badge() > 0) {
			skin.badge(g, font, s[2] + 3, s[1] - 3, entry.badge() > 99 ? "99+" : String.valueOf(entry.badge()));
		}
		int cx = (s[0] + s[2]) / 2;
		TextFit.drawCentered(g, font, entry.label(), cx, s[3] + 2, cellW - 2, TITLE);
	}

	private static void renderQuick(GuiGraphics g, Font font, Quick quick, int[] s, boolean hover) {
		int size = s[2] - s[0];
		if (quick.on()) {
			slot(g, s[0], s[1], size, SLOT_ON);
		} else {
			slot(g, s[0], s[1], size);
		}
		int itemX = s[0] + (size - 16) / 2;
		int itemY = s[1] + (size - 16) / 2;
		if (hover) {
			slotHoverBack(g, s[0], s[1], size);
		}
		WerkbankItems.draw(g, WerkbankItems.module(quick.id()), itemX, itemY);
		if (quick.key() != null) {
			stackCount(g, font, quick.key(), itemX, itemY);
		}
		if (hover) {
			slotHoverFront(g, s[0], s[1], size);
		}
	}

	private void renderButton(GuiGraphics g, Font font, int[] r, String label, int mx, int my) {
		skin.button(g, font, r[0], r[1], r[2] - r[0], r[3] - r[1], label, Skin.ButtonKind.SECONDARY, Skin.UiState.of(inside(r, mx, my)));
	}

	// ------------------------------------------------------------------ Treffer und Tooltip

	private Layout localLayout(Model model) {
		return layout(Minecraft.getInstance().font, model);
	}

	@Override
	public Entry hit(Model model, int uiWidth, int uiHeight, double x, double y) {
		Layout l = localLayout(model);
		Placement p = place(l, uiWidth, uiHeight);
		double lx = p.localX(x);
		double ly = p.localY(y);
		for (int i = 0; i < l.slots().size(); i++) {
			if (inside(l.slots().get(i), lx, ly)) {
				return l.grid().get(i);
			}
		}
		if (inside(l.panels(), lx, ly)) {
			return model.entry(Kind.PANELS);
		}
		return null;
	}

	@Override
	public int hitQuick(Model model, int uiWidth, int uiHeight, double x, double y) {
		Layout l = localLayout(model);
		Placement p = place(l, uiWidth, uiHeight);
		double lx = p.localX(x);
		double ly = p.localY(y);
		for (int i = 0; i < l.quick().size() && i < model.quick().size(); i++) {
			if (inside(l.quick().get(i), lx, ly)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean hitClose(Model model, int uiWidth, int uiHeight, double x, double y) {
		Layout l = localLayout(model);
		Placement p = place(l, uiWidth, uiHeight);
		return inside(l.done(), p.localX(x), p.localY(y));
	}

	@Override
	public List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		Layout l = layout(font, model);
		Placement p = place(l, uiWidth, uiHeight);
		double lx = p.localX(mouseX);
		double ly = p.localY(mouseY);
		for (int i = 0; i < l.slots().size(); i++) {
			if (inside(l.slots().get(i), lx, ly)) {
				return entryTooltip(model, l.grid().get(i));
			}
		}
		for (int i = 0; i < l.quick().size() && i < model.quick().size(); i++) {
			if (inside(l.quick().get(i), lx, ly)) {
				return quickTooltip(model.quick().get(i));
			}
		}
		return null;
	}

	private static List<Component> entryTooltip(Model model, Entry entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(entry.label()).withStyle(ChatFormatting.YELLOW));
		String detail = entry.kind() == Kind.MODS ? model.status() : entry.detail();
		if (detail != null && !detail.isEmpty()) {
			lines.add(Component.literal(detail).withStyle(ChatFormatting.GRAY));
		}
		if (entry.badge() > 0) {
			lines.add(Component.translatable("salami.werkbank.unread", entry.badge()).withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("salami.werkbank.click_open").withStyle(ChatFormatting.BLUE));
		return lines;
	}

	private static List<Component> quickTooltip(Quick quick) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(quick.label()).withStyle(ChatFormatting.YELLOW));
		MutableComponent state = Component.translatable(quick.on() ? "salami.on" : "salami.off")
			.withStyle(quick.on() ? ChatFormatting.GREEN : ChatFormatting.RED);
		if (quick.key() != null) {
			state.append(Component.literal("  ")).append(Component.translatable("salami.werkbank.key", quick.key()).withStyle(ChatFormatting.GRAY));
		}
		lines.add(state);
		if (quick.description() != null && !quick.description().getString().isEmpty()) {
			lines.add(quick.description().copy().withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("salami.werkbank.click_toggle").withStyle(ChatFormatting.BLUE));
		if (quick.hasSettings()) {
			lines.add(Component.translatable("salami.werkbank.right_click_settings").withStyle(ChatFormatting.BLUE));
		}
		return lines;
	}
}
