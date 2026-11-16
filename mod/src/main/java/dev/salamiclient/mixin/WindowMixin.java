package dev.salamiclient.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.salamiclient.modules.client.BorderlessFullscreenModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.3 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
*///?} else {
import com.mojang.blaze3d.platform.Monitor;
//? if >=26.2 {
/*import com.mojang.blaze3d.platform.MonitorManager;
*///?} else
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.VideoMode;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** Borderless Fullscreen: statt echtem Vollbild ein randloses Fenster über den ganzen Bildschirm. */
@Mixin(Window.class)
public abstract class WindowMixin {
	//? if >=26.3 {
	/*// Ab 26.3 kann Minecraft selbst randlos (statt exklusivem Vollbild) – das Modul schaltet nur darauf um.
	@ModifyReturnValue(method = "useBorderlessFullscreenWindow", at = @At("RETURN"))
	private boolean salami$borderless(boolean vanilla) {
		return vanilla || BorderlessFullscreenModule.active();
	}
	*///?} else {
	@Shadow
	@Final
	private long handle;
	@Shadow
	@Final
	//? if >=26.2 {
	/*private MonitorManager monitorManager;
	*///?} else
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
			//? if >=26.2 {
			/*Monitor monitor = monitorManager.findBestMonitor((Window) (Object) this);
			*///?} else
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
			//? if >=26.2 {
			/*VideoMode mode = monitor.currentMode();
			int targetX = monitor.x();
			int targetY = monitor.y();
			*///?} else {
			VideoMode mode = monitor.getCurrentMode();
			int targetX = monitor.getX();
			int targetY = monitor.getY();
			//?}
			int targetWidth = mode.getWidth();
			int targetHeight = mode.getHeight();
			// Erst den Rahmen entfernen, dann die Felder setzen: Bei maximiertem Fenster ändert das
			// Entfernen die Fenstergröße, und Minecrafts Größen-Callbacks überschreiben dabei
			// x/y/width/height. Mit den überschriebenen Werten blieb das Fenster sonst maximiert.
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
			x = targetX;
			y = targetY;
			width = targetWidth;
			height = targetHeight;
			GLFW.glfwSetWindowMonitor(handle, 0L, targetX, targetY, targetWidth, targetHeight, GLFW.GLFW_DONT_CARE);
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
	//?}
}
