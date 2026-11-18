package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.EmoteCatalog;
import dev.salamiclient.emotes.EmoteIcons;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.modules.social.EmotesModule;
import dev.salamiclient.capes.AnimatedCapes;
import dev.salamiclient.capes.CapeLibrary;
import dev.salamiclient.capes.OfficialCapes;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.cosmetics.HatCatalog;
import dev.salamiclient.cosmetics.HatIcons;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.modules.visual.HatsModule;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.minime.MiniMeSkins;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
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
import java.util.concurrent.CompletableFuture;

/**
 * Cosmetics: Reiter „Capes“, „Hüte“, „Mini Me“ und „Emotes“. Ein Klick auf ein Cape legt es an (mit
 * dem Launcher: dieselbe Auswahl wie dort), „Hochladen“ fügt ein eigenes PNG hinzu und legt es
 * gleich an. Eigene Bilder sehen andere Salami-Spieler erst nach der Freigabe durch einen Admin.
 *
 * <p>Hüte, Emotes und die offiziellen Capes kommen als Listen vom Salami-Server
 * ({@link dev.salamiclient.cosmetics.Catalogs}) – kommt dort etwas dazu, baut sich das Menü neu auf.
 */
public final class CosmeticsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int TABS = 18;
	private static final int PAD = 10;
	private static final int CARD_W = 78;
	private static final int CARD_H = 64;
	private static final int GAP = 6;
	private static final int PREVIEW_W = 22;
	private static final int PREVIEW_H = 35;
	/** Abstand der Vorschau vom oberen Kartenrand; darunter Name und Kennzeichen. */
	private static final int PREVIEW_Y = 5;
	/** Lange Namen werden bis auf diese Größe verkleinert, erst danach gekürzt. */
	private static final float MIN_LABEL_SCALE = 0.7f;
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

	/** Reiter des Menüs. */
	public enum Tab {
		CAPES("salami.cosmetics.tab.capes"),
		HATS("salami.cosmetics.tab.hats"),
		MINIME("salami.cosmetics.tab.minime"),
		EMOTES("salami.cosmetics.tab.emotes");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	/** Höhe der Zeile mit dem Namensfeld im Reiter Mini Me. */
	private static final int NAME_ROW = 26;

	private final Screen parent;
	private Tab tab;
	/** Eingabe im Namensfeld (Mini Me) und ob es gerade die Tastatur hat. */
	private String nameBuffer = "";
	private boolean nameFocused;
	private float uiScale = 1f;
	/** Erste sichtbare Kartenreihe – es wird immer um ganze Reihen gescrollt, nichts ist halb abgeschnitten. */
	private int scrollRow;
	private final List<Card> cards = new ArrayList<>();
	/** Für eigene Capes registrierte Vorschau-Texturen – beim Schließen wieder freigeben. */
	private final List<Identifier> previews = new ArrayList<>();
	private @Nullable Component notice;
	private boolean noticeError;
	/** Angelegtes Cape ({@link #current()}), höchstens einmal pro Sekunde neu gelesen – es steht in einer Datei. */
	private String currentKey = "none";
	/** Stand der Kataloge (Emotes, Hüte, offizielle Capes), mit dem die Kacheln gebaut wurden. */
	private int emoteRevision;
	private int hatRevision;
	private int capeRevision;
	private long currentReadAt;

	public CosmeticsScreen(@Nullable Screen parent) {
		this(parent, Tab.CAPES);
	}

	public CosmeticsScreen(@Nullable Screen parent, Tab tab) {
		super(Component.translatable("salami.cosmetics.title"));
		this.parent = parent;
		this.tab = tab;
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

	// ------------------------------------------------------------------ Daten

	private void reload() {
		releasePreviews();
		cards.clear();
		if (tab == Tab.MINIME) {
			MiniMeModule mini = MiniMeModule.instance();
			if (!nameFocused) { // init() läuft auch beim Ändern der Fenstergröße: Getipptes behalten
				nameBuffer = mini == null ? "" : mini.name();
			}
			for (MiniMeModule.Place place : MiniMeModule.Place.values()) {
				String id = place.name().toLowerCase(Locale.ROOT);
				cards.add(new Card("minime:" + id, I18n.get("salami.module.minime.setting.place." + id), null, 0));
			}
			return;
		}
		if (tab == Tab.HATS) {
			// Symbole zeichnet HatIcons aus dem Modell – die Liste kommt vom Server und kann wachsen
			hatRevision = HatCatalog.revision();
			cards.add(new Card("hat:none", I18n.get("salami.cosmetics.hat.none"), null, 0));
			for (Hat hat : HatCatalog.all()) {
				cards.add(new Card("hat:" + hat.id(), hat.displayName(), null, 0));
			}
			return;
		}
		if (tab == Tab.EMOTES) {
			// Symbole zeichnet EmoteIcons aus der Haltung – die Liste kommt vom Server und kann wachsen
			emoteRevision = EmoteCatalog.revision();
			for (Emote emote : EmoteCatalog.all()) {
				cards.add(new Card("emote:" + emote.id(), emote.displayName(), null, 0));
			}
			return;
		}
		cards.add(new Card("none", I18n.get("salami.cosmetics.none"), null, 0));
		for (CapesModule.Cape builtin : CapesModule.Cape.values()) {
			if (builtin == CapesModule.Cape.LAUNCHER || builtin == CapesModule.Cape.CUSTOM || builtin == CapesModule.Cape.NONE) {
				continue;
			}
			String name = builtin.name().toLowerCase(Locale.ROOT);
			cards.add(new Card("builtin:" + name, I18n.get("salami.module.capes.setting.cape." + name), null, 0, builtin));
		}
		// offizielle Capes vom Server: die Vorschau lädt OfficialCapes, sobald die Kachel zu sehen ist
		capeRevision = OfficialCapes.revision();
		for (OfficialCapes.Cape official : OfficialCapes.all()) {
			cards.add(new Card("official:" + official.id(), official.displayName(), null, 0));
		}
		Path dir = CapeLibrary.launcherDir();
		if (dir != null) {
			for (CapeLibrary.OwnCape own : CapeLibrary.ownCapes(dir)) {
				if (OfficialCapes.byOwnId(own.id()) != null) {
					continue; // angelegtes offizielles Cape: steht schon oben
				}
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
	private String current() {
		if (tab == Tab.HATS) {
			HatsModule hats = HatsModule.instance();
			return hats == null || !hats.isEnabled() || hats.selected() == null ? "hat:none" : "hat:" + hats.selected().id();
		}
		if (tab == Tab.MINIME) {
			MiniMeModule mini = MiniMeModule.instance();
			return mini == null || !mini.isEnabled() ? "minime:none" : "minime:" + mini.place().name().toLowerCase(Locale.ROOT);
		}
		if (tab == Tab.EMOTES) {
			Emote playing = Emotes.current();
			return playing == null ? "" : "emote:" + playing.id();
		}
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
				OfficialCapes.Cape official = OfficialCapes.byOwnId(selected);
				yield selected == null ? "none" : selected.startsWith("builtin:") ? selected
					: official != null ? "official:" + official.id() : "own:" + selected;
			}
			case CUSTOM -> "custom";
			case NONE -> "none";
			default -> "builtin:" + module.mode().name().toLowerCase(Locale.ROOT);
		};
	}

	/** Platz des Mini Me wählen ({@code minime:follow} …); ohne gültigen Namen geht es nicht. */
	private void chooseMiniMe(String key) {
		MiniMeModule mini = MiniMeModule.instance();
		if (mini == null) {
			return;
		}
		MiniMeModule.Place place = MiniMeModule.Place.valueOf(key.substring("minime:".length()).toUpperCase(Locale.ROOT));
		applyName();
		if (place != MiniMeModule.Place.NONE && !MiniMeSkins.NAME.matcher(mini.name()).matches()) {
			setNotice(Component.translatable("salami.cosmetics.minime.need_name"), true);
			nameFocused = true;
			return;
		}
		if (!mini.isEnabled()) {
			SalamiClient.get().modules().setEnabled(mini, true);
		}
		mini.setPlace(place);
		setNotice(Component.translatable(place == MiniMeModule.Place.NONE ? "salami.cosmetics.minime.removed" : "salami.cosmetics.minime.placed",
			I18n.get("salami.module.minime.setting.place." + place.name().toLowerCase(Locale.ROOT))), false);
		currentReadAt = 0;
		SalamiClient.get().config().markDirty();
	}

	/** Namen aus dem Feld übernehmen (lädt den Skin). */
	private void applyName() {
		MiniMeModule mini = MiniMeModule.instance();
		String name = nameBuffer.trim();
		if (mini == null || name.equals(mini.name())) {
			return;
		}
		if (!name.isEmpty() && !MiniMeSkins.NAME.matcher(name).matches()) {
			setNotice(Component.translatable("salami.cosmetics.minime.bad_name"), true);
			return;
		}
		mini.setName(name);
		SalamiClient.get().config().markDirty();
		if (!name.isEmpty()) {
			MiniMeSkins.state(name); // Laden anstoßen
			setNotice(Component.translatable("salami.cosmetics.minime.name_set", name), false);
		}
	}

	/** Für {@link SalamiClient#isTyping}: das Namensfeld hat die Tastatur. */
	public boolean isTyping() {
		return tab == Tab.MINIME && nameFocused;
	}

	/** Hut aufsetzen ({@code hat:santa}) oder abnehmen ({@code hat:none}). */
	private void chooseHat(String key) {
		HatsModule hats = HatsModule.instance();
		if (hats == null) {
			return;
		}
		if (!hats.isEnabled()) {
			SalamiClient.get().modules().setEnabled(hats, true);
		}
		Hat hat = HatCatalog.get(key.substring("hat:".length()));
		hats.choose(hat == null ? null : hat.id());
		setNotice(hat == null ? Component.translatable("salami.cosmetics.hat.removed")
			: Component.translatable("salami.cosmetics.hat.equipped", hat.displayName()), false);
		currentReadAt = 0;
		SalamiClient.get().config().markDirty();
	}

	/** Emote abspielen: Menü schließen, damit du es siehst. */
	private void playEmote(String key) {
		Emote emote = EmoteCatalog.get(key.substring("emote:".length()));
		EmotesModule module = EmotesModule.instance();
		if (emote == null || module == null) {
			return;
		}
		if (minecraft.player == null) {
			setNotice(Component.translatable("salami.cosmetics.emote.in_world"), true);
			return;
		}
		if (!module.isEnabled()) {
			SalamiClient.get().modules().setEnabled(module, true);
		}
		minecraft.setScreen(null);
		Emotes.play(emote);
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

	/**
	 * Offizielles Cape anlegen ({@code official:frost}): das Bild vom Server zu den eigenen Capes holen
	 * und auswählen. Der Server kennt das Bild schon, deshalb sehen es alle sofort.
	 */
	private void chooseOfficial(String key) {
		OfficialCapes.Cape cape = OfficialCapes.get(key.substring("official:".length()));
		if (cape == null) {
			return;
		}
		Path dir = CapeLibrary.launcherDir();
		String ownId = OfficialCapes.ownId(cape);
		if (dir != null && CapeLibrary.ownCapes(dir).stream().anyMatch(own -> own.id().equals(ownId))) {
			choose("own:" + ownId); // schon geholt
			return;
		}
		CompletableFuture<byte[]> download = OfficialCapes.download(cape);
		if (download == null) {
			setNotice(Component.translatable("salami.cosmetics.official.error"), true);
			return;
		}
		setNotice(Component.translatable("salami.cosmetics.official.loading", cape.displayName()), false);
		download.whenComplete((png, error) -> minecraft.execute(() -> {
			if (error != null || png == null) {
				SalamiClient.LOG.warn("Offizielles Cape {} nicht geladen: {}", cape.id(), String.valueOf(error));
				setNotice(Component.translatable("salami.cosmetics.official.error"), true);
				return;
			}
			try {
				if (dir != null) {
					CapeLibrary.adoptOfficial(dir, ownId, cape.displayName(), png);
					choose("own:" + ownId);
				} else {
					CapeLibrary.replaceCustom(png);
					choose("custom");
				}
			} catch (CapeLibrary.InvalidCapeException e) {
				setNotice(Component.translatable(e.getMessage()), true);
			} catch (IOException | RuntimeException e) {
				SalamiClient.LOG.warn("Offizielles Cape konnte nicht gespeichert werden: {}", e.toString());
				setNotice(Component.translatable("salami.cosmetics.error.save"), true);
			}
		}));
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

	/** Reiter oben, nebeneinander: {x0, y0, x1, y1}. */
	private int[] tabRect(Tab which) {
		int[] f = frame();
		int x = f[0] + PAD;
		for (Tab t : Tab.values()) {
			int w = font.width(I18n.get(t.key)) + 14;
			if (t == which) {
				return new int[] {x, f[1] + HEADER + 4, x + w, f[1] + HEADER + 4 + TABS - 2};
			}
			x += w + 4;
		}
		throw new IllegalArgumentException(String.valueOf(which));
	}

	private int[] content() {
		int[] f = frame();
		int top = f[1] + HEADER + 4 + TABS + 4 + (tab == Tab.MINIME ? NAME_ROW : 0);
		return new int[] {f[0] + PAD, top, f[2] - PAD, f[3] - PAD - 22};
	}

	private int columns() {
		int[] c = content();
		return Math.max(1, (c[2] - c[0] + GAP) / (CARD_W + GAP));
	}

	private int rows() {
		return (cards.size() + columns() - 1) / columns();
	}

	/** So viele Reihen passen ganz in den Inhalt (mindestens eine). */
	private int visibleRows() {
		int[] c = content();
		return Math.max(1, (c[3] - c[1] + GAP) / (CARD_H + GAP));
	}

	/** Ob die Kachel {@code index} in einer gerade sichtbaren Reihe liegt. */
	private boolean visible(int index) {
		int row = index / columns() - scrollRow;
		return row >= 0 && row < visibleRows();
	}

	/** Oberkante der sichtbaren Reihen: bei Platz für mehr als die ganzen Reihen mittig. */
	private int gridTop() {
		int[] c = content();
		int used = Math.min(rows(), visibleRows()) * (CARD_H + GAP) - GAP;
		return c[1] + Math.max(0, (c[3] - c[1] - used) / 2);
	}

	/** Linke obere Ecke der Kachel {@code index}, Raster zentriert. */
	private int[] cardPos(int index) {
		int[] c = content();
		int cols = columns();
		int gridW = cols * CARD_W + (cols - 1) * GAP;
		int left = c[0] + (c[2] - c[0] - gridW) / 2;
		return new int[] {left + (index % cols) * (CARD_W + GAP), gridTop() + (index / cols - scrollRow) * (CARD_H + GAP)};
	}

	private void clampScroll() {
		scrollRow = Math.max(0, Math.min(scrollRow, rows() - visibleRows()));
	}

	/** Namensfeld im Reiter Mini Me: {x0, y0, x1, y1}. */
	private int[] nameField() {
		int[] f = frame();
		int y = f[1] + HEADER + 4 + TABS + 6;
		return new int[] {f[0] + PAD + 22, y, f[0] + PAD + 22 + 140, y + 16};
	}

	private int[] nameButton() {
		int[] field = nameField();
		int w = font.width(I18n.get("salami.cosmetics.minime.apply")) + 12;
		return new int[] {field[2] + 4, field[1], field[2] + 4 + w, field[3]};
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

		// Reiter: Capes, Hüte, Mini Me
		int[] last = null;
		for (Tab t : Tab.values()) {
			int[] r = tabRect(t);
			boolean active = t == tab;
			boolean hovered = inside(r, mx, my);
			GuiDraw.roundRect(g, r[0], r[1], r[2] - r[0], r[3] - r[1], active ? theme.accent() : hovered ? theme.textDim() : theme.outline());
			if (!active) {
				GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - r[0] - 2, r[3] - r[1] - 2, hovered ? theme.header() : theme.panel() | 0xFF000000);
			}
			GuiDraw.text(g, font, I18n.get(t.key), r[0] + 7, r[1] + 4, active ? GuiDraw.textOn(theme.accent()) : theme.headerText(), false);
			last = r;
		}
		String soon = I18n.get("salami.cosmetics.more_soon");
		if (last[2] + 8 + font.width(soon) < f[2] - PAD) {
			GuiDraw.text(g, font, soon, last[2] + 8, last[1] + 4, theme.textDim(), false);
		}

		if (tab == Tab.MINIME) {
			renderNameRow(g, theme, mx, my);
		}
		if (tab == Tab.EMOTES && emoteRevision != EmoteCatalog.revision()
			|| tab == Tab.HATS && hatRevision != HatCatalog.revision()
			|| tab == Tab.CAPES && capeRevision != OfficialCapes.revision()) {
			reload(); // Neues vom Server, während das Menü offen ist
		}
		int[] c = content();
		long now = System.currentTimeMillis();
		if (now - currentReadAt > 1_000L) {
			currentKey = current();
			currentReadAt = now;
		}
		String current = currentKey;
		g.enableScissor(c[0], c[1], c[2], c[3]);
		for (int i = 0; i < cards.size(); i++) {
			if (!visible(i)) {
				continue;
			}
			int[] pos = cardPos(i);
			boolean hovered = mx >= pos[0] && mx < pos[0] + CARD_W && my >= pos[1] && my < pos[1] + CARD_H && inside(c, mx, my);
			renderCard(g, theme, cards.get(i), pos[0], pos[1], hovered, cards.get(i).key().equals(current));
		}
		g.disableScissor();
		renderScrollbar(g, theme, c);

		Component line = notice != null ? notice : Component.translatable(switch (tab) {
			case HATS -> "salami.cosmetics.hat.info";
			case EMOTES -> "salami.cosmetics.emote.info";
			case MINIME -> "salami.cosmetics.minime.info";
			default -> "salami.cosmetics.info";
		});
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
		int py = y + PREVIEW_Y;
		switch (card.key()) {
			case "none", "hat:none", "minime:none" -> {
				GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.textDim());
				GuiDraw.centeredText(g, font, "—", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.textDim());
			}
			case "upload" -> {
				GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, hovered ? theme.accent() : theme.textDim());
				g.fill(px + PREVIEW_W / 2 - 1, py + 12, px + PREVIEW_W / 2 + 1, py + PREVIEW_H - 12, hovered ? theme.accent() : theme.text());
				g.fill(px + 5, py + PREVIEW_H / 2 - 1, px + PREVIEW_W - 5, py + PREVIEW_H / 2 + 1, hovered ? theme.accent() : theme.text());
			}
			case "minime:hand", "minime:shoulder", "minime:head", "minime:follow" -> {
				int size = PREVIEW_W;
				renderFace(g, miniMeTexture(), x + (CARD_W - size) / 2, py + (PREVIEW_H - size) / 2, size);
			}
			default -> {
				Emote emote = card.key().startsWith("emote:") ? EmoteCatalog.get(card.key().substring("emote:".length())) : null;
				if (emote != null) {
					// Strichfigur in der Haltung des Emotes
					int size = PREVIEW_H - 3;
					int ix = x + (CARD_W - size) / 2;
					g.blit(RenderPipelines.GUI_TEXTURED, EmoteIcons.texture(emote), ix, py + 2, 0f, 0f, size, size,
						EmoteIcons.SIZE, EmoteIcons.SIZE, EmoteIcons.SIZE, EmoteIcons.SIZE);
					break;
				}
				Hat hat = card.key().startsWith("hat:") ? HatCatalog.get(card.key().substring("hat:".length())) : null;
				if (hat != null) {
					// das Modell des Hutes, quadratisch in der Mitte der Vorschau
					int size = PREVIEW_H - 3;
					int ix = x + (CARD_W - size) / 2;
					g.blit(RenderPipelines.GUI_TEXTURED, HatIcons.texture(hat), ix, py + 2, 0f, 0f, size, size,
						HatIcons.SIZE, HatIcons.SIZE, HatIcons.SIZE, HatIcons.SIZE);
					break;
				}
				Identifier texture = card.texture();
				int textureWidth = card.textureWidth();
				OfficialCapes.Cape official = card.key().startsWith("official:")
					? OfficialCapes.get(card.key().substring("official:".length())) : null;
				if (official != null) {
					texture = OfficialCapes.preview(official);
					textureWidth = official.width();
				}
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
				} else if (card.builtin() != null || official != null) {
					// wird gerade gemalt (wenige Zehntelsekunden) oder vom Server geladen
					GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.outline());
					GuiDraw.centeredText(g, font, "…", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.textDim());
				} else {
					GuiDraw.outline(g, px, py, PREVIEW_W, PREVIEW_H, theme.warning());
					GuiDraw.centeredText(g, font, "?", px + PREVIEW_W / 2, py + PREVIEW_H / 2 - 4, theme.warning());
				}
			}
		}
		int labelY = py + PREVIEW_H + 5;
		renderLabel(g, card.label(), x + CARD_W / 2, labelY, CARD_W - 6, equipped ? theme.headerText() : theme.text());
		if (equipped) {
			renderLabel(g, I18n.get("salami.cosmetics.equipped_badge"), x + CARD_W / 2, labelY + 11, CARD_W - 6, theme.accent());
		} else if (card.key().startsWith("own:") || card.key().equals("custom")) {
			renderLabel(g, I18n.get("salami.cosmetics.own_badge"), x + CARD_W / 2, labelY + 11, CARD_W - 6, theme.textDim());
		} else if (card.key().startsWith("official:")) {
			renderLabel(g, I18n.get("salami.cosmetics.official_badge"), x + CARD_W / 2, labelY + 11, CARD_W - 6, theme.textDim());
		}
	}

	/** Zentrierter Text; zu lange Namen werden erst verkleinert, dann mit „…“ gekürzt. */
	private void renderLabel(GuiGraphics g, String text, int centerX, int y, int maxWidth, int color) {
		int width = font.width(text);
		if (width <= maxWidth) {
			GuiDraw.centeredText(g, font, text, centerX, y, color);
			return;
		}
		float scale = Math.max(MIN_LABEL_SCALE, maxWidth / (float) width);
		String shown = text;
		if (width * scale > maxWidth) {
			int room = (int) (maxWidth / scale) - font.width("…");
			shown = font.plainSubstrByWidth(text, room).stripTrailing() + "…";
		}
		g.pose().pushMatrix();
		g.pose().translate(centerX - font.width(shown) * scale / 2f, y + (1 - scale) * 4);
		g.pose().scale(scale, scale);
		GuiDraw.text(g, font, shown, 0, 0, color, false);
		g.pose().popMatrix();
	}

	/** Gesicht des Mini-Me-Skins (mit Kopfbedeckungs-Schicht) als Quadrat. */
	private void renderFace(GuiGraphics g, Identifier texture, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
	}

	private Identifier miniMeTexture() {
		MiniMeModule mini = MiniMeModule.instance();
		MiniMeSkins.Skin skin = mini == null ? null : MiniMeSkins.get(mini.name());
		return skin != null ? skin.texture() : DefaultPlayerSkin.getDefaultTexture();
	}

	/** Gesicht, Namensfeld, Übernehmen und der Ladezustand des Skins. */
	private void renderNameRow(GuiGraphics g, Theme theme, int mx, int my) {
		int[] field = nameField();
		renderFace(g, miniMeTexture(), field[0] - 20, field[1], 16);
		GuiDraw.roundRect(g, field[0], field[1], field[2] - field[0], field[3] - field[1], nameFocused ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, field[0] + 1, field[1] + 1, field[2] - field[0] - 2, field[3] - field[1] - 2, 0xFF0C0C10);
		if (nameBuffer.isEmpty() && !nameFocused) {
			GuiDraw.text(g, font, I18n.get("salami.cosmetics.minime.placeholder"), field[0] + 4, field[1] + 4, theme.textDim(), false);
		} else {
			GuiDraw.text(g, font, nameBuffer, field[0] + 4, field[1] + 4, theme.text(), false);
		}
		if (nameFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = field[0] + 4 + font.width(nameBuffer);
			g.fill(cx, field[1] + 3, cx + 1, field[3] - 3, theme.text());
		}
		int[] b = nameButton();
		boolean hovered = inside(b, mx, my);
		GuiDraw.roundRect(g, b[0], b[1], b[2] - b[0], b[3] - b[1], theme.accent());
		if (hovered) {
			GuiDraw.roundRect(g, b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2, 0x30FFFFFF);
		}
		GuiDraw.text(g, font, I18n.get("salami.cosmetics.minime.apply"), b[0] + 6, b[1] + 4, GuiDraw.textOn(theme.accent()), false);
		MiniMeModule mini = MiniMeModule.instance();
		String name = mini == null ? "" : mini.name();
		if (!name.isEmpty()) {
			String key = switch (MiniMeSkins.state(name)) {
				case LOADING -> "salami.cosmetics.minime.loading";
				case READY -> "salami.cosmetics.minime.ready";
				case NOT_FOUND -> "salami.cosmetics.minime.not_found";
			};
			int color = MiniMeSkins.state(name) == MiniMeSkins.State.NOT_FOUND ? theme.warning() : theme.textDim();
			GuiDraw.text(g, font, I18n.get(key, name), b[2] + 8, b[1] + 4, color, false);
		}
	}

	/** Schmale Leiste rechts neben den Karten, nur wenn nicht alle Reihen passen. */
	private void renderScrollbar(GuiGraphics g, Theme theme, int[] c) {
		int rows = rows();
		int shown = visibleRows();
		if (rows <= shown) {
			return;
		}
		int x = c[2] + 4;
		int top = gridTop();
		int height = shown * (CARD_H + GAP) - GAP;
		g.fill(x, top, x + 2, top + height, theme.outline());
		int thumb = Math.max(12, height * shown / rows);
		int offset = (height - thumb) * scrollRow / Math.max(1, rows - shown);
		g.fill(x, top + offset, x + 2, top + offset + thumb, theme.accent());
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
		if (tab == Tab.MINIME) {
			if (inside(nameField(), x, y)) {
				nameFocused = true;
				return true;
			}
			if (inside(nameButton(), x, y)) {
				applyName();
				nameFocused = false;
				return true;
			}
			nameFocused = false;
		}
		for (Tab t : Tab.values()) {
			if (t != tab && inside(tabRect(t), x, y)) {
				tab = t;
				scrollRow = 0;
				notice = null;
				reload();
				currentReadAt = 0;
				return true;
			}
		}
		int[] c = content();
		if (!inside(c, x, y)) {
			return super.mouseClicked(event, doubleClick);
		}
		for (int i = 0; i < cards.size(); i++) {
			if (!visible(i)) {
				continue;
			}
			int[] pos = cardPos(i);
			if (x >= pos[0] && x < pos[0] + CARD_W && y >= pos[1] && y < pos[1] + CARD_H) {
				pick(cards.get(i).key());
				return true;
			}
		}
		return true;
	}

	/**
	 * Kachel wählen, als hätte man sie angeklickt: {@code builtin:board}, {@code official:frost},
	 * {@code own:<id>}, {@code hat:crown}, {@code emote:wave}, {@code minime:head}, {@code upload} …
	 */
	public void pick(String key) {
		if (key.equals("upload")) {
			upload();
		} else if (key.startsWith("official:")) {
			chooseOfficial(key);
		} else if (key.startsWith("hat:")) {
			chooseHat(key);
		} else if (key.startsWith("emote:")) {
			playEmote(key);
		} else if (key.startsWith("minime:")) {
			chooseMiniMe(key);
		} else {
			choose(key);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isTyping()) {
			switch (event.key()) {
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
					applyName();
					nameFocused = false;
				}
				case InputConstants.KEY_ESCAPE -> nameFocused = false;
				case InputConstants.KEY_BACKSPACE -> {
					if (!nameBuffer.isEmpty()) {
						nameBuffer = nameBuffer.substring(0, nameBuffer.length() - 1);
					}
				}
				default -> {
					// Zeichen kommen über charTyped
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (isTyping()) {
			String typed = event.codepointAsString();
			// Minecraft-Namen: nur Buchstaben, Ziffern, _ und höchstens 16 Zeichen
			if (typed.matches("[A-Za-z0-9_]") && nameBuffer.length() < 16) {
				nameBuffer += typed;
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		if (nameFocused) {
			applyName();
		}
		releasePreviews();
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			scrollRow -= (int) Math.signum(scrollY);
			clampScroll();
		}
		return true;
	}
}
