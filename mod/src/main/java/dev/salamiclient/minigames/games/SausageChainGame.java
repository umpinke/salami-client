package dev.salamiclient.minigames.games;

import dev.salamiclient.minigames.Canvas;
import dev.salamiclient.minigames.Colors;
import dev.salamiclient.minigames.GameKey;
import dev.salamiclient.minigames.Minigame;
import dev.salamiclient.minigames.Sfx;
import dev.salamiclient.theme.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * „Wurstkette“: Eine Wurstkette frisst Käsewürfel und wird mit jedem Bissen länger und schneller.
 * Gurken bringen Extrapunkte, verschwinden aber nach ein paar Sekunden. Wand oder eigene Kette = Ende.
 */
public final class SausageChainGame extends Minigame {
	public static final int COLS = 20;
	public static final int ROWS = 15;
	private static final float CELL = 10;
	/** Schritte pro Feld am Anfang (≈ 6,7 Felder/s) und im schnellsten Fall (15 Felder/s). */
	private static final int START_INTERVAL = 9;
	private static final int MIN_INTERVAL = 4;
	private static final int BONUS_STEPS = 6 * STEPS_PER_SECOND;

	/** Kettenglieder, Kopf zuerst (x + y * COLS). */
	private final List<Integer> body = new ArrayList<>();
	/** Stellung vor dem letzten Schritt – zum Überblenden. */
	private final List<Integer> previous = new ArrayList<>();
	private final ArrayDeque<int[]> turns = new ArrayDeque<>();
	private int dx;
	private int dy;
	private int moveTimer;
	private int interval;
	private int food;
	private int bonus;
	private int bonusTimer;
	private int grow;
	private int eaten;
	private boolean waiting;

	@Override
	public String id() {
		return "sausage_chain";
	}

	@Override
	public float width() {
		return COLS * CELL;
	}

	@Override
	public float height() {
		return ROWS * CELL;
	}

	@Override
	public List<String> controls() {
		return List.of("salami.minigames.controls.steer");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.length", String.valueOf(body.size())));
	}

	@Override
	protected void reset() {
		body.clear();
		for (int i = 0; i < 3; i++) {
			body.add(pos(6 - i, ROWS / 2));
		}
		previous.clear();
		previous.addAll(body);
		turns.clear();
		dx = 1;
		dy = 0;
		moveTimer = 0;
		interval = START_INTERVAL;
		grow = 0;
		eaten = 0;
		bonus = -1;
		bonusTimer = 0;
		waiting = true;
		food = freeCell();
	}

	@Override
	public void demo() {
		restart(7);
		waiting = false;
		int[][] cells = {{12, 5}, {11, 5}, {10, 5}, {10, 6}, {10, 7}, {9, 7}, {8, 7}, {7, 7}, {6, 7}, {6, 8}, {6, 9}};
		body.clear();
		for (int[] cell : cells) {
			body.add(pos(cell[0], cell[1]));
		}
		previous.clear();
		previous.addAll(body);
		food = pos(16, 3);
		bonus = pos(4, 12);
		bonusTimer = BONUS_STEPS;
		eaten = 8;
		setScore(13);
	}

	private static int pos(int x, int y) {
		return x + y * COLS;
	}

