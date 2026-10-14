package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.salamiclient.modules.misc.ConfirmDisconnectModule;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Confirm Disconnect: den Trennen-Knopf mit einer Rückfrage umhüllen. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
	@WrapOperation(method = "createPauseMenu",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"))
	private Button.Builder salami$confirmDisconnect(Component label, Button.OnPress onPress, Operation<Button.Builder> original) {
		return original.call(label, ConfirmDisconnectModule.wrap((Screen) (Object) this, label, onPress));
	}
}
