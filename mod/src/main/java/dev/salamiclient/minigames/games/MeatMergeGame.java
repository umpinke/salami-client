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
 * „Verwursten“: Alle Stücke rutschen in eine Richtung; zwei gleiche verschmelzen zu einem doppelt so
 * großen. Ziel ist die 2048er-Riesenwurst – weiterspielen geht auch danach, bis nichts mehr rutscht.
 */
public final class MeatMergeGame extends Minigame {
	public static final int N = 4;
	private static final float CELL = 40;
	private static final float GAP = 5;
	private static final float SIZE = N * CELL + (N + 1) * GAP;
	private static final int SLIDE = 6;
	private static final int POP = 6;
	private static final int GOAL = 2048;

	private static final int KIND_STAY = 0;
	private static final int KIND_MERGED = 1;
	private static final int KIND_SPAWNED = 2;

	private static final class Tile {
		final int value;
		int x;
		int y;
		int fromX;
		int fromY;
		int kind;

		Tile(int value, int x, int y, int kind) {
			this.value = value;
			this.x = x;
			this.y = y;
			this.fromX = x;
			this.fromY = y;
			this.kind = kind;
		}
	}

	private final Tile[][] grid = new Tile[N][N];
	/** Stücke, die gerade in ein anderes hineinrutschen (nur für die Animation). */
	private final List<Tile> ghosts = new ArrayList<>();
	private int anim;
	private int biggest;
	private boolean goalReached;
	private int bannerTimer;

	@Override
	public String id() {
		return "meat_merge";
	}

	@Override
	public float width() {
		return SIZE;
	}

	@Override
	public float height() {
		return SIZE;
	}

	@Override
	public List<String> controls() {
		return List.of("salami.minigames.controls.slide");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.biggest", String.valueOf(biggest)));
	}

	@Override
	protected void reset() {
		for (Tile[] row : grid) {
			java.util.Arrays.fill(row, null);
		}
		ghosts.clear();
		goalReached = false;
		bannerTimer = 0;
		spawn();
		spawn();
		anim = SLIDE;
		updateBiggest();
	}

	@Override
	public void demo() {
		restart(3);
		int[][] values = {{2, 4, 16, 2}, {0, 8, 64, 4}, {0, 2, 128, 512}, {2, 0, 32, 1024}};
		setBoard(values);
		setScore(9876);
	}

