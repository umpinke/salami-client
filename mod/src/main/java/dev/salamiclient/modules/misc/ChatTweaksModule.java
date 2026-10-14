package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Chat Tweaks: Uhrzeit vor jeder Nachricht und ein längerer Chat-Verlauf (statt 100 Zeilen). */
public final class ChatTweaksModule extends Module {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private static ChatTweaksModule instance;

	private final BoolSetting timestamps = add(new BoolSetting("timestamps", true));
	private final NumberSetting history = add(new NumberSetting("history", 500, 100, 2000, 100));

	public ChatTweaksModule() {
		super("chat_tweaks", Category.MISC);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	/** Länger als das (oder aus mehr Teilen) wird eine Nachricht als gekürzter Klartext behalten. */
	private static final int MAX_CHARS = 4096;
	private static final int MAX_PARTS = 512;

	/** Für {@code ChatComponentMixin}: Zeitstempel voranstellen. */
	public static Component decorate(Component message) {
		if (!on() || message == null) {
			return message;
		}
		// Bis zu 20× mehr Verlauf als Vanilla: einzelne Riesen-Nachrichten (z. B. von einem
		// böswilligen Server) dürfen den Speicher nicht füllen.
		message = limit(message);
		if (!instance.timestamps.isOn()) {
			return message;
		}
		return Component.empty()
			.append(Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY))
			.append(message);
	}

	private static Component limit(Component message) {
		String text = message.getString();
		if (text.length() <= MAX_CHARS && parts(message, 0) <= MAX_PARTS) {
			return message;
		}
		String shown = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "\u2026" : text;
		return Component.literal(shown).withStyle(message.getStyle().getColor() == null
			? net.minecraft.network.chat.Style.EMPTY
			: net.minecraft.network.chat.Style.EMPTY.withColor(message.getStyle().getColor()));
	}

	/** Anzahl der Teile (Geschwister und Übersetzungs-Argumente), bricht über dem Limit ab. */
	private static int parts(Component component, int count) {
		count++;
		if (count > MAX_PARTS) {
			return count;
		}
		if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component child) {
					count = parts(child, count);
					if (count > MAX_PARTS) {
						return count;
					}
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			count = parts(sibling, count);
			if (count > MAX_PARTS) {
				return count;
			}
		}
		return count;
	}

	/** Für {@code ChatComponentMixin}: wie viele Zeilen der Chat behält. */
	public static int historySize(int vanilla) {
		return on() ? instance.history.intValue() : vanilla;
	}
}
