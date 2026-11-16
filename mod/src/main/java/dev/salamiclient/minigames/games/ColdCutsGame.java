package dev.salamiclient.minigames.games;

import dev.salamiclient.minigames.Canvas;
import dev.salamiclient.minigames.Colors;
import dev.salamiclient.minigames.GameKey;
import dev.salamiclient.minigames.Minigame;
import dev.salamiclient.minigames.Sfx;
import dev.salamiclient.theme.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * „Aufschnitt“: Aufschnitt-Stücke aus je vier Scheiben fallen aufs Brett. Volle Reihen werden
 * serviert (verschwinden); alle zehn Reihen steigt das Level und alles fällt schneller.
 */
public final class ColdCutsGame extends Minigame {
	public static final int COLS = 10;
	public static final int VISIBLE_ROWS = 20;
	/** Unsichtbare Reihen über dem Brett, in denen neue Stücke erscheinen. */
	public static final int HIDDEN = 2;
	public static final int ROWS = VISIBLE_ROWS + HIDDEN;
	private static final float CELL = 10;
	private static final float SIDE_X = COLS * CELL + 8;
	private static final float SIDE_W = 52;

	/** Schritte pro Reihe je Level (Level 1 = 0,8 s). */
	private static final int[] GRAVITY = {48, 40, 33, 27, 22, 18, 14, 11, 9, 7, 6, 5, 4, 4, 3, 3, 2, 2, 2, 1};
	private static final int SOFT_DROP = 2;
	private static final int LOCK_DELAY = 30;
	private static final int MAX_LOCK_RESETS = 15;
	/** Verzögerung bis zum automatischen Weiterschieben und dessen Takt (Schritte). */
	private static final int DAS = 10;
	private static final int ARR = 2;
	private static final int CLEAR_STEPS = 14;
	private static final int[] LINE_POINTS = {0, 100, 300, 500, 800};

