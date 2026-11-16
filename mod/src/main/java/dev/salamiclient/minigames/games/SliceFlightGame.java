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
 * „Scheibenflug“: Eine Salamischeibe flattert mit einer Taste durch die Lücken zwischen
 * Grissini-Stangen. Jede geschaffte Lücke gibt einen Punkt, mit der Zeit wird es enger und schneller.
 */
public final class SliceFlightGame extends Minigame {
	private static final float W = 240;
	private static final float H = 180;
	private static final float GROUND = H - 16;
	private static final float PLAYER_X = 64;
	private static final float RADIUS = 7;
	private static final float GRAVITY = 0.27f;
	private static final float FLAP = -4.3f;
	private static final float MAX_FALL = 6.5f;
	private static final float STICK_W = 22;
	private static final float SPACING = 104;

	private static final class Stick {
		float x;
		float prevX;
		final float gapY;
		final float gapH;
		boolean passed;

		Stick(float x, float gapY, float gapH) {
			this.x = x;
			this.prevX = x;
			this.gapY = gapY;
			this.gapH = gapH;
		}
	}

	private final List<Stick> sticks = new ArrayList<>();
	private float y;
	private float prevY;
	private float vy;
	private float scroll;
	private float prevScroll;
	private float speed;
	private float lastGap;
	private int flapTimer;
	private boolean waiting;

	@Override
	public String id() {
		return "slice_flight";
	}

	@Override
	public float width() {
		return W;
	}

	@Override
	public float height() {
		return H;
	}

	@Override
	public List<String> controls() {
		return List.of("salami.minigames.controls.flap");
	}

	@Override
	public List<Stat> stats() {
		return List.of(new Stat("salami.minigames.stat.speed", Math.round(speed / 1.6f * 100) + "%"));
	}

	@Override
	public int overlayDelay() {
		return 50;
	}

	@Override
	protected void reset() {
		sticks.clear();
		y = GROUND * 0.45f;
		prevY = y;
		vy = 0;
		scroll = 0;
		prevScroll = 0;
		speed = 1.6f;
		lastGap = GROUND / 2;
		flapTimer = 0;
		waiting = true;
		addStick(W + 30);
	}

	@Override
	public void demo() {
		restart(5);
		waiting = false;
		sticks.clear();
		sticks.add(new Stick(36, 70, 60));
		sticks.add(new Stick(140, 92, 58));
		sticks.add(new Stick(244, 60, 58));
		sticks.get(0).passed = true;
		y = 84;
		prevY = y;
		vy = -2.2f;
		flapTimer = 4;
		scroll = 37;
		prevScroll = scroll;
		speed = 1.75f;
		setScore(11);
	}

	private void addStick(float x) {
		float gapH = Math.max(46, 64 - score() * 0.6f);
		float min = gapH / 2 + 14;
		float max = GROUND - gapH / 2 - 14;
		float gap = lastGap + (random.nextFloat() * 2 - 1) * 55;
		gap = Math.max(min, Math.min(max, gap));
		lastGap = gap;
		sticks.add(new Stick(x, gap, gapH));
	}

