package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
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

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		var options = mc().options;
		key(g, theme, options.keyUp, KEY + GAP, 0, KEY, KEY, null);
		int row2 = KEY + GAP;
		key(g, theme, options.keyLeft, 0, row2, KEY, KEY, null);
		key(g, theme, options.keyDown, KEY + GAP, row2, KEY, KEY, null);
		key(g, theme, options.keyRight, (KEY + GAP) * 2, row2, KEY, KEY, null);
		int y = row2 + KEY + GAP;
		if (mouse.isOn()) {
			key(g, theme, options.keyAttack, 0, y, MOUSE_WIDTH, KEY, cps.isOn() ? ClickTracker.left() + " CPS" : null);
			key(g, theme, options.keyUse, WIDTH - MOUSE_WIDTH, y, MOUSE_WIDTH, KEY, cps.isOn() ? ClickTracker.right() + " CPS" : null);
			y += KEY + GAP;
		}
		if (space.isOn()) {
			boolean down = options.keyJump.isDown();
			g.fill(0, y, WIDTH, y + SPACE_HEIGHT, down ? pressed(theme) : 0x80000000);
			int barY = y + SPACE_HEIGHT / 2;
			g.fill(WIDTH / 2 - 12, barY, WIDTH / 2 + 12, barY + 1, down ? 0xFF000000 : theme.text());
		}
	}

	private void key(GuiGraphics g, Theme theme, KeyMapping mapping, int x, int y, int w, int h, String sub) {
		boolean down = mapping.isDown();
		g.fill(x, y, x + w, y + h, down ? pressed(theme) : 0x80000000);
		String label = shortName(mapping);
		int color = down ? 0xFF000000 : theme.text();
		var font = mc().font;
		if (sub == null) {
			g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
		} else {
			g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, color, false);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 13);
			g.pose().scale(0.5f, 0.5f);
			g.drawString(font, sub, -font.width(sub) / 2, 0, color, false);
			g.pose().popMatrix();
		}
	}

	/** Akzentfarbe mit etwas Transparenz für gedrückte Tasten. */
	private static int pressed(Theme theme) {
		return (theme.accent() & 0x00FFFFFF) | 0xE0000000;
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
