package dev.salamiclient.util;

/**
 * Wechselt kurzzeitig den Hotbar-Slot und stellt den ursprünglichen nach einigen
 * Ticks wieder her. Mehrere Wechsel hintereinander merken sich nur den ersten
 * Ursprungs-Slot.
 */
public final class SlotSwapper {
	private int originalSlot = InventoryUtil.NOT_FOUND;
	private int restoreIn;

	/**
	 * @param restoreAfterTicks 0 = nicht zurückwechseln
	 */
	public void swapTo(int hotbarSlot, int restoreAfterTicks) {
		int current = InventoryUtil.selectedHotbar();
		if (current == hotbarSlot) {
			return;
		}
		if (restoreAfterTicks > 0 && originalSlot == InventoryUtil.NOT_FOUND) {
			originalSlot = current;
		}
		restoreIn = restoreAfterTicks;
		InventoryUtil.selectHotbar(hotbarSlot);
	}

	/** Pro Tick aufrufen. */
	public void tick() {
		if (originalSlot == InventoryUtil.NOT_FOUND || restoreIn <= 0) {
			return;
		}
		if (--restoreIn == 0) {
			InventoryUtil.selectHotbar(originalSlot);
			originalSlot = InventoryUtil.NOT_FOUND;
		}
	}

	public void reset() {
		originalSlot = InventoryUtil.NOT_FOUND;
		restoreIn = 0;
	}
}
