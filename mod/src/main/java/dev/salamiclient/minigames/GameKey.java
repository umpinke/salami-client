package dev.salamiclient.minigames;

/**
 * Tasten, die ein Minispiel versteht – unabhängig von Minecrafts Tastencodes. Die Zuordnung
 * (Pfeiltasten und WASD, Leertaste …) macht der Bildschirm.
 */
public enum GameKey {
	UP,
	DOWN,
	LEFT,
	RIGHT,
	/** Leertaste oder Enter */
	ACTION,
	/** C, F oder Umschalt (Halten, Markieren) */
	ALT,
	/** Z oder Y (gegen den Uhrzeigersinn drehen) */
	ROTATE_BACK
}
