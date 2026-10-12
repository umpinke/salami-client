package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Block ESP: hebt bestimmte Blöcke in der Nähe mit Rahmen hervor – optional durch Wände.
 * Steht unter "Utility", weil Sicht durch Wände auf den meisten Servern verboten ist.
 *
 * <p>Gesucht wird nur alle {@code SCAN_INTERVAL} Ticks und mit Obergrenze, gezeichnet
 * wird jedes Frame aus der gemerkten Liste.
 */
public final class BlockEspModule extends Module implements WorldRenderListener {
	public enum Preset { CPVP, STORAGE, ORES, SPAWNERS }

	private static final int SCAN_INTERVAL = 20;
	private static final int MAX_BLOCKS = 512;

	private final ModeSetting<Preset> preset = add(new ModeSetting<>("preset", Preset.CPVP));
	private final NumberSetting range = add(new NumberSetting("range", 24, 4, 64, 1));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", true));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFFAA00));

	private final List<BlockPos> found = new ArrayList<>();
	private int scanIn;

	public BlockEspModule() {
		super("block_esp", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		scanIn = 0;
	}

	@Override
	protected void onDisable() {
		found.clear();
	}

	@Override
	public void onTick() {
		if (--scanIn > 0) {
			return;
		}
		scanIn = SCAN_INTERVAL;
		found.clear();
		ClientLevel level = mc().level;
		BlockPos center = mc().player.blockPosition();
		int r = range.intValue();
		int minY = Math.max(level.getMinY(), center.getY() - r);
		int maxY = Math.min(level.getMaxY(), center.getY() + r);
		Predicate<BlockState> matcher = matcher(preset.get());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = center.getX() - r; x <= center.getX() + r && found.size() < MAX_BLOCKS; x++) {
			for (int z = center.getZ() - r; z <= center.getZ() + r && found.size() < MAX_BLOCKS; z++) {
				for (int y = minY; y <= maxY; y++) {
					pos.set(x, y, z);
					if (matcher.test(level.getBlockState(pos))) {
						found.add(pos.immutable());
						if (found.size() >= MAX_BLOCKS) {
							break;
						}
					}
				}
			}
		}
	}

	@Override
	public void renderWorld(WorldRenderContext context) {
		if (found.isEmpty()) {
			return;
		}
		RenderType type = throughWalls.isOn() ? WorldLines.THROUGH_WALLS : WorldLines.VISIBLE;
		for (BlockPos pos : found) {
			WorldLines.shape(context.matrices(), context.consumers(), type, pos, Shapes.block(), color.argb(), 2f);
		}
		WorldLines.flush(context.consumers(), type);
	}

	private static Predicate<BlockState> matcher(Preset preset) {
		return switch (preset) {
			case CPVP -> s -> s.is(Blocks.RESPAWN_ANCHOR) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN);
			case STORAGE -> s -> s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.ENDER_CHEST)
				|| s.is(Blocks.BARREL) || s.is(BlockTags.SHULKER_BOXES);
			case ORES -> s -> s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.ANCIENT_DEBRIS)
				|| s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE);
			case SPAWNERS -> s -> s.is(Blocks.SPAWNER) || s.is(Blocks.TRIAL_SPAWNER);
		};
	}
}
