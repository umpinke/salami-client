package dev.salamiclient.friends;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.ChatSafe;
import dev.salamiclient.util.UserInput;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
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
 *
 * <p>{@code /salami msg} sendet nur, wenn der Spieler den Befehl selbst abschickt ({@link UserInput}). Kommt er aus
 * einem Klick auf Text eines Servers (Chat, Buch, Schild, Dialog), geht nichts raus: im Chat steht der Befehl dann
 * zum Prüfen in der Eingabezeile, Enter schickt ihn ab.
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
						.executes(FriendsCommand::message)))));
	}

	private static int message(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		if (!UserInput.active()) {
			return notTyped(source, ctx.getInput());
		}
		String name = StringArgumentType.getString(ctx, "friend");
		String text = StringArgumentType.getString(ctx, "text");
		if (Friends.status() != Friends.Status.ONLINE) {
			source.sendError(Chat.prefixed(Component.translatable("salami.friends.notice.not_connected")));
			return 0;
		}
		Friends.Friend friend = Friends.friendNamed(name);
		if (friend == null) {
			source.sendError(Chat.prefixed(Component.translatable("salami.friends.command.unknown", ChatSafe.clean(name, ChatSafe.MAX_NAME))));
			return 0;
		}
		Friends.send(friend.uuid(), text, sent -> {
			if (sent) {
				source.sendFeedback(Chat.prefixed(Component.literal("✉ ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("salami.friends.command.to", friend.name()).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(ChatSafe.clean(text, Friends.MAX_MESSAGE)).withStyle(ChatFormatting.GRAY))));
			} else if (Friends.notice() != null) {
				source.sendError(Chat.prefixed(Friends.notice().copy()));
			}
		});
		return 1;
	}

	/**
	 * Befehl kam nicht vom Spieler (Klick auf Text eines Servers): nichts senden. Ist der Chat offen, steht der
	 * Befehl danach in der Eingabezeile – abschicken muss der Spieler ihn selbst.
	 */
	private static int notTyped(FabricClientCommandSource source, String input) {
		Minecraft mc = source.getClient();
		if (mc.screen instanceof ChatScreen chat) {
			chat.insertText("/" + input, true);
			source.sendFeedback(Chat.prefixed(Component.translatableWithFallback("salami.friends.command.click_prefilled",
				"Not sent: messages from links never go out on their own. Check the text in the chat box and press Enter to send it.")
				.withStyle(ChatFormatting.GOLD)));
		} else {
			source.sendError(Chat.prefixed(Component.translatableWithFallback("salami.friends.command.click_refused",
				"Not sent: friend messages can't be sent from links. Type the command yourself.")));
		}
		return 0;
	}
}
