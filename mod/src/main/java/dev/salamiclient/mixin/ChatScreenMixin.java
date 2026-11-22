package dev.salamiclient.mixin;

import dev.salamiclient.util.UserInput;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Im Chat getippte und mit Enter abgeschickte Eingaben gelten als Eingabe des Spielers ({@link UserInput}) – Klicks
 * auf Chat-Text laufen nicht hier durch, sondern über {@code sendUnattendedCommand}.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "handleChatInput", at = @At("HEAD"))
	private void salami$typedStart(String text, boolean addToRecentChat, CallbackInfo ci) {
		UserInput.begin();
	}

	@Inject(method = "handleChatInput", at = @At("RETURN"))
	private void salami$typedEnd(String text, boolean addToRecentChat, CallbackInfo ci) {
		UserInput.end();
	}
}
