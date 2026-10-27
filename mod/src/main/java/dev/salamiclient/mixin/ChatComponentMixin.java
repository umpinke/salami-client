package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.misc.ChatTweaksModule;
import dev.salamiclient.modules.misc.NoAdvancementModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat: Fortschritts-Meldungen ausblenden, Streamer-Name, Zeitstempel, längerer Verlauf. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	/** Die Methode, durch die jede Chat-Zeile läuft (ab 26.1 privat, mit Quelle). */
	//? if >=26.1 {
	/*private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
	*///?} else
	private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";

	@Inject(method = ADD_MESSAGE, at = @At("HEAD"), cancellable = true)
	private void salami$dropMessage(CallbackInfo ci, @Local(argsOnly = true) Component message) {
		if (NoAdvancementModule.blocksChat(message)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
	private Component salami$decorateMessage(Component message) {
		return ChatTweaksModule.decorate(StreamerModeModule.hideName(message));
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int salami$historySize(int vanilla) {
		return ChatTweaksModule.historySize(vanilla);
	}
}
