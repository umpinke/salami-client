package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.salamiclient.modules.hud.PotionStatusModule;
import dev.salamiclient.modules.hud.ScoreboardModule;
import dev.salamiclient.modules.hud.TitlesModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla-HUD anpassen: Effektsymbole (Potion Status), Scoreboard, Titel und Actionbar.
 * Jede Skalierung merkt sich, ob sie die Matrix verschoben hat, und stellt sie am Ende zurück.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Unique
	private boolean salami$scoreboardScaled;
	@Unique
	private boolean salami$titleScaled;
	@Unique
	private boolean salami$actionBarMoved;

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void salami$hideVanillaEffects(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (PotionStatusModule.hidesVanillaEffects()) {
			ci.cancel();
		}
	}

	// ------------------------------------------------------------------ Scoreboard

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void salami$scoreboardBegin(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		if (ScoreboardModule.hides()) {
			ci.cancel();
			return;
		}
		salami$scoreboardScaled = ScoreboardModule.pushScale(graphics);
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
	private void salami$scoreboardEnd(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		if (salami$scoreboardScaled) {
			graphics.pose().popMatrix();
			salami$scoreboardScaled = false;
		}
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
	private NumberFormat salami$hideScoreNumbers(NumberFormat format) {
		return ScoreboardModule.numberFormat(format);
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
	private int salami$scoreboardBackground(int argb) {
		return ScoreboardModule.background(argb);
	}

	// ------------------------------------------------------------------ Titel

	@Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
	private void salami$titleBegin(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (TitlesModule.hidesTitles()) {
			ci.cancel();
			return;
		}
		salami$titleScaled = TitlesModule.pushTitleScale(graphics);
	}

	@Inject(method = "renderTitle", at = @At("RETURN"))
	private void salami$titleEnd(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (salami$titleScaled) {
			graphics.pose().popMatrix();
			salami$titleScaled = false;
		}
	}

	// ------------------------------------------------------------------ Actionbar

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void salami$actionBarBegin(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (TitlesModule.hidesActionBar()) {
			ci.cancel();
			return;
		}
		salami$actionBarMoved = TitlesModule.pushActionBarOffset(graphics);
	}

	@Inject(method = "renderOverlayMessage", at = @At("RETURN"))
	private void salami$actionBarEnd(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (salami$actionBarMoved) {
			graphics.pose().popMatrix();
			salami$actionBarMoved = false;
		}
	}
}
