package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.KeyTrigger;
import dev.salamiclient.util.SlotSwapper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lunge Macro: Auf Tastendruck – oder automatisch im eingestellten Takt – zum
 * Streitkolben bzw. Dreizack (sonst zum Schwert) wechseln, das Ziel im Fadenkreuz
 * schlagen und nach kurzer Zeit zurück zur vorherigen Waffe.
 */
public final class LungeMacroModule extends Module {
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", KeybindSetting.NONE));
	private final BoolSetting continuous = add(new BoolSetting("continuous", false));
	/** Nur für {@link #continuous}: Ticks zwischen zwei automatischen Schlägen. */
	private final NumberSetting interval = add(new NumberSetting("interval", 9, 0, 40, 1));
	private final BoolSetting swapBack = add(new BoolSetting("swap_back", true));
	private final NumberSetting swapDelay = add(new NumberSetting("swap_delay", 1, 1, 10, 1));
	/** Zurückwechseln zufällig einen Tick später. */
	private final BoolSetting randomize = add(new BoolSetting("randomize", false));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);
	private final SlotSwapper swapper = new SlotSwapper();
	/** Solange gewechselt ist, wird kein neuer Schlag ausgelöst. */
	private int busy;
	private int sinceLunge;

	public LungeMacroModule() {
		super("lunge_macro", Category.UTILITY);
		interval.showIf(continuous::isOn);
		swapDelay.showIf(swapBack::isOn);
		randomize.showIf(swapBack::isOn);
	}

	@Override
	protected void onDisable() {
		swapper.reset();
		busy = 0;
	}

	@Override
	public void onTick() {
		swapper.tick();
		sinceLunge++;
		boolean pressed = trigger.pressed();
		if (busy > 0) {
			busy--;
			return;
		}
		boolean autoReady = continuous.isOn()
			&& mc().screen == null
			&& sinceLunge >= interval.intValue()
			&& mc().player.getAttackStrengthScale(0) >= 1;
		if (pressed || autoReady) {
			lunge();
		}
	}

	private void lunge() {
		int weapon = findWeapon();
		if (weapon == InventoryUtil.NOT_FOUND) {
			return;
		}
		sinceLunge = 0;
		int hold = swapDelay.intValue() + (randomize.isOn() ? ThreadLocalRandom.current().nextInt(2) : 0);
		swapper.swapTo(weapon, swapBack.isOn() ? hold : 0);
		busy = hold;
		if (mc().hitResult instanceof EntityHitResult hit) {
			InteractUtil.attack(hit.getEntity());
		}
	}

	/** Streitkolben oder Dreizack, sonst ein Schwert. */
	private static int findWeapon() {
		int heavy = InventoryUtil.findHotbar(stack -> stack.is(Items.MACE) || stack.is(Items.TRIDENT));
		return heavy != InventoryUtil.NOT_FOUND ? heavy : InventoryUtil.findHotbar(stack -> stack.is(ItemTags.SWORDS));
	}
}