	/** Zufälliges freies Feld oder -1, wenn das Brett voll ist. */
	private int freeCell() {
		int free = COLS * ROWS - body.size() - (food >= 0 && !body.contains(food) ? 1 : 0) - (bonus >= 0 ? 1 : 0);
		if (free <= 0) {
			return -1;
		}
		int pick = random.nextInt(free);
		for (int i = 0; i < COLS * ROWS; i++) {
			if (body.contains(i) || i == food || i == bonus) {
				continue;
			}
			if (pick-- == 0) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void update() {
		if (isOver() || waiting) {
			return;
		}
		if (bonus >= 0 && --bonusTimer <= 0) {
			bonus = -1;
		}
		if (++moveTimer >= interval) {
			moveTimer = 0;
			move();
		}
	}

	private void move() {
		int[] turn = turns.poll();
		if (turn != null) {
			dx = turn[0];
			dy = turn[1];
		}
		previous.clear();
		previous.addAll(body);
		int head = body.get(0);
		int nx = head % COLS + dx;
		int ny = head / COLS + dy;
		if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
			crash();
			return;
		}
		int next = pos(nx, ny);
		boolean growing = grow > 0;
		// das Schwanzende rückt weiter – außer die Kette wächst gerade
		int check = growing ? body.size() : body.size() - 1;
		for (int i = 0; i < check; i++) {
			if (body.get(i) == next) {
				crash();
				return;
			}
		}
		body.add(0, next);
		if (growing) {
			grow--;
		} else {
			body.remove(body.size() - 1);
		}
		if (next == food) {
			eaten++;
			addScore(1);
			grow++;
			sound(Sfx.EAT, 0.9f + Math.min(eaten, 20) * 0.03f);
			food = -1;
			food = freeCell();
			interval = Math.max(MIN_INTERVAL, START_INTERVAL - eaten / 5);
			if (bonus < 0 && eaten % 4 == 0 && random.nextInt(3) > 0) {
				bonus = freeCell();
				bonusTimer = BONUS_STEPS;
			}
			if (food < 0) {
				sound(Sfx.WIN);
				gameOver();
			}
		} else if (next == bonus) {
			addScore(5);
			grow += 2;
			bonus = -1;
			sound(Sfx.BONUS);
		}
	}

	private void crash() {
		previous.clear();
		previous.addAll(body);
		sound(Sfx.CRASH);
		gameOver();
	}

	@Override
	protected void onPress(GameKey key) {
		int[] d = switch (key) {
			case UP -> new int[] {0, -1};
			case DOWN -> new int[] {0, 1};
			case LEFT -> new int[] {-1, 0};
			case RIGHT -> new int[] {1, 0};
			default -> null;
		};
		if (d == null || isOver()) {
			return;
		}
		if (waiting) {
			waiting = false;
			if (d[0] != -dx || d[1] != -dy) {
				dx = d[0];
				dy = d[1];
			}
			return;
		}
		int[] last = turns.isEmpty() ? new int[] {dx, dy} : turns.peekLast();
		boolean same = d[0] == last[0] && d[1] == last[1];
		boolean reverse = d[0] == -last[0] && d[1] == -last[1];
		if (!same && !reverse && turns.size() < 2) {
			turns.add(d);
		}
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		int board = Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.35f);
		c.rect(0, 0, width(), height(), board);
		for (int y = 0; y < ROWS; y++) {
			for (int x = (y % 2); x < COLS; x += 2) {
				c.rect(x * CELL, y * CELL, (x + 1) * CELL, (y + 1) * CELL, 0x0AFFFFFF);
			}
		}

		if (food >= 0) {
			drawCheese(c, food % COLS * CELL, food / COLS * CELL);
		}
		if (bonus >= 0 && (bonusTimer > 90 || (bonusTimer / 8) % 2 == 0)) {
			drawPickle(c, bonus % COLS * CELL, bonus / COLS * CELL);
		}

		float a = isOver() || waiting ? 1f : Math.min(1f, (moveTimer + partial) / interval);
		int n = body.size();
		float[] xs = new float[n];
		float[] ys = new float[n];
		float[] cornerX = new float[n];
		float[] cornerY = new float[n];
		for (int i = 0; i < n; i++) {
			int from = previous.get(Math.min(i, previous.size() - 1));
			int to = body.get(i);
			xs[i] = (lerp(from % COLS, to % COLS, a) + 0.5f) * CELL;
			ys[i] = (lerp(from / COLS, to / COLS, a) + 0.5f) * CELL;
			cornerX[i] = (from % COLS + 0.5f) * CELL;
			cornerY[i] = (from / COLS + 0.5f) * CELL;
		}
		boolean flash = isOver() && overSteps() < 40 && (overSteps() / 5) % 2 == 0;
		int casing = flash ? Colors.FAT : Colors.SALAMI;
		float half = CELL * 0.36f;
		// Pelle (dunkler Rand), dann die Wurst selbst: Glied i → Feld, auf dem es vorher lag → Glied i + 1
		drawChain(c, xs, ys, cornerX, cornerY, half + CELL * 0.08f, Colors.CASING);
		drawChain(c, xs, ys, cornerX, cornerY, half, casing);
		// Fettaugen
		for (int i = 1; i < n; i++) {
			float ox = (i % 3 - 1) * 1.6f;
			float oy = ((i + 1) % 3 - 1) * 1.4f;
			c.rect(xs[i] + ox - 1.6f, ys[i] + oy - 1.6f, xs[i] + ox, ys[i] + oy, Colors.alpha(Colors.FAT, 0.85f));
			c.rect(xs[i] - ox + 0.6f, ys[i] - oy + 0.8f, xs[i] - ox + 1.6f, ys[i] - oy + 1.8f, Colors.alpha(Colors.FAT, 0.6f));
		}
		// Zipfel am Ende
		c.rect(xs[n - 1] - 1, ys[n - 1] - 1, xs[n - 1] + 1, ys[n - 1] + 1, Colors.CASING);
		// Kopf mit Augen in Laufrichtung
		float hx = xs[0];
		float hy = ys[0];
		c.roundRect(hx - half - 1, hy - half - 1, hx + half + 1, hy + half + 1, 3, Colors.CASING);
		c.roundRect(hx - half, hy - half, hx + half, hy + half, 2.5f, casing);
		float ex = dy != 0 ? 2.2f : 0;
		float ey = dx != 0 ? 2.2f : 0;
		float lookX = dx * 1.4f;
		float lookY = dy * 1.4f;
		for (int side = -1; side <= 1; side += 2) {
			float cx = hx + ex * side + lookX;
			float cy = hy + ey * side + lookY;
			c.rect(cx - 1.4f, cy - 1.4f, cx + 1.4f, cy + 1.4f, 0xFFFFFFFF);
			c.rect(cx - 0.6f + dx * 0.6f, cy - 0.6f + dy * 0.6f, cx + 0.6f + dx * 0.6f, cy + 0.6f + dy * 0.6f, Colors.PEPPER);
		}

		if (waiting) {
			c.rect(0, height() * 0.66f, width(), height() * 0.66f + 16, 0x90000000);
			c.text(c.tr("salami.minigames.hint.press_arrow"), width() / 2, height() * 0.66f + 8.5f, 9, theme.headerText());
		}
	}

