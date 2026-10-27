package dev.salamiclient.modules.utility;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.mixin.LocalPlayerAccessor;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.Keys;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Auto Pot: benutzt Tränke automatisch –
 * <ul>
 *   <li><b>Heilung</b>, sobald dein Leben auf die eingestellte Grenze fällt,</li>
 *   <li><b>Stärke</b> und <b>Schnelligkeit</b>, wenn der Effekt fehlt oder gleich ausläuft.</li>
 * </ul>
 * Wurftränke landen vor deinen Füßen: nur der Wurf zielt nach unten, deine Kamera bleibt, wo
 * sie ist. Trinktränke optional (das Trinken dauert 1,6 s und bremst dich). Tränke mit
 * schädlichen Effekten werden nie benutzt. Liegt der passende Trank nicht in der Hotbar, holt
 * das Modul ihn auf Wunsch aus dem Inventar in einen festen Hotbar-Slot. Nach jedem Trank wird
 * kurz gewartet, bis der Effekt vom Server da ist – so wird nichts doppelt verbraucht.
 */
public final class AutoPotModule extends Module {
	private enum Need {
		HEALTH(MobEffects.INSTANT_HEALTH),
		STRENGTH(MobEffects.STRENGTH),
		SPEED(MobEffects.SPEED);

		final Holder<MobEffect> effect;

		Need(Holder<MobEffect> effect) {
			this.effect = effect;
		}
	}

	/** Abbruch, falls das Trinken nach so vielen Ticks nicht fertig ist (Vanilla: 32 Ticks). */
	private static final int DRINK_TIMEOUT = 50;
	/** Neigung für den Wurf: senkrecht nach unten. */
	private static final float LOOK_DOWN = 90;

	private final BoolSetting heal = add(new BoolSetting("heal", true));
	private final NumberSetting health = add(new NumberSetting("health", 10, 1, 19, 1));
	private final BoolSetting strength = add(new BoolSetting("strength", true));
	private final BoolSetting speed = add(new BoolSetting("speed", true));
	/** Restdauer in Sekunden, ab der Stärke/Schnelligkeit erneuert werden (0 = erst wenn weg). */
	private final NumberSetting refresh = add(new NumberSetting("refresh", 3, 0, 30, 1));
	private final BoolSetting drink = add(new BoolSetting("drink", false));
	private final BoolSetting fromInventory = add(new BoolSetting("from_inventory", true));
	/** Hotbar-Slot (1–9), in den Tränke aus dem Inventar geholt werden. */
	private final NumberSetting slot = add(new NumberSetting("slot", 7, 1, 9, 1));
	/** Pause nach jedem Trank, bis der Effekt vom Server angekommen ist. */
	private final NumberSetting delay = add(new NumberSetting("delay", 10, 2, 40, 1));
	private final BoolSetting switchBack = add(new BoolSetting("switch_back", true));

	private int cooldown;
	/** Ticks seit Beginn des Trinkens; -1 = es wird nicht getrunken. */
	private int drinkTicks = -1;
	private boolean drinkStarted;
	private int previousSlot = InventoryUtil.NOT_FOUND;

	public AutoPotModule() {
		super("auto_pot", Category.UTILITY);
		health.showIf(heal::isOn);
		slot.showIf(fromInventory::isOn);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
		drinkTicks = -1;
		previousSlot = InventoryUtil.NOT_FOUND;
	}

	@Override
	protected void onDisable() {
		if (drinkTicks >= 0) {
			finishDrinking();
		}
	}

