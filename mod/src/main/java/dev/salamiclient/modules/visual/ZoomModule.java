package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom: Taste gedrückt halten (Standard C) zoomt heran, das Mausrad ändert dabei die Stärke.
 * Optional mit weicher Kamera wie bei OptiFine.
 */
public final class ZoomModule extends Module {
	private static ZoomModule instance;

	private final KeybindSetting zoomKey = add(new KeybindSetting("zoom_key", GLFW.GLFW_KEY_C));
	private final NumberSetting strength = add(new NumberSetting("strength", 4, 1.5, 10, 0.5));
	private final BoolSetting scroll = add(new BoolSetting("scroll", true));
	private final BoolSetting smoothCamera = add(new BoolSetting("smooth_camera", true));
	private final BoolSetting animation = add(new BoolSetting("animation", true));

	/** Stärke dieses Zooms (per Mausrad änderbar, beim Loslassen zurück auf die Einstellung). */
	private double targetZoom = 1.0;
	private double currentZoom = 1.0;
	private double scrolledZoom = -1;
	private long lastFrameNanos;
	private boolean zooming;
	private boolean smoothBefore;

	public ZoomModule() {
		super("zoom", Category.VISUAL);
		instance = this;
	}

	@Override
	protected void onDisable() {
		stopZoom();
		currentZoom = 1.0;
	}

	/** Für {@code GameRendererTweaksMixin}: Sichtfeld pro Frame. */
	public static float applyFov(float fov, float partialTick) {
		if (instance == null || !instance.isEnabled()) {
			return fov;
		}
		return (float) (fov / instance.frameZoom());
	}

	/** Für {@code MouseHandlerMixin}: {@code true}, wenn das Mausrad den Zoom verstellt hat. */
	public static boolean onScroll(double amount) {
		if (instance == null || !instance.isEnabled() || !instance.zooming || !instance.scroll.isOn() || amount == 0) {
			return false;
		}
		double base = instance.scrolledZoom > 0 ? instance.scrolledZoom : instance.strength.get();
		instance.scrolledZoom = Math.max(1.0, Math.min(50.0, base * (amount > 0 ? 1.15 : 1 / 1.15)));
		return true;
	}

	private double frameZoom() {
		Minecraft mc = Minecraft.getInstance();
		boolean keyDown = mc.screen == null && zoomKey.isBound()
			&& InputConstants.isKeyDown(mc.getWindow(), zoomKey.key());
		if (keyDown && !zooming) {
			startZoom(mc);
		} else if (!keyDown && zooming) {
			stopZoom();
		}

		targetZoom = zooming ? (scrolledZoom > 0 ? scrolledZoom : strength.get()) : 1.0;
		long now = System.nanoTime();
		double seconds = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
		lastFrameNanos = now;
		if (!animation.isOn()) {
			currentZoom = targetZoom;
		} else {
			// exponentielles Annähern, unabhängig von der Bildrate
			currentZoom += (targetZoom - currentZoom) * (1 - Math.exp(-seconds * 14));
			if (Math.abs(currentZoom - targetZoom) < 0.001) {
				currentZoom = targetZoom;
			}
		}
		return Math.max(1.0, currentZoom);
	}

	private void startZoom(Minecraft mc) {
		zooming = true;
		scrolledZoom = -1;
		smoothBefore = mc.options.smoothCamera;
		if (smoothCamera.isOn()) {
			mc.options.smoothCamera = true;
		}
	}

	private void stopZoom() {
		if (!zooming) {
			return;
		}
		zooming = false;
		scrolledZoom = -1;
		if (smoothCamera.isOn()) {
			Minecraft.getInstance().options.smoothCamera = smoothBefore;
		}
	}
}
