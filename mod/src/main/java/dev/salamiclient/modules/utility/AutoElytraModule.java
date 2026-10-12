package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Auto Elytra (für Streitkolben-Kombos):
 * <ul>
 *   <li>Beim Fallen ab einer Mindesthöhe Elytra anziehen und gleiten.</li>
 *   <li>Kurz vor dem Boden oder sobald ein Ziel mit Streitkolben anvisiert ist:
 *       zurück zum Brustpanzer (Schutz + Smash-Treffer).</li>
 * </ul>
 */
public final class AutoElytraModule extends Module {
	private final NumberSetting deployFall = add(new NumberSetting("deploy_fall", 3, 1, 30, 0.5));
	private final NumberSetting groundDistance = add(new NumberSetting("ground_distance", 4, 1, 15, 0.5));
	private final BoolSetting maceSwap = add(new BoolSetting("mace_swap", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 2, 0, 10, 1));

	private int cooldown;
	private boolean wantGlide;

	public AutoElytraModule() {
		super("auto_elytra", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
		wantGlide = false;
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (cooldown > 0) {
			cooldown--;
		}
		boolean wearingElytra = isGlider(player.getItemBySlot(EquipmentSlot.CHEST));

		// Nach dem Anziehen: Gleiten starten (wie Sprungtaste in der Luft)
		if (wantGlide && wearingElytra && !player.onGround() && !player.isFallFlying()) {
			if (player.tryToStartFallFlying()) {
				player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			}
			wantGlide = false;
			return;
		}
		if (cooldown > 0 || mc().screen != null || !InventoryUtil.canClickPlayerInventory()) {
			return;
		}

		if (!wearingElytra) {
			if (!player.onGround() && !player.isFallFlying() && player.fallDistance >= deployFall.get() && !nearGround(player)) {
				int elytra = InventoryUtil.find(this::isGlider, 0, 35);
				if (elytra != InventoryUtil.NOT_FOUND) {
					InventoryUtil.swapWithArmor(elytra, InventoryUtil.ARMOR_CHEST);
					wantGlide = true;
					cooldown = delay.intValue();
				}
			}
			return;
		}

		boolean landing = player.onGround() || nearGround(player);
		boolean maceTarget = maceSwap.isOn()
			&& player.getMainHandItem().is(Items.MACE)
			&& mc().hitResult instanceof EntityHitResult hit
			&& hit.getEntity() instanceof LivingEntity;
		if (landing || maceTarget) {
			int chestplate = bestChestplate();
			if (chestplate != InventoryUtil.NOT_FOUND) {
				InventoryUtil.swapWithArmor(chestplate, InventoryUtil.ARMOR_CHEST);
				cooldown = delay.intValue();
			}
		}
	}

	private boolean isGlider(ItemStack stack) {
		return stack.has(DataComponents.GLIDER);
	}

	private boolean nearGround(LivingEntity player) {
		Vec3 from = player.position();
		Vec3 to = from.subtract(0, groundDistance.get(), 0);
		HitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return hit.getType() != HitResult.Type.MISS;
	}

	/** Bester Brustpanzer im Inventar (höchster Rüstungswert zuerst über die Item-Reihenfolge). */
	private int bestChestplate() {
		ItemStack[] order = {
			new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.DIAMOND_CHESTPLATE),
			new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
			new ItemStack(Items.GOLDEN_CHESTPLATE), new ItemStack(Items.COPPER_CHESTPLATE),
			new ItemStack(Items.LEATHER_CHESTPLATE),
		};
		for (ItemStack wanted : order) {
			int slot = InventoryUtil.find(stack -> stack.is(wanted.getItem()), 0, 35);
			if (slot != InventoryUtil.NOT_FOUND) {
				return slot;
			}
		}
		return InventoryUtil.find(stack -> {
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isGlider(stack);
		}, 0, 35);
	}
}
