package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.ShinyPotsModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shiny Pots: Tränke mit Glanz zeichnen. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean salami$shinyPots(boolean foil) {
		return foil || ShinyPotsModule.shines((ItemStack) (Object) this);
	}
}
