package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
*///?} else
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Zeichen-Kontext beim Weltrendering (siehe {@link dev.salamiclient.module.WorldRenderListener}).
 * Bis 26.1 schreiben die Module direkt in Puffer; ab 26.2 gibt es nur noch das Einreichen von
 * Geometrie für später ({@code SubmitNodeCollector}). {@link WorldLines} kapselt beides.
 */
public final class WorldContext {
	private final PoseStack matrices;
	//? if >=26.2 {
	/*private final SubmitNodeCollector submits;

	public WorldContext(PoseStack matrices, SubmitNodeCollector submits) {
		this.matrices = matrices;
		this.submits = submits;
	}

	SubmitNodeCollector submits() {
		return submits;
	}
	*///?} else {
	private final MultiBufferSource consumers;

	public WorldContext(PoseStack matrices, MultiBufferSource consumers) {
		this.matrices = matrices;
		this.consumers = consumers;
	}

	MultiBufferSource consumers() {
		return consumers;
	}
	//?}

	public PoseStack matrices() {
		return matrices;
	}
}
