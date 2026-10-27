package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.util.Keys;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Free Look: Taste halten (Standard: linke Alt-Taste) und dich in der Third-Person-Ansicht
 * umsehen, ohne dass sich deine Spielfigur mitdreht. Beim Loslassen ist alles wie vorher.
 */
public final class FreeLookModule extends Module {
	private static FreeLookModule instance;

	private final KeybindSetting lookKey = add(new KeybindSetting("look_key", InputConstants.KEY_LALT));
	private final BoolSetting toggle = add(new BoolSetting("toggle", false));
	private final BoolSetting invertPitch = add(new BoolSetting("invert_pitch", false));

	private boolean active;
	private boolean keyWasDown;
	private float yaw;
	private float pitch;
	private CameraType previousCamera;

	public FreeLookModule() {
		super("free_look", Category.VISUAL);
		instance = this;
	}

	@Override
	public void onTick() {
		Minecraft mc = mc();
		boolean down = mc.screen == null && lookKey.isBound() && Keys.isDown(lookKey.key());
		boolean wanted = toggle.isOn() ? (down && !keyWasDown ? !active : active) : down;
		keyWasDown = down;
		if (wanted && !active) {
			start(mc);
		} else if (!wanted && active) {
			stop(mc);
		}
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop(Minecraft.getInstance());
		}
	}

	private void start(Minecraft mc) {
		active = true;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		previousCamera = mc.options.getCameraType();
		if (previousCamera.isFirstPerson()) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	private void stop(Minecraft mc) {
		active = false;
		if (previousCamera != null) {
			mc.options.setCameraType(previousCamera);
			previousCamera = null;
		}
	}

	/** Für {@code MouseHandlerMixin}: {@code true} = Mausbewegung dreht nur die Kamera. */
	public static boolean turn(double dx, double dy) {
		if (instance == null || !instance.isEnabled() || !instance.active) {
			return false;
		}
		instance.yaw += (float) dx * 0.15F;
		instance.pitch = Mth.clamp(instance.pitch + (float) (instance.invertPitch.isOn() ? -dy : dy) * 0.15F, -90.0F, 90.0F);
		return true;
	}

	/** Für {@code CameraMixin}: freie Blickrichtung statt der der Spielfigur. */
	public static boolean active() {
		return instance != null && instance.isEnabled() && instance.active;
	}

	public static float yaw() {
		return instance.yaw;
	}

	public static float pitch() {
		return instance.pitch;
	}
}
