package dev.salamiclient.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.util.HurtOverlay;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Hit Color: Farbe, mit der getroffene Entities aufblinken. Vanilla füllt die obere
 * Hälfte der Overlay-Textur mit halbdurchsichtigem Rot – wir schreiben dort eine
 * eigene Farbe hinein (nur, wenn sie sich ändert).
 */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements HurtOverlay {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Override
	public void salami$setHurtColor(int argb) {
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, argb);
			}
		}
		texture.upload();
	}
}
