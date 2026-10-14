package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.KeyTrigger;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Key Pearl: Auf Tastendruck wird sofort eine Enderperle aus der Hotbar geworfen –
 * Slot wechseln, werfen und (optional) zurück, alles im selben Tick.
 */
public final class KeyPearlModule extends Module {
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", GLFW.GLFW_KEY_R));
	private final NumberSetting delay = add(new NumberSetting("delay", 8, 0, 20, 1));
	private final BoolSetting switchBack = add(new BoolSetting("switch_back", true));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);
	private int cooldown;

	public KeyPearlModule() {
		super("key_pearl", Category.UTILITY);
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
		}
		if (!trigger.pressed() || cooldown > 0) {
			return;
		}
		int pearl = InventoryUtil.findHotbar(stack -> stack.is(Items.ENDER_PEARL));
		if (pearl == InventoryUtil.NOT_FOUND) {
			return;
		}
		int previous = InventoryUtil.selectedHotbar();
		InventoryUtil.selectHotbar(pearl);
		InteractUtil.use();
		if (switchBack.isOn()) {
			InventoryUtil.selectHotbar(previous);
		}
		cooldown = delay.intValue();
	}
}
