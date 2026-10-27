package dev.salamiclient.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.setting.StringListSetting;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.Keys;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Kits: fertige Inventar-Layouts für gängige PvP-Modi plus drei eigene.
 *
 * <p>"Anwenden" (Standard-Taste K) sortiert das Inventar Klick für Klick so um,
 * dass jedes Item an seinem Kit-Platz liegt. Standardmäßig nur bei offenem
 * Inventar – wie von Hand sortieren, nur schneller. Eigene Kits speichern das
 * aktuelle Inventar ("Aktuelles speichern").
 *
 * <p>Layout-Index: 0–8 Hotbar, 9–35 Inventar, 36 Offhand.
 */
public final class KitsModule extends Module {
	public enum Kit { CRYSTAL, ANCHOR, MACE, SPEAR, SWORD, AXE, SMP, NETH_POT, UHC, CUSTOM_1, CUSTOM_2, CUSTOM_3 }

	private static final int SIZE = 37;
	private static final int OFFHAND = 36;
	private static final String AIR = "minecraft:air";
	/** Obergrenze pro Anwenden: ein Kit braucht höchstens {@link #SIZE} Züge, ein kaputtes darf nicht endlos klicken. */
	private static final int MAX_MOVES = 64;

	private final ModeSetting<Kit> kit = add(new ModeSetting<>("kit", Kit.CRYSTAL));
	private final KeybindSetting applyKey = add(new KeybindSetting("apply_key", InputConstants.KEY_K));
	private final BoolSetting onlyInventory = add(new BoolSetting("only_inventory", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 5, 1));
	private final BoolSetting saveCurrent = add(new BoolSetting("save_current", false));
	private final StringListSetting custom1 = add(new StringListSetting("custom_1", List.of()));
	private final StringListSetting custom2 = add(new StringListSetting("custom_2", List.of()));
	private final StringListSetting custom3 = add(new StringListSetting("custom_3", List.of()));

	private boolean applying;
	private boolean keyWasDown;
	private int cooldown;
	private int moves;

	public KitsModule() {
		super("kits", Category.MISC);
		custom1.hide();
		custom2.hide();
		custom3.hide();
		saveCurrent.onChange(on -> {
			if (on) {
				saveCurrentInventory();
				saveCurrent.set(false);
			}
		});
	}

	@Override
	protected void onDisable() {
		applying = false;
	}

	/** Startet das Anwenden des gewählten Kits (auch für Tests/Befehle). */
	public void apply() {
		applying = true;
		moves = 0;
		cooldown = 0;
	}

	public boolean isApplying() {
		return applying;
	}

	/** Bricht ein laufendes Anwenden ab (Verbindung getrennt, Weltwechsel). */
	public void cancel() {
		applying = false;
		keyWasDown = false;
		moves = 0;
		cooldown = 0;
	}

