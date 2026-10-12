package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Item Counter: wie viele Totems, Kristalle, Obsidian, Äpfel und Perlen du noch hast. */
public final class ItemCounterModule extends HudModule {
	private static final int ROW = 17;

	private final BoolSetting totems = add(new BoolSetting("totems", true));
	private final BoolSetting crystals = add(new BoolSetting("crystals", true));
	private final BoolSetting obsidian = add(new BoolSetting("obsidian", false));
	private final BoolSetting gapples = add(new BoolSetting("gapples", true));
	private final BoolSetting pearls = add(new BoolSetting("pearls", true));
	private final BoolSetting hideEmpty = add(new BoolSetting("hide_empty", true));

	private record Row(ItemStack icon, int count) {}

	private final List<Row> rows = new ArrayList<>();

	public ItemCounterModule() {
		super("item_counter", Category.VISUAL, false, 0.94, 0.45);
	}

	@Override
	public void onTick() {
		rows.clear();
		addRow(totems, Items.TOTEM_OF_UNDYING);
		addRow(crystals, Items.END_CRYSTAL);
		addRow(obsidian, Items.OBSIDIAN);
		addRow(gapples, Items.GOLDEN_APPLE);
		addRow(gapples, Items.ENCHANTED_GOLDEN_APPLE);
		addRow(pearls, Items.ENDER_PEARL);
	}

	private void addRow(BoolSetting toggle, Item item) {
		if (!toggle.isOn()) {
			return;
		}
		int count = count(item);
		if (count > 0 || !hideEmpty.isOn()) {
			rows.add(new Row(new ItemStack(item), count));
		}
	}

	private int count(Item item) {
		Inventory inventory = mc().player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@Override
	public boolean hasContent() {
		return !rows.isEmpty();
	}

	@Override
	public int width() {
		return 16 + 4 + mc().font.width("999");
	}

	@Override
	public int height() {
		return Math.max(ROW, rows.size() * ROW);
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		int y = 0;
		for (Row row : rows) {
			graphics.renderItem(row.icon(), 0, y);
			int color = row.count() == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
			graphics.drawString(mc().font, String.valueOf(row.count()), 20, y + 4, color, true);
			y += ROW;
		}
	}
}
