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
 * „Chili-Alarm“: Unter den Brotscheiben verstecken sich scharfe Chilis. Zahlen verraten, wie viele
 * Chilis um ein Feld liegen; verdächtige Felder mit einem Zahnstocher markieren. Der erste Klick
 * trifft nie eine Chili (auch nicht daneben).
 */
public final class ChiliAlertGame extends Minigame {
	public static final int COLS = 16;
	public static final int ROWS = 12;
	public static final int CHILIS = 30;
	private static final float CELL = 12;
	private static final int[] NUMBER_COLORS = {0, 0xFF6FA8DC, 0xFF8BC34A, 0xFFE0454A, 0xFFB36AE2, 0xFFE69138, 0xFF4DD0E1, 0xFFF3E3D3, 0xFFA88C7A};

	private boolean[] chili;
	private boolean[] open;
	private boolean[] flag;
	private int[] near;
	private boolean placed;
	private int opened;
	private int flags;
	private int exploded;
	private boolean won;
	private int startStep;
	private int endStep;
	private int cursor;
	private boolean keyboardCursor;
	private int hover;

	@Override
	public String id() {
		return "chili_alert";
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
		return List.of("salami.minigames.controls.reveal", "salami.minigames.controls.flag", "salami.minigames.controls.cursor");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.chilis", String.valueOf(CHILIS - flags)),
			new Stat("salami.minigames.stat.time", clock(elapsed())));
	}

	@Override
	public String overTitleKey() {
		return won ? "salami.minigames.won" : super.overTitleKey();
	}

	@Override
	public int overlayDelay() {
		return won ? 30 : 75;
	}

	@Override
	protected void reset() {
		int n = COLS * ROWS;
		chili = new boolean[n];
		open = new boolean[n];
		flag = new boolean[n];
		near = new int[n];
		placed = false;
		opened = 0;
		flags = 0;
		exploded = -1;
		won = false;
		startStep = 0;
		endStep = 0;
		cursor = index(COLS / 2, ROWS / 2);
		keyboardCursor = false;
		hover = -1;
	}

	@Override
	public void demo() {
		restart(9);
		reveal(index(5, 6));
		int flagged = 0;
		for (int i = 0; i < COLS * ROWS && flagged < 4; i++) {
			if (chili[i] && neighborOpen(i)) {
				flag[i] = true;
				flags++;
				flagged++;
			}
		}
		hover = -1;
	}

	private boolean neighborOpen(int i) {
		for (int j : neighbors(i)) {
			if (open[j]) {
				return true;
			}
		}
		return false;
	}

	private static int index(int x, int y) {
		return x + y * COLS;
	}

	private int elapsed() {
		if (!placed) {
			return 0;
		}
		return (isOver() ? endStep : steps()) - startStep;
	}

	private static List<Integer> neighbors(int i) {
		List<Integer> list = new ArrayList<>(8);
		int x = i % COLS;
		int y = i / COLS;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < COLS && ny < ROWS) {
					list.add(index(nx, ny));
				}
			}
		}
		return list;
	}

	/** Chilis verteilen – nie auf das erste Feld und seine Nachbarn. */
	private void place(int safe) {
		List<Integer> candidates = new ArrayList<>();
		List<Integer> blocked = neighbors(safe);
		for (int i = 0; i < COLS * ROWS; i++) {
			if (i != safe && !blocked.contains(i)) {
				candidates.add(i);
			}
		}
		Collections.shuffle(candidates, random);
		for (int k = 0; k < CHILIS; k++) {
			chili[candidates.get(k)] = true;
		}
		for (int i = 0; i < COLS * ROWS; i++) {
			int count = 0;
			for (int j : neighbors(i)) {
				if (chili[j]) {
					count++;
				}
			}
			near[i] = count;
		}
		placed = true;
		startStep = steps();
	}

	@Override
	protected void update() {}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public void click(float x, float y, int button) {
		int cell = cellAt(x, y);
		if (cell < 0) {
			return;
		}
		keyboardCursor = false;
		cursor = cell;
		switch (button) {
			case 0 -> primary(cell);
			case 1 -> toggleFlag(cell);
			case 2 -> chord(cell);
			default -> {
			}
		}
	}

	@Override
	public void hover(float x, float y) {
		int cell = cellAt(x, y);
		if (cell != hover) {
			hover = cell;
			keyboardCursor = false;
		}
	}

	private static int cellAt(float x, float y) {
		int cx = (int) Math.floor(x / CELL);
		int cy = (int) Math.floor(y / CELL);
		return cx >= 0 && cy >= 0 && cx < COLS && cy < ROWS ? index(cx, cy) : -1;
	}

	@Override
	protected void onPress(GameKey key) {
		if (isOver()) {
			return;
		}
		int x = cursor % COLS;
		int y = cursor / COLS;
		switch (key) {
			case UP -> y = Math.max(0, y - 1);
			case DOWN -> y = Math.min(ROWS - 1, y + 1);
			case LEFT -> x = Math.max(0, x - 1);
			case RIGHT -> x = Math.min(COLS - 1, x + 1);
			case ACTION -> primary(cursor);
			case ALT -> toggleFlag(cursor);
			default -> {
			}
		}
		if (key == GameKey.UP || key == GameKey.DOWN || key == GameKey.LEFT || key == GameKey.RIGHT) {
			cursor = index(x, y);
			keyboardCursor = true;
		}
	}

	/** Linksklick: zugedecktes Feld aufdecken, aufgedeckte Zahl „aufräumen“. */
	public void primary(int i) {
		if (isOver()) {
			return;
		}
		if (open[i]) {
			chord(i);
		} else {
			reveal(i);
		}
	}

	private void reveal(int i) {
		if (isOver() || flag[i] || open[i]) {
			return;
		}
		if (!placed) {
			place(i);
		}
		if (chili[i]) {
			explode(i);
			return;
		}
		flood(i);
		sound(Sfx.REVEAL, 0.9f + random.nextFloat() * 0.2f);
		checkWin();
	}

	/** Aufgedeckte Zahl mit passend vielen Markierungen: alle übrigen Nachbarn aufdecken. */
	private void chord(int i) {
		if (isOver() || !open[i] || near[i] == 0) {
			return;
		}
		int marked = 0;
		for (int j : neighbors(i)) {
			if (flag[j]) {
				marked++;
			}
		}
		if (marked != near[i]) {
			return;
		}
		boolean any = false;
		for (int j : neighbors(i)) {
			if (!flag[j] && !open[j]) {
				if (chili[j]) {
					explode(j);
					return;
				}
				flood(j);
				any = true;
			}
		}
		if (any) {
			sound(Sfx.REVEAL);
			checkWin();
		}
	}

	private void flood(int start) {
		ArrayDeque<Integer> todo = new ArrayDeque<>();
		todo.add(start);
		while (!todo.isEmpty()) {
			int i = todo.poll();
			if (open[i] || flag[i] || chili[i]) {
				continue;
			}
			open[i] = true;
			opened++;
			addScore(5);
			if (near[i] == 0) {
				todo.addAll(neighbors(i));
			}
		}
	}

	public void toggleFlag(int i) {
		if (isOver() || open[i]) {
			return;
		}
		flag[i] = !flag[i];
		flags += flag[i] ? 1 : -1;
		sound(Sfx.FLAG, flag[i] ? 1.2f : 0.9f);
	}

	private void explode(int i) {
		exploded = i;
		open[i] = true;
		endStep = steps();
		sound(Sfx.BOOM);
		gameOver();
	}

	private void checkWin() {
		if (opened != COLS * ROWS - CHILIS) {
			return;
		}
		won = true;
		endStep = steps();
		for (int i = 0; i < COLS * ROWS; i++) {
			if (chili[i] && !flag[i]) {
				flag[i] = true;
				flags++;
			}
		}
		int seconds = elapsed() / STEPS_PER_SECOND;
		addScore(1000 + Math.max(0, 300 - seconds) * 5);
		sound(Sfx.WIN);
		gameOver();
	}

	// ------------------------------------------------------------------ für Tests

	public boolean isChili(int x, int y) {
		return chili[index(x, y)];
	}

	public boolean isOpen(int x, int y) {
		return open[index(x, y)];
	}

	public boolean isWon() {
		return won;
	}

	/** Alle sicheren Felder aufdecken (Test für den Sieg). */
	public void revealAllSafe() {
		for (int i = 0; i < COLS * ROWS && !isOver(); i++) {
			if (!chili[i]) {
				reveal(i);
			}
		}
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		int boardColor = Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.3f);
		int grid = Colors.alpha(Colors.opaque(theme.outline()), 0.6f);
		c.rect(0, 0, width(), height(), boardColor);
		int ex = exploded % COLS;
		int ey = exploded / COLS;
		for (int i = 0; i < COLS * ROWS; i++) {
			float x = i % COLS * CELL;
			float y = i / COLS * CELL;
			boolean showChili = chili[i] && isOver() && !won
				&& overSteps() >= 3 * Math.max(Math.abs(i % COLS - ex), Math.abs(i / COLS - ey));
			if (open[i] || showChili) {
				c.rect(x, y, x + CELL, y + CELL, i == exploded ? 0xFFC0392B : boardColor);
				c.outline(x, y, x + CELL, y + CELL, c.pixel(), grid);
				if (chili[i]) {
					drawChili(c, x, y);
				} else if (near[i] > 0) {
					c.text(String.valueOf(near[i]), x + CELL / 2, y + CELL / 2 + 0.5f, 9, NUMBER_COLORS[near[i]]);
				}
			} else {
				// zugedeckt: Brotscheibe mit Kruste
				c.rect(x, y, x + CELL, y + CELL, Colors.CRUST);
				c.rect(x, y, x + CELL - 1, y + CELL - 1, Colors.lighter(Colors.BREAD, 0.3f));
				c.rect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, Colors.BREAD);
				if (!isOver() && (i == hover && !keyboardCursor)) {
					c.rect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
				}
				if (flag[i]) {
					drawFlag(c, x, y, theme);
					if (isOver() && !won && !chili[i] && overSteps() > 20) {
						// falsch markiert
						for (int k = 0; k < 8; k++) {
							c.rect(x + 2 + k, y + 2 + k, x + 3 + k, y + 3 + k, 0xFFE0454A);
							c.rect(x + 9 - k, y + 2 + k, x + 10 - k, y + 3 + k, 0xFFE0454A);
						}
					}
				}
			}
		}
		if (keyboardCursor && !isOver()) {
			float x = cursor % COLS * CELL;
			float y = cursor / COLS * CELL;
			c.outline(x - 0.5f, y - 0.5f, x + CELL + 0.5f, y + CELL + 0.5f, 1.2f, Colors.opaque(theme.accent()));
		}
		if (!placed && !isOver()) {
			c.rect(0, height() / 2 - 9, width(), height() / 2 + 8, 0x90000000);
			c.text(c.tr("salami.minigames.hint.first_click"), width() / 2, height() / 2, 9, theme.headerText());
		}
	}

	/** Eine rote Chili mit grünem Stiel (12er-Raster). */
	private static void drawChili(Canvas c, float x, float y) {
		float u = CELL / 12f;
		int red = 0xFFD63A2A;
		c.rect(x + 6 * u, y + 1.5f * u, x + 7 * u, y + 3.5f * u, 0xFF4E8A2E);
		c.rect(x + 4.5f * u, y + 3 * u, x + 8.5f * u, y + 4.2f * u, 0xFF4E8A2E);
		c.rect(x + 4 * u, y + 4 * u, x + 8.5f * u, y + 7 * u, red);
		c.rect(x + 5 * u, y + 7 * u, x + 8.5f * u, y + 9 * u, red);
		c.rect(x + 6 * u, y + 9 * u, x + 8.3f * u, y + 10 * u, red);
		c.rect(x + 7 * u, y + 10 * u, x + 8 * u, y + 11 * u, red);
		c.rect(x + 5 * u, y + 4.6f * u, x + 6 * u, y + 6.6f * u, 0xFFF07A5A);
	}

	/** Zahnstocher mit Fähnchen. */
	private static void drawFlag(Canvas c, float x, float y, Theme theme) {
		float u = CELL / 12f;
		c.rect(x + 5.5f * u, y + 2 * u, x + 6.5f * u, y + 10 * u, 0xFF8A6A40);
		c.rect(x + 6.5f * u, y + 2 * u, x + 9.5f * u, y + 4 * u, Colors.SALAMI);
		c.rect(x + 6.5f * u, y + 4 * u, x + 8.5f * u, y + 5 * u, Colors.SALAMI);
		c.rect(x + 4 * u, y + 9.5f * u, x + 8 * u, y + 10.5f * u, Colors.darker(Colors.CRUST, 0.3f));
	}
}
