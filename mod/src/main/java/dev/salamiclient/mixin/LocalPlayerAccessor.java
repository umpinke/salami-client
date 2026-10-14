package dev.salamiclient.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
	/** Zuletzt an den Server gemeldeter Neigungswinkel – weicht er ab, schickt der Client im nächsten Tick die echte Blickrichtung. */
	@Accessor("xRotLast")
	void salami$setXRotLast(float xRot);
}
