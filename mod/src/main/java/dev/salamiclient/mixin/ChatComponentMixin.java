package dev.salamiclient.mixin;

import dev.salamiclient.modules.misc.ChatTweaksModule;
import dev.salamiclient.modules.misc.NoAdvancementModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
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
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
	private void salami$dropMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (NoAdvancementModule.blocksChat(message)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
	private Component salami$decorateMessage(Component message) {
		return ChatTweaksModule.decorate(StreamerModeModule.hideName(message));
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int salami$historySize(int vanilla) {
		return ChatTweaksModule.historySize(vanilla);
	}
}
