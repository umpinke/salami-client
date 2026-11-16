package dev.salamiclient.friends;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.salamiclient.util.Chat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Chat-Befehle für Freunde (nur im Client, gehen nie an den Server):
 *
 * <pre>
 * /salami friends                 Freunde-Menü öffnen
 * /salami msg &lt;freund&gt; &lt;text&gt;    einem Freund schreiben
 * </pre>
 *
 * Ein Klick auf eine erhaltene Nachricht im Chat schreibt {@code /salami msg <name> } vor.
 */
public final class FriendsCommand {
	private FriendsCommand() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		SuggestionProvider<FabricClientCommandSource> friends = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Friends.friends().stream().map(Friends.Friend::name), builder);
		dispatcher.register(ClientCommandManager.literal("salami")
			.then(ClientCommandManager.literal("friends").executes(ctx -> {
				// Der Chat schließt sich nach dem Befehl noch selbst – das Menü erst im nächsten Tick öffnen
				Friends.openScreenNextTick();
				return 1;
			}))
			.then(ClientCommandManager.literal("msg")
				.then(ClientCommandManager.argument("friend", StringArgumentType.word()).suggests(friends)
					.then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
						.executes(ctx -> message(ctx.getSource(), StringArgumentType.getString(ctx, "friend"),
							StringArgumentType.getString(ctx, "text")))))));
	}

	private static int message(FabricClientCommandSource source, String name, String text) {
		if (Friends.status() != Friends.Status.ONLINE) {
			source.sendError(Chat.prefixed(Component.translatable("salami.friends.notice.not_connected")));
			return 0;
		}
		Friends.Friend friend = Friends.friendNamed(name);
		if (friend == null) {
			source.sendError(Chat.prefixed(Component.translatable("salami.friends.command.unknown", name)));
			return 0;
		}
		Friends.send(friend.uuid(), text, sent -> {
			if (sent) {
				source.sendFeedback(Chat.prefixed(Component.literal("✉ ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("salami.friends.command.to", friend.name()).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(text.strip()).withStyle(ChatFormatting.GRAY))));
			} else if (Friends.notice() != null) {
				source.sendError(Chat.prefixed(Friends.notice().copy()));
			}
		});
		return 1;
	}
}
