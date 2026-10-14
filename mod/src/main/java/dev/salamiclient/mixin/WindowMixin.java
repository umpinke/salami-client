package dev.salamiclient.mixin;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import dev.salamiclient.modules.client.BorderlessFullscreenModule;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Borderless Fullscreen: statt echtem Vollbild ein randloses Fenster über den ganzen Bildschirm. */
@Mixin(Window.class)
public abstract class WindowMixin {
	@Shadow
	@Final
	private long handle;
	@Shadow
	@Final
	private ScreenManager screenManager;
	@Shadow
	private boolean fullscreen;
	@Shadow
	private int x;
	@Shadow
	private int y;
	@Shadow
	private int width;
	@Shadow
	private int height;
	@Shadow
	private int windowedX;
	@Shadow
	private int windowedY;
	@Shadow
	private int windowedWidth;
	@Shadow
	private int windowedHeight;

	@Unique
	private boolean salami$borderless;

	@Inject(method = "setMode", at = @At("HEAD"), cancellable = true)
	private void salami$borderlessMode(CallbackInfo ci) {
		if (fullscreen && BorderlessFullscreenModule.active()) {
			Monitor monitor = screenManager.findBestMonitor((Window) (Object) this);
			if (monitor == null) {
				return; // Vanilla übernimmt (und meldet den Fehler)
			}
			boolean wasMonitorFullscreen = GLFW.glfwGetWindowMonitor(handle) != 0L;
			if (!salami$borderless && !wasMonitorFullscreen) {
				windowedX = x;
				windowedY = y;
				windowedWidth = width;
				windowedHeight = height;
			}
			VideoMode mode = monitor.getCurrentMode();
			x = monitor.getX();
			y = monitor.getY();
			width = mode.getWidth();
			height = mode.getHeight();
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
			GLFW.glfwSetWindowMonitor(handle, 0L, x, y, width, height, GLFW.GLFW_DONT_CARE);
			salami$borderless = true;
			ci.cancel();
			return;
		}
		if (salami$borderless) {
			// zurück ins Fenster (oder ins echte Vollbild): Rahmen wieder an, dann macht Vanilla weiter
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
			salami$borderless = false;
		}
	}
}
