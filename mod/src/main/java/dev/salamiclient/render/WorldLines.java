package dev.salamiclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
//? if >=26.1 {
/*import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
*///?} else
import com.mojang.blaze3d.platform.DepthTestFunction;
//? if >=26.3
/*import com.mojang.renderpearl.api.pipeline.ColorTargetState;*/
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderStateShard;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
*///?}
import net.minecraft.client.renderer.rendertype.RenderType;
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
 *
 * <p>Bis 26.1 gehen die Linien sofort in einen Puffer ({@link #flush} zeichnet ihn); ab 26.2
 * werden sie eingereicht und von Minecraft später im selben Frame gezeichnet.
 */
public final class WorldLines {
	private static final RenderPipeline NO_DEPTH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "pipeline/lines_through_walls"))
			//? if >=26.3
			/*.withColorTargetState(ColorTargetState.DEFAULT)*/
			//? if >=26.1 {
			/*.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			*///?} else
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	//? if >=1.21.11 {
	public static final RenderType VISIBLE = RenderTypes.lines();
	public static final RenderType THROUGH_WALLS = RenderType.create(
		"salami_lines_through_walls",
		RenderSetup.builder(NO_DEPTH_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
	//?} else {
	/*public static final RenderType VISIBLE = RenderType.lines();
	public static final RenderType THROUGH_WALLS = linesType("salami_lines_through_walls", NO_DEPTH_PIPELINE, OptionalDouble.empty());

	// Bis 1.21.10 ist die Linienbreite eine Eigenschaft des RenderType (nicht der Ecke):
	// pro Basistyp und Breite eine eigene Variante.
	private static final Map<RenderType, Map<Float, RenderType>> WIDTHS = new HashMap<>();

	private static RenderType linesType(String name, RenderPipeline pipeline, OptionalDouble width) {
		return RenderType.create(name, 1536, pipeline, RenderType.CompositeState.builder()
			.setLineState(new RenderStateShard.LineStateShard(width))
			.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
			.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
			.createCompositeState(false));
	}

	private static RenderType withWidth(RenderType type, float width) {
		return WIDTHS.computeIfAbsent(type, t -> new HashMap<>()).computeIfAbsent(width, w -> {
			boolean throughWalls = type == THROUGH_WALLS;
			return linesType((throughWalls ? "salami_lines_through_walls_" : "salami_lines_") + w,
				throughWalls ? NO_DEPTH_PIPELINE : RenderPipelines.LINES, OptionalDouble.of(w));
		});
	}
	*///?}

	private WorldLines() {}

	/** Lädt die Klasse (und registriert damit die Pipeline) schon beim Start. */
	public static void init() {}

	/** Zeichnet Linien selbst (Koordinaten relativ zur Kamera, siehe {@link #line}). */
	@FunctionalInterface
	public interface LineDrawer {
		void draw(VertexConsumer lines, PoseStack.Pose pose);
	}

	/** Viele Linien eines Typs in derselben Breite, z. B. Markierungen auf dem Boden. */
	public static void lines(WorldContext context, RenderType type, float width, LineDrawer drawer) {
		//? if >=26.2 {
		/*context.submits().submitCustomGeometry(context.matrices(), type, (pose, lines) -> drawer.draw(lines, pose));
		*///?} else
		drawer.draw(buffer(context.consumers(), type, width), context.matrices().last());
	}

	/** Zeichnet den Umriss einer Form an einer Blockposition. */
	public static void shape(WorldContext context, RenderType type, BlockPos pos, VoxelShape shape, int argb, float width) {
		Vec3 cam = camera();
		//? if >=26.2 {
		/*PoseStack pose = context.matrices();
		pose.pushPose();
		pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
		context.submits().submitShapeOutline(pose, shape, type, argb, width, false);
		pose.popPose();
		*///?} else if >=1.21.11 {
		ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(type), shape,
			pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, argb, width);
		//?} else
		/*ShapeRenderer.renderShape(context.matrices(), buffer(context.consumers(), type, width), shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, argb);*/
	}

	/**
	 * Zeichnet den Rahmen einer Box in Weltkoordinaten (z. B. eine Entity-Hitbox) – direkt als
	 * 12 Kanten, ohne pro Aufruf eine Form zu bauen (läuft für viele Boxen in jedem Frame).
	 */
	public static void box(WorldContext context, RenderType type, AABB box, int argb, float width) {
		Vec3 cam = camera();
		float x0 = (float) (box.minX - cam.x);
		float y0 = (float) (box.minY - cam.y);
		float z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x);
		float y1 = (float) (box.maxY - cam.y);
		float z1 = (float) (box.maxZ - cam.z);
		lines(context, type, width, (lines, last) -> {
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
		});
	}

	/**
	 * Eine Linie (Koordinaten relativ zur Kamera, Normale = Linienrichtung). Nur in einem
	 * {@link LineDrawer} – bis 1.21.10 hängt die Breite am Puffer, nicht an der Ecke.
	 */
	public static void line(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz,
		float nx, float ny, float nz, int argb, float width) {
		//? if >=1.21.11 {
		lines.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
		lines.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
		//?} else {
		/*lines.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, nx, ny, nz);
		lines.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, nx, ny, nz);
		*///?}
	}

	/** Gepufferte Linien eines Typs sofort zeichnen (z. B. am Ende eines Welt-Events). Ab 26.2 unnötig. */
	public static void flush(WorldContext context, RenderType type) {
		//? if <26.2 {
		if (context.consumers() instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(type);
			//? if <1.21.11 {
			/*for (RenderType variant : WIDTHS.getOrDefault(type, Map.of()).values()) {
				source.endBatch(variant);
			}
			*///?}
		}
		//?}
	}

	private static Vec3 camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().position();
	}

	//? if <26.2 {
	private static VertexConsumer buffer(MultiBufferSource buffers, RenderType type, float width) {
		//? if >=1.21.11 {
		return buffers.getBuffer(type);
		//?} else
		/*return buffers.getBuffer(withWidth(type, width));*/
	}
	//?}
}
