package dev.salamiclient.mixin;

//? if >=26.3 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.ItemModelModule;
import dev.salamiclient.modules.visual.SideShieldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else {
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
//?}

// Item Model, Side Shield und Old Animations: Items in der Ego-Sicht.
//? if >=26.3 {
/*// Ab 26.3 zeichnet FirstPersonHandsAndItemsRenderer; Old Animations steckt in FirstPersonHandsMixin.
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void salami$handItemTransforms(CallbackInfo ci, @Local(argsOnly = true) InteractionHand hand,
		@Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) PoseStack poseStack) {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
		ItemDisplayContext context = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		ItemModelModule.transform(player, context, poseStack);
		SideShieldModule.transform(player, stack, context, poseStack);
	}
}
*///?} else {
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

	// Old Animations: nach dem Vanilla-Tick die Höhe der Hand festhalten.
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
//?}
