package dev.salamiclient.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Text in Chat-Komponenten ersetzen, ohne Farben und Formatierung zu verlieren. */
public final class ComponentText {
	private ComponentText() {}

	/** Ersetzt {@code from} durch {@code to} in Klartext, Übersetzungs-Argumenten und Geschwistern. */
	public static Component replace(Component component, String from, String to) {
		if (from == null || from.isEmpty() || !component.getString().contains(from)) {
			return component;
		}
		return copyReplacing(component, from, to);
	}

	private static MutableComponent copyReplacing(Component component, String from, String to) {
		ComponentContents contents = component.getContents();
		MutableComponent out;
		if (contents instanceof PlainTextContents plain) {
			out = Component.literal(plain.text().replace(from, to));
		} else if (contents instanceof TranslatableContents translatable) {
			Object[] args = translatable.getArgs().clone();
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Component arg) {
					args[i] = copyReplacing(arg, from, to);
				} else if (args[i] instanceof String text) {
					args[i] = text.replace(from, to);
				}
			}
			out = Component.translatableWithFallback(translatable.getKey(), translatable.getFallback(), args);
		} else {
			out = MutableComponent.create(contents);
		}
		out.setStyle(component.getStyle());
		for (Component sibling : component.getSiblings()) {
			out.append(copyReplacing(sibling, from, to));
		}
		return out;
	}
}
