package dev.salamiclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Inventar-Helfer für Utility-Module.
 *
 * <p>Slot-Nummern: {@code Inventory}-Index 0–8 = Hotbar, 9–35 = Hauptinventar.
 * Klicks laufen über das Spieler-Inventar-Menü ({@code InventoryMenu}), dort gilt:
 * Hotbar 0–8 → Menü-Slot 36–44, Hauptinventar 9–35 → gleich, Offhand = 45.
 */
public final class InventoryUtil {
	/** Button-Wert für {@link ClickType#SWAP}, der mit der Offhand tauscht (wie Taste F im Inventar). */
	public static final int OFFHAND_SWAP_BUTTON = 40;
	public static final int NOT_FOUND = -1;

	private InventoryUtil() {}

	private static LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	/** Inventar-Index (0–35) → Slot-Nummer im InventoryMenu. */
	public static int toMenuSlot(int inventoryIndex) {
		return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
	}

	/** Erster Inventar-Index in [from, to] mit passendem Item, sonst {@link #NOT_FOUND}. */
	public static int find(Predicate<ItemStack> matcher, int from, int to) {
		Inventory inventory = player().getInventory();
		for (int i = from; i <= to; i++) {
			if (matcher.test(inventory.getItem(i))) {
				return i;
			}
		}
		return NOT_FOUND;
	}

	public static int find(Item item, int from, int to) {
		return find(stack -> stack.is(item), from, to);
	}

	/** Sucht zuerst im Hauptinventar, dann in der Hotbar (Hotbar-Items bleiben so griffbereit). */
	public static int findPreferInventory(Item item) {
		int slot = find(item, 9, 35);
		return slot != NOT_FOUND ? slot : find(item, 0, 8);
	}

	/** Hotbar-Index (0–8) mit passendem Item, sonst {@link #NOT_FOUND}. */
	public static int findHotbar(Predicate<ItemStack> matcher) {
		return find(matcher, 0, 8);
	}

	/**
	 * Klicks im Spieler-Inventar sind nur gültig, solange kein anderes Menü
	 * (Truhe, Ofen …) offen ist.
	 */
	public static boolean canClickPlayerInventory() {
		LocalPlayer player = player();
		return player != null && player.containerMenu == player.inventoryMenu;
	}

	/** Tauscht den Inventar-Slot mit der Offhand. */
	public static void swapWithOffhand(int inventoryIndex) {
		click(toMenuSlot(inventoryIndex), OFFHAND_SWAP_BUTTON);
	}

	/** Tauscht den Inventar-Slot mit einem Hotbar-Slot (0–8). */
	public static void swapWithHotbar(int inventoryIndex, int hotbarIndex) {
		click(toMenuSlot(inventoryIndex), hotbarIndex);
	}

	/** Tauscht einen Slot des Menüs (z. B. den gehoverten) mit der Offhand. */
	public static void swapMenuSlotWithOffhand(int menuSlot) {
		click(menuSlot, OFFHAND_SWAP_BUTTON);
	}

	/** Tauscht einen Slot des Menüs mit einem Hotbar-Slot (0–8). */
	public static void swapMenuSlotWithHotbar(int menuSlot, int hotbarIndex) {
		click(menuSlot, hotbarIndex);
	}

	/** Menü-Slots der Rüstung im InventoryMenu. */
	public static final int ARMOR_HEAD = 5;
	public static final int ARMOR_CHEST = 6;
	public static final int ARMOR_LEGS = 7;
	public static final int ARMOR_FEET = 8;

	/**
	 * Tauscht ein Inventar-Item mit einem Rüstungs-Slot (z. B. Elytra ↔ Brustpanzer).
	 * Aus der Hotbar mit einem SWAP-Klick, sonst mit drei Pickup-Klicks.
	 */
	public static void swapWithArmor(int inventoryIndex, int armorMenuSlot) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.gameMode == null || player == null) {
			return;
		}
		int container = player.inventoryMenu.containerId;
		if (inventoryIndex < 9) {
			mc.gameMode.handleInventoryMouseClick(container, armorMenuSlot, inventoryIndex, ClickType.SWAP, player);
		} else {
			int source = toMenuSlot(inventoryIndex);
			mc.gameMode.handleInventoryMouseClick(container, source, 0, ClickType.PICKUP, player);
			mc.gameMode.handleInventoryMouseClick(container, armorMenuSlot, 0, ClickType.PICKUP, player);
			mc.gameMode.handleInventoryMouseClick(container, source, 0, ClickType.PICKUP, player);
		}
	}

	/** Tauscht zwei beliebige Menü-Slots (3 Pickup-Klicks; funktioniert auch mit leeren Slots). */
	public static void swapMenuSlots(int menuSlotA, int menuSlotB) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.gameMode == null || player == null) {
			return;
		}
		int container = player.inventoryMenu.containerId;
		mc.gameMode.handleInventoryMouseClick(container, menuSlotA, 0, ClickType.PICKUP, player);
		mc.gameMode.handleInventoryMouseClick(container, menuSlotB, 0, ClickType.PICKUP, player);
		mc.gameMode.handleInventoryMouseClick(container, menuSlotA, 0, ClickType.PICKUP, player);
	}

	public static void selectHotbar(int hotbarIndex) {
		player().getInventory().setSelectedSlot(hotbarIndex);
	}

	public static int selectedHotbar() {
		return player().getInventory().getSelectedSlot();
	}

	private static void click(int menuSlot, int button) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.gameMode == null || player == null) {
			return;
		}
		mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, button, ClickType.SWAP, player);
	}

	/** Leben inkl. Absorption. */
	public static float totalHealth() {
		LocalPlayer player = player();
		return player.getHealth() + player.getAbsorptionAmount();
	}
}
