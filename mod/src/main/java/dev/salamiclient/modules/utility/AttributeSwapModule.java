package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.SlotSwapper;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * Attribute Swap: Beim Schlag wird im selben Tick auf eine andere Waffe gewechselt.
 * Der Server rechnet den Treffer dann mit den Verzauberungs-Effekten der neuen Waffe,
 * aber noch mit Angriffswerten und Aufladung der alten. Danach geht es zurück.
 */
public final class AttributeSwapModule extends Module implements AttackListener {
	public enum Target { MACE, SPEAR, AXE, SWORD, SLOT }

	private final ModeSetting<Target> target = add(new ModeSetting<>("target", Target.MACE));
	/** Nur für {@link Target#SLOT}: fester Hotbar-Slot 1–9. */
	private final NumberSetting slot = add(new NumberSetting("slot", 1, 1, 9, 1));
	private final NumberSetting swapBack = add(new NumberSetting("swap_back", 2, 0, 10, 1));

	private final SlotSwapper swapper = new SlotSwapper();

	public AttributeSwapModule() {
		super("attribute_swap", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		swapper.reset();
	}

	@Override
	public void onTick() {
		swapper.tick();
	}

	@Override
	public void beforeAttack(Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return;
		}
		int destination = switch (target.get()) {
			case SLOT -> slot.intValue() - 1;
			case MACE -> find(stack -> stack.is(Items.MACE));
			case SPEAR -> find(WeaponUtil::isSpear);
			case AXE -> find(stack -> stack.is(ItemTags.AXES));
			case SWORD -> find(stack -> stack.is(ItemTags.SWORDS));
		};
		if (destination != InventoryUtil.NOT_FOUND && destination != InventoryUtil.selectedHotbar()) {
			swapper.swapTo(destination, swapBack.intValue());
		}
	}

	private static int find(Predicate<ItemStack> matcher) {
		ItemStack held = net.minecraft.client.Minecraft.getInstance().player.getMainHandItem();
		if (matcher.test(held)) {
			return InventoryUtil.NOT_FOUND; // hält bereits die Zielwaffe
		}
		return InventoryUtil.findHotbar(matcher);
	}
}
