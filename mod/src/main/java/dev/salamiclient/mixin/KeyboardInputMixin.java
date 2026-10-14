package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.salamiclient.modules.utility.SpeedBridgeModule;
import dev.salamiclient.modules.utility.TapResetModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bewegungstasten beim Einlesen anpassen (Tap Reset, Speed Bridge) – statt die Tasten
 * selbst umzuschalten, was gedrückt gehaltene Tasten durcheinanderbringen würde.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean salami$movementOverrides(KeyMapping key, Operation<Boolean> original) {
		boolean down = original.call(key);
		down = TapResetModule.filterMovement(key, down);
		return SpeedBridgeModule.filterMovement(key, down);
	}
}