	@Override
	public void onTick() {
		boolean down = applyKey.isBound()
			&& Keys.isDown(applyKey.key());
		// In Textfeldern (Chat, Schilder, Suche …) gehört die Taste dem Feld
		if (down && !keyWasDown && (mc().screen == null || !SalamiClient.isTyping(mc().screen))) {
			apply();
		}
		keyWasDown = down;

		if (!applying) {
			return;
		}
		if (moves >= MAX_MOVES) {
			applying = false;
			SalamiClient.LOG.warn("Kit {} aborted after {} moves (limit reached)", kit.get(), moves);
			Chat.warn("salami.kits.aborted", label(kit.get()), moves);
			return;
		}
		if (onlyInventory.isOn() && !(mc().screen instanceof InventoryScreen)) {
			return; // wartet, bis das Inventar offen ist
		}
		if (!InventoryUtil.canClickPlayerInventory()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (step()) {
			moves++;
			cooldown = delay.intValue();
		} else {
			applying = false;
			Chat.info("salami.kits.applied", label(kit.get()), moves);
		}
	}

	// ------------------------------------------------------------------ Anwenden

	/** Führt eine Verschiebung aus; {@code false}, wenn nichts mehr zu tun ist. */
	private boolean step() {
		List<Predicate<ItemStack>> layout = layout(kit.get());
		boolean[] locked = new boolean[SIZE];
		int[] order = targetOrder();
		for (int target : order) {
			Predicate<ItemStack> wanted = layout.get(target);
			if (wanted != null && wanted.test(item(target))) {
				locked[target] = true;
			}
		}
		for (int target : order) {
			Predicate<ItemStack> wanted = layout.get(target);
			if (wanted == null || locked[target]) {
				continue;
			}
			for (int source = 0; source < SIZE; source++) {
				if (source != target && !locked[source] && wanted.test(item(source))) {
					move(source, target);
					return true;
				}
			}
		}
		return false;
	}

	private static int[] targetOrder() {
		int[] order = new int[SIZE];
		int i = 0;
		for (int s = 0; s < 9; s++) order[i++] = s;
		order[i++] = OFFHAND;
		for (int s = 9; s < 36; s++) order[i++] = s;
		return order;
	}

	private ItemStack item(int index) {
		LocalPlayer player = mc().player;
		return index == OFFHAND ? player.getOffhandItem() : player.getInventory().getItem(index);
	}

	private static int menuSlot(int index) {
		return index == OFFHAND ? 45 : InventoryUtil.toMenuSlot(index);
	}

	private void move(int source, int target) {
		LocalPlayer player = mc().player;
		int container = player.inventoryMenu.containerId;
		if (target < 9) {
			mc().gameMode.handleInventoryMouseClick(container, menuSlot(source), target, ClickType.SWAP, player);
		} else if (target == OFFHAND) {
			mc().gameMode.handleInventoryMouseClick(container, menuSlot(source), InventoryUtil.OFFHAND_SWAP_BUTTON, ClickType.SWAP, player);
		} else if (source < 9) {
			mc().gameMode.handleInventoryMouseClick(container, menuSlot(target), source, ClickType.SWAP, player);
		} else if (source == OFFHAND) {
			mc().gameMode.handleInventoryMouseClick(container, menuSlot(target), InventoryUtil.OFFHAND_SWAP_BUTTON, ClickType.SWAP, player);
		} else {
			InventoryUtil.swapMenuSlots(menuSlot(source), menuSlot(target));
		}
	}

	// ------------------------------------------------------------------ Layouts

	private List<Predicate<ItemStack>> layout(Kit kit) {
		return switch (kit) {
			case CUSTOM_1 -> fromIds(custom1.get());
			case CUSTOM_2 -> fromIds(custom2.get());
			case CUSTOM_3 -> fromIds(custom3.get());
			default -> preset(kit);
		};
	}

	private static List<Predicate<ItemStack>> emptyLayout() {
		List<Predicate<ItemStack>> layout = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			layout.add(null);
		}
		return layout;
	}

	private static Predicate<ItemStack> is(Item item) {
		return stack -> stack.is(item);
	}

	private static final Predicate<ItemStack> SWORD = stack -> stack.is(ItemTags.SWORDS);
	private static final Predicate<ItemStack> AXE = stack -> stack.is(ItemTags.AXES);
	// Speere gibt es erst ab 1.21.11
	//? if >=1.21.11 {
	private static final Predicate<ItemStack> SPEAR = stack -> stack.is(ItemTags.SPEARS);
	//?} else
	/*private static final Predicate<ItemStack> SPEAR = stack -> false;*/
	private static final Predicate<ItemStack> TOTEM = is(Items.TOTEM_OF_UNDYING);

	@SafeVarargs
	private static List<Predicate<ItemStack>> hotbar(Predicate<ItemStack> offhand, Predicate<ItemStack>... slots) {
		List<Predicate<ItemStack>> layout = emptyLayout();
		for (int i = 0; i < slots.length && i < 9; i++) {
			layout.set(i, slots[i]);
		}
		layout.set(OFFHAND, offhand);
		return layout;
	}

