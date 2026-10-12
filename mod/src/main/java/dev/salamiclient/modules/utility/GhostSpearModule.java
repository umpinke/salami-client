package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghost Spear: führt die Tastenfolge Droppen → Offhand-Tausch → Slotwechsel
 * automatisch und in exaktem Timing aus (Speer in der Haupthand nötig).
 *
 * <p>Einmal-Makro: Modul einschalten (am besten per Taste) → Ablauf läuft →
 * Modul schaltet sich selbst wieder aus. Jeder Schritt entspricht genau dem,
 * was die jeweilige Vanilla-Taste auslösen würde.
 */
public final class GhostSpearModule extends Module {
	public enum Sequence { DROP_SWAP_SLOT, SWAP_DROP_SLOT, SWAP_SLOT }
	private enum Step { DROP, SWAP, SLOT }

	private final ModeSetting<Sequence> sequence = add(new ModeSetting<>("sequence", Sequence.DROP_SWAP_SLOT));
	/** 0 = nächster Slot, sonst fester Hotbar-Slot 1–9. */
	private final NumberSetting targetSlot = add(new NumberSetting("target_slot", 0, 0, 9, 1));
	/** Ticks zwischen den Schritten; 0 = alles im selben Tick (in dieser Reihenfolge). */
	private final NumberSetting delay = add(new NumberSetting("delay", 0, 0, 5, 1));

	private final List<Step> steps = new ArrayList<>(3);
	private int stepIndex;
	private int wait;

	public GhostSpearModule() {
		super("ghost_spear", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		steps.clear();
		stepIndex = 0;
		wait = 0;
		LocalPlayer player = mc().player;
		if (player == null || !player.getMainHandItem().is(ItemTags.SPEARS)) {
			Chat.warn("salami.ghost_spear.no_spear");
			return; // leere Schrittliste -> nächster Tick schaltet ab
		}
		switch (sequence.get()) {
			case DROP_SWAP_SLOT -> steps.addAll(List.of(Step.DROP, Step.SWAP, Step.SLOT));
			case SWAP_DROP_SLOT -> steps.addAll(List.of(Step.SWAP, Step.DROP, Step.SLOT));
			case SWAP_SLOT -> steps.addAll(List.of(Step.SWAP, Step.SLOT));
		}
	}

	@Override
	public void onTick() {
		if (wait > 0 && --wait > 0) {
			return;
		}
		while (stepIndex < steps.size()) {
			run(steps.get(stepIndex++));
			if (delay.intValue() > 0 && stepIndex < steps.size()) {
				wait = delay.intValue(); // genau "delay" Ticks bis zum nächsten Schritt
				return;
			}
		}
		disable();
	}

	private void run(Step step) {
		LocalPlayer player = mc().player;
		switch (step) {
			// wie Taste Q (Drop-Taste)
			case DROP -> {
				if (!player.isSpectator() && player.drop(false)) {
					player.swing(InteractionHand.MAIN_HAND);
				}
			}
			// wie Taste F (Offhand tauschen)
			case SWAP -> {
				if (!player.isSpectator()) {
					player.connection.send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
				}
			}
			// wie Zifferntaste / Mausrad
			case SLOT -> {
				int fixed = targetSlot.intValue();
				int target = fixed > 0 ? fixed - 1 : (InventoryUtil.selectedHotbar() + 1) % 9;
				InventoryUtil.selectHotbar(target);
			}
		}
	}
}
