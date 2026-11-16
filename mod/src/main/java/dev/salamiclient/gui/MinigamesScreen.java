package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.minigames.Colors;
import dev.salamiclient.minigames.GameKey;
import dev.salamiclient.minigames.Highscores;
import dev.salamiclient.minigames.Minigame;
import dev.salamiclient.minigames.Minigames;
import dev.salamiclient.minigames.Sfx;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Minispiele: Menü mit einer Karte pro Spiel (Vorschau, Name, Rekord), Bestenliste und das
 * laufende Spiel mit Infospalte, Steuerungshinweisen, Pause- und Ergebnis-Fenster.
 *
 * <p>Die Spiellogik läuft in festen Schritten ({@link Minigame#STEPS_PER_SECOND}) unabhängig von den
 * FPS; gezeichnet wird mit dem Anteil bis zum nächsten Schritt (weiche Bewegungen). Verliert das
 * Fenster den Fokus, pausiert das Spiel. Bestenlisten liegen in {@code config/salami/minigames.json}.
 *
 * <p>Tasten: Esc pausiert (im Menü: zurück), R startet neu, M schaltet den Ton, P pausiert.
 */
public final class MinigamesScreen extends Screen {
	private static final int HEADER = 22;
	private static final int PAD = 10;
	private static final int GAP = 8;
	private static final int INFO_W = 88;
	private static final long STEP_NANOS = 1_000_000_000L / Minigame.STEPS_PER_SECOND;
	/** Mehr holt die Logik nach einem Hänger nicht auf (sonst „springt“ das Spiel). */
	private static final long MAX_FRAME_NANOS = 250_000_000L;
	private static final int[] MEDALS = {0xFFFFC857, 0xFFC9CCD6, 0xFFD08A55};

	private static Highscores highscores;
	private static boolean pauseOnFocusLoss = true;

	private enum View { MENU, LEADERBOARD, GAME }

	/** Klickfläche aus dem zuletzt gezeichneten Bild (UI-Koordinaten). */
	private record Button(float x0, float y0, float x1, float y1, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	/** Ein Konfetti-Schnipsel für neue Rekorde. */
	private static final class Confetti {
		float x;
		float y;
		float vx;
		float vy;
		final int color;
		final float size;

		Confetti(float x, float y, float vx, float vy, int color, float size) {
			this.x = x;
			this.y = y;
			this.vx = vx;
			this.vy = vy;
			this.color = color;
			this.size = size;
		}
	}

	private final Screen parent;
	private final List<Minigame> previews = Minigames.previews();
	private final MinigameCanvas canvas = new MinigameCanvas();
	private final List<Button> buttons = new ArrayList<>();
	private final List<Confetti> confetti = new ArrayList<>();
	private final Random random = new Random();
	private float uiScale = 1f;
	private View view = View.MENU;
	/** Menü: per Tastatur gewählte Karte; Bestenliste: gewähltes Spiel. */
	private int selected;
	private boolean keyboardNav;
	private int lastCols = 3;
	private float mouseX;
	private float mouseY;

	private Minigame game;
	private int gameIndex;
	private boolean paused;
	private boolean frozen;
	private long lastNanos = System.nanoTime();
	private long accumulator;
	private float frameSeconds;
	private boolean submitted;
	private boolean resultShown;
	private boolean newRecord;
	private int rank = -1;
	private int bestBefore;
	private int overlayIndex;
	private float boardX;
	private float boardY;
	private float boardScale = 1f;

	public MinigamesScreen(Screen parent) {
		super(Component.translatable("salami.minigames.title"));
		this.parent = parent;
	}

	/**
	 * Öffnet das Minispiele-Menü (im Client-Thread aufrufen, z. B. aus einem Client-Befehl oder einer
	 * Taste). {@code parent} erscheint beim Schließen wieder und darf {@code null} sein.
	 */
	public static MinigamesScreen open(Screen parent) {
		MinigamesScreen screen = new MinigamesScreen(parent);
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(screen);
		return screen;
	}

	/** Bestenlisten und Ton-Schalter (beim ersten Zugriff aus der Datei geladen). */
	public static Highscores highscores() {
		if (highscores == null) {
			highscores = new Highscores(FabricLoader.getInstance().getConfigDir().resolve(SalamiClient.MOD_ID).resolve("minigames.json"))
				.logTo(SalamiClient.LOG::warn);
			highscores.load();
		}
		return highscores;
	}

	/** Automatische Pause bei Fokusverlust (Tests schalten sie ab: das Testfenster hat oft keinen Fokus). */
	public static void setPauseOnFocusLoss(boolean pause) {
		pauseOnFocusLoss = pause;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void onClose() {
		recordAbandoned();
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		recordAbandoned();
		super.removed();
	}

	// ------------------------------------------------------------------ Steuerung (auch für Tests)

	/** Startet Spiel Nummer {@code index} (Reihenfolge wie im Menü). */
	public void startGame(int index) {
		recordAbandoned();
		gameIndex = index;
		selected = index;
		game = Minigames.create(index);
		game.restart();
		beginRun();
		view = View.GAME;
	}

	/** Spiel im Vorschau-Zustand ({@link Minigame#demo()}) zeigen, z. B. für Screenshots – zählt nicht für die Bestenliste. */
	public void startDemo(int index) {
		startGame(index);
		game.demo();
		submitted = true;
	}

	/** Das laufende Spiel oder {@code null} (Menü, Bestenliste). */
	public Minigame game() {
		return view == View.GAME ? game : null;
	}

	/** Bestenliste eines Spiels anzeigen. */
	public void showLeaderboard(int index) {
		recordAbandoned();
		game = null;
		paused = false;
		selected = index;
		view = View.LEADERBOARD;
	}

	public void showMenu() {
		recordAbandoned();
		game = null;
		paused = false;
		view = View.MENU;
	}

	/** Zeit anhalten: keine Spielschritte mehr, bis wieder freigegeben (Screenshots in Tests). */
	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
		lastNanos = System.nanoTime();
	}

	/** Ob gerade ein Spiel läuft (nicht pausiert, nicht vorbei) – dann gehört die Tastatur dem Spiel. */
	public boolean isPlaying() {
		return view == View.GAME && game != null && !paused && !game.isOver();
	}

	public boolean isPaused() {
		return paused;
	}

	public void pause() {
		if (isPlaying()) {
			paused = true;
			overlayIndex = 0;
			game.releaseAll();
		}
	}

	public void resume() {
		if (paused) {
			paused = false;
			lastNanos = System.nanoTime();
		}
	}

	private void restartGame() {
		if (game == null) {
			return;
		}
		recordAbandoned();
		game.restart();
		beginRun();
	}

	private void beginRun() {
		paused = false;
		submitted = false;
		resultShown = false;
		newRecord = false;
		rank = -1;
		bestBefore = highscores().best(game.id());
		accumulator = 0;
		lastNanos = System.nanoTime();
		overlayIndex = 0;
		confetti.clear();
	}

	/** Ergebnis eintragen (einmal pro Spiel). */
	private void submit() {
		if (submitted || game == null) {
			return;
		}
		submitted = true;
		int score = game.score();
		bestBefore = highscores().best(game.id());
		rank = highscores().submit(game.id(), playerName(), score, LocalDate.now());
		newRecord = score > 0 && score > bestBefore;
	}

	/** Abgebrochene Spiele zählen auch – mit dem bis dahin erreichten Stand. */
	private void recordAbandoned() {
		if (game != null && !submitted && game.score() > 0) {
			submit();
		}
	}

	private String playerName() {
		return minecraft != null && minecraft.getUser() != null ? minecraft.getUser().getName() : "Salami";
	}

	// ------------------------------------------------------------------ Zeit

	private void advance() {
		long now = System.nanoTime();
		long frame = Math.max(0, Math.min(now - lastNanos, MAX_FRAME_NANOS));
		lastNanos = now;
		frameSeconds = frame / 1_000_000_000f;
		if (view != View.GAME || game == null || paused || frozen) {
			return;
		}
		accumulator += frame;
		while (accumulator >= STEP_NANOS) {
			game.step();
			accumulator -= STEP_NANOS;
		}
		if (game.overlayReady() && !resultShown) {
			resultShown = true;
			overlayIndex = 0;
			submit();
			if (newRecord) {
				uiSound(Sfx.BIG_CLEAR);
				spawnConfetti();
			}
		}
	}

	private float partial() {
		return Math.min(1f, accumulator / (float) STEP_NANOS);
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Theme theme = SalamiClient.get().theme();
		int base = Colors.opaque(theme.panel());
		int top = Colors.mix(base, 0xFF000000, 0.45f);
		int bottom = Colors.mix(base, Colors.opaque(theme.header()), 0.2f);
		if (minecraft.level != null) {
			top = Colors.alpha(top, 0.92f);
			bottom = Colors.alpha(bottom, 0.92f);
		}
		g.fillGradient(0, 0, width, height, top, bottom);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		if (pauseOnFocusLoss && isPlaying() && !minecraft.isWindowActive()) {
			pause();
		}
		advance();
		Theme theme = SalamiClient.get().theme();
		this.mouseX = mouseX / uiScale;
		this.mouseY = mouseY / uiScale;
		buttons.clear();
		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		switch (view) {
			case MENU -> drawMenu(g, theme);
			case LEADERBOARD -> drawLeaderboard(g, theme);
			case GAME -> drawGame(g, theme);
		}
		g.pose().popMatrix();
		if (game != null && view == View.GAME) {
			for (Minigame.Sound sound : game.drainSounds()) {
				play(sound.sfx(), sound.pitch());
			}
		}
	}

	private int uiW() {
		return Math.round(width / uiScale);
	}

	private int uiH() {
		return Math.round(height / uiScale);
	}

	/** Bildschirmpixel pro UI-Einheit. */
	private float pixels() {
		return minecraft.getWindow().getGuiScale() * uiScale;
	}

	private float snap(float value) {
		float px = pixels();
		return Math.round(value * px) / px;
	}

	private boolean hovered(float x0, float y0, float x1, float y1) {
		return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
	}

	private void text(GuiGraphics g, String text, int x, int y, int color) {
		GuiDraw.text(g, font, text, x, y, color, false);
	}

	private void centered(GuiGraphics g, String text, int centerX, int y, int color) {
		GuiDraw.text(g, font, text, centerX - font.width(text) / 2, y, color, false);
	}

	/** Text in {@code scale}-facher Größe, mittig um {@code centerX}. */
	private void bigText(GuiGraphics g, String text, float centerX, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(snap(centerX - font.width(text) * scale / 2), snap(y));
		g.pose().scale(scale, scale);
		GuiDraw.text(g, font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
	}

	/** Knopf mit Text; liefert die rechte Kante. */
	private int textButton(GuiGraphics g, Theme theme, int x, int y, String label, Runnable action) {
		int w = font.width(label) + 12;
		boolean hover = hovered(x, y, x + w, y + 14);
		g.fill(x, y, x + w, y + 14, hover ? theme.accent() : theme.panel());
		GuiDraw.outline(g, x, y, w, 14, hover ? theme.accent() : theme.outline());
		text(g, label, x + 6, y + 3, hover ? 0xFF000000 : theme.headerText());
		buttons.add(new Button(x, y, x + w, y + 14, action));
		return x + w;
	}

	/** Kopfzeile mit Titel, Zurück-Knopf und Ton-Schalter; liefert die x-Position links vom Ton-Schalter. */
	private int header(GuiGraphics g, Theme theme, String title, String back, Runnable backAction) {
		int w = uiW();
		g.fill(0, 0, w, HEADER - 2, theme.header());
		g.fill(0, HEADER - 2, w, HEADER - 1, theme.outline());
		int backRight = textButton(g, theme, 4, 4, back, backAction);
		int muteX = w - 4 - 18;
		iconButton(g, theme, muteX, 4, highscores().muted() ? Icon.MUTED : Icon.SOUND, this::toggleMute);
		int titleMax = Math.max(20, 2 * Math.min(w / 2 - backRight - 6, muteX - 6 - w / 2));
		centered(g, fit(title, titleMax), w / 2, 7, theme.headerText());
		return muteX - 4;
	}

	private enum Icon { SOUND, MUTED, PAUSE, PLAY, TROPHY }

	private void iconButton(GuiGraphics g, Theme theme, int x, int y, Icon icon, Runnable action) {
		boolean hover = hovered(x, y, x + 18, y + 14);
		g.fill(x, y, x + 18, y + 14, hover ? theme.accent() : theme.panel());
		GuiDraw.outline(g, x, y, 18, 14, hover ? theme.accent() : theme.outline());
		drawIcon(g, x + 1, y, icon, hover ? 0xFF000000 : theme.headerText(), theme.warning());
		buttons.add(new Button(x, y, x + 18, y + 14, action));
	}

	private static void drawIcon(GuiGraphics g, int x, int y, Icon icon, int color, int warning) {
		switch (icon) {
			case SOUND, MUTED -> {
				g.fill(x + 3, y + 5, x + 6, y + 9, color);
				g.fill(x + 6, y + 4, x + 7, y + 10, color);
				g.fill(x + 7, y + 3, x + 8, y + 11, color);
				if (icon == Icon.MUTED) {
					for (int k = 0; k < 5; k++) {
						g.fill(x + 10 + k, y + 4 + k, x + 11 + k, y + 5 + k, warning);
						g.fill(x + 14 - k, y + 4 + k, x + 15 - k, y + 5 + k, warning);
					}
				} else {
					g.fill(x + 10, y + 5, x + 11, y + 9, color);
					g.fill(x + 12, y + 3, x + 13, y + 11, color);
				}
			}
			case PAUSE -> {
				g.fill(x + 5, y + 3, x + 7, y + 11, color);
				g.fill(x + 10, y + 3, x + 12, y + 11, color);
			}
			case PLAY -> {
				for (int k = 0; k < 4; k++) {
					g.fill(x + 6 + k, y + 3 + k, x + 7 + k, y + 11 - k, color);
				}
			}
			case TROPHY -> {
				g.fill(x + 3, y + 2, x + 13, y + 3, color);
				g.fill(x + 4, y + 3, x + 12, y + 7, color);
				g.fill(x + 5, y + 7, x + 11, y + 8, color);
				g.fill(x + 1, y + 3, x + 3, y + 4, color);
				g.fill(x + 1, y + 4, x + 2, y + 6, color);
				g.fill(x + 13, y + 3, x + 15, y + 4, color);
				g.fill(x + 14, y + 4, x + 15, y + 6, color);
				g.fill(x + 7, y + 8, x + 9, y + 10, color);
				g.fill(x + 5, y + 10, x + 11, y + 12, color);
			}
		}
	}

	/** Steuerungshinweise unten, bei Platzmangel auf mehrere Zeilen verteilt. */
	private List<String> hintLines(List<String> parts) {
		int maxWidth = uiW() - 2 * PAD;
		String separator = "  ·  ";
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String part : parts) {
			String candidate = line.isEmpty() ? part : line + separator + part;
			if (!line.isEmpty() && font.width(candidate) > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(part);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private void footer(GuiGraphics g, Theme theme, List<String> lines) {
		int y = uiH() - 4 - lines.size() * 10;
		for (String line : lines) {
			centered(g, line, uiW() / 2, y, theme.textDim());
			y += 10;
		}
	}

	/** Zeichnet ein Spiel eingepasst in ein Rechteck; liefert {Ursprung x, Ursprung y, Maßstab}. */
	private float[] drawGameInto(GuiGraphics g, Theme theme, Minigame target, float x0, float y0, float x1, float y1, float partial) {
		float s = Math.min((x1 - x0) / target.width(), (y1 - y0) / target.height());
		float ox = snap(x0 + (x1 - x0 - target.width() * s) / 2);
		float oy = snap(y0 + (y1 - y0 - target.height() * s) / 2);
		canvas.begin(g, font, theme, ox, oy, s, pixels());
		target.render(canvas, partial);
		return new float[] {ox, oy, s};
	}

	// ------------------------------------------------------------------ Menü

	private void drawMenu(GuiGraphics g, Theme theme) {
		int right = header(g, theme, I18n.get("salami.minigames.title"), I18n.get("salami.mods.back"), this::onClose);
		String board = I18n.get("salami.minigames.leaderboard");
		textButton(g, theme, right - font.width(board) - 12, 4, board, () -> showLeaderboard(selected));

		List<String> hints = hintLines(List.of(I18n.get("salami.minigames.hint.menu"), I18n.get("salami.minigames.controls.mute"),
			I18n.get("salami.minigames.hint.back")));
		int count = previews.size();
		int top = HEADER + PAD;
		int bottom = uiH() - hints.size() * 10 - 8;
		int availW = uiW() - 2 * PAD;
		int availH = bottom - top;
		int cols = (availW - 2 * GAP) / 3 >= 92 ? 3 : 2;
		int rows = (count + cols - 1) / cols;
		int cardW = Math.min(190, (availW - (cols - 1) * GAP) / cols);
		int cardH = Math.min(150, (availH - (rows - 1) * GAP) / rows);
		lastCols = cols;
		int gridW = cols * cardW + (cols - 1) * GAP;
		int gridH = rows * cardH + (rows - 1) * GAP;
		int x0 = (uiW() - gridW) / 2;
		int y0 = top + Math.max(0, (availH - gridH) / 2);
		for (int i = 0; i < count; i++) {
			card(g, theme, i, x0 + (i % cols) * (cardW + GAP), y0 + (i / cols) * (cardH + GAP), cardW, cardH);
		}
		footer(g, theme, hints);
	}

	private void card(GuiGraphics g, Theme theme, int index, int x, int y, int w, int h) {
		Minigame preview = previews.get(index);
		boolean active = hovered(x, y, x + w, y + h) || (keyboardNav && selected == index);
		g.nextStratum();
		g.fill(x, y, x + w, y + h, theme.panel());
		int previewBottom = y + h - 26;
		if (previewBottom - (y + 4) >= 14) {
			g.fill(x + 4, y + 4, x + w - 4, previewBottom, Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.5f));
			g.enableScissor(x + 4, y + 4, x + w - 4, previewBottom);
			drawGameInto(g, theme, preview, x + 7, y + 7, x + w - 7, previewBottom - 3, 0f);
			g.disableScissor();
			g.nextStratum();
		}
		if (active) {
			g.fill(x, y, x + w, y + h, theme.hover());
		}
		GuiDraw.outline(g, x, y, w, h, active ? theme.accent() : theme.outline());
		if (active) {
			GuiDraw.outline(g, x + 1, y + 1, w - 2, h - 2, Colors.alpha(theme.accent(), 0.4f));
		}
		text(g, fit(I18n.get(preview.nameKey()), w - 30), x + 6, y + h - 22, theme.headerText());
		int best = highscores().best(preview.id());
		String bestText = best > 0 ? I18n.get("salami.minigames.best", best) : I18n.get("salami.minigames.no_best");
		text(g, fit(bestText, w - 30), x + 6, y + h - 11, best > 0 ? theme.accent() : theme.textDim());
		buttons.add(new Button(x, y, x + w, y + h, () -> startGame(index)));
		// Pokal: Bestenliste dieses Spiels
		int tx = x + w - 22;
		int ty = y + h - 21;
		boolean trophyHover = hovered(tx, ty, tx + 18, ty + 14);
		if (trophyHover) {
			g.fill(tx, ty, tx + 18, ty + 14, theme.accent());
		}
		drawIcon(g, tx + 1, ty, Icon.TROPHY, trophyHover ? 0xFF000000 : best > 0 ? Colors.GOLD : theme.textDim(), 0);
		buttons.add(new Button(tx, ty, tx + 18, ty + 14, () -> showLeaderboard(index)));
	}

	// ------------------------------------------------------------------ Bestenliste

	private void drawLeaderboard(GuiGraphics g, Theme theme) {
		header(g, theme, I18n.get("salami.minigames.leaderboard"), I18n.get("salami.minigames.menu_back"), this::showMenu);
		List<String> hints = hintLines(List.of(I18n.get("salami.minigames.hint.leaderboard"), I18n.get("salami.minigames.hint.back")));
		int top = HEADER + PAD;
		int bottom = uiH() - hints.size() * 10 - 8;
		int tabsW = Math.min(130, uiW() / 3);
		int x0 = PAD;
		for (int i = 0; i < previews.size(); i++) {
			int y = top + i * 20;
			int index = i;
			boolean current = i == selected;
			boolean hover = hovered(x0, y, x0 + tabsW, y + 18);
			g.fill(x0, y, x0 + tabsW, y + 18, current ? theme.header() : hover ? theme.hover() : theme.panel());
			if (current) {
				g.fill(x0, y, x0 + 2, y + 18, theme.accent());
			}
			text(g, fit(I18n.get(previews.get(i).nameKey()), tabsW - 12), x0 + 8, y + 5, current ? theme.headerText() : theme.text());
			buttons.add(new Button(x0, y, x0 + tabsW, y + 18, () -> selected = index));
		}

		int tx0 = x0 + tabsW + GAP;
		int tx1 = uiW() - PAD;
		g.fill(tx0, top, tx1, bottom, theme.panel());
		GuiDraw.outline(g, tx0, top, tx1 - tx0, bottom - top, theme.outline());
		Minigame shown = previews.get(selected);
		String id = shown.id();
		centered(g, fit(I18n.get(shown.nameKey()), tx1 - tx0 - 12), (tx0 + tx1) / 2, top + 8, theme.headerText());

		List<Highscores.Entry> entries = highscores().top(id);
		int dateW = font.width(formatDate("2000-10-20"));
		for (Highscores.Entry entry : entries) {
			dateW = Math.max(dateW, font.width(formatDate(entry.date())));
		}
		int scoreW = font.width("0000000");
		int dateX = tx1 - 8 - dateW;
		int nameX = tx0 + 26;
		boolean showDate = dateX - 10 - scoreW - nameX >= 50;
		int scoreRight = showDate ? dateX - 10 : tx1 - 8;
		int headY = top + 26;
		text(g, "#", tx0 + 10, headY, theme.textDim());
		text(g, I18n.get("salami.minigames.column.name"), nameX, headY, theme.textDim());
		String scoreHead = I18n.get("salami.minigames.column.score");
		text(g, scoreHead, scoreRight - font.width(scoreHead), headY, theme.textDim());
		if (showDate) {
			text(g, I18n.get("salami.minigames.column.date"), dateX, headY, theme.textDim());
		}
		g.fill(tx0 + 6, headY + 11, tx1 - 6, headY + 12, theme.outline());
		if (entries.isEmpty()) {
			centered(g, I18n.get("salami.minigames.empty"), (tx0 + tx1) / 2, headY + 26, theme.textDim());
		}
		String me = playerName();
		for (int i = 0; i < entries.size(); i++) {
			Highscores.Entry entry = entries.get(i);
			int y = headY + 17 + i * 15;
			if (entry.name().equals(me)) {
				g.fill(tx0 + 4, y - 3, tx1 - 4, y + 11, theme.enabledRow());
			}
			text(g, String.valueOf(i + 1), tx0 + 10, y, i < MEDALS.length ? MEDALS[i] : theme.textDim());
			String score = String.valueOf(entry.score());
			text(g, score, scoreRight - font.width(score), y, theme.accent());
			text(g, fit(displayName(entry.name()), scoreRight - font.width(score) - 8 - nameX), nameX, y, theme.text());
			if (showDate) {
				text(g, formatDate(entry.date()), dateX, y, theme.textDim());
			}
		}
		String play = I18n.get("salami.minigames.play");
		int playW = font.width(play) + 12;
		int playY = Math.max(headY + 17 + Highscores.MAX_ENTRIES * 15 + 6, bottom - 22);
		if (playY + 14 <= bottom - 4) {
			textButton(g, theme, (tx0 + tx1 - playW) / 2, playY, play, () -> startGame(selected));
		}
		footer(g, theme, hints);
	}

	private static String displayName(String name) {
		return StreamerModeModule.hideName(Component.literal(name)).getString();
	}

	private static String formatDate(String iso) {
		try {
			return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(I18n.get("salami.minigames.date_pattern")));
		} catch (Exception e) {
			return iso;
		}
	}

	// ------------------------------------------------------------------ Spiel

	private List<String> gameHints() {
		List<String> parts = new ArrayList<>();
		for (String key : game.controls()) {
			parts.add(I18n.get(key));
		}
		parts.add(I18n.get("salami.minigames.controls.pause"));
		parts.add(I18n.get("salami.minigames.controls.restart"));
		parts.add(I18n.get("salami.minigames.controls.mute"));
		return hintLines(parts);
	}

	private void drawGame(GuiGraphics g, Theme theme) {
		int right = header(g, theme, I18n.get(game.nameKey()), I18n.get("salami.minigames.menu_back"), this::showMenu);
		if (!game.isOver()) {
			iconButton(g, theme, right - 18, 4, paused ? Icon.PLAY : Icon.PAUSE, () -> {
				if (paused) {
					resume();
				} else {
					pause();
				}
			});
		}
		List<String> hints = gameHints();
		int top = HEADER + PAD;
		int bottom = uiH() - hints.size() * 10 - 8;
		float availW = uiW() - 2 * PAD - INFO_W - GAP;
		float availH = bottom - top;
		float s = Math.max(0.1f, Math.min(availW / game.width(), availH / game.height()));
		float bw = game.width() * s;
		float bh = game.height() * s;
		float gx = PAD + (uiW() - 2 * PAD - (INFO_W + GAP + bw)) / 2;
		boardX = snap(gx + INFO_W + GAP);
		boardY = snap(top + (availH - bh) / 2);
		boardScale = s;

		infoPanel(g, theme, Math.round(gx), Math.round(boardY), Math.round(bh));

		g.nextStratum();
		float frame = 3;
		MinigameCanvas.fill(g, boardX - frame, boardY - frame, boardX + bw + frame, boardY + bh + frame, theme.outline());
		MinigameCanvas.fill(g, boardX - frame + 1, boardY - frame + 1, boardX + bw + frame - 1, boardY + bh + frame - 1,
			Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.5f));
		g.enableScissor((int) Math.floor(boardX - frame), (int) Math.floor(boardY - frame),
			(int) Math.ceil(boardX + bw + frame), (int) Math.ceil(boardY + bh + frame));
		canvas.begin(g, font, theme, boardX, boardY, s, pixels());
		game.render(canvas, partial());
		g.disableScissor();

		g.nextStratum();
		if (paused || game.overlayReady()) {
			overlay(g, theme, bw, bh);
		}
		updateConfetti(g);
		footer(g, theme, hints);
	}

	private void infoPanel(GuiGraphics g, Theme theme, int x, int y, int boardHeight) {
		List<Minigame.Stat> stats = game.stats();
		int h = Math.min(Math.max(boardHeight, 60), 66 + stats.size() * 22);
		g.fill(x, y, x + INFO_W, y + h, theme.panel());
		GuiDraw.outline(g, x, y, INFO_W, h, theme.outline());
		int score = game.score();
		int best = Math.max(bestBefore, highscores().best(game.id()));
		text(g, I18n.get("salami.minigames.score"), x + 6, y + 6, theme.textDim());
		String scoreText = String.valueOf(score);
		float big = font.width(scoreText) * 2 <= INFO_W - 12 ? 2f : 1f;
		g.pose().pushMatrix();
		g.pose().translate(x + 6, y + 17);
		g.pose().scale(big, big);
		text(g, scoreText, 0, 0, score > best && best > 0 ? Colors.GOLD : theme.headerText());
		g.pose().popMatrix();
		text(g, I18n.get("salami.minigames.record"), x + 6, y + 40, theme.textDim());
		text(g, best > 0 ? String.valueOf(Math.max(best, score)) : "–", x + 6, y + 50, score > best && best > 0 ? Colors.GOLD : theme.accent());
		int sy = y + 66;
		for (Minigame.Stat stat : stats) {
			if (sy + 20 > y + h) {
				break;
			}
			text(g, fit(I18n.get(stat.labelKey()), INFO_W - 12), x + 6, sy, theme.textDim());
			text(g, stat.value(), x + 6, sy + 10, theme.text());
			sy += 22;
		}
	}

	private record OverlayAction(String labelKey, Runnable action) {}

	private List<OverlayAction> overlayActions() {
		if (paused) {
			return List.of(new OverlayAction("salami.minigames.continue", this::resume),
				new OverlayAction("salami.minigames.restart", this::restartGame),
				new OverlayAction("salami.minigames.to_menu", this::showMenu));
		}
		return List.of(new OverlayAction("salami.minigames.again", this::restartGame),
			new OverlayAction("salami.minigames.leaderboard", () -> showLeaderboard(gameIndex)),
			new OverlayAction("salami.minigames.to_menu", this::showMenu));
	}

	private void overlay(GuiGraphics g, Theme theme, float bw, float bh) {
		MinigameCanvas.fill(g, boardX, boardY, boardX + bw, boardY + bh, 0x99000000);
		List<OverlayAction> actions = overlayActions();
		boolean over = !paused;
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		if (over) {
			lines.add(I18n.get("salami.minigames.result_score", game.score()));
			colors.add(theme.headerText());
			lines.add(I18n.get("salami.minigames.result_best", Math.max(bestBefore, game.score())));
			colors.add(theme.textDim());
			if (!newRecord && rank >= 0) {
				lines.add(I18n.get("salami.minigames.rank", rank + 1));
				colors.add(theme.accent());
			}
		} else {
			lines.add(I18n.get("salami.minigames.result_score", game.score()));
			colors.add(theme.headerText());
		}
		int pw = 168;
		int ph = 10 + 18 + 6 + lines.size() * 12 + (newRecord && over ? 16 : 0) + 6 + actions.size() * 18 + 6;
		int cx = Math.round(boardX + bw / 2);
		int cy = Math.round(boardY + bh / 2);
		int x0 = Math.max(4, Math.min(uiW() - pw - 4, cx - pw / 2));
		int y0 = Math.max(HEADER + 2, Math.min(uiH() - ph - 4, cy - ph / 2));
		g.fill(x0, y0, x0 + pw, y0 + ph, Colors.opaque(theme.panel()));
		g.fill(x0, y0, x0 + pw, y0 + 3, theme.accent());
		GuiDraw.outline(g, x0, y0, pw, ph, theme.outline());
		String title = I18n.get(over ? game.overTitleKey() : "salami.minigames.pause");
		bigText(g, title, x0 + pw / 2f, y0 + 10, font.width(title) * 2 <= pw - 12 ? 2f : 1f, theme.headerText());
		int y = y0 + 10 + 18 + 6;
		for (int i = 0; i < lines.size(); i++) {
			centered(g, lines.get(i), x0 + pw / 2, y, colors.get(i));
			y += 12;
		}
		if (newRecord && over) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0);
			String record = I18n.get("salami.minigames.new_record");
			bigText(g, record, x0 + pw / 2f, y + 1, 1f + 0.12f * pulse, Colors.mix(Colors.GOLD, 0xFFFFFFFF, pulse * 0.5f));
			y += 16;
		}
		y += 6;
		for (int i = 0; i < actions.size(); i++) {
			OverlayAction action = actions.get(i);
			int bx0 = x0 + 24;
			int bx1 = x0 + pw - 24;
			boolean hover = hovered(bx0, y, bx1, y + 15);
			if (hover && !keyboardNav) {
				overlayIndex = i;
			}
			boolean active = overlayIndex == i;
			g.fill(bx0, y, bx1, y + 15, active ? theme.accent() : theme.header());
			GuiDraw.outline(g, bx0, y, bx1 - bx0, 15, active ? theme.accent() : theme.outline());
			centered(g, I18n.get(action.labelKey()), (bx0 + bx1) / 2, y + 4, active ? 0xFF000000 : theme.headerText());
			buttons.add(new Button(bx0, y, bx1, y + 15, action.action()));
			y += 18;
		}
	}

	private void spawnConfetti() {
		int[] palette = {Colors.GOLD, Colors.SALAMI, Colors.FAT, Colors.CHEESE, Colors.PICKLE};
		float cx = boardX + game.width() * boardScale / 2;
		float cy = boardY + game.height() * boardScale * 0.3f;
		for (int i = 0; i < 80; i++) {
			float angle = (float) (random.nextFloat() * Math.PI * 2);
			float speed = 60 + random.nextFloat() * 140;
			confetti.add(new Confetti(cx, cy, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 80,
				palette[random.nextInt(palette.length)], 1.5f + random.nextFloat() * 2));
		}
	}

	private void updateConfetti(GuiGraphics g) {
		if (confetti.isEmpty()) {
			return;
		}
		float dt = Math.min(frameSeconds, 0.05f);
		confetti.removeIf(piece -> piece.y > uiH() + 10);
		for (Confetti piece : confetti) {
			if (!frozen) {
				piece.vy += 160 * dt;
				piece.vx *= 1 - 0.8f * dt;
				piece.x += piece.vx * dt;
				piece.y += piece.vy * dt;
			}
			MinigameCanvas.fill(g, snap(piece.x), snap(piece.y), snap(piece.x + piece.size), snap(piece.y + piece.size * 1.4f), piece.color);
		}
	}

	// ------------------------------------------------------------------ Ton

	private void toggleMute() {
		highscores().setMuted(!highscores().muted());
		uiSound(Sfx.CLICK);
	}

	private void uiSound(Sfx sfx) {
		play(sfx, 1f);
	}

	private void play(Sfx sfx, float pitch) {
		if (highscores().muted() || minecraft == null) {
			return;
		}
		SoundEvent event;
		float volume;
		switch (sfx) {
			case CLICK -> {
				event = SoundEvents.UI_BUTTON_CLICK.value();
				volume = 0.25f;
			}
			case MOVE -> {
				event = SoundEvents.NOTE_BLOCK_HAT.value();
				pitch *= 1.6f;
				volume = 0.12f;
			}
			case ROTATE -> {
				event = SoundEvents.NOTE_BLOCK_HAT.value();
				pitch *= 2f;
				volume = 0.15f;
			}
			case LAND -> {
				event = SoundEvents.NOTE_BLOCK_BASEDRUM.value();
				volume = 0.3f;
			}
			case HARD_DROP -> {
				event = SoundEvents.NOTE_BLOCK_BASEDRUM.value();
				pitch *= 0.7f;
				volume = 0.4f;
			}
			case HOLD -> {
				event = SoundEvents.NOTE_BLOCK_CHIME.value();
				pitch *= 1.2f;
				volume = 0.2f;
			}
			case CLEAR -> {
				event = SoundEvents.NOTE_BLOCK_PLING.value();
				volume = 0.35f;
			}
			case BIG_CLEAR -> {
				event = SoundEvents.PLAYER_LEVELUP;
				pitch *= 1.3f;
				volume = 0.3f;
			}
			case LEVEL_UP -> {
				event = SoundEvents.NOTE_BLOCK_BELL.value();
				pitch *= 1.5f;
				volume = 0.35f;
			}
			case EAT -> {
				event = SoundEvents.NOTE_BLOCK_BIT.value();
				volume = 0.3f;
			}
			case BONUS -> {
				event = SoundEvents.EXPERIENCE_ORB_PICKUP;
				pitch *= 1.2f;
				volume = 0.3f;
			}
			case FLAP -> {
				event = SoundEvents.NOTE_BLOCK_HAT.value();
				pitch *= 1.1f;
				volume = 0.2f;
			}
			case SCORE -> {
				event = SoundEvents.NOTE_BLOCK_BELL.value();
				pitch *= 1.3f;
				volume = 0.25f;
			}
			case MERGE -> {
				event = SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
				volume = 0.35f;
			}
			case REVEAL -> {
				event = SoundEvents.NOTE_BLOCK_HAT.value();
				pitch *= 1.3f;
				volume = 0.15f;
			}
			case FLAG -> {
				event = SoundEvents.NOTE_BLOCK_SNARE.value();
				volume = 0.2f;
			}
			case BOOM -> {
				event = SoundEvents.GENERIC_EXPLODE.value();
				pitch *= 1.3f;
				volume = 0.25f;
			}
			case DROP -> {
				event = SoundEvents.NOTE_BLOCK_BASEDRUM.value();
				volume = 0.3f;
			}
			case CRASH -> {
				event = SoundEvents.ITEM_BREAK.value();
				pitch *= 0.9f;
				volume = 0.35f;
			}
			case WIN -> {
				event = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
				volume = 0.25f;
			}
			default -> {
				event = SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
				pitch *= 0.8f;
				volume = 0.4f;
			}
		}
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, Math.max(0.5f, Math.min(2f, pitch)), volume));
	}

	// ------------------------------------------------------------------ Eingabe

	private static GameKey gameKey(int key) {
		return switch (key) {
			case InputConstants.KEY_UP, InputConstants.KEY_W, InputConstants.KEY_X -> GameKey.UP;
			case InputConstants.KEY_DOWN, InputConstants.KEY_S -> GameKey.DOWN;
			case InputConstants.KEY_LEFT, InputConstants.KEY_A -> GameKey.LEFT;
			case InputConstants.KEY_RIGHT, InputConstants.KEY_D -> GameKey.RIGHT;
			case InputConstants.KEY_SPACE, InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> GameKey.ACTION;
			case InputConstants.KEY_C, InputConstants.KEY_F, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> GameKey.ALT;
			case InputConstants.KEY_Z, InputConstants.KEY_Y, InputConstants.KEY_Q -> GameKey.ROTATE_BACK;
			default -> null;
		};
	}

	private static boolean isConfirm(int key) {
		return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_M) {
			toggleMute();
			return true;
		}
		switch (view) {
			case MENU -> menuKey(key);
			case LEADERBOARD -> leaderboardKey(key);
			case GAME -> gameKeyPressed(key);
		}
		return true;
	}

	private void menuKey(int key) {
		int count = previews.size();
		GameKey direction = gameKey(key);
		if (direction == GameKey.LEFT) {
			moveSelection(-1, count);
		} else if (direction == GameKey.RIGHT) {
			moveSelection(1, count);
		} else if (direction == GameKey.UP) {
			moveSelection(-lastCols, count);
		} else if (direction == GameKey.DOWN) {
			moveSelection(lastCols, count);
		} else if (key == InputConstants.KEY_ESCAPE) {
			onClose();
		} else if (isConfirm(key)) {
			uiSound(Sfx.CLICK);
			startGame(selected);
		} else if (key == InputConstants.KEY_L || key == InputConstants.KEY_TAB) {
			showLeaderboard(selected);
		}
	}

	private void moveSelection(int delta, int count) {
		if (!keyboardNav) {
			keyboardNav = true;
			return;
		}
		int next = selected + delta;
		if (next >= 0 && next < count) {
			selected = next;
			uiSound(Sfx.MOVE);
		}
	}

	private void leaderboardKey(int key) {
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE) {
			showMenu();
		} else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
			selected = Math.floorMod(selected - 1, previews.size());
		} else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S || key == InputConstants.KEY_TAB) {
			selected = Math.floorMod(selected + 1, previews.size());
		} else if (isConfirm(key)) {
			startGame(selected);
		}
	}

	private void gameKeyPressed(int key) {
		boolean overlay = paused || game.overlayReady();
		if (key == InputConstants.KEY_R) {
			uiSound(Sfx.CLICK);
			restartGame();
			return;
		}
		if (overlay) {
			List<OverlayAction> actions = overlayActions();
			if (key == InputConstants.KEY_ESCAPE) {
				if (paused) {
					resume();
				} else {
					showMenu();
				}
			} else if (key == InputConstants.KEY_P && paused) {
				resume();
			} else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
				keyboardNav = true;
				overlayIndex = Math.floorMod(overlayIndex - 1, actions.size());
			} else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) {
				keyboardNav = true;
				overlayIndex = Math.floorMod(overlayIndex + 1, actions.size());
			} else if (isConfirm(key)) {
				uiSound(Sfx.CLICK);
				actions.get(Math.min(overlayIndex, actions.size() - 1)).action().run();
			}
			return;
		}
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_P) {
			if (game.isOver()) {
				showMenu();
			} else {
				pause();
			}
			return;
		}
		GameKey gameKey = gameKey(key);
		if (gameKey != null && !game.isOver()) {
			game.press(gameKey);
		}
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		GameKey gameKey = gameKey(event.key());
		if (view == View.GAME && game != null && gameKey != null) {
			game.release(gameKey);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		int button = event.button();
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = buttons.size() - 1; i >= 0; i--) {
				Button b = buttons.get(i);
				if (b.contains(x, y)) {
					uiSound(Sfx.CLICK);
					b.action().run();
					return true;
				}
			}
		}
		if (isPlaying() && game.width() > 0) {
			float gx = (float) ((x - boardX) / boardScale);
			float gy = (float) ((y - boardY) / boardScale);
			if (gx >= 0 && gy >= 0 && gx < game.width() && gy < game.height()) {
				int gameButton = button == InputConstants.MOUSE_BUTTON_LEFT ? 0
					: button == InputConstants.MOUSE_BUTTON_RIGHT ? 1
					: button == InputConstants.MOUSE_BUTTON_MIDDLE ? 2 : -1;
				if (gameButton >= 0) {
					game.click(gx, gy, gameButton);
				}
			}
		}
		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		keyboardNav = false;
		if (isPlaying()) {
			game.hover((float) ((x / uiScale - boardX) / boardScale), (float) ((y / uiScale - boardY) / boardScale));
		}
	}
}
