package dev.salamiclient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
//? if <26.2
import net.minecraft.client.renderer.MultiBufferSource;
//? if >=1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Modelle (z. B. ein Mini Me) in die Welt zeichnen – bis 26.1 direkt in den Puffer, ab 26.2 als
 * eingereichtes Modellteil (siehe {@link WorldContext}). Die Haltung des Modells muss bis zum
 * eigentlichen Zeichnen gleich bleiben (eigene Modelle pro Figur).
 */
public final class WorldModels {
	private WorldModels() {}

	/** Kamera-Position: die Matrizen beim Weltrendern sind relativ zur Kamera. */
	public static Vec3 camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().position();
	}

	/** Render-Typ für Wesen-Texturen mit durchsichtigen Stellen (Skins, äußere Schicht). */
	public static RenderType entityType(Identifier texture) {
		//? if >=1.21.11 {
		return RenderTypes.entityCutout(texture);
		//?} else
		/*return RenderType.entityCutout(texture);*/
	}

	public static void model(WorldContext context, ModelPart part, RenderType type, int light) {
		//? if >=26.2 {
		/*context.submits().submitModelPart(part, context.matrices(), type, light, OverlayTexture.NO_OVERLAY, null);
		*///?} else {
		part.render(context.matrices(), context.consumers().getBuffer(type), light, OverlayTexture.NO_OVERLAY);
		if (context.consumers() instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(type);
		}
		//?}
	}
}
