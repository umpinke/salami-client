package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.friends.Friends;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.Skins;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Salami-Menü (Menü-Taste, Standard Rechte Umschalttaste; außerdem Knöpfe im Titelbildschirm und
 * Pausemenü und der Eintrag in Mod Menu): Einstieg ins Spielmenü – Mods, HUD, Configs, Panel-Ansicht,
 * Freunde, Cosmetics und Minispiele.
 *
 * <p>Aufbau und Aussehen kommen vom Look ({@link MenuView} des aktuellen {@link dev.salamiclient.gui.skin.Skin});
 * dieser Bildschirm hält nur Logik und Eingabe. Rechnet wie die übrigen Salami-Menüs in skalierten
 * Koordinaten (Modul „Oberfläche“).
 */
public final class SalamiMenuScreen extends Screen {
	/**
	 * Bildschirm, aus dem das Salami-Menü zuletzt geöffnet wurde (Titelbildschirm, Pausemenü, Mod Menu;
	 * {@code null} = im Spiel über die Taste). Dorthin führt {@link #closeAll}.
	 */
	private static Screen origin;

	private final ModuleManager modules;
	private final ConfigManager config;
	private final Screen parent;
	private float uiScale = 1f;

	/** Im Spiel über die Menü-Taste: schließen führt zurück ins Spiel. */
	public SalamiMenuScreen(ModuleManager modules, ConfigManager config) {
		this(modules, config, null);
	}

	/** Aus einem anderen Bildschirm (Titelbildschirm, Pausemenü, Mod Menu): schließen führt dorthin zurück. */
	public SalamiMenuScreen(ModuleManager modules, ConfigManager config, Screen parent) {
		super(Component.translatable("salami.clickgui.title"));
		this.modules = modules;
		this.config = config;
		this.parent = parent;
		origin = parent;
	}

	/** Öffnet das Salami-Menü über {@code parent} (Knöpfe im Titelbildschirm und Pausemenü, Mod Menu). */
	public static SalamiMenuScreen over(Screen parent) {
		SalamiClient salami = SalamiClient.get();
		return new SalamiMenuScreen(salami.modules(), salami.config(), parent);
	}

	/**
	 * Schließt alle Salami-Menüs auf einmal (Menü-Taste): zurück zu dem Bildschirm, aus dem das Menü
	 * geöffnet wurde – ins Spiel, ins Pausemenü oder zum Titelbildschirm.
	 */
	public static void closeAll(Minecraft minecraft) {
		Screen target = origin;
		// Pausemenü nur in einer Welt, sonst (z. B. Titelbildschirm) nur ohne Welt
		boolean inWorld = minecraft.level != null;
		if (target instanceof PauseScreen ? !inWorld : inWorld) {
			target = null;
		}
		minecraft.setScreen(target);
	}

	/**
	 * Menü-Taste in einem Salami-Bildschirm (Mods, Panel-Ansicht, HUD-Editor, Configs …): schließt immer
	 * alle Salami-Menüs auf einmal ({@link #closeAll}) – außer {@code typing}, dann gehört die Taste dem
	 * Textfeld bzw. Spiel. Esc geht dagegen eine Ebene zurück.
	 *
	 * @return {@code true}, wenn die Taste verbraucht wurde
	 */
	public static boolean closeOnMenuKey(Minecraft minecraft, KeyEvent event, boolean typing) {
		if (typing || !SalamiClient.get().clickGuiKey().matches(event)) {
			return false;
		}
		closeAll(minecraft);
		return true;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return pausesGame();
	}

	/**
	 * Aus dem Pausemenü geöffnet: dann bleibt eine Einzelspielerwelt in allen Salami-Menüs angehalten
	 * (sonst liefe sie weiter, während man Einstellungen durchsieht). Mit der Taste im Spiel geöffnet
	 * läuft das Spiel wie bisher weiter.
	 */
	public static boolean pausesGame() {
		return origin instanceof PauseScreen;
	}

	// ------------------------------------------------------------------ Inhalt

	private int uiWidth() {
		return Math.round(width / uiScale);
	}

	private int uiHeight() {
		return Math.round(height / uiScale);
	}

	private MenuView.Model model() {
		int total = 0;
		int enabled = 0;
		for (Module module : modules.all()) {
			total++;
			if (module.isEnabled()) {
				enabled++;
			}
		}
		// ungelesene Nachrichten und offene Anfragen als Zahl an den Freunden
		int friendsBadge = Friends.unreadTotal() + (int) Friends.requests().stream().filter(Friends.Request::incoming).count();
		long online = Friends.friends().stream().filter(Friends.Friend::online).count();
		List<MenuView.Entry> entries = List.of(
			new MenuView.Entry(MenuView.Kind.MODS, I18n.get("salami.menu.mods"), enabled + " / " + total, 0),
			new MenuView.Entry(MenuView.Kind.HUD, I18n.get("salami.menu.hud"), null, 0),
			new MenuView.Entry(MenuView.Kind.CONFIGS, I18n.get("salami.clickgui.configs"), null, 0),
			new MenuView.Entry(MenuView.Kind.PANELS, I18n.get("salami.menu.panels"), null, 0),
			new MenuView.Entry(MenuView.Kind.FRIENDS, I18n.get("salami.menu.friends"), online > 0 ? I18n.get("salami.menu.online", online) : null,
				friendsBadge),
			new MenuView.Entry(MenuView.Kind.COSMETICS, I18n.get("salami.menu.cosmetics"), null, 0),
			new MenuView.Entry(MenuView.Kind.MINIGAMES, I18n.get("salami.clickgui.minigames"), null, 0));
		return new MenuView.Model("Salami", I18n.get("salami.menu.status", enabled, total), entries, I18n.get("salami.menu.hint"), quick());
	}