	/**
	 * Zeichnet die Kette als dicke Linie. Glied i liegt zwischen seinem alten und neuen Feld, Glied
	 * i + 1 rückt genau auf das alte Feld von Glied i – über dieses Eckfeld sind beide Teilstücke
	 * immer achsparallel, auch mitten in einer Kurve.
	 */
	private static void drawChain(Canvas c, float[] xs, float[] ys, float[] cornerX, float[] cornerY, float half, int color) {
		int n = xs.length;
		c.rect(xs[0] - half, ys[0] - half, xs[0] + half, ys[0] + half, color);
		for (int i = 0; i + 1 < n; i++) {
			c.bar(xs[i], ys[i], cornerX[i], cornerY[i], half, color);
			c.bar(cornerX[i], cornerY[i], xs[i + 1], ys[i + 1], half, color);
		}
	}

	private static void drawCheese(Canvas c, float x, float y) {
		c.rect(x + 1.5f, y + 2, x + 8.5f, y + 8.5f, Colors.CHEESE_DARK);
		c.rect(x + 1.5f, y + 1.5f, x + 8.5f, y + 7.5f, Colors.CHEESE);
		c.rect(x + 3, y + 3, x + 4.5f, y + 4.5f, Colors.CHEESE_DARK);
		c.rect(x + 5.5f, y + 5, x + 6.5f, y + 6, Colors.CHEESE_DARK);
	}

	private static void drawPickle(Canvas c, float x, float y) {
		c.roundRect(x + 1, y + 2.5f, x + 9, y + 7.5f, 2.5f, Colors.darker(Colors.PICKLE, 0.35f));
		c.roundRect(x + 1.5f, y + 3, x + 8.5f, y + 7, 2, Colors.PICKLE);
		c.rect(x + 3, y + 4, x + 4, y + 5, 0xFFB8D888);
		c.rect(x + 5.5f, y + 4.5f, x + 6.5f, y + 5.5f, 0xFFB8D888);
	}
}