	@Override
	protected void update() {
		prevY = y;
		prevScroll = scroll;
		for (Stick stick : sticks) {
			stick.prevX = stick.x;
		}
		if (flapTimer > 0) {
			flapTimer--;
		}
		if (waiting) {
			y = GROUND * 0.45f + (float) Math.sin(steps() * 0.08) * 3;
			return;
		}
		if (isOver()) {
			// nach dem Aufprall auf den Boden fallen
			if (y + RADIUS < GROUND) {
				vy = Math.min(vy + GRAVITY, MAX_FALL);
				y = Math.min(y + vy, GROUND - RADIUS);
			}
			return;
		}
		vy = Math.min(vy + GRAVITY, MAX_FALL);
		y += vy;
		if (y < RADIUS) {
			y = RADIUS;
			vy = Math.max(vy, 0);
		}
		speed = 1.6f + Math.min(score(), 40) * 0.015f;
		scroll += speed;
		for (Stick stick : sticks) {
			stick.x -= speed;
		}
		sticks.removeIf(stick -> stick.x + STICK_W < -4 && stick.prevX + STICK_W < -4);
		Stick last = sticks.isEmpty() ? null : sticks.get(sticks.size() - 1);
		if (last == null || last.x < W + 10 - SPACING) {
			addStick(last == null ? W + 10 : last.x + SPACING);
		}
		for (Stick stick : sticks) {
			if (!stick.passed && stick.x + STICK_W < PLAYER_X - RADIUS) {
				stick.passed = true;
				addScore(1);
				sound(Sfx.SCORE, 1f + Math.min(score(), 30) * 0.01f);
			}
		}
		if (y + RADIUS >= GROUND) {
			y = GROUND - RADIUS;
			crash();
			return;
		}
		// etwas kleinerer Treffer-Kreis: knappe Vorbeiflüge zählen
		float r = RADIUS - 1.5f;
		for (Stick stick : sticks) {
			float top = stick.gapY - stick.gapH / 2;
			float bottom = stick.gapY + stick.gapH / 2;
			if (hits(PLAYER_X, y, r, stick.x, -50, stick.x + STICK_W, top)
				|| hits(PLAYER_X, y, r, stick.x, bottom, stick.x + STICK_W, GROUND)) {
				crash();
				return;
			}
		}
	}

	private static boolean hits(float cx, float cy, float r, float x0, float y0, float x1, float y1) {
		float nx = Math.max(x0, Math.min(cx, x1));
		float ny = Math.max(y0, Math.min(cy, y1));
		float dx = cx - nx;
		float dy = cy - ny;
		return dx * dx + dy * dy < r * r;
	}

	private void crash() {
		vy = Math.max(vy, 0);
		sound(Sfx.CRASH);
		gameOver();
	}

	private void flap() {
		if (isOver()) {
			return;
		}
		waiting = false;
		vy = FLAP;
		flapTimer = 8;
		sound(Sfx.FLAP, 0.9f + random.nextFloat() * 0.2f);
	}

	@Override
	protected void onPress(GameKey key) {
		if (key == GameKey.ACTION || key == GameKey.UP) {
			flap();
		}
	}

	@Override
	public void click(float x, float y, int button) {
		if (button == 0) {
			flap();
		}
	}

	// ------------------------------------------------------------------ für Tests

	public float playerY() {
		return y;
	}

