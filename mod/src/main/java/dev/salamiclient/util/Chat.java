package dev.salamiclient.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-seitige Chat-Meldungen mit Salami-Präfix (werden nie an den Server gesendet). */
public final class Chat {
	private Chat() {}

	public static void info(String key, Object... args) {
		send(Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
	}

	public static void warn(String key, Object... args) {
		send(Component.translatable(key, args).withStyle(ChatFormatting.GOLD));
	}

	public static Component translated(String key) {
		return Component.translatable(key);
	}

	private static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui == null) {
			return;
		}
		MutableComponent prefix = Component.literal("[")
			.withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal("Salami").withStyle(ChatFormatting.RED))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
		//? if >=26.1 {
		/*mc.gui.getChat().addClientSystemMessage(prefix.append(message));
		*///?} else
		mc.gui.getChat().addMessage(prefix.append(message));
	}
}
