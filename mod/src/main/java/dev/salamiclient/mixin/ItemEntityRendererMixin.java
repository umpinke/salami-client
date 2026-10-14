package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.salamiclient.modules.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Item Physics: kein Schweben, kein Drehen, flache Items liegen auf dem Boden.
 * Ohne aktives Modul ohne Allokation (läuft für jedes Item auf dem Boden in jedem Frame).
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Unique
	private static final Quaternionf LAY_FLAT = Axis.XP.rotationDegrees(90.0F);
	@Unique
	private static final Quaternionf SPIN = new Quaternionf();

	@ModifyArg(method = "submit",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
	private float salami$restOnGround(float y, @Local AABB box) {
		return ItemPhysicsModule.active() ? ItemPhysicsModule.restingHeight(box) : y;
	}

	@WrapWithCondition(method = "submit",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private boolean salami$layFlat(PoseStack poseStack, Quaternionfc spin,
		@Local(argsOnly = true) ItemEntityRenderState state, @Local AABB box) {
		if (!ItemPhysicsModule.active()) {
			return true; // Vanilla-Drehung
		}
		// feste, pro Item zufällige Ausrichtung statt Drehen (Render-Thread: wiederverwendbares Objekt)
		poseStack.mulPose(SPIN.rotationY(state.bobOffset));
		if (ItemPhysicsModule.isFlat(box)) {
			poseStack.mulPose(LAY_FLAT);
		}
		return false;
	}
}
