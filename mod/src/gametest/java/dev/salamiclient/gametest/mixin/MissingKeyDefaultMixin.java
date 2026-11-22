package dev.salamiclient.gametest.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nur in den Gametests, nur mit Punchy geladen (siehe {@link GametestMixinPlugin}): Fabrics
 * Gametest-API merkt sich die Standard-Einstellungen, sobald {@code Options} gebaut ist, und setzt sie
 * vor jedem Test zurück. Punchy legt eine Taste erst danach an – für sie gibt es keinen gemerkten
 * Wert, Minecraft bekommt {@code null} als Tastenname und stürzt ab, bevor ein Test läuft. Ohne Namen
 * bleibt die Taste hier einfach unbelegt. Die ausgelieferte Mod enthält das nicht.
 */
@Mixin(InputConstants.class)
public abstract class MissingKeyDefaultMixin {
	@Inject(method = "getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", at = @At("HEAD"), cancellable = true)
	private static void salamiGametest$noName(String name, CallbackInfoReturnable<InputConstants.Key> cir) {
		if (name == null) {
			cir.setReturnValue(InputConstants.UNKNOWN);
		}
	}
}
