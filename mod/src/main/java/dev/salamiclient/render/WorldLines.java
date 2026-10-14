package dev.salamiclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

	/**
	 * Zeichnet den Rahmen einer Box in Weltkoordinaten (z. B. eine Entity-Hitbox) – direkt als
	 * 12 Kanten, ohne pro Aufruf eine Form zu bauen (läuft für viele Boxen in jedem Frame).
	 */
	public static void box(PoseStack pose, MultiBufferSource buffers, RenderType type, AABB box, int argb, float width) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		VertexConsumer lines = buffers.getBuffer(type);
		PoseStack.Pose last = pose.last();
		float x0 = (float) (box.minX - cam.x);
		float y0 = (float) (box.minY - cam.y);
		float z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x);
		float y1 = (float) (box.maxY - cam.y);
		float z1 = (float) (box.maxZ - cam.z);
		// entlang x
		line(lines, last, x0, y0, z0, x1, y0, z0, 1, 0, 0, argb, width);
		line(lines, last, x0, y1, z0, x1, y1, z0, 1, 0, 0, argb, width);
		line(lines, last, x0, y0, z1, x1, y0, z1, 1, 0, 0, argb, width);
		line(lines, last, x0, y1, z1, x1, y1, z1, 1, 0, 0, argb, width);
		// entlang y
		line(lines, last, x0, y0, z0, x0, y1, z0, 0, 1, 0, argb, width);
		line(lines, last, x1, y0, z0, x1, y1, z0, 0, 1, 0, argb, width);
		line(lines, last, x0, y0, z1, x0, y1, z1, 0, 1, 0, argb, width);
		line(lines, last, x1, y0, z1, x1, y1, z1, 0, 1, 0, argb, width);
		// entlang z
		line(lines, last, x0, y0, z0, x0, y0, z1, 0, 0, 1, argb, width);
		line(lines, last, x1, y0, z0, x1, y0, z1, 0, 0, 1, argb, width);
		line(lines, last, x0, y1, z0, x0, y1, z1, 0, 0, 1, argb, width);
		line(lines, last, x1, y1, z0, x1, y1, z1, 0, 0, 1, argb, width);
	}

	/** Eine Linie (Koordinaten relativ zur Kamera, Normale = Linienrichtung). */
	public static void line(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz,
		float nx, float ny, float nz, int argb, float width) {
		lines.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
		lines.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}

	/** Gepufferte Linien eines Typs sofort zeichnen (z. B. am Ende eines Welt-Events). */
	public static void flush(MultiBufferSource buffers, RenderType type) {
		if (buffers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(type);
		}
	}
}
