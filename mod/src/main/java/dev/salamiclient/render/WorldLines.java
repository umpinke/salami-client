package dev.salamiclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Linien in der Welt (Block-Umrisse, ESP-Boxen).
 *
 * <p>{@link #VISIBLE}: normale Tiefenprüfung (verdeckt hinter Blöcken).
 * {@link #THROUGH_WALLS}: eigene Pipeline ohne Tiefentest – sichtbar durch Wände.
 */
public final class WorldLines {
	private static final RenderPipeline NO_DEPTH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "pipeline/lines_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	public static final RenderType VISIBLE = RenderTypes.lines();
	public static final RenderType THROUGH_WALLS = RenderType.create(
		"salami_lines_through_walls",
		RenderSetup.builder(NO_DEPTH_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());

	private WorldLines() {}

	/** Lädt die Klasse (und registriert damit die Pipeline) schon beim Start. */
	public static void init() {}

	/** Zeichnet den Umriss einer Form an einer Blockposition. */
	public static void shape(PoseStack pose, MultiBufferSource buffers, RenderType type, BlockPos pos, VoxelShape shape, int argb, float width) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		ShapeRenderer.renderShape(pose, buffers.getBuffer(type), shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, argb, width);
	}

	/** Zeichnet den Rahmen einer Box in Weltkoordinaten (z. B. eine Entity-Hitbox). */
	public static void box(PoseStack pose, MultiBufferSource buffers, RenderType type, AABB box, int argb, float width) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		VoxelShape shape = Shapes.create(0, 0, 0, box.getXsize(), box.getYsize(), box.getZsize());
		ShapeRenderer.renderShape(pose, buffers.getBuffer(type), shape, box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z, argb, width);
	}

	/** Gepufferte Linien eines Typs sofort zeichnen (z. B. am Ende eines Welt-Events). */
	public static void flush(MultiBufferSource buffers, RenderType type) {
		if (buffers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(type);
		}
	}
}