	static List<Predicate<ItemStack>> preset(Kit kit) {
		return switch (kit) {
			case CRYSTAL -> hotbar(TOTEM, SWORD, is(Items.END_CRYSTAL), is(Items.OBSIDIAN), is(Items.RESPAWN_ANCHOR),
				is(Items.GLOWSTONE), is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), is(Items.EXPERIENCE_BOTTLE), TOTEM);
			case ANCHOR -> hotbar(TOTEM, SWORD, is(Items.RESPAWN_ANCHOR), is(Items.GLOWSTONE), is(Items.OBSIDIAN),
				is(Items.END_CRYSTAL), is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), is(Items.EXPERIENCE_BOTTLE), TOTEM);
			case MACE -> hotbar(TOTEM, SWORD, is(Items.MACE), is(Items.WIND_CHARGE), is(Items.ENDER_PEARL),
				is(Items.GOLDEN_APPLE), is(Items.FIREWORK_ROCKET), is(Items.COBWEB), is(Items.EXPERIENCE_BOTTLE), TOTEM);
			case SPEAR -> hotbar(TOTEM, SPEAR, SWORD, is(Items.MACE), is(Items.WIND_CHARGE),
				is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), is(Items.COBWEB), is(Items.EXPERIENCE_BOTTLE), TOTEM);
			case SWORD -> hotbar(TOTEM, SWORD, AXE, is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL),
				is(Items.COBWEB), is(Items.WATER_BUCKET), is(Items.LAVA_BUCKET), is(Items.EXPERIENCE_BOTTLE), TOTEM);
			case AXE -> hotbar(is(Items.SHIELD), AXE, SWORD, is(Items.CROSSBOW), is(Items.BOW),
				is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), is(Items.COBWEB), is(Items.WATER_BUCKET), is(Items.ARROW));
			case SMP -> hotbar(TOTEM, SWORD, AXE, is(Items.END_CRYSTAL), is(Items.OBSIDIAN),
				is(Items.RESPAWN_ANCHOR), is(Items.GLOWSTONE), is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), TOTEM);
			case NETH_POT -> hotbar(TOTEM, SWORD, is(Items.SPLASH_POTION), is(Items.SPLASH_POTION), is(Items.SPLASH_POTION),
				is(Items.GOLDEN_APPLE), is(Items.ENDER_PEARL), is(Items.EXPERIENCE_BOTTLE), is(Items.COBWEB), TOTEM);
			case UHC -> hotbar(is(Items.SHIELD), SWORD, AXE, is(Items.BOW), is(Items.LAVA_BUCKET),
				is(Items.WATER_BUCKET), is(Items.COBWEB), is(Items.GOLDEN_APPLE), is(Items.CROSSBOW), is(Items.COBBLESTONE));
			default -> hotbar(null);
		};
	}

	private static List<Predicate<ItemStack>> fromIds(List<String> ids) {
		List<Predicate<ItemStack>> layout = emptyLayout();
		for (int i = 0; i < Math.min(SIZE, ids.size()); i++) {
			String id = ids.get(i);
			if (id.equals(AIR)) {
				continue;
			}
			Identifier key = Identifier.tryParse(id);
			Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
			if (item != Items.AIR) {
				layout.set(i, is(item));
			}
		}
		return layout;
	}

	// ------------------------------------------------------------------ Speichern

	private void saveCurrentInventory() {
		if (mc().player == null) {
			return;
		}
		Kit target = kit.get().name().startsWith("CUSTOM") ? kit.get() : Kit.CUSTOM_1;
		List<String> ids = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = item(i);
			ids.add(stack.isEmpty() ? AIR : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}
		switch (target) {
			case CUSTOM_2 -> custom2.set(ids);
			case CUSTOM_3 -> custom3.set(ids);
			default -> custom1.set(ids);
		}
		kit.set(target);
		Chat.info("salami.kits.saved", label(target));
	}

	private static String label(Kit kit) {
		return net.minecraft.client.resources.language.I18n.get(
			"salami.module.kits.setting.kit." + kit.name().toLowerCase(Locale.ROOT));
	}
}
