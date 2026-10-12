package dev.salamiclient.mixin;

import dev.salamiclient.util.Projection;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Übernimmt die finale Welt-Projektionsmatrix jedes Frames für {@link Projection}. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyArg(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		)
	)
	private Matrix4f salami$captureProjection(Matrix4f projection) {
		Projection.capture(projection);
		return projection;
	}
}
