package dev.salamiclient.mixin;

import dev.salamiclient.theme.SalamiTextures;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Salami-Modus: "SALAMI / CLIENT" statt "MINECRAFT / Java Edition" im Titelbildschirm. */
@Mixin(LogoRenderer.class)
public abstract class LogoRendererMixin {
	private static final String BLIT =
		"Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIII)V";

	@ModifyArg(
		method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V",
		at = @At(value = "INVOKE", target = BLIT, ordinal = 0),
		index = 1
	)
	private Identifier salami$logo(Identifier original) {
		return SalamiTextures.logo(original);
	}

	@ModifyArg(
		method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V",
		at = @At(value = "INVOKE", target = BLIT, ordinal = 1),
		index = 1
	)
	private Identifier salami$edition(Identifier original) {
		return SalamiTextures.edition(original);
	}
}