	public boolean isWaiting() {
		return waiting;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Canvas c, float partial) {
		Theme theme = c.theme();
		int top = Colors.mix(Colors.opaque(theme.panel()), 0xFF000000, 0.45f);
		int bottom = Colors.mix(Colors.opaque(theme.header()), Colors.opaque(theme.panel()), 0.55f);
		c.clip(0, 0, W, H);
		c.gradient(0, 0, W, GROUND, top, bottom);

		float sc = lerp(prevScroll, scroll, partial);
		// ferne Salami-Monde (langsamer: Tiefe)
		for (int i = 0; i < 4; i++) {
			float x = ((i * 90 - sc * 0.2f) % 360 + 360) % 360 - 40;
			float cy = 38 + (i % 2) * 34;
			c.circle(x, cy, 16 + (i % 3) * 4, 0x12FFFFFF);
		}

		for (Stick stick : sticks) {
			float x = lerp(stick.prevX, stick.x, partial);
			float gapTop = stick.gapY - stick.gapH / 2;
			float gapBottom = stick.gapY + stick.gapH / 2;
			drawStick(c, x, -2, gapTop, true);
			drawStick(c, x, gapBottom, GROUND, false);
		}

		// Schneidebrett als Boden
		c.rect(0, GROUND, W, H, Colors.WOOD);
		c.rect(0, GROUND, W, GROUND + 1.5f, Colors.lighter(Colors.WOOD, 0.25f));
		float offset = sc % 14;
		for (float x = -offset; x < W; x += 14) {
			c.rect(x, GROUND + 4, x + 7, GROUND + 5, Colors.darker(Colors.WOOD, 0.25f));
			c.rect(x + 5, GROUND + 9, x + 12, GROUND + 10, Colors.darker(Colors.WOOD, 0.2f));
		}

		float py = lerp(prevY, y, partial);
		float angle = Math.max(-0.5f, Math.min(1.2f, vy * 0.12f));
		if (flapTimer > 0) {
			int swoosh = Colors.alpha(0xFFFFFFFF, flapTimer / 10f);
			c.rect(PLAYER_X - RADIUS - 9, py - 3, PLAYER_X - RADIUS - 3, py - 2, swoosh);
			c.rect(PLAYER_X - RADIUS - 11, py + 1, PLAYER_X - RADIUS - 4, py + 2, swoosh);
		}
		drawSlice(c, PLAYER_X, py, RADIUS, isOver() ? angle + overSteps() * 0.1f : angle);
		c.unclip();

		if (!isOver() && !waiting) {
			c.text(String.valueOf(score()), W / 2, 16, 18, Colors.alpha(0xFFFFFFFF, 0.85f));
		}
		if (waiting) {
			c.rect(0, H * 0.7f - 9, W, H * 0.7f + 8, 0x90000000);
			c.text(c.tr("salami.minigames.hint.flap"), W / 2, H * 0.7f, 9, theme.headerText());
		}
	}

	/** Eine Grissini-Stange zwischen y0 und y1, mit dickerem Ende an der Lücke. */
	private static void drawStick(Canvas c, float x, float y0, float y1, boolean fromTop) {
		if (y1 <= y0) {
			return;
		}
		c.rect(x + 2, y0, x + STICK_W - 2, y1, Colors.CRUST);
		c.rect(x + 3, y0, x + STICK_W - 3, y1, Colors.BREAD);
		c.rect(x + 5, y0, x + 8, y1, Colors.lighter(Colors.BREAD, 0.25f));
		// Sesam
		for (float sy = y0 + 6; sy < y1 - 4; sy += 11) {
			c.rect(x + 10, sy, x + 12, sy + 1.2f, Colors.FAT);
			c.rect(x + 15, sy + 5, x + 17, sy + 6.2f, Colors.FAT);
		}
		float capY = fromTop ? y1 - 8 : y0;
		c.roundRect(x, capY, x + STICK_W, capY + 8, 2.5f, Colors.CRUST);
		c.roundRect(x + 1, capY + 1, x + STICK_W - 1, capY + 7, 2, Colors.darker(Colors.BREAD, 0.08f));
	}

	/** Die Salamischeibe; die Fettaugen drehen sich mit dem Winkel. */
	private static void drawSlice(Canvas c, float cx, float cy, float r, float angle) {
		c.circle(cx, cy, r + 1, Colors.CASING);
		c.circle(cx, cy, r, Colors.SALAMI);
		float[][] dots = {{0.45f, 0.3f, 1.6f}, {0.5f, 2.4f, 1.3f}, {0.3f, 4.2f, 1.5f}, {0.62f, 5.2f, 1.1f}, {0f, 0f, 1.2f}};
		for (float[] dot : dots) {
			float dx = (float) Math.cos(dot[1] + angle) * dot[0] * r;
			float dy = (float) Math.sin(dot[1] + angle) * dot[0] * r;
			float s = dot[2];
			c.rect(cx + dx - s / 2, cy + dy - s / 2, cx + dx + s / 2, cy + dy + s / 2, Colors.FAT);
		}
		float pdx = (float) Math.cos(1.4f + angle) * 0.55f * r;
		float pdy = (float) Math.sin(1.4f + angle) * 0.55f * r;
		c.rect(cx + pdx - 0.6f, cy + pdy - 0.6f, cx + pdx + 0.6f, cy + pdy + 0.6f, Colors.PEPPER);
	}
}
