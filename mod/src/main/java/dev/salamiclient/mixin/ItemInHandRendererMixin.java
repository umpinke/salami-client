package dev.salamiclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.SideShieldModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Side Shield: Schild in der Ego-Sicht verschieben und verkleinern. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderItem", at = @At("HEAD"))
	private void salami$sideShield(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		SideShieldModule.transform(entity, stack, context, poseStack);
	}
}
