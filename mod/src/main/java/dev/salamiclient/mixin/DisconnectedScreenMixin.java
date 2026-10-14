package dev.salamiclient.mixin;

import dev.salamiclient.modules.misc.AutoReconnectModule;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect: „Neu verbinden“-Knopf unter die Vanilla-Knöpfe setzen. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	@Shadow
	@Final
	private LinearLayout layout;

	protected DisconnectedScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void salami$addReconnect(CallbackInfo ci) {
		Button button = AutoReconnectModule.createButton();
		if (button == null) {
			return;
		}
		layout.addChild(button);
		layout.arrangeElements();
		addRenderableWidget(button);
		repositionElements(); // virtuell: DisconnectedScreen zentriert das Layout neu
	}
}
