package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.Setting;
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
import java.util.Locale;

/**
 * Mods-Menü: alle Salami-Module als Karten – suchen, nach Kategorie filtern, per Klick
 * an/aus, über das Zahnrad (oder Rechtsklick) zu den Einstellungen.
 *
 * <p>Wie die ClickGUI in skalierten Koordinaten (Modul „Oberfläche“); Layout wird für
 * Zeichnen und Klicken aus derselben Berechnung abgeleitet.
 */
public final class ModsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int TABS = 18;
	private static final int CARD_W = 108;
	private static final int CARD_H = 58;
	private static final int GAP = 6;
	private static final int PAD = 8;

	/** Reiter für die mitgelieferten Mods (Wavey Capes, 3D Skin Layers …). */
	private static final Object EXTRAS = new Object();

	/** Gewählter Reiter bleibt während der Sitzung erhalten ({@code null} = alle, sonst Kategorie oder {@link #EXTRAS}). */
	private static Object lastTab;

	private final ModuleManager modules;
	private final Screen parent;
	private Object tab = lastTab;
	private String query = "";
	private double scroll;
	private float uiScale = 1f;

	/** Eine Karte zeigt entweder ein Salami-Modul oder eine mitgelieferte Mod. */
	private record Card(Module module, ExternalMods.Entry external, int x, int y) {}

	public ModsScreen(ModuleManager modules, Screen parent) {
		super(Component.translatable("salami.mods.title"));
		this.modules = modules;
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		clampScroll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		lastTab = tab;
		minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ Layout

	private int uiWidth() {
		return Math.round(width / uiScale);
	}

	private int uiHeight() {
		return Math.round(height / uiScale);
	}

	/** Fenster {x0, y0, x1, y1}. */
	private int[] frame() {
		int w = Math.min(uiWidth() - 16, CARD_W * 4 + GAP * 3 + PAD * 2 + 6);
		int h = Math.min(uiHeight() - 16, 320);
		int x0 = (uiWidth() - w) / 2;
		int y0 = (uiHeight() - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] content() {
		int[] f = frame();
		return new int[] {f[0] + PAD, f[1] + HEADER + TABS + 4, f[2] - PAD, f[3] - PAD};
	}

	private int[] searchBox() {
		int[] f = frame();
		return new int[] {f[2] - 22 - 120, f[1] + 5, f[2] - 22, f[1] + 19};
	}

	private int[] closeButton() {
		int[] f = frame();
		return new int[] {f[2] - 18, f[1] + 5, f[2] - 4, f[1] + 19};
	}

	/** Reiter: {@code null} (Alle), alle Kategorien und ggf. Extras, jeweils {key, x0, y0, x1, y1}. */
	private List<Object[]> tabs() {
		int[] f = frame();
		List<Object[]> result = new ArrayList<>();
		int x = f[0] + PAD;
		int y = f[1] + HEADER;
		List<Object> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(List.of(Category.values()));
		if (!ExternalMods.installed().isEmpty()) {
			entries.add(EXTRAS);
		}
		for (Object category : entries) {
			String label = tabLabel(category);
			int w = font.width(label) + 12;
			result.add(new Object[] {category, x, y, x + w, y + TABS - 2});
			x += w + 3;
		}
		return result;
	}

	private String tabLabel(Object tab) {
		if (tab == null) {
			return I18n.get("salami.mods.tab.all");
		}
		return tab instanceof Category category ? I18n.get(category.translationKey()) : I18n.get("salami.mods.tab.extras");
	}

	/** Sichtbare Einträge: {@link Module} und {@link ExternalMods.Entry} gemischt. */
	private List<Object> visibleEntries() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Object> list = new ArrayList<>();
		if (tab != EXTRAS) {
			for (Module module : modules.all()) {
				if (tab != null && module.category() != tab) {
					continue;
				}
				if (!q.isEmpty() && !matches(module, q)) {
					continue;
				}
				list.add(module);
			}
		}
		if (tab == null || tab == EXTRAS) {
			for (ExternalMods.Entry entry : ExternalMods.installed()) {
				if (q.isEmpty() || I18n.get(entry.nameKey()).toLowerCase(Locale.ROOT).contains(q) || entry.modId().contains(q)) {
					list.add(entry);
				}
			}
		}
		return list;
	}

	private static boolean matches(Module module, String q) {
		if (I18n.get(module.translationKey()).toLowerCase(Locale.ROOT).contains(q) || module.id().contains(q)) {
			return true;
		}
		return Language.getInstance().has(module.descriptionKey()) && I18n.get(module.descriptionKey()).toLowerCase(Locale.ROOT).contains(q);
	}

	private int columns() {
		int[] c = content();
		return Math.max(1, (c[2] - c[0] - 6 + GAP) / (CARD_W + GAP));
	}

	private List<Card> cards() {
		int[] c = content();
		int cols = columns();
		int gridWidth = cols * CARD_W + (cols - 1) * GAP;
		int left = c[0] + (c[2] - c[0] - 6 - gridWidth) / 2;
		List<Card> cards = new ArrayList<>();
		List<Object> list = visibleEntries();
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int x = left + col * (CARD_W + GAP);
			int y = c[1] + row * (CARD_H + GAP) - (int) Math.round(scroll);
			Object entry = list.get(i);
			cards.add(entry instanceof Module module ? new Card(module, null, x, y) : new Card(null, (ExternalMods.Entry) entry, x, y));
		}
		return cards;
	}

	private int contentHeight() {
		int rows = (visibleEntries().size() + columns() - 1) / columns();
		return Math.max(0, rows * (CARD_H + GAP) - GAP);
	}

	private void clampScroll() {
		int[] c = content();
		int max = Math.max(0, contentHeight() - (c[3] - c[1]));
		scroll = Math.max(0, Math.min(scroll, max));
	}

	/** Zahnrad-Feld einer Karte {x0, y0, x1, y1}. */
	private static int[] gear(Card card) {
		return new int[] {card.x() + CARD_W - 18, card.y() + CARD_H - 16, card.x() + CARD_W - 4, card.y() + CARD_H - 4};
	}

	private static boolean hasSettings(Module module) {
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				return true;
			}
		}
		return false;
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
		GuiDraw.text(g, font, I18n.get("salami.mods.title"), f[0] + PAD, f[1] + 8, theme.headerText(), false);

		renderSearch(g, theme, mx, my);
		int[] close = closeButton();
		boolean closeHover = inside(close, mx, my);
		g.fill(close[0], close[1], close[2], close[3], closeHover ? theme.accent() : theme.panel());
		GuiDraw.text(g, font, "x", close[0] + 5, close[1] + 3, theme.headerText(), false);

		for (Object[] t : tabs()) {
			Object key = t[0];
			int[] r = {(int) t[1], (int) t[2], (int) t[3], (int) t[4]};
			boolean selected = key == tab;
			boolean hovered = inside(r, mx, my);
			g.fill(r[0], r[1], r[2], r[3], selected ? theme.accent() : hovered ? theme.hover() : theme.header());
			int color = selected ? 0xFF000000 : theme.text();
			GuiDraw.text(g, font, tabLabel(key), r[0] + 6, r[1] + 4, color, false);
			if (key instanceof Category category && category.isRestricted()) {
				GuiDraw.text(g, font, "!", r[2] - 5, r[1] + 4, theme.warning(), false);
				if (hovered) {
					tooltip = Component.translatable("salami.category.utility.warning");
				}
			}
		}

		int[] c = content();
		g.enableScissor(c[0], c[1], c[2], c[3]);
		List<Card> cards = cards();
		boolean overContent = inside(c, mx, my);
		for (Card card : cards) {
			if (card.y() + CARD_H < c[1] || card.y() > c[3]) {
				continue;
			}
			boolean hovered = overContent && mx >= card.x() && mx < card.x() + CARD_W && my >= card.y() && my < card.y() + CARD_H;
			renderCard(g, theme, card, hovered, mx, my);
			if (hovered) {
				tooltip = cardTooltip(card, mx, my);
			}
		}
		g.disableScissor();
		if (cards.isEmpty()) {
			GuiDraw.centeredText(g, font, I18n.get("salami.mods.empty"), (c[0] + c[2]) / 2, c[1] + 20, theme.textDim());
		}
		renderScrollbar(g, theme, c);

		g.pose().popMatrix();
		if (tooltip != null) {
			g.setTooltipForNextFrame(font.split(tooltip, 220), mouseX, mouseY);
		}
	}

	private void renderSearch(GuiGraphics g, Theme theme, int mx, int my) {
		int[] r = searchBox();
		g.fill(r[0], r[1], r[2], r[3], 0xFF0B0B0E);
		GuiDraw.outline(g, r[0], r[1], r[2] - r[0], r[3] - r[1], query.isEmpty() ? theme.outline() : theme.accent());
		if (query.isEmpty()) {
			GuiDraw.text(g, font, I18n.get("salami.mods.search"), r[0] + 4, r[1] + 3, theme.textDim(), false);
		} else {
			String shown = font.plainSubstrByWidth(query, r[2] - r[0] - 12, true);
			GuiDraw.text(g, font, shown, r[0] + 4, r[1] + 3, theme.text(), false);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = r[0] + 4 + font.width(shown);
				g.fill(cx, r[1] + 3, cx + 1, r[1] + 12, theme.text());
			}
		}
	}

	private void renderCard(GuiGraphics g, Theme theme, Card card, boolean hovered, int mx, int my) {
		if (card.external() != null) {
			renderExternalCard(g, theme, card, hovered);
			return;
		}
		Module module = card.module();
		int x = card.x();
		int y = card.y();
		boolean on = module.isEnabled();
		g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF101014);
		if (on) {
			g.fill(x, y, x + CARD_W, y + CARD_H, theme.enabledRow());
		}
		if (hovered) {
			g.fill(x, y, x + CARD_W, y + CARD_H, theme.hover());
		}
		GuiDraw.outline(g, x, y, CARD_W, CARD_H, hovered || on ? theme.accent() : theme.outline());

		String name = font.plainSubstrByWidth(I18n.get(module.translationKey()), CARD_W - 16);
		GuiDraw.text(g, font, name, x + 6, y + 6, on ? theme.accent() : theme.text(), false);
		if (module.category().isRestricted()) {
			GuiDraw.text(g, font, "!", x + CARD_W - 9, y + 6, theme.warning(), false);
		}

		if (Language.getInstance().has(module.descriptionKey())) {
			List<FormattedCharSequence> lines = font.split(Component.translatable(module.descriptionKey()), (int) ((CARD_W - 12) / 0.75f));
			g.pose().pushMatrix();
			g.pose().translate(x + 6, y + 18);
			g.pose().scale(0.75f, 0.75f);
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				GuiDraw.text(g, font, lines.get(i), 0, i * 10, theme.textDim(), false);
			}
			g.pose().popMatrix();
		}

		// An/Aus-Schalter
		int px = x + 6;
		int py = y + CARD_H - 15;
		g.fill(px, py, px + 22, py + 10, on ? theme.accent() : 0xFF3A3A44);
		int knob = on ? px + 13 : px + 1;
		g.fill(knob, py + 1, knob + 8, py + 9, on ? 0xFF1A1A1F : 0xFFF3F3F3);
		GuiDraw.text(g, font, I18n.get(on ? "salami.on" : "salami.off"), px + 26, py + 1, on ? theme.accent() : theme.textDim(), false);

		if (hasSettings(module)) {
			int[] gr = gear(card);
			boolean gearHover = inside(gr, mx, my);
			if (gearHover) {
				g.fill(gr[0], gr[1], gr[2], gr[3], theme.hover());
			}
			GuiDraw.text(g, font, "⚙", gr[0] + 3, gr[1] + 2, gearHover ? theme.accent() : theme.textDim(), false);
		}
	}

	/** Karte einer mitgelieferten Mod: kein An/Aus, ein Klick öffnet ihre Einstellungen. */
	private void renderExternalCard(GuiGraphics g, Theme theme, Card card, boolean hovered) {
		ExternalMods.Entry entry = card.external();
		int x = card.x();
		int y = card.y();
		g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF101014);
		if (hovered) {
			g.fill(x, y, x + CARD_W, y + CARD_H, theme.hover());
		}
		GuiDraw.outline(g, x, y, CARD_W, CARD_H, hovered ? theme.accent() : theme.outline());
		GuiDraw.text(g, font, font.plainSubstrByWidth(I18n.get(entry.nameKey()), CARD_W - 12), x + 6, y + 6, theme.text(), false);
		if (Language.getInstance().has(entry.descriptionKey())) {
			List<FormattedCharSequence> lines = font.split(Component.translatable(entry.descriptionKey()), (int) ((CARD_W - 12) / 0.75f));
			g.pose().pushMatrix();
			g.pose().translate(x + 6, y + 18);
			g.pose().scale(0.75f, 0.75f);
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				GuiDraw.text(g, font, lines.get(i), 0, i * 10, theme.textDim(), false);
			}
			g.pose().popMatrix();
		}
		String open = I18n.get("salami.mods.open_settings");
		GuiDraw.text(g, font, open, x + 6, y + CARD_H - 14, hovered ? theme.accent() : theme.textDim(), false);
	}

	private Component cardTooltip(Card card, int mx, int my) {
		if (card.external() != null) {
			return null;
		}
		if (hasSettings(card.module()) && inside(gear(card), mx, my)) {
			return Component.translatable("salami.mods.settings");
		}
		String key = card.module().descriptionKey();
		if (Language.getInstance().has(key) && font.split(Component.translatable(key), (int) ((CARD_W - 12) / 0.75f)).size() > 3) {
			return Component.translatable(key);
		}
		return null;
	}

	private void renderScrollbar(GuiGraphics g, Theme theme, int[] c) {
		int total = contentHeight();
		int visible = c[3] - c[1];
		if (total <= visible) {
			return;
		}
		int barHeight = Math.max(16, visible * visible / total);
		int barY = c[1] + (int) ((visible - barHeight) * (scroll / (total - visible)));
		g.fill(c[2] - 3, c[1], c[2] - 1, c[3], theme.outline());
		g.fill(c[2] - 3, barY, c[2] - 1, barY + barHeight, theme.accent());
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		int button = event.button();

		if (inside(closeButton(), x, y)) {
			onClose();
			return true;
		}
		for (Object[] t : tabs()) {
			if (inside(new int[] {(int) t[1], (int) t[2], (int) t[3], (int) t[4]}, x, y)) {
				tab = t[0];
				scroll = 0;
				return true;
			}
		}
		if (inside(searchBox(), x, y) && button == InputConstants.MOUSE_BUTTON_RIGHT) {
			query = "";
			return true;
		}
		if (inside(content(), x, y)) {
			for (Card card : cards()) {
				if (x < card.x() || x >= card.x() + CARD_W || y < card.y() || y >= card.y() + CARD_H) {
					continue;
				}
				if (card.external() != null) {
					Screen settings = ExternalMods.configScreen(card.external(), this);
					if (settings != null) {
						minecraft.setScreen(settings);
					}
					return true;
				}
				Module module = card.module();
				boolean wantsSettings = button == InputConstants.MOUSE_BUTTON_RIGHT || inside(gear(card), x, y);
				if (wantsSettings && hasSettings(module)) {
					minecraft.setScreen(new ModSettingsScreen(modules, module, this));
				} else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					modules.toggle(module);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * (CARD_H + GAP) / 2.0;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			scroll = 0;
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE && !query.isEmpty()) {
			query = "";
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter() && query.length() < 32) {
			query += event.codepointAsString();
			scroll = 0;
			return true;
		}
		return super.charTyped(event);
	}
}
