package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.salamiclient.modules.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item Physics: kein Schweben, kein Drehen, flache Items liegen auf dem Boden. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@WrapOperation(method = "submit",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void salami$restOnGround(PoseStack poseStack, float x, float y, float z, Operation<Void> original, @Local AABB box) {
		original.call(poseStack, x, ItemPhysicsModule.active() ? ItemPhysicsModule.restingHeight(box) : y, z);
	}

	@WrapOperation(method = "submit",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void salami$layFlat(PoseStack poseStack, Quaternionfc spin, Operation<Void> original,
		@Local(argsOnly = true) ItemEntityRenderState state, @Local AABB box) {
		if (!ItemPhysicsModule.active()) {
			original.call(poseStack, spin);
			return;
		}
		// feste, pro Item zufällige Ausrichtung statt Drehen
		original.call(poseStack, Axis.YP.rotation(state.bobOffset));
		if (ItemPhysicsModule.isFlat(box)) {
			original.call(poseStack, Axis.XP.rotationDegrees(90.0F));
		}
	}
}