	/** Rahmengröße und Zellen der sieben Stücke in Grundstellung. */
	private static final int[] SIZE = {4, 2, 3, 3, 3, 3, 3};
	private static final int[][][] SHAPES = {
		{{0, 1}, {1, 1}, {2, 1}, {3, 1}},
		{{0, 0}, {1, 0}, {0, 1}, {1, 1}},
		{{1, 0}, {0, 1}, {1, 1}, {2, 1}},
		{{1, 0}, {2, 0}, {0, 1}, {1, 1}},
		{{0, 0}, {1, 0}, {1, 1}, {2, 1}},
		{{0, 0}, {0, 1}, {1, 1}, {2, 1}},
		{{2, 0}, {0, 1}, {1, 1}, {2, 1}},
	};
	/** [Stück][Drehung][Zelle] = {x, y} */
	private static final int[][][][] ROTATIONS = new int[7][4][4][2];
	/** Ausweichversuche beim Drehen (x, y; y negativ = nach oben). */
	private static final int[][] KICKS = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {-1, -1}, {1, -1}, {-2, 0}, {2, 0}, {0, 1}};

	/** Salami, Käse, Schinken, Gurke, Peperoni, Mortadella, Leberkäse */
	private static final int[] COLORS = {0xFFB23A3A, 0xFFE8C14A, 0xFFE89A9A, 0xFF6E9E3A, 0xFFD9542E, 0xFFF2BDB4, 0xFFC98B4F};

	static {
		for (int type = 0; type < 7; type++) {
			int n = SIZE[type];
			for (int i = 0; i < 4; i++) {
				int x = SHAPES[type][i][0];
				int y = SHAPES[type][i][1];
				for (int rot = 0; rot < 4; rot++) {
					ROTATIONS[type][rot][i][0] = x;
					ROTATIONS[type][rot][i][1] = y;
					int nx = n - 1 - y;
					y = x;
					x = nx;
				}
			}
		}
	}

	private final int[][] grid = new int[ROWS][COLS];
	private final ArrayDeque<Integer> queue = new ArrayDeque<>();
	private boolean active;
	private int type;
	private int rot;
	private int px;
	private int py;
	private int hold;
	private boolean holdUsed;
	private int gravityTimer;
	private int lockTimer;
	private int lockResets;
	private int dasDir;
	private int dasTimer;
	private int lines;
	private int level;
	private final List<Integer> clearing = new ArrayList<>();
	private int clearTimer;
	private String popup = "";
	private int popupTimer;

	@Override
	public String id() {
		return "cold_cuts";
	}

	@Override
	public float width() {
		return SIDE_X + SIDE_W;
	}

	@Override
	public float height() {
		return VISIBLE_ROWS * CELL;
	}

	@Override
	public List<String> controls() {
		return List.of("salami.minigames.controls.move", "salami.minigames.controls.rotate", "salami.minigames.controls.soft_drop",
			"salami.minigames.controls.hard_drop", "salami.minigames.controls.hold");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.level", String.valueOf(level)),
			new Stat("salami.minigames.stat.lines", String.valueOf(lines)));
	}

	@Override
	public int overlayDelay() {
		return 45;
	}

	@Override
	protected void reset() {
		for (int[] row : grid) {
			java.util.Arrays.fill(row, 0);
		}
		queue.clear();
		hold = -1;
		holdUsed = false;
		lines = 0;
		level = 1;
		clearing.clear();
		clearTimer = 0;
		dasDir = 0;
		dasTimer = 0;
		popupTimer = 0;
		next();
	}

	@Override
	public void demo() {
		restart(11);
		String[] rows = {
			"..........",
			"....1.....",
			"..4411...6",
			".344.1..66",
			"3337222.56",
			"7777.22255",
			"1166.33355",
			"11662233.4",
		};
		for (int r = 0; r < rows.length; r++) {
			for (int x = 0; x < COLS; x++) {
				char ch = rows[r].charAt(x);
				grid[ROWS - rows.length + r][x] = ch == '.' ? 0 : ch - '0';
			}
		}
		type = 2;
		rot = 1;
		px = 6;
		py = 9;
		hold = 0;
		lines = 14;
		level = 2;
		setScore(3260);
	}

	// ------------------------------------------------------------------ Logik

	private boolean fits(int t, int r, int x, int y) {
		for (int[] cell : ROTATIONS[t][r]) {
			int gx = x + cell[0];
			int gy = y + cell[1];
			if (gx < 0 || gx >= COLS || gy >= ROWS) {
				return false;
			}
			if (gy >= 0 && grid[gy][gx] != 0) {
				return false;
			}
		}
		return true;
	}

	private void refill() {
		while (queue.size() < 7) {
			List<Integer> bag = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6));
			Collections.shuffle(bag, random);
			queue.addAll(bag);
		}
	}

	private void next() {
		refill();
		holdUsed = false;
		spawn(queue.poll());
	}

	private void spawn(int t) {
		type = t;
		rot = 0;
		px = (COLS - SIZE[t]) / 2;
		py = 0;
		gravityTimer = 0;
		lockTimer = 0;
		lockResets = 0;
		if (!fits(type, rot, px, py)) {
			active = false;
			sound(Sfx.LOSE);
			gameOver();
			return;
		}
		active = true;
		if (fits(type, rot, px, py + 1)) {
			py++;
		}
	}

	private int gravity() {
		return GRAVITY[Math.min(level, GRAVITY.length) - 1];
	}

	private boolean grounded() {
		return !fits(type, rot, px, py + 1);
	}

	@Override
	protected void update() {
		if (popupTimer > 0) {
			popupTimer--;
		}
		if (isOver()) {
			return;
		}
		if (clearTimer > 0) {
			if (--clearTimer == 0) {
				collapse();
				next();
			}
			return;
		}
		if (!active) {
			return;
		}
		if (dasDir != 0 && isHeld(dasDir < 0 ? GameKey.LEFT : GameKey.RIGHT)) {
			dasTimer++;
			if (dasTimer >= DAS && (dasTimer - DAS) % ARR == 0) {
				shift(dasDir);
			}
		}
		if (grounded()) {
			gravityTimer = 0;
			if (++lockTimer >= LOCK_DELAY) {
				lock();
			}
			return;
		}
		lockTimer = 0;
		boolean soft = isHeld(GameKey.DOWN);
		int g = soft ? Math.min(gravity(), SOFT_DROP) : gravity();
		if (++gravityTimer >= g) {
			gravityTimer = 0;
			py++;
			if (soft) {
				addScore(1);
			}
		}
	}

	@Override
	protected void onPress(GameKey key) {
		if (key == GameKey.LEFT || key == GameKey.RIGHT) {
			dasDir = key == GameKey.LEFT ? -1 : 1;
			dasTimer = 0;
		}
		if (isOver() || !active || clearTimer > 0) {
			return;
		}
		switch (key) {
			case LEFT -> shift(-1);
			case RIGHT -> shift(1);
			case UP -> rotate(1);
			case ROTATE_BACK -> rotate(-1);
			case ACTION -> hardDrop();
			case ALT -> hold();
			case DOWN -> gravityTimer = Math.max(gravityTimer, SOFT_DROP - 1);
		}
	}

	@Override
	protected void onRelease(GameKey key) {
		if (key == GameKey.LEFT || key == GameKey.RIGHT) {
			GameKey other = key == GameKey.LEFT ? GameKey.RIGHT : GameKey.LEFT;
			dasDir = isHeld(other) ? (other == GameKey.LEFT ? -1 : 1) : 0;
			dasTimer = 0;
		}
	}

	private void shift(int dir) {
		if (fits(type, rot, px + dir, py)) {
			px += dir;
			moved();
			sound(Sfx.MOVE);
		}
	}

	private void rotate(int dir) {
		int next = (rot + dir + 4) % 4;
		for (int[] kick : KICKS) {
			if (fits(type, next, px + kick[0], py + kick[1])) {
				rot = next;
				px += kick[0];
				py += kick[1];
				moved();
				sound(Sfx.ROTATE);
				return;
			}
		}
	}

	/** Bewegt am Boden: Liegezeit neu starten (begrenzt, damit man nicht ewig schieben kann). */
	private void moved() {
		if (grounded() && lockResets < MAX_LOCK_RESETS) {
			lockTimer = 0;
			lockResets++;
		}
	}

	private void hardDrop() {
		int rows = 0;
		while (fits(type, rot, px, py + 1)) {
			py++;
			rows++;
		}
		addScore(2 * rows);
		sound(Sfx.HARD_DROP);
		lock();
	}

	private void hold() {
		if (holdUsed) {
			return;
		}
		int current = type;
		if (hold < 0) {
			hold = current;
			refill();
			spawn(queue.poll());
		} else {
			int held = hold;
			hold = current;
			spawn(held);
		}
		holdUsed = true;
		sound(Sfx.HOLD);
	}

	private void lock() {
		boolean visible = false;
		for (int[] cell : ROTATIONS[type][rot]) {
			int gx = px + cell[0];
			int gy = py + cell[1];
			if (gy >= 0) {
				grid[gy][gx] = type + 1;
			}
			if (gy >= HIDDEN) {
				visible = true;
			}
		}
		active = false;
		if (!visible) {
			sound(Sfx.LOSE);
			gameOver();
			return;
		}
		clearing.clear();
		for (int y = 0; y < ROWS; y++) {
			boolean full = true;
			for (int x = 0; x < COLS && full; x++) {
				full = grid[y][x] != 0;
			}
			if (full) {
				clearing.add(y);
			}
		}
		if (clearing.isEmpty()) {
			sound(Sfx.LAND);
			next();
			return;
		}
		int n = clearing.size();
		int points = LINE_POINTS[n] * level;
		addScore(points);
		lines += n;
		popup = "+" + points;
		popupTimer = 50;
		clearTimer = CLEAR_STEPS;
		int newLevel = 1 + lines / 10;
		sound(n == 4 ? Sfx.BIG_CLEAR : Sfx.CLEAR, 0.9f + n * 0.1f);
		if (newLevel > level) {
			level = newLevel;
			sound(Sfx.LEVEL_UP);
		}
	}

	private void collapse() {
		int write = ROWS - 1;
		for (int y = ROWS - 1; y >= 0; y--) {
			if (clearing.contains(y)) {
				continue;
			}
			if (write != y) {
				System.arraycopy(grid[y], 0, grid[write], 0, COLS);
			}
			write--;
		}
		for (; write >= 0; write--) {
			java.util.Arrays.fill(grid[write], 0);
		}
		clearing.clear();
	}

	// ------------------------------------------------------------------ für Tests

	/** Reihe {@code y} (0 = oberste sichtbare) mit Lücke in Spalte {@code gap} füllen. */
	public void fillRow(int y, int gap) {
		for (int x = 0; x < COLS; x++) {
			grid[y + HIDDEN][x] = x == gap ? 0 : 7;
		}
	}

	public int lines() {
		return lines;
	}

	public int level() {
		return level;
	}

	public boolean cellFilled(int x, int y) {
		return grid[y + HIDDEN][x] != 0;
	}

	/** Aktuelles Stück durch ein bestimmtes ersetzen (oben in der Mitte). */
	public void forcePiece(int t) {
		spawn(t);
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		float boardW = COLS * CELL;
		float boardH = VISIBLE_ROWS * CELL;
		c.rect(0, 0, boardW, boardH, Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.4f));
		for (int x = 1; x < COLS; x++) {
			c.rect(x * CELL, 0, x * CELL + c.pixel(), boardH, 0x10FFFFFF);
		}
		for (int y = 1; y < VISIBLE_ROWS; y++) {
			c.rect(0, y * CELL, boardW, y * CELL + c.pixel(), 0x10FFFFFF);
		}

		// liegende Scheiben (am Spielende von unten her grau)
		int grey = isOver() ? Math.min(VISIBLE_ROWS, overSteps() / 2) : 0;
		for (int y = HIDDEN; y < ROWS; y++) {
			for (int x = 0; x < COLS; x++) {
				if (grid[y][x] != 0) {
					boolean dead = ROWS - y <= grey;
					drawSlice(c, x * CELL, (y - HIDDEN) * CELL, CELL, grid[y][x] - 1, dead);
				}
			}
		}
		if (clearTimer > 0) {
			float flash = clearTimer / (float) CLEAR_STEPS;
			for (int y : clearing) {
				if (y >= HIDDEN) {
					c.rect(0, (y - HIDDEN) * CELL, boardW, (y - HIDDEN + 1) * CELL, Colors.alpha(0xFFFFFFFF, flash));
				}
			}
		}

		if (active && !isOver()) {
			// Landeplatz
			int ghost = py;
			while (fits(type, rot, px, ghost + 1)) {
				ghost++;
			}
			for (int[] cell : ROTATIONS[type][rot]) {
				int gy = ghost + cell[1] - HIDDEN;
				if (gy >= 0) {
					float x0 = (px + cell[0]) * CELL;
					float y0 = gy * CELL;
					c.outline(x0 + 0.5f, y0 + 0.5f, x0 + CELL - 0.5f, y0 + CELL - 0.5f, 1, Colors.alpha(COLORS[type], 0.55f));
				}
			}
			// fallendes Stück, zwischen zwei Reihen weich überblendet
			float offset = 0;
			if (!grounded()) {
				int g = isHeld(GameKey.DOWN) ? Math.min(gravity(), SOFT_DROP) : gravity();
				offset = Math.min(1f, (gravityTimer + partial) / g);
				offset = g <= 2 ? offset : Math.max(0f, offset * 3 - 2); // langsam: erst kurz vor dem Schritt gleiten
			}
			c.clip(0, 0, boardW, boardH);
			for (int[] cell : ROTATIONS[type][rot]) {
				float y0 = (py + cell[1] - HIDDEN + offset) * CELL;
				drawSlice(c, (px + cell[0]) * CELL, y0, CELL, type, false);
			}
			c.unclip();
		}
		c.outline(-1, -1, boardW + 1, boardH + 1, 1, Colors.opaque(theme.outline()));

		// Seitenleiste: halten und als Nächstes
		c.text(c.tr("salami.minigames.cold_cuts.hold"), SIDE_X + SIDE_W / 2, 5, 7, theme.textDim());
		previewBox(c, SIDE_X, 10, SIDE_W, 30, hold, 8, holdUsed);
		c.text(c.tr("salami.minigames.cold_cuts.next"), SIDE_X + SIDE_W / 2, 51, 7, theme.textDim());
		int index = 0;
		float y = 56;
		for (int t : queue) {
			if (index >= 3) {
				break;
			}
			float h = index == 0 ? 30 : 24;
			previewBox(c, SIDE_X, y, SIDE_W, h, t, index == 0 ? 8 : 6, false);
			y += h + 4;
			index++;
		}

		if (popupTimer > 0) {
			float rise = (50 - popupTimer + partial) * 0.4f;
			c.text(popup, boardW / 2, boardH * 0.4f - rise, 12, Colors.alpha(Colors.GOLD, Math.min(1f, popupTimer / 20f)));
		}
	}

	private void previewBox(Canvas c, float x, float y, float w, float h, int t, float cell, boolean dim) {
		Theme theme = c.theme();
		c.rect(x, y, x + w, y + h, Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.3f));
		c.outline(x, y, x + w, y + h, 1, Colors.opaque(theme.outline()));
		if (t < 0) {
			return;
		}
		int minX = 9;
		int maxX = -9;
		int minY = 9;
		int maxY = -9;
		for (int[] cellPos : ROTATIONS[t][0]) {
			minX = Math.min(minX, cellPos[0]);
			maxX = Math.max(maxX, cellPos[0]);
			minY = Math.min(minY, cellPos[1]);
			maxY = Math.max(maxY, cellPos[1]);
		}
		float ox = x + (w - (maxX - minX + 1) * cell) / 2 - minX * cell;
		float oy = y + (h - (maxY - minY + 1) * cell) / 2 - minY * cell;
		for (int[] cellPos : ROTATIONS[t][0]) {
			drawSlice(c, ox + cellPos[0] * cell, oy + cellPos[1] * cell, cell, t, dim);
		}
	}

	/** Eine Aufschnitt-Scheibe: Grundfarbe, Licht oben links, Schatten unten rechts und Muster der Sorte. */
	private static void drawSlice(Canvas c, float x, float y, float s, int t, boolean dead) {
		int base = dead ? Colors.mix(COLORS[t], 0xFF707070, 0.75f) : COLORS[t];
		float e = s * 0.12f;
		c.rect(x, y, x + s, y + s, Colors.darker(base, 0.45f));
		c.rect(x, y, x + s - e, y + s - e, Colors.lighter(base, 0.3f));
		c.rect(x + e, y + e, x + s - e, y + s - e, base);
		float u = s / 10f;
		int dot;
		switch (t) {
			case 0 -> { // Salami: Fettaugen
				dot = Colors.alpha(Colors.FAT, dead ? 0.4f : 0.9f);
				c.rect(x + 2.5f * u, y + 2.5f * u, x + 4 * u, y + 4 * u, dot);
				c.rect(x + 6 * u, y + 5.5f * u, x + 7.5f * u, y + 7 * u, dot);
				c.rect(x + 3 * u, y + 6.5f * u, x + 4 * u, y + 7.5f * u, dot);
			}
			case 1 -> { // Käse: Löcher
				dot = Colors.darker(base, 0.2f);
				c.rect(x + 2.5f * u, y + 3 * u, x + 4.5f * u, y + 5 * u, dot);
				c.rect(x + 6 * u, y + 6 * u, x + 7.5f * u, y + 7.5f * u, dot);
			}
			case 2 -> { // Schinken: Fettrand
				c.rect(x + e, y + e, x + s - e, y + 2.5f * u, Colors.alpha(Colors.FAT, dead ? 0.4f : 0.8f));
			}
			case 3 -> { // Gurke: Kerne
				dot = Colors.lighter(base, 0.5f);
				c.rect(x + 3 * u, y + 3 * u, x + 4 * u, y + 4 * u, dot);
				c.rect(x + 6 * u, y + 4 * u, x + 7 * u, y + 5 * u, dot);
				c.rect(x + 4.5f * u, y + 6 * u, x + 5.5f * u, y + 7 * u, dot);
			}
			case 4 -> { // Peperoni: dunkle Pünktchen
				dot = Colors.darker(base, 0.35f);
				c.rect(x + 3 * u, y + 3 * u, x + 4.5f * u, y + 4.5f * u, dot);
				c.rect(x + 5.5f * u, y + 6 * u, x + 7 * u, y + 7.5f * u, dot);
			}
			case 5 -> { // Mortadella: Pistazien und Fett
				c.rect(x + 2.5f * u, y + 3 * u, x + 4 * u, y + 4.5f * u, dead ? 0xFF8A8A8A : 0xFF7FB04A);
				c.rect(x + 6 * u, y + 6 * u, x + 7.5f * u, y + 7.5f * u, Colors.alpha(Colors.FAT, 0.9f));
			}
			default -> { // Leberkäse: Kruste
				c.rect(x + e, y + s - e - 2 * u, x + s - e, y + s - e, Colors.darker(base, 0.25f));
			}
		}
	}
}