	/**
	 * Module für die Schnellschalter (Looks dürfen sie zeigen, z. B. als Hotbar der Werkbank): die am
	 * häufigsten umgeschalteten, sofern es sie gibt.
	 */
	private static final List<String> QUICK = List.of("zoom", "free_look", "fullbright", "hitbox", "capes", "hats", "time_changer",
		"particles", "toggle_sprint");

	private List<Module> quickModules() {
		List<Module> list = new ArrayList<>();
		for (String id : QUICK) {
			modules.get(id).ifPresent(list::add);
		}
		return list;
	}

	private List<MenuView.Quick> quick() {
		List<MenuView.Quick> list = new ArrayList<>();
		for (Module module : quickModules()) {
			list.add(new MenuView.Quick(module.id(), I18n.get(module.translationKey()), module.description(), module.isEnabled(),
				shortKey(module), hasSettings(module)));
		}
		return list;
	}

	/** Kurzer Name der Taste des Moduls (eigene Umschalt-Taste, sonst die erste belegte Taste seiner Einstellungen), höchstens 2 Zeichen. */
	private static String shortKey(Module module) {
		KeybindSetting key = module.keybind.isBound() ? module.keybind : null;
		if (key == null) {
			for (Setting<?> setting : module.settings()) {
				if (setting instanceof KeybindSetting bind && !setting.isHidden() && bind.isBound()) {
					key = bind;
					break;
				}
			}
		}
		if (key == null) {
			return null;
		}
		String name = key.keyName();
		return name.length() <= 2 ? name : null;
	}

	private static boolean hasSettings(Module module) {
		for (Setting<?> setting : module.settings()) {
			if (!setting.isHidden()) {
				return true;
			}
		}
		return false;
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
		MenuView view = Skins.current().menuView();
		MenuView.Model model = model();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		view.render(g, font, model, uiWidth(), uiHeight(), mx, my);
		g.pose().popMatrix();

		List<Component> tooltip = view.tooltip(font, model, uiWidth(), uiHeight(), mx, my);
		if (tooltip != null && !tooltip.isEmpty()) {
			List<FormattedCharSequence> lines = new ArrayList<>();
			for (Component line : tooltip) {
				lines.addAll(font.split(line, 220));
			}
			g.setTooltipForNextFrame(lines, mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ Für Tests

	/** Mitte des Eintrags {@code kind} in GUI-Koordinaten oder {@code null} – für Tests (sucht über {@link MenuView#hit}). */
	public double[] pointOf(MenuView.Kind kind) {
		MenuView view = Skins.current().menuView();
		MenuView.Model model = model();
		return find((x, y) -> {
			MenuView.Entry entry = view.hit(model, uiWidth(), uiHeight(), x, y);
			return entry != null && entry.kind() == kind;
		});
	}

	/** Mitte des Schnellschalters {@code index} in GUI-Koordinaten oder {@code null}, wenn der Look keine zeigt – für Tests. */
	public double[] pointOfQuick(int index) {
		MenuView view = Skins.current().menuView();
		MenuView.Model model = model();
		return find((x, y) -> view.hitQuick(model, uiWidth(), uiHeight(), x, y) == index);
	}

	/** Mitte aller UI-Punkte (Raster 2 Pixel), auf die {@code test} zutrifft, in GUI-Koordinaten. */
	private double[] find(java.util.function.BiPredicate<Double, Double> test) {
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int x1 = -1;
		int y1 = -1;
		for (int y = 1; y < uiHeight(); y += 2) {
			for (int x = 1; x < uiWidth(); x += 2) {
				if (test.test((double) x, (double) y)) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return x1 < 0 ? null : new double[] {(x0 + x1) / 2.0 * uiScale, (y0 + y1) / 2.0 * uiScale};
	}

	// ------------------------------------------------------------------ Eingabe

	private void open(MenuView.Kind kind) {
		switch (kind) {
			case MODS -> minecraft.setScreen(new ModsScreen(modules, this));
			case HUD -> minecraft.setScreen(new HudEditorScreen(this));
			case CONFIGS -> minecraft.setScreen(new ConfigsScreen(this));
			// Panel-Ansicht nur als Unteransicht: Esc führt hierher zurück
			case PANELS -> minecraft.setScreen(new ClickGuiScreen(modules, config, this));
			case FRIENDS -> minecraft.setScreen(new FriendsScreen(this));
			case COSMETICS -> minecraft.setScreen(new CosmeticsScreen(this));
			case MINIGAMES -> MinigamesScreen.open(this);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MenuView view = Skins.current().menuView();
		MenuView.Model model = model();
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			MenuView.Entry entry = view.hit(model, uiWidth(), uiHeight(), x, y);
			if (entry != null) {
				open(entry.kind());
				return true;
			}
			if (view.hitClose(model, uiWidth(), uiHeight(), x, y)) {
				onClose();
				return true;
			}
		}
		int quick = view.hitQuick(model, uiWidth(), uiHeight(), x, y);
		List<Module> quickModules = quickModules();
		if (quick >= 0 && quick < quickModules.size()) {
			Module module = quickModules.get(quick);
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hasSettings(module)) {
				minecraft.setScreen(new ModSettingsScreen(modules, module, this));
			} else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				modules.toggle(module);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Die Menü-Taste schließt das Menü wieder (wie in allen Salami-Bildschirmen)
		if (closeOnMenuKey(minecraft, event, false)) {
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			open(MenuView.Kind.MODS);
			return true;
		}
		return super.keyPressed(event);
	}
}
