package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.AnimatedCapes;
import dev.salamiclient.capes.CapeLibrary;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cosmetics: vorerst nur der Reiter „Capes“. Ein Klick auf ein Cape legt es an (mit dem
 * Launcher: dieselbe Auswahl wie dort), „Hochladen“ fügt ein eigenes PNG hinzu und legt es
 * gleich an. Eigene Bilder sehen andere Salami-Spieler erst nach der Freigabe durch einen Admin.
 */
public final class CosmeticsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int TABS = 18;
	private static final int PAD = 10;
	private static final int CARD_W = 78;
	private static final int CARD_H = 84;
	private static final int GAP = 6;
	private static final int PREVIEW_W = 30;
	private static final int PREVIEW_H = 48;
	/** Breite der festen Cape-Bilder in der Jar (Peperoni, Schneidebrett). */
	private static final int STATIC_SIZE = 256;

	/**
	 * Eine Kachel: {@code key} wie in {@link #current()}. Eigene Capes haben eine feste Vorschau
	 * ({@code texture}), mitgelieferte ({@code builtin}) holen sie jedes Bild neu – sie sind gemalt
	 * und animiert.
	 */
	private record Card(String key, String label, @Nullable Identifier texture, int textureWidth, CapesModule.@Nullable Cape builtin) {
		Card(String key, String label, @Nullable Identifier texture, int textureWidth) {
			this(key, label, texture, textureWidth, null);
		}
	}

	private final Screen parent;
	private float uiScale = 1f;
	private double scroll;
	private final List<Card> cards = new ArrayList<>();
	/** Für eigene Capes registrierte Vorschau-Texturen – beim Schließen wieder freigeben. */
	private final List<Identifier> previews = new ArrayList<>();
	private @Nullable Component notice;
	private boolean noticeError;
	/** Angelegtes Cape ({@link #current()}), höchstens einmal pro Sekunde neu gelesen – es steht in einer Datei. */
	private String currentKey = "none";
	private long currentReadAt;

	public CosmeticsScreen(@Nullable Screen parent) {
		super(Component.translatable("salami.cosmetics.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		reload();
		currentReadAt = 0;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		releasePreviews();
		super.removed();
	}

	// ------------------------------------------------------------------ Daten

	private void reload() {
		releasePreviews();
		cards.clear();
		cards.add(new Card("none", I18n.get("salami.cosmetics.none"), null, 0));
		for (CapesModule.Cape builtin : CapesModule.Cape.values()) {
			if (builtin == CapesModule.Cape.LAUNCHER || builtin == CapesModule.Cape.CUSTOM || builtin == CapesModule.Cape.NONE) {
				continue;
			}
			String name = builtin.name().toLowerCase(Locale.ROOT);
			cards.add(new Card("builtin:" + name, I18n.get("salami.module.capes.setting.cape." + name), null, 0, builtin));
		}
		Path dir = CapeLibrary.launcherDir();
		if (dir != null) {
			for (CapeLibrary.OwnCape own : CapeLibrary.ownCapes(dir)) {
				addOwn("own:" + own.id(), own.name(), own.png());
			}
		} else if (Files.isRegularFile(CapesModule.customCapeFile())) {
			addOwn("custom", I18n.get("salami.module.capes.setting.cape.custom"), CapesModule.customCapeFile());
		}
		cards.add(new Card("upload", I18n.get("salami.cosmetics.upload"), null, 0));
	}

	/** Vorschau eines eigenen Capes laden (kaputte Bilder bekommen eine leere Kachel). */
	private void addOwn(String key, String label, Path png) {
		Identifier id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cosmetics/preview_" + previews.size());
		NativeImage image = null;
		try (InputStream in = Files.newInputStream(png)) {
			byte[] data = in.readNBytes(CapeLibrary.MAX_FILE_BYTES + 1);
			if (data.length <= CapeLibrary.MAX_FILE_BYTES) {
				image = NativeImage.read(data);
			}
		} catch (IOException | RuntimeException e) {
			SalamiClient.LOG.debug("Cape-Vorschau nicht lesbar: {}", e.toString());
		}
		if (image == null || !CapesModule.validCapeSize(image.getWidth(), image.getHeight())) {
			if (image != null) {
				image.close();
			}
			cards.add(new Card(key, label, null, 0));
			return;
		}
		int width = image.getWidth();
		minecraft.getTextureManager().register(id, new DynamicTexture(() -> "salami cosmetics preview", image));
		previews.add(id);
		cards.add(new Card(key, label, id, width));
	}

	private void releasePreviews() {
		for (Identifier id : previews) {
			minecraft.getTextureManager().release(id);
		}
		previews.clear();
	}

	/** Was gerade angelegt ist, als Kachel-Schlüssel. */
	private static String current() {
		CapesModule module = CapesModule.instance();
		if (module == null || !module.isEnabled()) {
			return "none";
		}
		return switch (module.mode()) {
			case LAUNCHER -> {
				Path dir = CapeLibrary.launcherDir();
				if (dir == null) {
					String fromStart = System.getProperty("salami.cape");
					yield fromStart == null || fromStart.isBlank() ? "builtin:salami" : fromStart.equals("none") ? "none"
						: fromStart.startsWith("builtin:") ? fromStart : "launcher";
				}
				String selected = CapeLibrary.selected(dir);
				yield selected == null ? "none" : selected.startsWith("builtin:") ? selected : "own:" + selected;
			}
			case CUSTOM -> "custom";
			case NONE -> "none";
			default -> "builtin:" + module.mode().name().toLowerCase(Locale.ROOT);
		};
	}

	/** Cape anlegen. Mit Launcher wird dessen Auswahl geändert, damit beide dasselbe zeigen. */
	private void choose(String key) {
		CapesModule module = CapesModule.instance();
		if (module == null) {
			return;
		}
		if (!module.isEnabled()) {
			SalamiClient.get().modules().setEnabled(module, true);
		}
		Path dir = CapeLibrary.launcherDir();
		try {
			if (dir != null && !key.equals("custom")) {
				String selection = key.equals("none") ? null : key.startsWith("own:") ? key.substring(4) : key;
				CapeLibrary.select(dir, selection);
				module.choose(CapesModule.Cape.LAUNCHER);
			} else {
				module.choose(switch (key) {
					case "none" -> CapesModule.Cape.NONE;
					case "custom" -> CapesModule.Cape.CUSTOM;
					default -> CapesModule.Cape.valueOf(key.substring("builtin:".length()).toUpperCase(Locale.ROOT));
				});
			}
			setNotice(Component.translatable(key.equals("none") ? "salami.cosmetics.removed" : "salami.cosmetics.equipped"), false);
			currentReadAt = 0;
		} catch (IOException | RuntimeException e) {
			SalamiClient.LOG.warn("Cape konnte nicht angelegt werden: {}", e.toString());
			setNotice(Component.translatable("salami.cosmetics.error.save"), true);
		}
		SalamiClient.get().config().markDirty();
	}

	private void upload() {
		FileDialog.open(I18n.get("salami.cosmetics.upload_title"), "PNG", "png", null, file -> {
			Path dir = CapeLibrary.launcherDir();
			try {
				if (dir != null) {
					CapeLibrary.OwnCape added = CapeLibrary.add(dir, file);
					reload();
					choose("own:" + added.id());
				} else {
					CapeLibrary.replaceCustom(file);
					reload();
					choose("custom");
				}
				setNotice(Component.translatable("salami.cosmetics.uploaded"), false);
			} catch (CapeLibrary.InvalidCapeException e) {
				setNotice(Component.translatable(e.getMessage()), true);
			} catch (IOException | RuntimeException e) {
				SalamiClient.LOG.warn("Cape konnte nicht hochgeladen werden: {}", e.toString());
				setNotice(Component.translatable("salami.cosmetics.error.save"), true);
			}
		});
	}

	private void setNotice(Component text, boolean error) {
		notice = text;
		noticeError = error;
	}

	// ------------------------------------------------------------------ Layout

	private int[] frame() {
		int uiW = Math.round(width / uiScale);
		int uiH = Math.round(height / uiScale);
		int w = Math.min(uiW - 16, 450);
		int h = Math.min(uiH - 16, 300);
		int x0 = (uiW - w) / 2;
		int y0 = (uiH - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] backButton() {
		int[] f = frame();
		return new int[] {f[0] + 4, f[1] + 5, f[0] + 4 + font.width(I18n.get("salami.mods.back")) + 12, f[1] + 19};
	}

	private int[] capesTab() {
		int[] f = frame();
		int w = font.width(I18n.get("salami.cosmetics.tab.capes")) + 14;
		return new int[] {f[0] + PAD, f[1] + HEADER + 4, f[0] + PAD + w, f[1] + HEADER + 4 + TABS - 2};
	}

	private int[] content() {
		int[] f = frame();
		return new int[] {f[0] + PAD, f[1] + HEADER + 4 + TABS + 4, f[2] - PAD, f[3] - PAD - 22};
	}

	private int columns() {
		int[] c = content();
		return Math.max(1, (c[2] - c[0] + GAP) / (CARD_W + GAP));
	}

	/** Linke obere Ecke der Kachel {@code index}, Raster zentriert. */
	private int[] cardPos(int index) {
		int[] c = content();
		int cols = columns();
		int gridW = cols * CARD_W + (cols - 1) * GAP;
		int left = c[0] + (c[2] - c[0] - gridW) / 2;
		return new int[] {left + (index % cols) * (CARD_W + GAP), c[1] + (index / cols) * (CARD_H + GAP) - (int) Math.round(scroll)};
	}

	private void clampScroll() {
		int[] c = content();
		int rows = (cards.size() + columns() - 1) / columns();
		int total = rows * (CARD_H + GAP) - GAP;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - (c[3] - c[1]))));
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
		clampScroll();

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		int[] f = frame();
		GuiDraw.roundRect(g, f[0], f[1], f[2] - f[0], f[3] - f[1], theme.outline());
		GuiDraw.roundRect(g, f[0] + 1, f[1] + 1, f[2] - f[0] - 2, f[3] - f[1] - 2, theme.panel() | 0xFF000000);
		g.fill(f[0] + 1, f[1] + 1, f[2] - 1, f[1] + HEADER - 2, theme.header());
		g.fill(f[0] + 1, f[1] + HEADER - 2, f[2] - 1, f[1] + HEADER - 1, theme.accent());
		GuiDraw.centeredText(g, font, I18n.get("salami.cosmetics.title"), (f[0] + f[2]) / 2, f[1] + 8, theme.headerText());
		int[] back = backButton();
		boolean backHover = inside(back, mx, my);
		GuiDraw.roundRect(g, back[0], back[1], back[2] - back[0], back[3] - back[1], backHover ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, back[0] + 1, back[1] + 1, back[2] - back[0] - 2, back[3] - back[1] - 2, backHover ? theme.header() : theme.panel() | 0xFF000000);
		GuiDraw.text(g, font, I18n.get("salami.mods.back"), back[0] + 6, back[1] + 3, theme.headerText(), false);

		// Reiter: vorerst nur Capes
		int[] tab = capesTab();
		GuiDraw.roundRect(g, tab[0], tab[1], tab[2] - tab[0], tab[3] - tab[1], theme.accent());
		GuiDraw.text(g, font, I18n.get("salami.cosmetics.tab.capes"), tab[0] + 7, tab[1] + 4, GuiDraw.textOn(theme.accent()), false);
		GuiDraw.text(g, font, I18n.get("salami.cosmetics.more_soon"), tab[2] + 8, tab[1] + 4, theme.textDim(), false);

		int[] c = content();
		long now = System.currentTimeMillis();
		if (now - currentReadAt > 1_000L) {
			currentKey = current();
			currentReadAt = now;
		}
		String current = currentKey;
		g.enableScissor(c[0], c[1], c[2], c[3]);
		for (int i = 0; i < cards.size(); i++) {
			int[] pos = cardPos(i);
			if (pos[1] + CARD_H < c[1] || pos[1] > c[3]) {
				continue;
			}
			boolean hovered = mx >= pos[0] && mx < pos[0] + CARD_W && my >= pos[1] && my < pos[1] + CARD_H && inside(c, mx, my);
			renderCard(g, theme, cards.get(i), pos[0], pos[1], hovered, cards.get(i).key().equals(current));
		}
		g.disableScissor();

		Component line = notice != null ? notice : Component.translatable("salami.cosmetics.info");
		List<FormattedCharSequence> lines = font.split(line, f[2] - f[0] - 2 * PAD);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			GuiDraw.text(g, font, lines.get(i), f[0] + PAD, f[3] - PAD - 18 + i * 10, notice != null && noticeError ? theme.warning() : theme.textDim(), false);
		}
		g.pose().popMatrix();
	}

	private void renderCard(GuiGraphics g, Theme theme, Card card, int x, int y, boolean hovered, boolean equipped) {
		GuiDraw.roundRect(g, x, y, CARD_W, CARD_H, equipped ? theme.accent() : hovered ? theme.textDim() : theme.outline());
		GuiDraw.roundRect(g, x + 1, y + 1, CARD_W - 2, CARD_H - 2, 0xFF101014);
		if (equipped) {
			GuiDraw.roundRect(g, x + 1, y + 1, CARD_W - 2, CARD_H - 2, theme.enabledRow());
		}
		if (hovered) {
			GuiDraw.roundRect(g, x + 1, y + 1, CARD_W - 2, CARD_H - 2, theme.hover());
		}
		int px = x + (CARD_W - PREVIEW_W) / 2;
		int py = y + 7;
		switch (card.key()) {
			case "none" -> {
				GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.textDim());
				GuiDraw.centeredText(g, font, "—", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.textDim());
			}
			case "upload" -> {
				GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, hovered ? theme.accent() : theme.textDim());
				g.fill(px + PREVIEW_W / 2 - 1, py + 14, px + PREVIEW_W / 2 + 1, py + PREVIEW_H - 14, hovered ? theme.accent() : theme.text());
				g.fill(px + 7, py + PREVIEW_H / 2 - 1, px + PREVIEW_W - 7, py + PREVIEW_H / 2 + 1, hovered ? theme.accent() : theme.text());
			}
			default -> {
				Identifier texture = card.texture();
				int textureWidth = card.textureWidth();
				if (card.builtin() != null) {
					ClientAsset.Texture painted = CapesModule.preview(card.builtin());
					String name = card.builtin().name().toLowerCase(Locale.ROOT);
					int scale = AnimatedCapes.shownScale(name);
					texture = painted != null ? painted.texturePath() : null;
					textureWidth = scale > 0 ? 64 * scale : STATIC_SIZE;
				}
				if (texture != null) {
					// Vorderseite des Capes: im 64x32-Layout bei (1,1), 10x16 Pixel – bei HD entsprechend größer
					int k = textureWidth / 64;
					g.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, k, k, PREVIEW_W, PREVIEW_H,
						10 * k, 16 * k, textureWidth, textureWidth / 2);
				} else if (card.builtin() != null) {
					// wird gerade gemalt (wenige Zehntelsekunden)
					GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.outline());
					GuiDraw.centeredText(g, font, "…", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.textDim());
				} else {
					GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.warning());
					GuiDraw.centeredText(g, font, "?", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.warning());
				}
			}
		}
		String label = font.plainSubstrByWidth(card.label(), CARD_W - 6);
		GuiDraw.centeredText(g, font, label, x + CARD_W / 2, y + CARD_H - 24, equipped ? theme.headerText() : theme.text());
		if (equipped) {
			GuiDraw.centeredText(g, font, I18n.get("salami.cosmetics.equipped_badge"), x + CARD_W / 2, y + CARD_H - 13, theme.accent());
		} else if (card.key().startsWith("own:") || card.key().equals("custom")) {
			GuiDraw.centeredText(g, font, I18n.get("salami.cosmetics.own_badge"), x + CARD_W / 2, y + CARD_H - 13, theme.textDim());
		}
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		if (inside(backButton(), x, y)) {
			onClose();
			return true;
		}
		int[] c = content();
		if (!inside(c, x, y)) {
			return super.mouseClicked(event, doubleClick);
		}
		for (int i = 0; i < cards.size(); i++) {
			int[] pos = cardPos(i);
			if (x >= pos[0] && x < pos[0] + CARD_W && y >= pos[1] && y < pos[1] + CARD_H) {
				String key = cards.get(i).key();
				if (key.equals("upload")) {
					upload();
				} else {
					choose(key);
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * (CARD_H + GAP) / 2.0;
		clampScroll();
		return true;
	}
}
