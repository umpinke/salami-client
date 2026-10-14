package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * Toggle Sprint: Sprint-Taste einmal drücken statt halten (Vanilla-Umschalten) oder
 * immer sprinten, sobald du vorwärts läufst. Zeigt optional den Zustand an.
 */
public final class ToggleSprintModule extends HudModule {
	public enum Mode {
		TOGGLE,
		ALWAYS
	}

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.TOGGLE));
	private final BoolSetting showText = add(new BoolSetting("show_text", true));

	/** Vanilla-Option „Sprinten: Umschalten“ vor dem Einschalten (wird beim Ausschalten zurückgesetzt). */
	private Boolean toggleOptionBefore;
	private boolean forcedDown;

	public ToggleSprintModule() {
		super("toggle_sprint", Category.MISC, false, 0.005, 0.95);
	}

	@Override
	public void onTick() {
		var options = mc().options;
		if (mode.get() == Mode.TOGGLE) {
			releaseForced();
			if (toggleOptionBefore == null) {
				toggleOptionBefore = options.toggleSprint().get();
			}
			if (!options.toggleSprint().get()) {
				options.toggleSprint().set(true);
			}
		} else {
			restoreOption();
			boolean moving = options.keyUp.isDown() && !options.keyShift.isDown();
			if (moving) {
				options.keySprint.setDown(true);
				forcedDown = true;
			} else {
				releaseForced();
			}
		}
		updateText();
	}

	@Override
	protected void onDisable() {
		releaseForced();
		restoreOption();
	}

	private void releaseForced() {
		if (forcedDown) {
			mc().options.keySprint.setDown(false);
			forcedDown = false;
		}
	}

	private void restoreOption() {
		if (toggleOptionBefore != null) {
			mc().options.toggleSprint().set(toggleOptionBefore);
			toggleOptionBefore = null;
		}
	}

	/** Anzeigetext, einmal pro Tick berechnet (siehe {@link #updateText()}). */
	private String text = "";
	private int width = 40;

	private void updateText() {
		var player = mc().player;
		if (player == null || !player.isSprinting()) {
			text = "";
		} else {
			String key = mode.get() == Mode.ALWAYS || mc().options.keySprint.isDown()
				? "salami.module.toggle_sprint.label.toggled"
				: "salami.module.toggle_sprint.label.held";
			text = I18n.get(key);
		}
		width = Math.max(40, mc().font.width(text) + 2);
	}

	@Override
	public boolean hasContent() {
		return showText.isOn() && !text.isEmpty();
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return 10;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		graphics.drawString(mc().font, text, 0, 0, accentColor(), shadow());
	}
}
