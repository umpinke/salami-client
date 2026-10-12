package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Projection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Live Tracker: zeigt über dem Namen von Gegnern, welche Rüstung sie tragen
 * (mit Haltbarkeitsbalken) – optional auch die Items in den Händen.
 *
 * <p>Ziele werden einmal pro Tick gesammelt (Reichweite, Sichtlinie), gezeichnet
 * wird pro Frame mit interpolierter Position.
 */
public final class LiveTrackerModule extends Module {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int ICON = 16;
	private static final int GAP = 1;

	private final NumberSetting range = add(new NumberSetting("range", 32, 4, 64, 1));
	private final BoolSetting hands = add(new BoolSetting("hands", true));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", false));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 1.5, 0.1));

	private final List<LivingEntity> targets = new ArrayList<>();
	private final List<ItemStack> items = new ArrayList<>(6);
	private final float[] screen = new float[2];

	public LiveTrackerModule() {
		super("live_tracker", Category.VISUAL);
	}

	@Override
	protected void onDisable() {
		targets.clear();
	}

	@Override
	public void onTick() {
		targets.clear();
		LocalPlayer self = mc().player;
		double maxSq = range.get() * range.get();
		for (Entity entity : mc().level.entitiesForRendering()) {
			if (entity == self || !(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			if (!(entity instanceof Player) && !mobs.isOn()) {
				continue;
			}
			if (entity.isInvisible() || self.distanceToSqr(entity) > maxSq) {
				continue;
			}
			if (!throughWalls.isOn() && !self.hasLineOfSight(entity)) {
				continue;
			}
			targets.add(living);
		}
		// weit entfernte zuerst zeichnen, nahe liegen oben
		targets.sort(Comparator.<LivingEntity>comparingDouble(self::distanceToSqr).reversed());
	}

	@Override
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (targets.isEmpty() || !Projection.begin()) {
			return;
		}
		float partialTick = delta.getGameTimeDeltaPartialTick(true);
		float s = scale.get().floatValue();

		for (LivingEntity target : targets) {
			if (!target.isAlive()) {
				continue;
			}
			collectItems(target);
			if (items.isEmpty()) {
				continue;
			}
			// Namensschild sitzt ~0.5 Blöcke über dem Kopf
			Vec3 anchor = target.getPosition(partialTick).add(0, target.getBbHeight() + 0.5, 0);
			if (!Projection.toGui(anchor, graphics.guiWidth(), graphics.guiHeight(), screen)) {
				continue;
			}
			int width = items.size() * (ICON + GAP) - GAP;
			graphics.pose().pushMatrix();
			graphics.pose().translate(screen[0], screen[1]);
			graphics.pose().scale(s, s);
			int x = -width / 2;
			int y = -ICON - 12; // über der Namenszeile
			graphics.fill(x - 2, y - 2, x + width + 2, y + ICON + 2, 0x66000000);
			for (ItemStack stack : items) {
				graphics.renderItem(stack, x, y);
				graphics.renderItemDecorations(mc().font, stack, x, y);
				x += ICON + GAP;
			}
			graphics.pose().popMatrix();
		}
	}

	private void collectItems(LivingEntity target) {
		items.clear();
		if (hands.isOn()) {
			addIfPresent(target.getItemBySlot(EquipmentSlot.MAINHAND));
		}
		for (EquipmentSlot slot : ARMOR) {
			addIfPresent(target.getItemBySlot(slot));
		}
		if (hands.isOn()) {
			addIfPresent(target.getItemBySlot(EquipmentSlot.OFFHAND));
		}
	}

	private void addIfPresent(ItemStack stack) {
		if (!stack.isEmpty()) {
			items.add(stack);
		}
	}
}
