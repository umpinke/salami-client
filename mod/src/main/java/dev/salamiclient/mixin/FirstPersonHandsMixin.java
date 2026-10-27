package dev.salamiclient.mixin;

//? if >=26.3 {
/*import dev.salamiclient.modules.visual.OldAnimationsModule;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Old Animations ab 26.3: nach dem Vanilla-Tick die Höhe der Hand festhalten (bis 26.2 in ItemInHandRendererMixin).
// Nur ab 26.3 in salami.mixins.json eingetragen (build.gradle.kts).
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsMixin {
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

	@Inject(method = "tick", at = @At("TAIL"))
	private void salami$oldAnimations(LocalPlayer player, CallbackInfo ci) {
		if (player.isHandsBusy()) {
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
*///?}
