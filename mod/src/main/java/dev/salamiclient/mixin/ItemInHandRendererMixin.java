package dev.salamiclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.ItemModelModule;
import dev.salamiclient.modules.visual.OldAnimationsModule;
import dev.salamiclient.modules.visual.SideShieldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Model, Side Shield und Old Animations: Items in der Ego-Sicht. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private ItemStack mainHandItem;
	@Shadow
	private ItemStack offHandItem;
	@Shadow
	private float mainHandHeight;
	@Shadow
	private float oMainHandHeight;
	@Shadow
	private float offHandHeight;
	@Shadow
	private float oOffHandHeight;

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void salami$handItemTransforms(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ItemModelModule.transform(entity, context, poseStack);
		SideShieldModule.transform(entity, stack, context, poseStack);
	}

	/** Old Animations: nach dem Vanilla-Tick die Höhe der Hand festhalten. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void salami$oldAnimations(CallbackInfo ci) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.isHandsBusy()) {
			return;
		}
		boolean instant = OldAnimationsModule.instantSwitch();
		if (instant) {
			mainHandItem = player.getMainHandItem();
			offHandItem = player.getOffhandItem();
		}
		if (instant || OldAnimationsModule.noCooldownDip() && mainHandItem == player.getMainHandItem()) {
			mainHandHeight = 1.0F;
			oMainHandHeight = 1.0F;
		}
		if (instant || OldAnimationsModule.noCooldownDip() && offHandItem == player.getOffhandItem()) {
			offHandHeight = 1.0F;
			oOffHandHeight = 1.0F;
		}
	}
}
