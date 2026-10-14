package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ColorSetting;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.io.InputStream;
import java.util.List;

/**
 * Glint Colorizer: eigene Farbe für den Verzauberungs-Glanz. Die Glanz-Texturen (Items und
 * Rüstung) werden einmal eingefärbt ersetzt – kein Aufwand pro Frame. Ausschalten lädt wieder
 * die Originale (auch die eines Ressourcenpakets).
 */
public final class GlintColorizerModule extends Module {
	private static final List<Identifier> GLINTS = List.of(ItemRenderer.ENCHANTED_GLINT_ITEM, ItemRenderer.ENCHANTED_GLINT_ARMOR);

	private final ColorSetting color = add(new ColorSetting("color", 0xFFB23A3A).noChroma().noAlpha());

	/** Zuletzt angewendete Farbe; {@code null} = Originale aktiv. */
	private Integer applied;

	public GlintColorizerModule() {
		super("glint_colorizer", Category.VISUAL);
	}

	@Override
	public void onTick() {
		int wanted = color.argb();
		if (applied == null || applied != wanted) {
			apply(wanted);
		}
	}

	@Override
	protected void onDisable() {
		if (applied != null) {
			TextureManager textures = mc().getTextureManager();
			GLINTS.forEach(textures::release); // nächster Zugriff lädt das Original neu
			applied = null;
		}
	}

	private void apply(int argb) {
		TextureManager textures = mc().getTextureManager();
		for (Identifier id : GLINTS) {
			AbstractTexture current = textures.getTexture(id);
			if (current instanceof TintedTexture) {
				textures.release(id); // erst wieder das Original laden, damit nicht doppelt eingefärbt wird
				current = textures.getTexture(id);
			}
			try (InputStream in = mc().getResourceManager().open(id)) {
				NativeImage image = NativeImage.read(in);
				tint(image, argb);
				textures.register(id, new TintedTexture(id, image, current));
			} catch (Exception e) {
				SalamiClient.LOG.warn("Glint Colorizer: could not tint {}", id, e);
				applied = argb; // erst bei einer anderen Farbe oder nach Aus/An erneut versuchen
				return;
			}
		}
		applied = argb;
	}

	/**
	 * Farbton ersetzen, Leuchtkraft des Originals behalten. Der Glanz wird additiv aufgetragen –
	 * deshalb zählt der hellste Kanal (nicht die Luminanz), und die gewählte Farbe wird auf volle
	 * Sättigung normiert, sonst wäre z. B. ein dunkles Rot kaum zu sehen.
	 */
	private static void tint(NativeImage image, int argb) {
		float peak = Math.max(1, Math.max(ARGB.red(argb), Math.max(ARGB.green(argb), ARGB.blue(argb))));
		float r = ARGB.red(argb) / peak;
		float g = ARGB.green(argb) / peak;
		float b = ARGB.blue(argb) / peak;
		int brightest = 1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				brightest = Math.max(brightest, channelMax(image.getPixel(x, y)));
			}
		}
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getPixel(x, y);
				float l = channelMax(pixel) / (float) brightest;
				image.setPixel(x, y, ARGB.color(ARGB.alpha(pixel), Math.round(r * l * 255), Math.round(g * l * 255), Math.round(b * l * 255)));
			}
		}
	}

	private static int channelMax(int argb) {
		return Math.max(ARGB.red(argb), Math.max(ARGB.green(argb), ARGB.blue(argb)));
	}

	/** Eingefärbte Kopie mit demselben Sampler (Wiederholen + weiche Filterung) wie das Original. */
	private static final class TintedTexture extends DynamicTexture {
		TintedTexture(Identifier id, NativeImage image, AbstractTexture original) {
			super(() -> "salami tinted " + id, image);
			this.sampler = original.getSampler();
		}
	}
}