	/** Brett direkt setzen (0 = leer) – Vorschau und Tests. */
	public void setBoard(int[][] values) {
		ghosts.clear();
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				grid[y][x] = values[y][x] == 0 ? null : new Tile(values[y][x], x, y, KIND_STAY);
			}
		}
		anim = SLIDE + POP;
		updateBiggest();
	}

	/** Wert an (x, y), 0 = leer. */
	public int valueAt(int x, int y) {
		return grid[y][x] == null ? 0 : grid[y][x].value;
	}

	private void spawn() {
		List<int[]> empty = new ArrayList<>();
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				if (grid[y][x] == null) {
					empty.add(new int[] {x, y});
				}
			}
		}
		if (empty.isEmpty()) {
			return;
		}
		int[] cell = empty.get(random.nextInt(empty.size()));
		grid[cell[1]][cell[0]] = new Tile(random.nextInt(10) == 0 ? 4 : 2, cell[0], cell[1], KIND_SPAWNED);
	}

	private void updateBiggest() {
		biggest = 0;
		for (Tile[] row : grid) {
			for (Tile tile : row) {
				if (tile != null) {
					biggest = Math.max(biggest, tile.value);
				}
			}
		}
	}

	@Override
	protected void update() {
		if (anim < SLIDE + POP) {
			anim++;
			if (anim == SLIDE) {
				ghosts.clear();
			}
		}
		if (bannerTimer > 0) {
			bannerTimer--;
		}
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
		if (d != null && !isOver()) {
			move(d[0], d[1]);
		}
	}

	/** Schiebt alle Stücke; {@code true}, wenn sich etwas bewegt hat. */
	public boolean move(int dx, int dy) {
		// laufende Animation sofort beenden, sonst wirkt schnelles Tippen träge
		anim = SLIDE + POP;
		ghosts.clear();
		for (Tile[] row : grid) {
			for (Tile tile : row) {
				if (tile != null) {
					tile.fromX = tile.x;
					tile.fromY = tile.y;
					tile.kind = KIND_STAY;
				}
			}
		}
		boolean[][] merged = new boolean[N][N];
		boolean moved = false;
		int gained = 0;
		int bestMerge = 0;
		for (int i = 0; i < N; i++) {
			int y = dy > 0 ? N - 1 - i : i;
			for (int j = 0; j < N; j++) {
				int x = dx > 0 ? N - 1 - j : j;
				Tile tile = grid[y][x];
				if (tile == null) {
					continue;
				}
				int cx = x;
				int cy = y;
				while (inside(cx + dx, cy + dy) && grid[cy + dy][cx + dx] == null) {
					cx += dx;
					cy += dy;
				}
				int nx = cx + dx;
				int ny = cy + dy;
				if (inside(nx, ny) && !merged[ny][nx] && grid[ny][nx].value == tile.value) {
					Tile other = grid[ny][nx];
					grid[y][x] = null;
					Tile result = new Tile(tile.value * 2, nx, ny, KIND_MERGED);
					grid[ny][nx] = result;
					merged[ny][nx] = true;
					tile.x = nx;
					tile.y = ny;
					ghosts.add(tile);
					ghosts.add(other);
					gained += result.value;
					bestMerge = Math.max(bestMerge, result.value);
					moved = true;
				} else if (cx != x || cy != y) {
					grid[y][x] = null;
					grid[cy][cx] = tile;
					tile.x = cx;
					tile.y = cy;
					moved = true;
				}
			}
		}
		if (!moved) {
			return false;
		}
		anim = 0;
		addScore(gained);
		spawn();
		updateBiggest();
		if (bestMerge > 0) {
			// höhere Stücke klingen höher
			sound(Sfx.MERGE, 0.7f + Math.min(11, Integer.numberOfTrailingZeros(bestMerge)) * 0.1f);
		} else {
			sound(Sfx.MOVE);
		}
		if (biggest >= GOAL && !goalReached) {
			goalReached = true;
			bannerTimer = 150;
			sound(Sfx.WIN);
		}
		if (!canMove()) {
			sound(Sfx.LOSE);
			gameOver();
		}
		return true;
	}

	private static boolean inside(int x, int y) {
		return x >= 0 && y >= 0 && x < N && y < N;
	}

	private boolean canMove() {
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				Tile tile = grid[y][x];
				if (tile == null) {
					return true;
				}
				if ((x + 1 < N && grid[y][x + 1] != null && grid[y][x + 1].value == tile.value)
					|| (y + 1 < N && grid[y + 1][x] != null && grid[y + 1][x].value == tile.value)) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		c.roundRect(0, 0, SIZE, SIZE, 6, Colors.mix(Colors.opaque(theme.header()), 0xFF000000, 0.35f));
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				c.roundRect(slot(x), slot(y), slot(x) + CELL, slot(y) + CELL, 4, 0x22FFFFFF);
			}
		}
		float progress = Math.min(1f, (anim + partial) / SLIDE);
		float ease = 1 - (1 - progress) * (1 - progress);
		if (anim < SLIDE) {
			for (Tile ghost : ghosts) {
				drawTile(c, ghost, ease, 1f);
			}
		}
		for (Tile[] row : grid) {
			for (Tile tile : row) {
				if (tile == null) {
					continue;
				}
				if (anim < SLIDE) {
					if (tile.kind == KIND_STAY) {
						drawTile(c, tile, ease, 1f);
					}
					continue;
				}
				float pop = Math.min(1f, (anim - SLIDE + partial) / POP);
				float scale = switch (tile.kind) {
					case KIND_MERGED -> 1f + 0.14f * (float) Math.sin(Math.PI * pop);
					case KIND_SPAWNED -> 0.2f + 0.8f * (1 - (1 - pop) * (1 - pop));
					default -> 1f;
				};
				drawTile(c, tile, 1f, scale);
			}
		}
		if (bannerTimer > 0) {
			float alpha = Math.min(1f, bannerTimer / 30f);
			c.rect(0, SIZE / 2 - 16, SIZE, SIZE / 2 + 16, Colors.alpha(0xC0000000, alpha));
			c.text(c.tr("salami.minigames.meat_merge.goal"), SIZE / 2, SIZE / 2, 16, Colors.alpha(Colors.GOLD, alpha));
		}
	}

	private static float slot(int i) {
		return GAP + i * (CELL + GAP);
	}

	private static void drawTile(Canvas c, Tile tile, float t, float scale) {
		float x = lerp(slot(tile.fromX), slot(tile.x), t) + CELL / 2;
		float y = lerp(slot(tile.fromY), slot(tile.y), t) + CELL / 2;
		float half = CELL / 2 * scale;
		int color = tileColor(tile.value);
		c.roundRect(x - half, y - half + 1.5f, x + half, y + half + 1.5f, 4 * scale, Colors.darker(color, 0.4f));
		c.roundRect(x - half, y - half, x + half, y + half, 4 * scale, color);
		// Fettauge als Deko auf den Wurststücken
		if (tile.value >= 8) {
			c.rect(x - half + 5 * scale, y - half + 5 * scale, x - half + 8 * scale, y - half + 8 * scale, Colors.alpha(Colors.FAT, 0.35f));
		}
		String text = String.valueOf(tile.value);
		float size = switch (text.length()) {
			case 1, 2 -> 18;
			case 3 -> 15;
			case 4 -> 12;
			default -> 10;
		};
		c.text(text, x, y + 0.5f, size * scale, tile.value <= 4 ? 0xFF5A3A2A : 0xFFFFF8F0);
	}

	private static int tileColor(int value) {
		return switch (value) {
			case 2 -> 0xFFF3E3D3;
			case 4 -> 0xFFEBCFB2;
			case 8 -> 0xFFE8A866;
			case 16 -> 0xFFE3874F;
			case 32 -> 0xFFD9653F;
			case 64 -> 0xFFC9453A;
			case 128 -> 0xFFE0B640;
			case 256 -> 0xFFD3A232;
			case 512 -> 0xFFC48D26;
			case 1024 -> 0xFFB23A3A;
			case 2048 -> 0xFF8E2230;
			default -> 0xFF4A1A1A;
		};
	}
}
