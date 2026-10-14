package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.WeatherChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather Changer: Regen/Gewitter der Client-Welt. Die Welt des eingebauten Servers
 * (Einzelspieler) bleibt unverändert – nur die Anzeige ändert sich.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float salami$rain(float rain) {
		return (Object) this instanceof ClientLevel ? WeatherChangerModule.rain(rain) : rain;
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float salami$thunder(float thunder) {
		return (Object) this instanceof ClientLevel ? WeatherChangerModule.thunder(thunder) : thunder;
	}
}