	@Override
	public void onTick() {
		if (drinkTicks >= 0) {
			tickDrinking();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (mc().screen != null || mc().player.isUsingItem()) {
			return;
		}
		for (Need need : Need.values()) {
			if (!needed(need)) {
				continue;
			}
			int index = findPotion(need);
			if (index == InventoryUtil.NOT_FOUND) {
				continue; // kein passender Trank – nächsten Bedarf prüfen
			}
			if (index > 8) {
				if (InventoryUtil.canClickPlayerInventory()) {
					InventoryUtil.swapWithHotbar(index, slot.intValue() - 1);
					cooldown = 1; // im nächsten Tick aus der Hotbar benutzen
				}
				return;
			}
			use(index);
			return;
		}
	}

	private boolean needed(Need need) {
		LocalPlayer player = mc().player;
		return switch (need) {
			case HEALTH -> heal.isOn() && player.getHealth() <= health.get() && player.getHealth() < player.getMaxHealth();
			case STRENGTH -> strength.isOn() && expiring(MobEffects.STRENGTH);
			case SPEED -> speed.isOn() && expiring(MobEffects.SPEED);
		};
	}

	private boolean expiring(Holder<MobEffect> effect) {
		MobEffectInstance active = mc().player.getEffect(effect);
		return active == null || (!active.isInfiniteDuration() && active.getDuration() <= refresh.get() * 20);
	}

	/** Inventar-Index des besten Tranks: Wurftränke vor Trinktränken, Hotbar vor Inventar. */
	private int findPotion(Need need) {
		Inventory inventory = mc().player.getInventory();
		int last = fromInventory.isOn() ? 35 : 8;
		int drinkable = InventoryUtil.NOT_FOUND;
		for (int i = 0; i <= last; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!matches(stack, need)) {
				continue;
			}
			if (stack.is(Items.SPLASH_POTION)) {
				return i;
			}
			if (drinkable == InventoryUtil.NOT_FOUND) {
				drinkable = i;
			}
		}
		return drinkable;
	}

	/** Wurf- oder (wenn erlaubt) Trinktrank mit dem gesuchten Effekt und ohne schädliche Effekte. */
	private boolean matches(ItemStack stack, Need need) {
		if (!stack.is(Items.SPLASH_POTION) && !(drink.isOn() && stack.is(Items.POTION))) {
			return false;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		boolean wanted = false;
		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				return false;
			}
			wanted |= effect.is(need.effect);
		}
		return wanted;
	}

	private void use(int hotbarIndex) {
		previousSlot = InventoryUtil.selectedHotbar();
		InventoryUtil.selectHotbar(hotbarIndex);
		if (mc().player.getMainHandItem().is(Items.SPLASH_POTION)) {
			throwAtFeet();
			restoreSlot();
			cooldown = delay.intValue();
			return;
		}
		// Trinken: Vanilla trinkt, solange die Benutzen-Taste gehalten wird
		if (InteractUtil.use()) {
			mc().options.keyUse.setDown(true);
			drinkTicks = 0;
			drinkStarted = false;
		} else {
			restoreSlot();
			cooldown = delay.intValue();
		}
	}

	/**
	 * Wirft den Trank senkrecht nach unten. Der Blickwinkel wird nur für das Wurf-Paket
	 * geändert und sofort zurückgesetzt – die Kamera springt nicht.
	 */
	private void throwAtFeet() {
		LocalPlayer player = mc().player;
		float pitch = player.getXRot();
		player.setXRot(LOOK_DOWN);
		InteractUtil.use();
		player.setXRot(pitch);
		// Der Server kennt jetzt 90° – im nächsten Tick die echte Blickrichtung melden
		((LocalPlayerAccessor) player).salami$setXRotLast(LOOK_DOWN);
	}

	private void tickDrinking() {
		drinkTicks++;
		if (mc().player.isUsingItem()) {
			drinkStarted = true;
		}
		boolean done = drinkStarted && !mc().player.isUsingItem();
		if (!done && drinkTicks < DRINK_TIMEOUT && mc().screen == null) {
			mc().options.keyUse.setDown(true);
			return;
		}
		finishDrinking();
	}

	private void finishDrinking() {
		releaseUseKey();
		drinkTicks = -1;
		restoreSlot();
		cooldown = delay.intValue();
	}

	/** Benutzen-Taste wieder auf den echten Zustand setzen (falls du sie selbst hältst, bleibt sie gedrückt). */
	private void releaseUseKey() {
		KeyMapping use = mc().options.keyUse;
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(use);
		boolean physical;
		if (key.getType() == InputConstants.Type.MOUSE) {
			physical = Keys.isMouseDown(key.getValue());
		} else {
			physical = key.getValue() != Keys.NONE && Keys.isDown(key.getValue());
		}
		use.setDown(physical);
	}

	private void restoreSlot() {
		if (switchBack.isOn() && previousSlot != InventoryUtil.NOT_FOUND) {
			InventoryUtil.selectHotbar(previousSlot);
		}
		previousSlot = InventoryUtil.NOT_FOUND;
	}
}
