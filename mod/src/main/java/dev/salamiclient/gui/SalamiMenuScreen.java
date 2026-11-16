package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.friends.Friends;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.misc.WaypointsModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Salami-Menü (Rechte Umschalttaste): Einstieg ins Spielmenü. Links ein Akzentstrich, daneben
 * Logo und Schriftzug, darunter der große Knopf zu den Mods, eine Reihe Kacheln für HUD,
 * Configs, Wegpunkte und die Panel-Ansicht und eine zweite für Freunde, Cosmetics und Minispiele.
 *
 * <p>Rechnet wie die übrigen Salami-Menüs in skalierten Koordinaten (Modul „Oberfläche“);
 * Zeichnen und Klicken nutzen dieselben Rechtecke.
 */
public final class SalamiMenuScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/logo.png");
	private static final int LOGO_TEXTURE_SIZE = 64;

	private static final int BLOCK_W = 236;
	private static final int LOGO_SIZE = 34;
	private static final int MAIN_H = 26;
	private static final int TILE_H = 34;
	private static final int GAP = 4;
	/** Abstand des Inhalts vom Akzentstrich links. */
	private static final int INSET = 10;

	/** Kacheln unter dem Mods-Knopf: erste Reihe Werkzeuge, zweite Reihe ({@link #social}) Freunde, Cosmetics und Minispiele. */
	private enum Tile {
		HUD("salami.menu.hud", false),
		CONFIGS("salami.clickgui.configs", false),
		WAYPOINTS("salami.menu.waypoints", false),
		PANELS("salami.menu.panels", false),
		FRIENDS("salami.menu.friends", true),
		COSMETICS("salami.menu.cosmetics", true),
		MINIGAMES("salami.clickgui.minigames", true);

		final String key;
		final boolean social;

		Tile(String key, boolean social) {
			this.key = key;
			this.social = social;
		}
	}

	private final ModuleManager modules;
	private final ConfigManager config;
	private float uiScale = 1f;

	public SalamiMenuScreen(ModuleManager modules, ConfigManager config) {
		super(Component.translatable("salami.clickgui.title"));
		this.modules = modules;
		this.config = config;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ Layout

	private int uiWidth() {
		return Math.round(width / uiScale);
	}

	private int uiHeight() {
		return Math.round(height / uiScale);
	}

	private int blockHeight() {
		return LOGO_SIZE + 12 + MAIN_H + GAP + TILE_H + GAP + TILE_H;
	}

	private int left() {
		return (uiWidth() - BLOCK_W) / 2;
	}

	private int top() {
		return (uiHeight() - blockHeight()) / 2 - 6;
	}

	/** Großer Knopf zu den Mods {x0, y0, x1, y1}. */
	private int[] mainButton() {
		int x = left() + INSET;
		int y = top() + LOGO_SIZE + 12;
		return new int[] {x, y, left() + BLOCK_W, y + MAIN_H};
	}

	/** Kacheln einer Reihe ({@code social} = zweite Reihe mit Freunden, Cosmetics und Minispielen). */
	private List<Tile> tiles(boolean social) {
		List<Tile> list = new ArrayList<>();
		for (Tile tile : Tile.values()) {
			if (tile.social == social) {
				list.add(tile);
			}
		}
		return list;
	}

	private WaypointsModule waypoints() {
		return modules.get(WaypointsModule.class);
	}

	private int[] tileRect(int index, int count, boolean social) {
		int[] main = mainButton();
		int total = main[2] - main[0];
		int w = (total - (count - 1) * GAP) / count;
		int x = main[0] + index * (w + GAP);
		// letzte Kachel schließt bündig mit dem Knopf ab
		int right = index == count - 1 ? main[2] : x + w;
		int top = main[3] + GAP + (social ? TILE_H + GAP : 0);
		return new int[] {x, top, right, top + TILE_H};
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
		graphics.fill(0, 0, width, height, 0xB00A0809);
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

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		int left = left();
		int top = top();
		// Akzentstrich über die ganze Höhe des Blocks
		g.fill(left, top, left + 2, top + blockHeight(), theme.accent());

		// Logo, Schriftzug in dreifacher Größe, darunter „12 von 64 Mods an“
		int x = left + INSET;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, top, 0f, 0f, LOGO_SIZE, LOGO_SIZE,
			LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
		int textX = x + LOGO_SIZE + 8;
		g.pose().pushMatrix();
		g.pose().translate(textX, top + 1);
		g.pose().scale(3f, 3f);
		GuiDraw.text(g, font, "SALAMI", 0, 0, theme.headerText(), false);
		g.pose().popMatrix();
		GuiDraw.text(g, font, status(), textX + 1, top + 26, theme.textDim(), false);

		// Mods-Knopf: vollflächig in der Akzentfarbe
		int[] main = mainButton();
		boolean mainHover = inside(main, mx, my);
		int onAccent = GuiDraw.textOn(theme.accent());
		GuiDraw.roundRect(g, main[0], main[1], main[2] - main[0], main[3] - main[1], theme.accent());
		if (mainHover) {
			GuiDraw.roundRect(g, main[0], main[1], main[2] - main[0], main[3] - main[1], 0x30FFFFFF);
		}
		g.pose().pushMatrix();
		g.pose().translate(main[0] + 10, main[1] + 6);
		g.pose().scale(1.5f, 1.5f);
		GuiDraw.text(g, font, I18n.get("salami.menu.mods"), 0, 0, onAccent, false);
		g.pose().popMatrix();
		GuiDraw.text(g, font, ">", main[2] - 14 + (mainHover ? 2 : 0), main[1] + 9, onAccent, false);

		for (boolean social : new boolean[] {false, true}) {
			List<Tile> tiles = tiles(social);
			for (int i = 0; i < tiles.size(); i++) {
				renderTile(g, theme, tiles.get(i), tileRect(i, tiles.size(), social), mx, my);
			}
		}

		GuiDraw.text(g, font, I18n.get("salami.menu.hint"), x, top + blockHeight() + 8, (theme.textDim() & 0x00FFFFFF) | 0x99000000, false);

		g.pose().popMatrix();
	}

	private String status() {
		int total = 0;
		int enabled = 0;
		for (Module module : modules.all()) {
			total++;
			if (module.isEnabled()) {
				enabled++;
			}
		}
		return I18n.get("salami.menu.status", enabled, total);
	}

	private void renderTile(GuiGraphics g, Theme theme, Tile tile, int[] r, int mx, int my) {
		boolean hovered = inside(r, mx, my);
		int w = r[2] - r[0];
		int h = r[3] - r[1];
		GuiDraw.roundRect(g, r[0], r[1], w, h, hovered ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, w - 2, h - 2, theme.panel() | 0xFF000000);
		if (hovered) {
			GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, w - 2, h - 2, theme.hover());
		}
		int color = hovered ? theme.accent() : theme.headerText();
		drawIcon(g, tile, r[0] + (w - 12) / 2, r[1] + 6, color);
		String label = font.plainSubstrByWidth(I18n.get(tile.key), (int) ((w - 6) / 0.75f));
		g.pose().pushMatrix();
		g.pose().translate(r[0] + (w - font.width(label) * 0.75f) / 2f, r[1] + h - 11);
		g.pose().scale(0.75f, 0.75f);
		GuiDraw.text(g, font, label, 0, 0, hovered ? theme.headerText() : theme.textDim(), false);
		g.pose().popMatrix();
		if (tile == Tile.FRIENDS) {
			// ungelesene Nachrichten und offene Anfragen als Zahl oben rechts
			int count = Friends.unreadTotal() + (int) Friends.requests().stream().filter(Friends.Request::incoming).count();
			if (count > 0) {
				String badge = count > 99 ? "99+" : String.valueOf(count);
				int bw = font.width(badge) + 6;
				GuiDraw.roundRect(g, r[2] - bw - 3, r[1] + 3, bw, 10, theme.accent());
				GuiDraw.text(g, font, badge, r[2] - bw, r[1] + 4, GuiDraw.textOn(theme.accent()), false);
			}
		}
	}

	/** Pixel-Symbole (12x12); (x, y) ist die linke obere Ecke. */
	private static void drawIcon(GuiGraphics g, Tile tile, int x, int y, int color) {
		switch (tile) {
			case HUD -> {
				// Bildschirm mit zwei HUD-Elementen
				GuiDraw.outline(g, x, y + 1, 12, 9, color);
				g.fill(x + 2, y + 3, x + 6, y + 4, color);
				g.fill(x + 8, y + 7, x + 10, y + 8, color);
				g.fill(x + 4, y + 11, x + 8, y + 12, color);
			}
			case CONFIGS -> {
				// drei Regler
				for (int i = 0; i < 3; i++) {
					int ly = y + 2 + i * 4;
					g.fill(x, ly, x + 12, ly + 1, color);
					int knob = x + (i == 1 ? 7 : i == 0 ? 2 : 4);
					g.fill(knob, ly - 1, knob + 2, ly + 2, color);
				}
			}
			case WAYPOINTS -> {
				// Raute mit Punkt
				for (int i = 0; i < 6; i++) {
					g.fill(x + 5 - i, y + i, x + 7 + i, y + i + 1, color);
					g.fill(x + 5 - i, y + 11 - i, x + 7 + i, y + 12 - i, color);
				}
				g.fill(x + 5, y + 5, x + 7, y + 7, 0xFF120F10);
			}
			case PANELS -> {
				// zwei Panels nebeneinander
				GuiDraw.outline(g, x, y, 5, 12, color);
				GuiDraw.outline(g, x + 7, y, 5, 8, color);
				g.fill(x + 1, y + 1, x + 4, y + 3, color);
				g.fill(x + 8, y + 1, x + 11, y + 3, color);
			}
			case FRIENDS -> {
				// zwei Köpfe mit Schultern
				g.fill(x + 1, y + 1, x + 5, y + 5, color);
				g.fill(x, y + 7, x + 6, y + 12, color);
				g.fill(x + 7, y + 2, x + 11, y + 6, color);
				g.fill(x + 6, y + 8, x + 12, y + 12, color);
			}
			case COSMETICS -> {
				// Cape: breiter Kragen, nach unten schmaler werdend
				g.fill(x + 1, y, x + 11, y + 2, color);
				g.fill(x + 2, y + 2, x + 10, y + 9, color);
				g.fill(x + 3, y + 9, x + 9, y + 12, color);
				g.fill(x + 5, y + 3, x + 7, y + 8, 0xFF120F10);
			}
			case MINIGAMES -> {
				// Gamepad: Gehäuse mit Griffen, Steuerkreuz und zwei Knöpfen
				g.fill(x + 1, y + 3, x + 11, y + 9, color);
				g.fill(x, y + 5, x + 12, y + 10, color);
				g.fill(x, y + 10, x + 3, y + 11, color);
				g.fill(x + 9, y + 10, x + 12, y + 11, color);
				g.fill(x + 2, y + 6, x + 5, y + 7, 0xFF120F10);
				g.fill(x + 3, y + 5, x + 4, y + 8, 0xFF120F10);
				g.fill(x + 8, y + 5, x + 9, y + 6, 0xFF120F10);
				g.fill(x + 9, y + 7, x + 10, y + 8, 0xFF120F10);
			}
		}
	}

	// ------------------------------------------------------------------ Eingabe

	private void openMods() {
		minecraft.setScreen(new ModsScreen(modules, this));
	}

	private void open(Tile tile) {
		switch (tile) {
			case HUD -> minecraft.setScreen(new HudEditorScreen(this));
			case CONFIGS -> minecraft.setScreen(new ConfigsScreen(this));
			case WAYPOINTS -> {
				WaypointsModule module = waypoints();
				if (module != null) {
					minecraft.setScreen(new WaypointsScreen(module, this));
				}
			}
			case PANELS -> minecraft.setScreen(new ClickGuiScreen(modules, config));
			case FRIENDS -> minecraft.setScreen(new FriendsScreen(this));
			case COSMETICS -> minecraft.setScreen(new CosmeticsScreen(this));
			case MINIGAMES -> MinigamesScreen.open(this);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (inside(mainButton(), x, y)) {
				openMods();
				return true;
			}
			for (boolean social : new boolean[] {false, true}) {
				List<Tile> tiles = tiles(social);
				for (int i = 0; i < tiles.size(); i++) {
					if (inside(tileRect(i, tiles.size(), social), x, y)) {
						open(tiles.get(i));
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Die Menü-Taste schließt das Menü wieder
		if (SalamiClient.get().clickGuiKey().matches(event)) {
			onClose();
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			openMods();
			return true;
		}
		return super.keyPressed(event);
	}
}
