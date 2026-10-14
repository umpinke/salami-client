package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Level: markiert Blöcke, auf denen Monster spawnen können (Blocklicht 0).
 * Rot = auch tagsüber dunkel, Gelb = nur nachts. Gesucht wird nur alle halbe Sekunde.
 */
public final class LightLevelModule extends Module implements WorldRenderListener {
	private static final VoxelShape MARK = Shapes.box(0.2, 0.0, 0.2, 0.8, 0.02, 0.8);
	private static final int MAX_MARKS = 3000;
	private static final int SCAN_INTERVAL = 10;

	private final NumberSetting range = add(new NumberSetting("range", 12, 4, 24, 1));

	private record Mark(BlockPos pos, boolean alwaysDark) {}

	private List<Mark> marks = List.of();
	private int ticksUntilScan;

	public LightLevelModule() {
		super("light_level", Category.VISUAL);
	}

	@Override
	protected void onEnable() {
		ticksUntilScan = 0;
	}

	@Override
	protected void onDisable() {
		marks = List.of();
	}

	@Override
	public void onTick() {
		if (--ticksUntilScan > 0) {
			return;
		}
		ticksUntilScan = SCAN_INTERVAL;
		var level = mc().level;
		BlockPos center = mc().player.blockPosition();
		int r = range.intValue();
		List<Mark> found = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r && found.size() < MAX_MARKS; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -8; dy <= 4; dy++) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
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
					found.add(new Mark(pos.immutable(), level.getBrightness(LightLayer.SKY, pos) < 8));
				}
			}
		}
		marks = found;
	}

	private static boolean spawnSpace(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.isSignalSource();
	}

	@Override
	public void renderWorld(WorldRenderContext context) {
		if (marks.isEmpty()) {
			return;
		}
		RenderType type = WorldLines.VISIBLE;
		for (Mark mark : marks) {
			WorldLines.shape(context.matrices(), context.consumers(), type, mark.pos(), MARK, mark.alwaysDark() ? 0xFFFF3B3B : 0xFFFFD23B, 2f);
		}
		WorldLines.flush(context.consumers(), type);
	}
}
