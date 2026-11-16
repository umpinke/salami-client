package dev.salamiclient.minigames.games;

import dev.salamiclient.minigames.Canvas;
import dev.salamiclient.minigames.Colors;
import dev.salamiclient.minigames.GameKey;
import dev.salamiclient.minigames.Minigame;
import dev.salamiclient.minigames.Sfx;
import dev.salamiclient.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * „Wurst gegen Käse“: Salamischeiben (du) gegen Käsescheiben (CPU) in einem senkrechten Brett.
 * Wer zuerst vier in einer Reihe hat – waagerecht, senkrecht oder schräg – gewinnt. Eine Partie
 * folgt der nächsten, der Käse wird mit jedem Sieg schlauer; die erste Niederlage beendet den Lauf.
 */
public final class SalamiVsCheeseGame extends Minigame {
	public static final int COLS = 7;
	public static final int ROWS = 6;
	public static final int PLAYER = 1;
	public static final int CPU = 2;
	private static final float CELL = 24;
	private static final float TOP = 26;
	private static final int[] ORDER = {3, 2, 4, 1, 5, 0, 6};
	private static final int WIN_SCORE = 1_000_000;

	private static final int RESULT_NONE = 0;
	private static final int RESULT_WIN = 1;
	private static final int RESULT_LOSS = 2;
	private static final int RESULT_DRAW = 3;

	private final int[][] board = new int[ROWS][COLS];
	private int turn;
	private int selected;
	private boolean dropping;
	private int dropCol;
	private int dropRow;
	private int dropWho;
	private float dropY;
	private float prevDropY;
	private float dropVy;
	private int cpuTimer;
	private int result;
	private int resultTimer;
	private int[][] winCells;
	private int round;
	private int wins;
	private int draws;
	private int lastGain;

	@Override
	public String id() {
		return "salami_vs_cheese";
	}

	@Override
	public float width() {
		return COLS * CELL;
	}

	@Override
	public float height() {
		return TOP + ROWS * CELL;
	}

