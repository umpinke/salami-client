package dev.salamiclient.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;

/**
 * Namen in Chat-Komponenten ersetzen, ohne Farben und Formatierung zu verlieren (für Streamer Mode).
 * Groß-/Kleinschreibung egal, nur ganze Namen (nicht „Max“ in „Maximum“). Hover-, Klick- und
 * Einfüge-Daten, die den Namen oder eine der {@code hidden}-Angaben (z. B. die UUID) enthalten,
 * werden entfernt – sonst verrät Drüberfahren oder Anklicken den echten Namen.
 */
public final class ComponentText {
	private ComponentText() {}

	public static Component replace(Component component, String from, String to, String... hidden) {
		if (from == null || from.isEmpty() || component == null) {
			return component;
		}
		String[] secrets = new String[hidden.length + 1];
		secrets[0] = from.toLowerCase(Locale.ROOT);
		for (int i = 0; i < hidden.length; i++) {
			secrets[i + 1] = hidden[i] == null || hidden[i].isEmpty() ? secrets[0] : hidden[i].toLowerCase(Locale.ROOT);
		}
		if (indexOfName(component.getString(), from, 0) < 0 && !anyLeak(component, secrets, 0)) {
			return component;
		}
		MutableComponent out = copyReplacing(component, from, to, secrets, 0);
		// Name über mehrere Teile verteilt (z. B. Farbverlauf pro Buchstabe): als Klartext ersetzen
		String flat = out.getString();
		if (indexOfName(flat, from, 0) >= 0) {
			return Component.literal(replaceName(flat, from, to)).setStyle(clean(component.getStyle(), secrets));
		}
		return out;
	}

	private static MutableComponent copyReplacing(Component component, String from, String to, String[] secrets, int depth) {
		ComponentContents contents = component.getContents();
		MutableComponent out;
		if (contents instanceof PlainTextContents plain) {
			out = Component.literal(replaceName(plain.text(), from, to));
		} else if (contents instanceof TranslatableContents translatable && depth < 64) {
			Object[] args = translatable.getArgs().clone();
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Component arg) {
					args[i] = copyReplacing(arg, from, to, secrets, depth + 1);
				} else if (args[i] instanceof String text) {
					args[i] = replaceName(text, from, to);
				}
			}
			out = Component.translatableWithFallback(translatable.getKey(), translatable.getFallback(), args);
		} else {
			out = MutableComponent.create(contents);
		}
		out.setStyle(clean(component.getStyle(), secrets));
		if (depth < 64) {
			for (Component sibling : component.getSiblings()) {
				out.append(copyReplacing(sibling, from, to, secrets, depth + 1));
			}
		}
		return out;
	}

	/** Stil ohne Hover/Klick/Einfügen, wenn diese etwas Geheimes enthalten. */
	private static Style clean(Style style, String[] secrets) {
		if (mentions(style.getHoverEvent(), secrets)) {
			style = style.withHoverEvent(null);
		}
		if (mentions(style.getClickEvent(), secrets)) {
			style = style.withClickEvent(null);
		}
		if (mentions(style.getInsertion(), secrets)) {
			style = style.withInsertion(null);
		}
		return style;
	}

	private static boolean anyLeak(Component component, String[] secrets, int depth) {
		Style style = component.getStyle();
		if (mentions(style.getHoverEvent(), secrets) || mentions(style.getClickEvent(), secrets) || mentions(style.getInsertion(), secrets)) {
			return true;
		}
		if (depth >= 64) {
			return false;
		}
		if (component.getContents() instanceof TranslatableContents translatable) {
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component child && anyLeak(child, secrets, depth + 1)) {
					return true;
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			if (anyLeak(sibling, secrets, depth + 1)) {
				return true;
			}
		}
		return false;
	}

	/** Ereignisse sind Records – ihr Text enthält Befehl, URL, Hover-Text bzw. Entity-Name und UUID. */
	private static boolean mentions(Object event, String[] secrets) {
		if (event == null) {
			return false;
		}
		String text = event.toString().toLowerCase(Locale.ROOT);
		for (String secret : secrets) {
			if (text.contains(secret)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ Namenssuche

	/** Nächstes Vorkommen von {@code name} als ganzes Wort (Groß-/Kleinschreibung egal), sonst -1. */
	static int indexOfName(String text, String name, int start) {
		int n = name.length();
		for (int i = start; i + n <= text.length(); i++) {
			if (text.regionMatches(true, i, name, 0, n) && !nameChar(text, i - 1) && !nameChar(text, i + n)) {
				return i;
			}
		}
		return -1;
	}

	static String replaceName(String text, String name, String to) {
		int i = indexOfName(text, name, 0);
		if (i < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		int last = 0;
		while (i >= 0) {
			out.append(text, last, i).append(to);
			last = i + name.length();
			i = indexOfName(text, name, last);
		}
		return out.append(text, last, text.length()).toString();
	}

	/** Zeichen, die in Minecraft-Namen vorkommen dürfen. */
	private static boolean nameChar(String text, int i) {
		if (i < 0 || i >= text.length()) {
			return false;
		}
		char c = text.charAt(i);
		return c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
	}
}
