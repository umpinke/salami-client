package dev.salamiclient.util;

import org.jetbrains.annotations.Nullable;

/**
 * Text von anderen Spielern oder vom Server (Freunde-Nachrichten, Namen, Fehlermeldungen), bevor er im Chat oder in
 * einem Menü landet: ohne {@code §}-Formatierung, Steuerzeichen, Zeilenumbrüche, Bidi-Steuerung (Text, der sich
 * selbst umdreht) und Zeichen aus dem privaten Bereich (Symbole aus Server-Ressourcenpaketen), gekürzt.
 */
public final class ChatSafe {
	/** Minecraft-Namen haben höchstens 16 Zeichen; etwas Luft für ältere Konten. */
	public static final int MAX_NAME = 32;

	private ChatSafe() {}

	/** Bereinigter Text, höchstens {@code maxChars} Zeichen; {@code null} wird zu „“. */
	public static String clean(@Nullable String text, int maxChars) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(Math.min(text.length(), maxChars));
		int i = 0;
		while (i < text.length() && out.length() < maxChars) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == '§') {
				// Formatierungscode „§x“ ganz entfernen (wie ChatFormatting.stripFormatting)
				if (i < text.length()) {
					i += Character.charCount(text.codePointAt(i));
				}
				continue;
			}
			int type = Character.getType(cp);
			if (cp == '\n' || cp == '\r' || cp == '\t' || type == Character.LINE_SEPARATOR
				|| type == Character.PARAGRAPH_SEPARATOR) {
				out.append(' ');
				continue;
			}
			// CONTROL: C0/C1, FORMAT: u. a. alle Bidi-Steuerzeichen (U+200E/F, U+202A–E, U+2066–9, U+061C) und
			// unsichtbare Zeichen; SURROGATE: einzelne Hälften; PRIVATE_USE: eigene Symbole von Ressourcenpaketen
			if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
				|| type == Character.PRIVATE_USE) {
				continue;
			}
			out.appendCodePoint(cp);
		}
		return out.toString().strip();
	}

	/** Bereinigter Spielername oder {@code null}, wenn nichts übrig bleibt. */
	public static @Nullable String name(@Nullable String name) {
		String cleaned = clean(name, MAX_NAME);
		return cleaned.isEmpty() ? null : cleaned;
	}
}
