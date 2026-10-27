package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Block ESP: hebt bestimmte Blöcke in der Nähe mit Rahmen hervor – optional durch Wände.
 * Steht unter "Utility", weil Sicht durch Wände auf den meisten Servern verboten ist.
 *
 * <p>Ein Suchdurchlauf ist auf {@code SCAN_TICKS} Ticks verteilt (pro Tick ein paar
 * x-Spalten) und überspringt Chunk-Abschnitte, deren Palette keinen passenden Block enthält.
 * Gezeichnet wird jedes Frame aus der zuletzt fertigen Liste.
 */
public final class BlockEspModule extends Module implements WorldRenderListener {
	public enum Preset { CPVP, STORAGE, ORES, SPAWNERS }

	private static final int SCAN_TICKS = 20;
	private static final int MAX_BLOCKS = 512;

	private final ModeSetting<Preset> preset = add(new ModeSetting<>("preset", Preset.CPVP));
	private final NumberSetting range = add(new NumberSetting("range", 24, 4, 64, 1));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", true));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFFAA00));

	private List<BlockPos> found = List.of();
	private List<BlockPos> pending = new ArrayList<>();
	/** Mitte des laufenden Durchlaufs; {@code null} = nächster Tick beginnt einen neuen. */
	private BlockPos scanCenter;
	private int scanX;
	private Predicate<BlockState> scanMatcher;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public BlockEspModule() {
		super("block_esp", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		scanCenter = null;
	}

	@Override
	protected void onDisable() {
		found = List.of();
		pending = new ArrayList<>();
		scanCenter = null;
	}

	@Override
	public void onTick() {
		int r = range.intValue();
		if (scanCenter == null) {
			scanCenter = mc().player.blockPosition();
			scanX = -r;
			scanMatcher = matcher(preset.get());
			pending = new ArrayList<>();
		}
		int columnsPerTick = (2 * r + 1 + SCAN_TICKS - 1) / SCAN_TICKS;
		for (int i = 0; i < columnsPerTick && scanX <= r && pending.size() < MAX_BLOCKS; i++, scanX++) {
			scanColumn(mc().level, scanCenter.getX() + scanX, r);
		}
		if (scanX > r || pending.size() >= MAX_BLOCKS) {
			found = pending;
			scanCenter = null;
		}
	}

	private void scanColumn(ClientLevel level, int x, int r) {
		int minY = Math.max(level.getMinY(), scanCenter.getY() - r);
		int maxY = Math.min(level.getMaxY(), scanCenter.getY() + r);
		for (int z = scanCenter.getZ() - r; z <= scanCenter.getZ() + r; z++) {
			LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
			if (chunk == null) {
				continue;
			}
			for (int y = minY; y <= maxY; y++) {
				LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
				if (section.hasOnlyAir() || !section.maybeHas(scanMatcher)) {
					// ganzer 16er-Abschnitt ohne passenden Block – zum nächsten springen
					y = (y | 15);
					continue;
				}
				pos.set(x, y, z);
				if (scanMatcher.test(section.getBlockState(x & 15, y & 15, z & 15))) {
					pending.add(pos.immutable());
					if (pending.size() >= MAX_BLOCKS) {
						return;
					}
				}
			}
		}
	}

	@Override
	public void renderWorld(WorldContext context) {
		if (found.isEmpty()) {
			return;
		}
		RenderType type = throughWalls.isOn() ? WorldLines.THROUGH_WALLS : WorldLines.VISIBLE;
		int argb = color.argb();
		for (BlockPos pos : found) {
			WorldLines.box(context, type, new AABB(pos), argb, 2f);
		}
		WorldLines.flush(context, type);
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
