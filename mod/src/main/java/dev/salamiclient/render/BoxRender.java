package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.cosmetics.BoxModel;
//? if <26.2
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Zeichnet Würfel-Modelle ({@link BoxModel}): Hüte, Buddys, Requisiten und Verwandlungen. Die Würfel
 * haben kein eigenes Bild – eine weiße Textur, die Farbe steht an jeder Fläche. Am Spielermodell
 * werden sie eingereicht ({@link #submit}), in der Welt je nach Version direkt in den Puffer
 * geschrieben oder ebenfalls eingereicht ({@link #world}).
 */
public final class BoxRender {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/cosmetics/white.png");
	// Ohne Rückseiten-Auslese: gespiegelte Modelle (andere Schulter) haben die Flächen andersherum.
	// Ab 26.1 heißt genau das „entityCutout“ (mit Auslese: entityCutoutCull).
	//? if >=26.1 {
	/*private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	*///?} else if >=1.21.11 {
	private static final RenderType TYPE = RenderTypes.entityCutoutNoCull(TEXTURE);
	//?} else
	/*private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);*/
	/** Volle Helligkeit (leuchtende Modelle, auch im Dunkeln). */
	public static final int FULL_BRIGHT = 0xF000F0;

	private BoxRender() {}

	/** An einem Wesen (in einer Render-Schicht): das Modell mit der aktuellen Matrix einreichen. */
	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, BoxModel model, boolean mirror, int light) {
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> model.emit(pose, buffer, mirror, light));
	}

	/** In der Welt (siehe {@link WorldContext}): das Modell mit der aktuellen Matrix zeichnen. */
	public static void world(WorldContext context, BoxModel model, int light) {
		//? if >=26.2 {
		/*context.submits().submitCustomGeometry(context.matrices(), TYPE, (pose, buffer) -> model.emit(pose, buffer, false, light));
		*///?} else {
		model.emit(context.matrices().last(), context.consumers().getBuffer(TYPE), false, light);
		if (context.consumers() instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(TYPE);
		}
		//?}
	}

	/** Licht an einer Stelle der Welt, so verpackt, wie die Puffer es erwarten. */
	public static int lightAt(Level level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y, z);
		return level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
	}
}
