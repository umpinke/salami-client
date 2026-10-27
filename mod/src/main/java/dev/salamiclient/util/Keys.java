package dev.salamiclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
//? if <26.3
import org.lwjgl.glfw.GLFW;

/**
 * Tasten und Maustasten unabhängig von der Minecraft-Version. Bis 26.2 sind Tastencodes
 * GLFW-Codes, ab 26.3 SDL-Scancodes – Minecrafts {@link InputConstants}{@code .KEY_…} haben
 * jeweils den passenden Wert, nur was dort fehlt, steht hier.
 */
public final class Keys {
	//? if >=26.3 {
	/*// Keine Taste (SDL_SCANCODE_UNKNOWN)
	public static final int NONE = 0;
	public static final int KEYPAD_SUBTRACT = 86;
	private static final int FIRST = 4;
	private static final int LAST = 511;
	*///?} else {
	// Keine Taste (GLFW_KEY_UNKNOWN)
	public static final int NONE = -1;
	public static final int KEYPAD_SUBTRACT = 333;
	private static final int FIRST = 32;
	private static final int LAST = 348;
	//?}

	private Keys() {}

	/** Ob ein Tastencode eine echte Taste sein kann (sonst {@link #NONE}). */
	public static boolean isValid(int key) {
		return key >= FIRST && key <= LAST;
	}

	/** Ob die Taste gerade gedrückt ist (auch während ein Menü offen ist). */
	public static boolean isDown(int key) {
		//? if >=26.3 {
		/*return InputConstants.isKeyDown(key);
		*///?} else
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
	}

	/** Ob die Maustaste ({@link InputConstants}{@code .MOUSE_BUTTON_…}) gerade gedrückt ist. */
	public static boolean isMouseDown(int button) {
		//? if >=26.3 {
		/*var mouse = Minecraft.getInstance().mouseHandler;
		return switch (button) {
			case InputConstants.MOUSE_BUTTON_LEFT -> mouse.isLeftPressed();
			case InputConstants.MOUSE_BUTTON_RIGHT -> mouse.isRightPressed();
			case InputConstants.MOUSE_BUTTON_MIDDLE -> mouse.isMiddlePressed();
			default -> false;
		};
		*///?} else
		return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;
	}
}
