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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

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
		return new MenuView.Model("Salami", I18n.get("salami.menu.status", enabled, total), entries, I18n.get("salami.menu.hint"));
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
		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		Skins.current().menuView().render(g, font, model(), uiWidth(), uiHeight(), (int) (mouseX / uiScale), (int) (mouseY / uiScale));
		g.pose().popMatrix();
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
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			MenuView.Entry entry = Skins.current().menuView().hit(model(), uiWidth(), uiHeight(), event.x() / uiScale, event.y() / uiScale);
			if (entry != null) {
				open(entry.kind());
				return true;
			}
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
