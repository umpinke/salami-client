package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skins;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mods-Menü: Kategorien mit „an/alle“-Zähler, Werkzeuge (HUD, Configs, Panel-Ansicht), Suche und alle
 * Module. Klick schaltet an/aus, Zahnrad oder Rechtsklick öffnet die Einstellungen.
 *
 * <p>Aufbau und Aussehen kommen vom Look ({@link ModsView} des aktuellen {@link dev.salamiclient.gui.skin.Skin});
 * dieser Bildschirm hält Filter, Suche, Scrollstand und Eingabe. Rechnet in skalierten Koordinaten
 * (Modul „Oberfläche“).
 */
public final class ModsScreen extends Screen {
	/** So weit scrollt ein Schritt des Mausrads. */
	private static final int SCROLL_STEP = 26;

	/** Reiter für die mitgelieferten Mods (Wavey Capes, 3D Skin Layers …). */
	private static final Object EXTRAS = new Object();

	/** Gewählter Reiter bleibt während der Sitzung erhalten ({@code null} = alle, sonst Kategorie oder {@link #EXTRAS}). */
	private static Object lastTab;

	/** Werkzeuge unten in der Seitenleiste. */
	private enum Action {
		HUD("salami.clickgui.hud_editor", MenuView.Kind.HUD),
		CONFIGS("salami.clickgui.configs", MenuView.Kind.CONFIGS),
		PANELS("salami.mods.panels", MenuView.Kind.PANELS);

		final String key;
		final MenuView.Kind kind;

		Action(String key, MenuView.Kind kind) {
			this.key = key;
			this.kind = kind;
		}
	}

