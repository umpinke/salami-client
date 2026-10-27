package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.TimeChangerModule;
//? if >=26.1 {
/*import net.minecraft.client.ClientClockManager;
*///?} else
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Time Changer: Tageszeit der Client-Welt (nur Anzeige). Ab 26.1 kommt sie aus den Welt-Uhren.
//? if >=26.3 {
/*@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long salami$timeChanger(long dayTime) {
		return TimeChangerModule.dayTime(dayTime);
	}
}
*///?} else if >=26.1 {
/*@Mixin(ClientClockManager.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long salami$timeChanger(long dayTime) {
		return TimeChangerModule.dayTime(dayTime);
	}
}
*///?} else {
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long salami$timeChanger(long dayTime) {
		return TimeChangerModule.dayTime(dayTime);
	}
}
//?}
