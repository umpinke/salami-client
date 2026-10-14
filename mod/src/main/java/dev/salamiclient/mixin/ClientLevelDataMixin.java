package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.TimeChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Changer: Tageszeit der Client-Welt (nur Anzeige). */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long salami$timeChanger(long dayTime) {
		return TimeChangerModule.dayTime(dayTime);
	}
}
