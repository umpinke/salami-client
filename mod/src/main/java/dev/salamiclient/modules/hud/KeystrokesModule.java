package dev.salamiclient.modules.hud;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Keystrokes: zeigt WASD, Maustasten (mit CPS) und Leertaste. Tasten sind Flächen im HUD-Stil;
 * gedrückte Tasten werden hell (oder in der eigenen Farbe) und blenden weich wieder aus.
 */
public final class KeystrokesModule extends HudModule {
	private final NumberSetting keySize = add(new NumberSetting("key_size", 22, 16, 32, 1));
	private final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 6, 1));
	private final BoolSetting mouse = add(new BoolSetting("mouse", true));
	private final BoolSetting cps = add(new BoolSetting("show_cps", true));
	private final BoolSetting space = add(new BoolSetting("space", true));
	private final BoolSetting fade = add(new BoolSetting("fade", true));
	private final BoolSetting customPressed = add(new BoolSetting("custom_pressed", false));
	private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", 0xE0B23A3A));
	private final ColorSetting pressedText = add(new ColorSetting("pressed_text_color", 0xFF000000));

	{
		pressedColor.showIf(customPressed::isOn);
		pressedText.showIf(customPressed::isOn);
	}

	/** Reihenfolge der Tasten: W, A, S, D, linke und rechte Maustaste, Leertaste. */
	private static final int KEYS = 7;
	/** Wie weit jede Taste gerade „gedrückt“ aussieht (0..1) – für das weiche Ausblenden. */
	private final float[] glow = new float[KEYS];
	private long lastFrameNanos;

	public KeystrokesModule() {
		super("keystrokes", Category.HUD, false, 0.99, 0.30);
	}

	private int key() {
		return keySize.intValue();
	}

	private int gapPx() {
		return gap.intValue();
	}

	@Override
	public int width() {
		return key() * 3 + gapPx() * 2;
	}

	private int spaceHeight() {
		return Math.max(8, Math.round(key() * 0.45F));
	}

	@Override
	public int height() {
		int h = key() * 2 + gapPx();
		if (mouse.isOn()) {
			h += gapPx() + key();
		}
		if (space.isOn()) {
			h += gapPx() + spaceHeight();
		}
		return h;
	}

	/** Beschriftungen und ihre Breiten: einmal pro Tick statt in jedem Frame (W, A, S, D, LMB, RMB). */
	private final String[] labels = new String[6];
	private final int[] labelWidths = new int[6];
	private String leftCps;
	private String rightCps;
	private int leftCpsWidth;
	private int rightCpsWidth;

	@Override
	public void onTick() {
		var options = mc().options;
		var font = mc().font;
		KeyMapping[] keys = {options.keyUp, options.keyLeft, options.keyDown, options.keyRight, options.keyAttack, options.keyUse};
		for (int i = 0; i < keys.length; i++) {
			labels[i] = shortName(keys[i]);
			labelWidths[i] = font.width(labels[i]);
		}
		leftCps = cps.isOn() ? ClickTracker.left() + " CPS" : null;
		rightCps = cps.isOn() ? ClickTracker.right() + " CPS" : null;
		leftCpsWidth = leftCps == null ? 0 : font.width(leftCps);
		rightCpsWidth = rightCps == null ? 0 : font.width(rightCps);
	}

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		if (labels[0] == null) {
			onTick(); // HUD-Editor-Vorschau ohne Welt: noch kein Tick gelaufen
		}
		long now = System.nanoTime();
		// Ausblenden in ~120 ms; Einblenden sofort, damit kein Tastendruck verzögert wirkt
		float step = lastFrameNanos == 0 ? 1F : Math.min(1F, (now - lastFrameNanos) / 120_000_000F);
		lastFrameNanos = now;

		var options = mc().options;
		int key = key();
		int gap = gapPx();
		int width = width();
		key(g, options.keyUp, 0, key + gap, 0, key, key, null, 0, step);
		int row2 = key + gap;
		key(g, options.keyLeft, 1, 0, row2, key, key, null, 0, step);
		key(g, options.keyDown, 2, key + gap, row2, key, key, null, 0, step);
		key(g, options.keyRight, 3, (key + gap) * 2, row2, key, key, null, 0, step);
		int y = row2 + key + gap;
		if (mouse.isOn()) {
			int mouseWidth = (width - gap) / 2;
			key(g, options.keyAttack, 4, 0, y, mouseWidth, key, leftCps, leftCpsWidth, step);
			key(g, options.keyUse, 5, width - mouseWidth, y, mouseWidth, key, rightCps, rightCpsWidth, step);
			y += key + gap;
		}
		if (space.isOn()) {
			int h = spaceHeight();
			float t = press(6, options.keyJump.isDown(), step);
			surface(g, 0, y, width, h, t);
			int barY = y + h / 2;
			int barColor = blend(textColor(), pressedTextColor(), t);
			GuiDraw.rounded(g, width / 2 - 12, barY, 24, 1, 0, barColor);
		}
	}

	private float press(int index, boolean down, float step) {
		if (down || !fade.isOn()) {
			glow[index] = down ? 1F : 0F;
		} else {
			glow[index] = Math.max(0F, glow[index] - step);
		}
		return glow[index];
	}

	private void key(GuiGraphics g, KeyMapping mapping, int index, int x, int y, int w, int h, String sub, int subWidth, float step) {
		float t = press(index, mapping.isDown(), step);
		surface(g, x, y, w, h, t);
		String label = labels[index];
		int labelX = x + (w - labelWidths[index]) / 2;
		int color = blend(textColor(), pressedTextColor(), t);
		boolean textShadow = shadow() && t < 0.5F;
		var font = mc().font;
		if (sub == null) {
			GuiDraw.text(g, font, label, labelX, y + (h - 8) / 2, color, textShadow);
		} else {
			int labelY = y + Math.max(2, (h - 14) / 2);
			GuiDraw.text(g, font, label, labelX, labelY, color, textShadow);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, labelY + 10);
			g.pose().scale(0.5f, 0.5f);
			GuiDraw.text(g, font, sub, -subWidth / 2, 0, GuiDraw.fade(color, 0.8F), false);
			g.pose().popMatrix();
		}
	}

	/** Tastenfläche: Hintergrund im HUD-Stil, beim Drücken zur Druckfarbe überblendet. */
	private void surface(GuiGraphics g, int x, int y, int w, int h, float t) {
		int base = backgroundColor();
		if (base == 0) {
			base = 0x30000000; // Stil ohne Hintergrund: Tasten trotzdem leicht andeuten
		}
		panel(g, x, y, w, h, t <= 0F ? base : blend(base, pressedColor(), t));
	}

	/** Gedrückte Taste: eigene Farbe oder hell in der Textfarbe. */
	private int pressedColor() {
		return customPressed.isOn() ? pressedColor.argb() : (textColor() & 0x00FFFFFF) | 0xE6000000;
	}

	private int pressedTextColor() {
		return customPressed.isOn() ? pressedText.argb() : GuiDraw.textOn(pressedColor());
	}

	/** Lineare Überblendung zweier ARGB-Farben (t = 0: a, t = 1: b). */
	private static int blend(int a, int b, float t) {
		if (t <= 0F) {
			return a;
		}
		if (t >= 1F) {
			return b;
		}
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			result |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return result;
	}

	@Override
	protected boolean drawsOwnBackground() {
		return true;
	}

	/** „W“, „LMB“, „RMB“ – lange Namen (z. B. „Linke Maustaste“) werden gekürzt. */
	private String shortName(KeyMapping mapping) {
		var options = mc().options;
		if (mapping == options.keyAttack && mapping.isDefault()) {
			return "LMB";
		}
		if (mapping == options.keyUse && mapping.isDefault()) {
			return "RMB";
		}
		String name = mapping.getTranslatedKeyMessage().getString();
		return name.length() > 4 ? name.substring(0, 4) : name;
	}
}
