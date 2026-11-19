package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
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
 * Salami-Menü (Rechte Umschalttaste): ein Fenster in der Bildschirmmitte – links die Kategorien
 * mit „an/alle“-Zähler und die Werkzeuge (HUD, Configs, Panel-Ansicht), rechts Suche und alle
 * Module als Karten. Klick schaltet an/aus, Zahnrad oder Rechtsklick öffnet die Einstellungen.
 *
 * <p>Rechnet wie die Panel-Ansicht in skalierten Koordinaten (Modul „Oberfläche“); Layout wird
 * für Zeichnen und Klicken aus derselben Berechnung abgeleitet.
 */
public final class ModsScreen extends Screen {
	private static final int HEADER = 26;
	private static final int SIDEBAR = 96;
	private static final int ENTRY_H = 16;
	private static final int ACTION_H = 15;
	private static final int CARD_W = 108;
	private static final int CARD_H = 58;
	private static final int GAP = 6;
	private static final int PAD = 8;

	/** Reiter für die mitgelieferten Mods (Wavey Capes, 3D Skin Layers …). */
	private static final Object EXTRAS = new Object();

	/** Gewählter Reiter bleibt während der Sitzung erhalten ({@code null} = alle, sonst Kategorie oder {@link #EXTRAS}). */
	private static Object lastTab;

	/** Werkzeuge unten in der Seitenleiste. */
	private enum Action {
		HUD("salami.clickgui.hud_editor"),
		CONFIGS("salami.clickgui.configs"),
		PANELS("salami.mods.panels");

		final String key;

		Action(String key) {
			this.key = key;
		}
	}

	private final ModuleManager modules;
	private final Screen parent;
	private Object tab = lastTab;
	private String query = "";
	private double scroll;
	private float uiScale = 1f;

	/** Eine Karte zeigt entweder ein Salami-Modul oder eine mitgelieferte Mod. */
	private record Card(Module module, ExternalMods.Entry external, int x, int y, int index) {}

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
		int w = Math.min(uiWidth() - 16, SIDEBAR + CARD_W * 4 + GAP * 3 + PAD * 2 + 6);
		int h = Math.min(uiHeight() - 16, 330);
		int x0 = (uiWidth() - w) / 2;
		int y0 = (uiHeight() - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] content() {
		int[] f = frame();
		return new int[] {f[0] + SIDEBAR + PAD, f[1] + HEADER + 2, f[2] - PAD, f[3] - PAD};
	}

	private int[] searchBox() {
		int[] f = frame();
		return new int[] {f[0] + SIDEBAR + PAD, f[1] + 6, f[2] - 26, f[1] + 20};
	}

	private int[] closeButton() {
		int[] f = frame();
		return new int[] {f[2] - 22, f[1] + 6, f[2] - PAD, f[1] + 20};
	}

	/** Kategorien in der Seitenleiste: {@code null} (Alle), alle Kategorien und ggf. Extras, jeweils {key, x0, y0, x1, y1}. */
	private List<Object[]> tabs() {
		int[] f = frame();
		List<Object[]> result = new ArrayList<>();
		int y = f[1] + HEADER + 2;
		List<Object> entries = new ArrayList<>();
		entries.add(null);
		for (Category category : Category.values()) {
			if (!modules.byCategory(category).isEmpty()) {
				entries.add(category);
			}
		}
		if (!ExternalMods.installed().isEmpty()) {
			entries.add(EXTRAS);
		}
		for (Object category : entries) {
			result.add(new Object[] {category, f[0] + 1, y, f[0] + SIDEBAR, y + ENTRY_H});
			y += ENTRY_H;
		}
		return result;
	}

	/** Feld eines Werkzeugs unten in der Seitenleiste {x0, y0, x1, y1}. */
	private int[] actionRect(Action action) {
		int[] f = frame();
		int bottom = f[3] - 5 - (Action.values().length - 1 - action.ordinal()) * ACTION_H;
		return new int[] {f[0] + 1, bottom - ACTION_H, f[0] + SIDEBAR, bottom};
	}

	private String tabLabel(Object tab) {
		if (tab == null) {
			return I18n.get("salami.mods.tab.all");
		}
		return tab instanceof Category category ? I18n.get(category.translationKey()) : I18n.get("salami.mods.tab.extras");
	}

