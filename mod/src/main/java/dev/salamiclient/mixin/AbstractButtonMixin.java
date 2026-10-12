package dev.salamiclient.mixin;

import dev.salamiclient.theme.SalamiTextures;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Salami-Modus: Button-Hintergründe aller Vanilla-Buttons (Menüs, Optionen, …). */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@ModifyArg(
		method = "renderDefaultSprite",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"
		),
		index = 1
	)
	private Identifier salami$buttonSprite(Identifier original) {
		return SalamiTextures.buttonSprite(original);
	}
}
