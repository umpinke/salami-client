package dev.salamiclient.emotes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.salamiclient.util.Chat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /salami emote <name>} (nur im Client): Emote abspielen, z. B. {@code /salami emote wave}.
 * Läuft erst im nächsten Tick an, nachdem sich der Chat geschlossen hat.
 */
public final class EmoteCommand {
	private EmoteCommand() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("salami")
			.then(ClientCommandManager.literal("emote")
				.then(ClientCommandManager.argument("emote", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(EmoteCatalog.all().stream().map(Emote::id), builder))
					.executes(ctx -> {
						Emote emote = EmoteCatalog.get(StringArgumentType.getString(ctx, "emote"));
						if (emote == null) {
							ctx.getSource().sendError(Chat.prefixed(Component.translatable("salami.emotes.unknown")));
							return 0;
						}
						Minecraft mc = Minecraft.getInstance();
						mc.execute(() -> Emotes.play(emote));
						return 1;
					}))));
	}
}