	/** „3/12“: eingeschaltete von allen Modulen eines Eintrags; bei den Extras nur die Anzahl. */
	private String tabCount(Object tab) {
		if (tab == EXTRAS) {
			return String.valueOf(ExternalMods.installed().size());
		}
		int total = 0;
		int enabled = 0;
		for (Module module : modules.all()) {
			if (tab != null && module.category() != tab) {
				continue;
			}
			total++;
			if (module.isEnabled()) {
				enabled++;
			}
		}
		return enabled + "/" + total;
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
			cards.add(entry instanceof Module module ? new Card(module, null, x, y, i) : new Card(null, (ExternalMods.Entry) entry, x, y, i));
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
		graphics.fill(0, 0, width, height, 0xA60A0809);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		// Tippen geht hier immer in die Suche
		TextInput.sync(this, true);
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		Component tooltip = null;

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		// Fenster: Schatten, Rahmen, Fläche – mit abgeschrägten Ecken wie die Panels
		int[] f = frame();
		int fw = f[2] - f[0];
		int fh = f[3] - f[1];
		GuiDraw.roundRect(g, f[0] + 2, f[1] + 3, fw, fh, 0x50000000);
		GuiDraw.roundRect(g, f[0], f[1], fw, fh, theme.outline());
		GuiDraw.roundRect(g, f[0] + 1, f[1] + 1, fw - 2, fh - 2, theme.panel() | 0xFF000000);

		tooltip = renderSidebar(g, theme, f, mx, my);

		renderSearch(g, theme, mx, my);
		int[] close = closeButton();
		boolean closeHover = inside(close, mx, my);
		GuiDraw.roundRect(g, close[0], close[1], close[2] - close[0], close[3] - close[1], closeHover ? theme.accent() : theme.header());
		GuiDraw.text(g, font, "x", close[0] + 5, close[1] + 3, closeHover ? GuiDraw.textOn(theme.accent()) : theme.headerText(), false);
		// Haarlinie unter Suche und Kopf
		g.fill(f[0] + SIDEBAR, f[1] + HEADER - 1, f[2] - 1, f[1] + HEADER, theme.outline());

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

	/** Seitenleiste: Schriftzug, Kategorien mit Zähler, unten die Werkzeuge. Liefert ggf. einen Tooltip. */
	private Component renderSidebar(GuiGraphics g, Theme theme, int[] f, int mx, int my) {
		Component tooltip = null;
		int right = f[0] + SIDEBAR;
		// dunklere Fläche mit Trennlinie zum Inhalt
		g.fill(f[0] + 1, f[1] + 2, right, f[3] - 2, 0x38000000);
		g.fill(right, f[1] + 1, right + 1, f[3] - 1, theme.outline());

		GuiDraw.text(g, font, "SALAMI", f[0] + PAD, f[1] + 9, theme.accent(), false);
		g.fill(f[0] + PAD, f[1] + HEADER - 5, f[0] + PAD + font.width("SALAMI"), f[1] + HEADER - 4, theme.accent());

		for (Object[] t : tabs()) {
			Object key = t[0];
			int[] r = {(int) t[1], (int) t[2], (int) t[3], (int) t[4]};
			boolean selected = key == tab;
			boolean hovered = inside(r, mx, my);
			if (selected) {
				g.fill(r[0], r[1], r[2], r[3], theme.enabledRow());
				g.fill(r[0], r[1], r[0] + 2, r[3], theme.accent());
			} else if (hovered) {
				g.fill(r[0], r[1], r[2], r[3], theme.hover());
			}
			String count = tabCount(key);
			int countX = r[2] - 6 - font.width(count);
			String label = font.plainSubstrByWidth(tabLabel(key), countX - r[0] - PAD - 8);
			GuiDraw.text(g, font, label, r[0] + PAD - 1 + (selected ? 2 : 0), r[1] + 4, selected ? theme.headerText() : theme.text(), false);
			GuiDraw.text(g, font, count, countX, r[1] + 4, selected ? theme.accent() : theme.textDim(), false);
		}

		int[] first = actionRect(Action.values()[0]);
		g.fill(f[0] + PAD, first[1] - 3, right - PAD, first[1] - 2, theme.outline());
		for (Action action : Action.values()) {
			int[] r = actionRect(action);
			boolean hovered = inside(r, mx, my);
			if (hovered) {
				g.fill(r[0], r[1], r[2], r[3], theme.hover());
			}
			String label = font.plainSubstrByWidth(I18n.get(action.key), SIDEBAR - PAD * 2 - 6);
			GuiDraw.text(g, font, label, r[0] + PAD - 1, r[1] + 4, hovered ? theme.headerText() : theme.textDim(), false);
			GuiDraw.text(g, font, ">", r[2] - 10, r[1] + 4, hovered ? theme.accent() : theme.outline(), false);
		}
		return tooltip;
	}

	private void renderSearch(GuiGraphics g, Theme theme, int mx, int my) {
		int[] r = searchBox();
		GuiDraw.roundRect(g, r[0], r[1], r[2] - r[0], r[3] - r[1], query.isEmpty() ? theme.outline() : theme.accent());
		GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - r[0] - 2, r[3] - r[1] - 2, 0xFF0D0B0C);
		if (query.isEmpty()) {
			GuiDraw.text(g, font, I18n.get("salami.mods.search"), r[0] + 5, r[1] + 3, theme.textDim(), false);
		} else {
			String shown = font.plainSubstrByWidth(query, r[2] - r[0] - 14, true);
			GuiDraw.text(g, font, shown, r[0] + 5, r[1] + 3, theme.text(), false);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = r[0] + 5 + font.width(shown);
				g.fill(cx, r[1] + 3, cx + 1, r[1] + 12, theme.text());
			}
		}
	}

	/** Kartenfläche: ruhige, weich abgerundete Fläche; eingeschaltet mit feinem Rand in der Akzentfarbe. */
	private void renderCardBase(GuiGraphics g, Theme theme, Card card, boolean hovered, boolean on) {
		int x = card.x();
		int y = card.y();
		GuiDraw.rounded(g, x, y, CARD_W, CARD_H, 4, 0xFF120F10);
		if (hovered) {
			GuiDraw.rounded(g, x, y, CARD_W, CARD_H, 4, theme.hover());
		}
		GuiDraw.roundedOutline(g, x, y, CARD_W, CARD_H, 4, on ? GuiDraw.fade(theme.accent(), 0.85F) : hovered ? 0x40FFFFFF : theme.outline());
	}

	private void renderDescription(GuiGraphics g, Theme theme, String key, int x, int y) {
		if (!Language.getInstance().has(key)) {
			return;
		}
		List<FormattedCharSequence> lines = font.split(Component.translatable(key), (int) ((CARD_W - 12) / 0.75f));
		g.pose().pushMatrix();
		g.pose().translate(x + 6, y + 19);
		g.pose().scale(0.75f, 0.75f);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			GuiDraw.text(g, font, lines.get(i), 0, i * 10, theme.textDim(), false);
		}
		g.pose().popMatrix();
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
		renderCardBase(g, theme, card, hovered, on);

		String name = font.plainSubstrByWidth(I18n.get(module.translationKey()), CARD_W - 28);
		GuiDraw.text(g, font, name, x + 6, y + 7, on ? theme.headerText() : theme.text(), false);
		renderDescription(g, theme, module.descriptionKey(), x, y);

		// An/Aus-Schalter
		int px = x + 6;
		int py = y + CARD_H - 13;
		GuiDraw.roundRect(g, px, py, 18, 8, on ? theme.accent() : theme.outline());
		int knob = on ? px + 11 : px + 1;
		g.fill(knob, py + 1, knob + 6, py + 7, on ? GuiDraw.textOn(theme.accent()) : theme.textDim());
		GuiDraw.text(g, font, I18n.get(on ? "salami.on" : "salami.off"), px + 22, py, on ? theme.accent() : theme.textDim(), false);

		if (hasSettings(module)) {
			int[] gr = gear(card);
			boolean gearHover = inside(gr, mx, my);
			if (gearHover) {
				GuiDraw.roundRect(g, gr[0], gr[1], gr[2] - gr[0], gr[3] - gr[1], theme.hover());
			}
			GuiDraw.text(g, font, "⚙", gr[0] + 3, gr[1] + 2, gearHover ? theme.accent() : theme.textDim(), false);
		}
	}

	/** Karte einer mitgelieferten Mod: kein An/Aus, ein Klick öffnet ihre Einstellungen. */
	private void renderExternalCard(GuiGraphics g, Theme theme, Card card, boolean hovered) {
		ExternalMods.Entry entry = card.external();
		int x = card.x();
		int y = card.y();
		renderCardBase(g, theme, card, hovered, false);
		GuiDraw.text(g, font, font.plainSubstrByWidth(I18n.get(entry.nameKey()), CARD_W - 28), x + 6, y + 7, theme.text(), false);
		renderDescription(g, theme, entry.descriptionKey(), x, y);
		String open = I18n.get("salami.mods.open_settings");
		GuiDraw.text(g, font, open, x + 6, y + CARD_H - 13, hovered ? theme.accent() : theme.textDim(), false);
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

	private void runAction(Action action) {
		lastTab = tab;
		switch (action) {
			case HUD -> minecraft.setScreen(new HudEditorScreen(this));
			case CONFIGS -> minecraft.setScreen(new ConfigsScreen(this));
			// Panel-Ansicht: zurück zu ihr, wenn das Menü von dort geöffnet wurde
			case PANELS -> minecraft.setScreen(parent instanceof ClickGuiScreen ? parent : new ClickGuiScreen(modules, SalamiClient.get().config()));
		}
	}

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
		for (Action action : Action.values()) {
			if (inside(actionRect(action), x, y)) {
				runAction(action);
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
		if (minecraft.hasControlDown() && scrollY != 0) {
			// Strg + Mausrad: Größe des Menüs, wie in der Panel-Ansicht
			GuiScaleModule module = GuiScaleModule.get();
			if (module != null) {
				if (!module.isEnabled()) {
					modules.setEnabled(module, true);
				}
				module.stepClickGui(scrollY > 0 ? 1 : -1);
				uiScale = GuiScaleModule.clickGuiScale();
				clampScroll();
			}
			return true;
		}
		scroll -= scrollY * (CARD_H + GAP) / 2.0;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		// Die Menü-Taste (Rechte Umschalttaste) schließt das Menü wieder
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			lastTab = tab;
			minecraft.setScreen(null);
			return true;
		}
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
