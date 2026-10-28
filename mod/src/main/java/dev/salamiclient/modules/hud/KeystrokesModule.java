package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.theme.Theme;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/** Keystrokes: zeigt WASD, Maustasten (mit CPS) und Leertaste – gedrückte Tasten leuchten. */
public final class KeystrokesModule extends HudModule {
	private static final int KEY = 22;
	private static final int GAP = 2;
	private static final int WIDTH = KEY * 3 + GAP * 2;
	private static final int MOUSE_WIDTH = (WIDTH - GAP) / 2;
	private static final int SPACE_HEIGHT = 10;

	private final BoolSetting mouse = add(new BoolSetting("mouse", true));
	private final BoolSetting cps = add(new BoolSetting("show_cps", true));
	private final BoolSetting space = add(new BoolSetting("space", true));
	private final BoolSetting customPressed = add(new BoolSetting("custom_pressed", false));
	private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", 0xE0B23A3A));
	private final ColorSetting pressedText = add(new ColorSetting("pressed_text_color", 0xFF000000));

	{
		pressedColor.showIf(customPressed::isOn);
	}

	public KeystrokesModule() {
		super("keystrokes", Category.HUD, false, 0.99, 0.30);
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		int h = KEY * 2 + GAP;
		if (mouse.isOn()) {
			h += GAP + KEY;
		}
		if (space.isOn()) {
			h += GAP + SPACE_HEIGHT;
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
		var options = mc().options;
		key(g, options.keyUp, 0, KEY + GAP, 0, KEY, KEY, null, 0);
		int row2 = KEY + GAP;
		key(g, options.keyLeft, 1, 0, row2, KEY, KEY, null, 0);
		key(g, options.keyDown, 2, KEY + GAP, row2, KEY, KEY, null, 0);
		key(g, options.keyRight, 3, (KEY + GAP) * 2, row2, KEY, KEY, null, 0);
		int y = row2 + KEY + GAP;
		if (mouse.isOn()) {
			key(g, options.keyAttack, 4, 0, y, MOUSE_WIDTH, KEY, leftCps, leftCpsWidth);
			key(g, options.keyUse, 5, WIDTH - MOUSE_WIDTH, y, MOUSE_WIDTH, KEY, rightCps, rightCpsWidth);
			y += KEY + GAP;
		}
		if (space.isOn()) {
			boolean down = options.keyJump.isDown();
			g.fill(0, y, WIDTH, y + SPACE_HEIGHT, down ? pressed() : backgroundColor());
			int barY = y + SPACE_HEIGHT / 2;
			g.fill(WIDTH / 2 - 12, barY, WIDTH / 2 + 12, barY + 1, down ? pressedText.argb() : textColor());
		}
	}

	private void key(GuiGraphics g, KeyMapping mapping, int index, int x, int y, int w, int h, String sub, int subWidth) {
		boolean down = mapping.isDown();
		g.fill(x, y, x + w, y + h, down ? pressed() : backgroundColor());
		String label = labels[index];
		int labelX = x + (w - labelWidths[index]) / 2;
		int color = down ? pressedText.argb() : textColor();
		var font = mc().font;
		if (sub == null) {
			GuiDraw.text(g, font, label, labelX, y + (h - 8) / 2, color, shadow() && !down);
		} else {
			GuiDraw.text(g, font, label, labelX, y + 3, color, shadow() && !down);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 13);
			g.pose().scale(0.5f, 0.5f);
			GuiDraw.text(g, font, sub, -subWidth / 2, 0, color, false);
			g.pose().popMatrix();
		}
	}

	/** Gedrückte Taste: eigene Farbe oder Akzentfarbe mit etwas Transparenz. */
	private int pressed() {
		return customPressed.isOn() ? pressedColor.argb() : (accentColor() & 0x00FFFFFF) | 0xE0000000;
	}

	@Override
	protected boolean defaultBackground() {
		return true; // Hintergrund der nicht gedrückten Tasten
	}

	@Override
	protected boolean drawsOwnBackground() {
		return true;
	}

	@Override
	protected boolean defaultShadow() {
		return false;
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