	private final ModuleManager modules;
	private final Screen parent;
	private Object tab = lastTab;
	private String query = "";
	private double scroll;
	private float uiScale = 1f;
	/** Menü-Taste gedrückt, Suche leer: beim Loslassen schließen (außer es wurde dazwischen getippt). */
	private boolean closeOnRelease;
	/** Das nächste Zeichen stammt von der Menü-Taste selbst (siehe keyPressed). */
	private boolean menuKeyChar;

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
		return SalamiMenuScreen.pausesGame();
	}

	@Override
	public void onClose() {
		lastTab = tab;
		minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ Inhalt

	private int uiWidth() {
		return Math.round(width / uiScale);
	}

	private int uiHeight() {
		return Math.round(height / uiScale);
	}

	/** Reiter: {@code null} (Alle), alle Kategorien mit Modulen und ggf. {@link #EXTRAS}. */
	private List<Object> tabKeys() {
		List<Object> keys = new ArrayList<>();
		keys.add(null);
		for (Category category : Category.values()) {
			if (!modules.byCategory(category).isEmpty()) {
				keys.add(category);
			}
		}
		if (!ExternalMods.installed().isEmpty()) {
			keys.add(EXTRAS);
		}
		return keys;
	}

	private String tabLabel(Object tab) {
		if (tab == null) {
			return I18n.get("salami.mods.tab.all");
		}
		return tab instanceof Category category ? I18n.get(category.translationKey()) : I18n.get("salami.mods.tab.extras");
	}

	/** Zähler „an / alle“ eines Reiters; bei den Extras nur die Anzahl. */
	private ModsView.Tab tabModel(Object tab) {
		if (tab == EXTRAS) {
			return new ModsView.Tab(tabLabel(tab), -1, ExternalMods.installed().size());
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
		return new ModsView.Tab(tabLabel(tab), enabled, total);
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
		Component description = module.description();
		return description != null && description.getString().toLowerCase(Locale.ROOT).contains(q);
	}

	private static boolean hasSettings(Module module) {
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				return true;
			}
		}
		return false;
	}

	private ModsView.Model model() {
		List<Object> keys = tabKeys();
		List<ModsView.Tab> tabs = new ArrayList<>();
		for (Object key : keys) {
			tabs.add(tabModel(key));
		}
		List<ModsView.Tool> tools = new ArrayList<>();
		for (Action action : Action.values()) {
			tools.add(new ModsView.Tool(I18n.get(action.key), action.kind));
		}
		List<ModsView.Item> items = new ArrayList<>();
		for (Object entry : visibleEntries()) {
			if (entry instanceof Module module) {
				items.add(new ModsView.Item(I18n.get(module.translationKey()), module.description(), module.isEnabled(), true,
					hasSettings(module), null));
			} else {
				ExternalMods.Entry external = (ExternalMods.Entry) entry;
				Component description = Language.getInstance().has(external.descriptionKey()) ? Component.translatable(external.descriptionKey()) : null;
				items.add(new ModsView.Item(I18n.get(external.nameKey()), description, false, false, true, I18n.get("salami.mods.tab.extras")));
			}
		}
		return new ModsView.Model(title.getString(), tabs, Math.max(0, keys.indexOf(tab)), tools, items, query, I18n.get("salami.mods.search"),
			I18n.get("salami.mods.empty"), scroll, "Esc");
	}

	private void clampScroll() {
		if (font == null) {
			return;
		}
		ModsView view = Skins.current().modsView();
		ModsView.Model model = model();
		int[] area = view.scrollArea(model, uiWidth(), uiHeight());
		int max = Math.max(0, view.contentHeight(font, model, uiWidth(), uiHeight()) - (area[3] - area[1]));
		scroll = Math.max(0, Math.min(scroll, max));
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
		// Tippen geht hier immer in die Suche
		TextInput.sync(this, true);
		ModsView view = Skins.current().modsView();
		ModsView.Model model = model();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		view.render(g, font, model, uiWidth(), uiHeight(), mx, my);
		g.pose().popMatrix();

		List<Component> tooltip = view.tooltip(font, model, uiWidth(), uiHeight(), mx, my);
		if (tooltip != null && !tooltip.isEmpty()) {
			List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
			for (Component line : tooltip) {
				lines.addAll(font.split(line, 220));
			}
			g.setTooltipForNextFrame(lines, mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ Eingabe

	private void runAction(Action action) {
		lastTab = tab;
		switch (action) {
			case HUD -> minecraft.setScreen(new HudEditorScreen(this));
			case CONFIGS -> minecraft.setScreen(new ConfigsScreen(this));
			// Panel-Ansicht: zurück zu ihr, wenn das Menü von dort geöffnet wurde
			case PANELS -> minecraft.setScreen(parent instanceof ClickGuiScreen ? parent : new ClickGuiScreen(modules, SalamiClient.get().config(), this));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		int button = event.button();
		ModsView.Model model = model();
		ModsView.Hit hit = Skins.current().modsView().hit(font, model, uiWidth(), uiHeight(), x, y);
		switch (hit) {
			case ModsView.Hit.CloseHit close -> {
				onClose();
				return true;
			}
			case ModsView.Hit.TabHit tabHit -> {
				List<Object> keys = tabKeys();
				if (tabHit.index() < keys.size()) {
					tab = keys.get(tabHit.index());
					scroll = 0;
				}
				return true;
			}
			case ModsView.Hit.ToolHit toolHit -> {
				if (toolHit.index() < Action.values().length) {
					runAction(Action.values()[toolHit.index()]);
				}
				return true;
			}
			case ModsView.Hit.SearchHit search -> {
				if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
					query = "";
				}
				return true;
			}
			case ModsView.Hit.ItemHit item -> {
				return clickEntry(item.index(), button, false);
			}
			case ModsView.Hit.SettingsHit settings -> {
				return clickEntry(settings.index(), button, true);
			}
			default -> {
				return super.mouseClicked(event, doubleClick);
			}
		}
	}

	/** Klick auf einen Eintrag: an/aus, mit Zahnrad oder Rechtsklick die Einstellungen. */
	private boolean clickEntry(int index, int button, boolean gear) {
		List<Object> entries = visibleEntries();
		if (index < 0 || index >= entries.size()) {
			return true;
		}
		Object entry = entries.get(index);
		if (entry instanceof ExternalMods.Entry external) {
			Screen settings = ExternalMods.configScreen(external, this);
			if (settings != null) {
				minecraft.setScreen(settings);
			}
			return true;
		}
		Module module = (Module) entry;
		boolean wantsSettings = button == InputConstants.MOUSE_BUTTON_RIGHT || gear;
		if (wantsSettings && hasSettings(module)) {
			lastTab = tab;
			minecraft.setScreen(new ModSettingsScreen(modules, module, this));
		} else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			modules.toggle(module);
		}
		return true;
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
		scroll -= scrollY * SCROLL_STEP;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		// Die Menü-Taste (Standard Rechte Umschalttaste) schließt das Menü – aber Tippen geht vor: mit
		// Text in der Suche gar nicht (Umschalt für Großbuchstaben), sonst erst beim Loslassen und nur,
		// wenn dazwischen kein Zeichen getippt wurde
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			closeOnRelease = query.isEmpty();
			// liegt die Menü-Taste auf einem Buchstaben, kommt gleich ihr Zeichen – das gehört nicht in die Suche
			menuKeyChar = closeOnRelease;
			return true;
		}
		menuKeyChar = false;
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
	public boolean keyReleased(KeyEvent event) {
		if (closeOnRelease && SalamiClient.get().clickGuiKey().matches(event)) {
			closeOnRelease = false;
			lastTab = tab;
			SalamiMenuScreen.closeAll(minecraft);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (menuKeyChar) {
			menuKeyChar = false;
			return true;
		}
		closeOnRelease = false;
		if (event.isAllowedChatCharacter() && query.length() < 32) {
			query += event.codepointAsString();
			scroll = 0;
			return true;
		}
		return super.charTyped(event);
	}
}
