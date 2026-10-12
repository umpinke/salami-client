package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Armor HUD: eigene Rüstung (und optional Haupthand) mit Haltbarkeit in Prozent. */
public final class ArmorHudModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int ROW = 17;

	private final BoolSetting percent = add(new BoolSetting("percent", true));
	private final BoolSetting mainHand = add(new BoolSetting("main_hand", true));

	public ArmorHudModule() {
		super("armor_hud", Category.VISUAL, false, 0.72, 0.62); // rechts neben der Hotbar, nicht im Chat
	}

	@Override
	public int width() {
		return percent.isOn() ? 16 + 4 + mc().font.width("100%") : 16;
	}

	@Override
	public int height() {
		return ROW * (SLOTS.length + (mainHand.isOn() ? 1 : 0));
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		int y = 0;
		if (mainHand.isOn()) {
			drawRow(graphics, mc().player.getMainHandItem(), y);
			y += ROW;
		}
		for (EquipmentSlot slot : SLOTS) {
			drawRow(graphics, mc().player.getItemBySlot(slot), y);
			y += ROW;
		}
	}

	private void drawRow(GuiGraphics graphics, ItemStack stack, int y) {
		if (stack.isEmpty()) {
			return;
		}
		graphics.renderItem(stack, 0, y);
		graphics.renderItemDecorations(mc().font, stack, 0, y);
		if (percent.isOn() && stack.isDamageableItem()) {
			int left = 100 - Math.round(100f * stack.getDamageValue() / stack.getMaxDamage());
			int color = left > 50 ? 0xFF55FF55 : left > 20 ? 0xFFFFAA00 : 0xFFFF5555;
			graphics.drawString(mc().font, left + "%", 20, y + 4, color, true);
		} else if (percent.isOn() && stack.getCount() > 1) {
			graphics.drawString(mc().font, "x" + stack.getCount(), 20, y + 4, SalamiClient.get().theme().text(), true);
		}
	}
}
