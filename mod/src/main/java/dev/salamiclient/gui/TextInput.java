package dev.salamiclient.gui;

import net.minecraft.client.gui.components.events.GuiEventListener;
//? if >=26.3 {
/*import net.minecraft.client.Minecraft;
*///?}

/**
 * Schaltet die Texteingabe des Fensters für unsere selbst gezeichneten Eingabefelder ein und aus.
 *
 * <p>Ab 26.3 liest Minecraft die Tastatur über SDL. Getippte Zeichen ({@code charTyped}) kommen dann nur an,
 * solange die Texteingabe eingeschaltet ist. Minecraft schaltet sie nur für seine eigenen Eingabefelder
 * ({@code EditBox}) ein und bei jedem Bildschirmwechsel wieder aus. Unsere Menüs melden darum bei jedem Bild,
 * ob gerade ein Feld Zeichen annimmt. Vor 26.3 kamen Zeichen immer an; dort tut das hier nichts.
 */
public final class TextInput {
	private TextInput() {
	}

	/**
	 * @param owner  der Bildschirm mit dem Feld
	 * @param typing ob dort gerade ein Feld Zeichen annimmt
	 */
	public static void sync(GuiEventListener owner, boolean typing) {
		//? if >=26.3 {
		/*// Mehrfaches Einschalten kostet nichts; ausgeschaltet wird nur, was dieser Bildschirm eingeschaltet hat.
		Minecraft.getInstance().onTextInputFocusChange(owner, typing);
		*///?}
	}
}
