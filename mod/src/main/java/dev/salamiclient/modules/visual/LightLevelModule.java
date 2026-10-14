package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Level: markiert Blöcke, auf denen Monster spawnen können (Blocklicht 0).
 * Rot = auch tagsüber dunkel, Gelb = nur nachts. Ein Suchdurchlauf ist auf eine halbe Sekunde
 * verteilt (pro Tick nur ein paar Spalten), damit kein einzelner Tick ruckelt.
 */
public final class LightLevelModule extends Module implements WorldRenderListener {
	private static final int MAX_MARKS = 1200;
	private static final int SCAN_TICKS = 10;

	private final NumberSetting range = add(new NumberSetting("range", 12, 4, 24, 1));
	private final ColorSetting darkColor = add(new ColorSetting("color_dark", 0xFFFF3B3B));
	private final ColorSetting nightColor = add(new ColorSetting("color_night", 0xFFFFD23B));

	private record Mark(BlockPos pos, boolean alwaysDark) {}

	private List<Mark> marks = List.of();
	private List<Mark> pending = new ArrayList<>();
	/** Mitte des laufenden Durchlaufs; {@code null} = nächster Tick beginnt einen neuen. */
	private BlockPos scanCenter;
	private int scanX;

	public LightLevelModule() {
		super("light_level", Category.VISUAL);
	}

	@Override
	protected void onEnable() {
		scanCenter = null;
	}

	@Override
	protected void onDisable() {
		marks = List.of();
		pending = new ArrayList<>();
		scanCenter = null;
	}

	@Override
	public void onTick() {
		int r = range.intValue();
		if (scanCenter == null) {
			scanCenter = mc().player.blockPosition();
			scanX = -r;
			pending = new ArrayList<>();
		}
		int columnsPerTick = (2 * r + 1 + SCAN_TICKS - 1) / SCAN_TICKS;
		for (int i = 0; i < columnsPerTick && scanX <= r && pending.size() < MAX_MARKS; i++, scanX++) {
			scanColumn(scanX, r);
		}
		if (scanX > r || pending.size() >= MAX_MARKS) {
			marks = pending;
			scanCenter = null;
		}
	}

	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

	private void scanColumn(int dx, int r) {
		var level = mc().level;
		for (int dz = -r; dz <= r && pending.size() < MAX_MARKS; dz++) {
			for (int dy = -8; dy <= 4; dy++) {
				pos.set(scanCenter.getX() + dx, scanCenter.getY() + dy, scanCenter.getZ() + dz);
				if (!spawnSpace(level.getBlockState(pos), level, pos)) {
					continue;
				}
				below.set(pos.getX(), pos.getY() - 1, pos.getZ());
				if (!level.getBlockState(below).isValidSpawn(level, below, EntityType.ZOMBIE)) {
					continue;
				}
				if (level.getBrightness(LightLayer.BLOCK, pos) > 0) {
					continue;
				}
				pending.add(new Mark(pos.immutable(), level.getBrightness(LightLayer.SKY, pos) < 8));
			}
		}
	}

	private static boolean spawnSpace(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.isSignalSource();
	}

	/**
	 * Nur die vier oberen Kanten je Markierung, direkt in einen Linienpuffer – ohne Form-Berechnung
	 * und ohne Objekte pro Kante (bei Tausenden Markierungen spürbar schneller).
	 */
	@Override
	public void renderWorld(WorldRenderContext context) {
		if (marks.isEmpty()) {
			return;
		}
		RenderType type = WorldLines.VISIBLE;
		VertexConsumer lines = context.consumers().getBuffer(type);
		PoseStack.Pose pose = context.matrices().last();
		Vec3 cam = mc().gameRenderer.getMainCamera().position();
		int dark = darkColor.argb();
		int night = nightColor.argb();
		for (Mark mark : marks) {
			int color = mark.alwaysDark() ? dark : night;
			float x0 = (float) (mark.pos().getX() + 0.2 - cam.x);
			float x1 = (float) (mark.pos().getX() + 0.8 - cam.x);
			float y = (float) (mark.pos().getY() + 0.02 - cam.y);
			float z0 = (float) (mark.pos().getZ() + 0.2 - cam.z);
			float z1 = (float) (mark.pos().getZ() + 0.8 - cam.z);
			WorldLines.line(lines, pose, x0, y, z0, x1, y, z0, 1, 0, 0, color, 2f);
			WorldLines.line(lines, pose, x0, y, z1, x1, y, z1, 1, 0, 0, color, 2f);
			WorldLines.line(lines, pose, x0, y, z0, x0, y, z1, 0, 0, 1, color, 2f);
			WorldLines.line(lines, pose, x1, y, z0, x1, y, z1, 0, 0, 1, color, 2f);
		}
		WorldLines.flush(context.consumers(), type);
	}
}
