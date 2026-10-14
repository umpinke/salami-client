package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.utility.HitboxExpandModule;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hitbox Expand: größerer Pick-Radius für das Fadenkreuz (nur Client, nur eigene Welt). */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyReturnValue(method = "getPickRadius", at = @At("RETURN"))
	private float salami$hitboxExpand(float radius) {
		return radius + HitboxExpandModule.extraPickRadius((Entity) (Object) this);
	}
}