	@Override
	public List<String> controls() {
		return List.of("salami.minigames.controls.column", "salami.minigames.controls.drop");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.round", String.valueOf(round)),
			new Stat("salami.minigames.stat.wins", String.valueOf(wins)),
			new Stat("salami.minigames.stat.draws", String.valueOf(draws)));
	}

	@Override
	public int overlayDelay() {
		return 80;
	}

	@Override
	protected void reset() {
		round = 1;
		wins = 0;
		draws = 0;
		startMatch();
	}

	@Override
	public void demo() {
		restart(4);
		String[] rows = {
			".......",
			".......",
			"...2...",
			"..21...",
			"..122..",
			".21112.",
		};
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				char ch = rows[r].charAt(c);
				board[r][c] = ch == '.' ? 0 : ch - '0';
			}
		}
		round = 3;
		wins = 2;
		turn = PLAYER;
		selected = 4;
		setScore(246);
	}

	private void startMatch() {
		for (int[] row : board) {
			java.util.Arrays.fill(row, 0);
		}
		result = RESULT_NONE;
		winCells = null;
		dropping = false;
		selected = 3;
		// abwechselnd beginnen: ungerade Runden du, gerade der Käse
		turn = round % 2 == 1 ? PLAYER : CPU;
		cpuTimer = 30 + random.nextInt(20);
	}

	@Override
	protected void update() {
		prevDropY = dropY;
		if (dropping) {
			dropVy += 0.55f;
			dropY += dropVy;
			float target = TOP + dropRow * CELL + CELL / 2;
			if (dropY >= target) {
				dropY = target;
				prevDropY = Math.min(prevDropY, target);
				land();
			}
			return;
		}
		if (result != RESULT_NONE) {
			if (result != RESULT_LOSS && --resultTimer <= 0) {
				round++;
				startMatch();
			}
			return;
		}
		if (isOver()) {
			return;
		}
		if (turn == CPU && --cpuTimer <= 0) {
			drop(chooseMove(), CPU);
		}
	}

	private boolean drop(int col, int who) {
		int row = lowestFree(board, col);
		if (row < 0) {
			return false;
		}
		dropping = true;
		dropCol = col;
		dropRow = row;
		dropWho = who;
		dropY = TOP / 2;
		prevDropY = dropY;
		dropVy = 1.5f;
		return true;
	}

	private void land() {
		dropping = false;
		board[dropRow][dropCol] = dropWho;
		sound(Sfx.DROP, dropWho == PLAYER ? 1f : 0.8f);
		winCells = findWin(board, dropWho);
		if (winCells != null) {
			if (dropWho == PLAYER) {
				result = RESULT_WIN;
				wins++;
				lastGain = 100 + 4 * emptyCells() + 20 * Math.min(wins - 1, 5);
				addScore(lastGain);
				resultTimer = 120;
				sound(Sfx.WIN);
			} else {
				result = RESULT_LOSS;
				sound(Sfx.LOSE);
				gameOver();
			}
			return;
		}
		if (emptyCells() == 0) {
			result = RESULT_DRAW;
			draws++;
			lastGain = 25;
			addScore(lastGain);
			resultTimer = 100;
			return;
		}
		turn = dropWho == PLAYER ? CPU : PLAYER;
		if (turn == CPU) {
			cpuTimer = 25 + random.nextInt(25);
		}
	}

	private int emptyCells() {
		int count = 0;
		for (int[] row : board) {
			for (int cell : row) {
				if (cell == 0) {
					count++;
				}
			}
		}
		return count;
	}

	private boolean playerCanAct() {
		return !isOver() && !dropping && result == RESULT_NONE && turn == PLAYER;
	}

	@Override
	protected void onPress(GameKey key) {
		if (isOver()) {
			return;
		}
		switch (key) {
			case LEFT -> {
				selected = Math.max(0, selected - 1);
				sound(Sfx.MOVE);
			}
			case RIGHT -> {
				selected = Math.min(COLS - 1, selected + 1);
				sound(Sfx.MOVE);
			}
			case ACTION, DOWN -> {
				if (playerCanAct()) {
					drop(selected, PLAYER);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void hover(float x, float y) {
		if (x >= 0 && x < width() && y >= 0 && y < height()) {
			selected = Math.max(0, Math.min(COLS - 1, (int) (x / CELL)));
		}
	}

	@Override
	public void click(float x, float y, int button) {
		if (button != 0 || x < 0 || x >= width()) {
			return;
		}
		selected = Math.max(0, Math.min(COLS - 1, (int) (x / CELL)));
		if (playerCanAct()) {
			drop(selected, PLAYER);
		}
	}

	// ------------------------------------------------------------------ CPU

	/** Zug des Käses: Suche mit Alpha-Beta, Tiefe wächst mit deinen Siegen; ab und zu ein Patzer. */
	public int chooseMove() {
		int depth = Math.min(3 + wins, 6);
		int[][] copy = new int[ROWS][];
		for (int r = 0; r < ROWS; r++) {
			copy[r] = board[r].clone();
		}
		List<Integer> best = new ArrayList<>();
		int bestScore = Integer.MIN_VALUE;
		List<Integer> safe = new ArrayList<>();
		for (int col : ORDER) {
			int row = lowestFree(copy, col);
			if (row < 0) {
				continue;
			}
			copy[row][col] = CPU;
			int score;
			if (wins(copy, row, col, CPU)) {
				score = WIN_SCORE + depth;
			} else {
				score = -negamax(copy, depth - 1, -WIN_SCORE * 2, WIN_SCORE * 2, PLAYER);
			}
			copy[row][col] = 0;
			if (score > -WIN_SCORE / 2) {
				safe.add(col);
			}
			if (score > bestScore) {
				bestScore = score;
				best.clear();
				best.add(col);
			} else if (score == bestScore) {
				best.add(col);
			}
		}
		if (best.isEmpty()) {
			return 3;
		}
		// Patzer (seltener, je öfter du gewinnst) – aber nie einen sofort verlorenen oder einen Gewinn liegen lassen
		float mistake = Math.max(0f, 0.3f - 0.08f * wins);
		if (bestScore < WIN_SCORE / 2 && !safe.isEmpty() && random.nextFloat() < mistake) {
			return safe.get(random.nextInt(safe.size()));
		}
		return best.get(random.nextInt(best.size()));
	}

	private static int negamax(int[][] b, int depth, int alpha, int beta, int who) {
		if (depth == 0) {
			return evaluate(b, who);
		}
		int other = who == PLAYER ? CPU : PLAYER;
		boolean any = false;
		int best = Integer.MIN_VALUE;
		for (int col : ORDER) {
			int row = lowestFree(b, col);
			if (row < 0) {
				continue;
			}
			any = true;
			b[row][col] = who;
			int score = wins(b, row, col, who) ? WIN_SCORE + depth : -negamax(b, depth - 1, -beta, -alpha, other);
			b[row][col] = 0;
			best = Math.max(best, score);
			alpha = Math.max(alpha, score);
			if (alpha >= beta) {
				break;
			}
		}
		return any ? best : 0;
	}

	/** Stellungsbewertung aus Sicht von {@code who}: offene Dreier/Zweier und die Mitte zählen. */
	private static int evaluate(int[][] b, int who) {
		int score = 0;
		for (int r = 0; r < ROWS; r++) {
			if (b[r][3] == who) {
				score += 3;
			} else if (b[r][3] != 0) {
				score -= 3;
			}
		}
		int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				for (int[] d : dirs) {
					int er = r + 3 * d[0];
					int ec = c + 3 * d[1];
					if (er < 0 || er >= ROWS || ec < 0 || ec >= COLS) {
						continue;
					}
					int own = 0;
					int opp = 0;
					for (int k = 0; k < 4; k++) {
						int cell = b[r + k * d[0]][c + k * d[1]];
						if (cell == who) {
							own++;
						} else if (cell != 0) {
							opp++;
						}
					}
					if (opp == 0) {
						score += own == 3 ? 5 : own == 2 ? 2 : 0;
					} else if (own == 0) {
						score -= opp == 3 ? 4 : opp == 2 ? 1 : 0;
					}
				}
			}
		}
		return score;
	}

	static int lowestFree(int[][] b, int col) {
		for (int r = ROWS - 1; r >= 0; r--) {
			if (b[r][col] == 0) {
				return r;
			}
		}
		return -1;
	}

	/** Ob der Stein in (row, col) eine Viererreihe für {@code who} schließt. */
	static boolean wins(int[][] b, int row, int col, int who) {
		int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
		for (int[] d : dirs) {
			int count = 1;
			for (int s = -1; s <= 1; s += 2) {
				int r = row + d[0] * s;
				int c = col + d[1] * s;
				while (r >= 0 && r < ROWS && c >= 0 && c < COLS && b[r][c] == who) {
					count++;
					r += d[0] * s;
					c += d[1] * s;
				}
			}
			if (count >= 4) {
				return true;
			}
		}
		return false;
	}

	/** Die vier Felder einer Gewinnreihe von {@code who} oder {@code null}. */
	static int[][] findWin(int[][] b, int who) {
		int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				for (int[] d : dirs) {
					int[][] cells = new int[4][2];
					boolean ok = true;
					for (int k = 0; k < 4 && ok; k++) {
						int rr = r + k * d[0];
						int cc = c + k * d[1];
						ok = rr >= 0 && rr < ROWS && cc >= 0 && cc < COLS && b[rr][cc] == who;
						cells[k][0] = rr;
						cells[k][1] = cc;
					}
					if (ok) {
						return cells;
					}
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ für Tests

	public int[][] board() {
		return board;
	}

	public boolean isPlayerTurn() {
		return playerCanAct();
	}

	public int wins() {
		return wins;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		float w = width();
		float r = CELL * 0.4f;

		// Auswahl oben: deine Scheibe über der Spalte, oder der Käse „überlegt“
		if (!isOver() && !dropping && result == RESULT_NONE) {
			if (turn == PLAYER) {
				c.rect(selected * CELL, TOP, (selected + 1) * CELL, height(), 0x14FFFFFF);
				drawDisc(c, selected * CELL + CELL / 2, TOP / 2, r, PLAYER, 0.85f);
			} else {
				// der Käse überlegt: Scheibe pendelt über dem Brett
				float wobble = (float) Math.sin((steps() + partial) * 0.12f) * CELL * 2f;
				drawDisc(c, w / 2 + wobble, TOP / 2, r, CPU, 0.75f);
			}
		}

		// Brett aus Holz mit Löchern
		int wood = Colors.WOOD;
		c.roundRect(-3, TOP - 3, w + 3, height() + 3, 4, Colors.darker(wood, 0.3f));
		c.rect(-1, TOP - 1, w + 1, height() + 1, wood);
		float pulse = 0.5f + 0.5f * (float) Math.sin((steps() + overSteps() + partial) * 0.25f);
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				float cx = col * CELL + CELL / 2;
				float cy = TOP + row * CELL + CELL / 2;
				c.circle(cx, cy + 0.8f, r + 0.8f, Colors.lighter(wood, 0.2f));
				c.circle(cx, cy, r + 0.8f, Colors.darker(wood, 0.45f));
				if (isWinCell(row, col)) {
					c.circle(cx, cy, r + 2.2f, Colors.alpha(Colors.GOLD, 0.45f + 0.55f * pulse));
				}
				if (board[row][col] != 0) {
					drawDisc(c, cx, cy, r, board[row][col], 1f);
				} else {
					c.circle(cx, cy, r, 0xFF1E1412);
				}
			}
		}
		if (dropping) {
			float cy = lerp(prevDropY, dropY, partial);
			drawDisc(c, dropCol * CELL + CELL / 2, cy, r, dropWho, 1f);
		}

		if (result == RESULT_WIN || result == RESULT_DRAW || (result == RESULT_LOSS && overSteps() > 25)) {
			String text = switch (result) {
				case RESULT_WIN -> c.tr("salami.minigames.salami_vs_cheese.win", lastGain);
				case RESULT_DRAW -> c.tr("salami.minigames.salami_vs_cheese.draw", lastGain);
				default -> c.tr("salami.minigames.salami_vs_cheese.loss");
			};
			float cy = TOP + ROWS * CELL / 2f;
			c.rect(-3, cy - 11, w + 3, cy + 11, 0xD0000000);
			c.text(text, w / 2, cy, 10, result == RESULT_WIN ? Colors.GOLD : result == RESULT_DRAW ? theme.headerText() : 0xFFE0454A);
		}
	}

	private boolean isWinCell(int row, int col) {
		if (winCells == null) {
			return false;
		}
		for (int[] cell : winCells) {
			if (cell[0] == row && cell[1] == col) {
				return true;
			}
		}
		return false;
	}

	private static void drawDisc(Canvas c, float cx, float cy, float r, int who, float alpha) {
		if (who == PLAYER) {
			c.circle(cx, cy, r, Colors.alpha(Colors.CASING, alpha));
			c.circle(cx, cy, r - 1.2f, Colors.alpha(Colors.SALAMI, alpha));
			int fat = Colors.alpha(Colors.FAT, alpha * 0.9f);
			c.rect(cx - 4, cy - 4, cx - 2, cy - 2, fat);
			c.rect(cx + 2, cy - 3, cx + 3.5f, cy - 1.5f, fat);
			c.rect(cx - 1, cy + 2, cx + 1, cy + 4, fat);
			c.rect(cx + 3, cy + 2, cx + 4, cy + 3, fat);
			c.rect(cx - 4, cy + 1, cx - 3, cy + 2, Colors.alpha(Colors.PEPPER, alpha));
		} else {
			c.circle(cx, cy, r, Colors.alpha(Colors.CHEESE_DARK, alpha));
			c.circle(cx, cy, r - 1.2f, Colors.alpha(Colors.CHEESE, alpha));
			int hole = Colors.alpha(Colors.darker(Colors.CHEESE, 0.18f), alpha);
			c.circle(cx - 3, cy - 2.5f, 2, hole);
			c.circle(cx + 3, cy + 0.5f, 1.6f, hole);
			c.circle(cx - 1, cy + 4, 1.2f, hole);
		}
	}
}
