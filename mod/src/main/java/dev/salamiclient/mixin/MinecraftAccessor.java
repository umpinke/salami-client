package dev.salamiclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	/** Pause zwischen zwei automatischen Rechtsklicks bei gehaltener Taste (Vanilla: 4 Ticks). */
	@Accessor("rightClickDelay")
	void salami$setRightClickDelay(int ticks);
}
