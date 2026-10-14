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

	/** Für {@code ChatComponentMixin}: Zeitstempel voranstellen. */
	public static Component decorate(Component message) {
		if (!on() || !instance.timestamps.isOn()) {
			return message;
		}
		return Component.empty()
			.append(Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY))
			.append(message);
	}

	/** Für {@code ChatComponentMixin}: wie viele Zeilen der Chat behält. */
	public static int historySize(int vanilla) {
		return on() ? instance.history.intValue() : vanilla;
	}
}
