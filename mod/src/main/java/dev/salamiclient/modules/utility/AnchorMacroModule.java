package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor-Makros für Seelenanker (Respawn Anchor) außerhalb des Nethers.
 *
 * <ul>
 *   <li>{@code CHARGE} – lädt den anvisierten Anker mit Glowstone.</li>
 *   <li>{@code CHARGE_EXPLODE} – lädt und zündet ihn mit einem anderen Item.</li>
 * </ul>
 * <b>Safe Anchor:</b> setzt vor dem Zünden einen Glowstone-Block zwischen dich und den
 * Anker – der Block fängt den Großteil der Explosion ab. Ist dafür kein Platz, wird
 * nicht gezündet.
 *
 * <p>Jeder Schritt passiert in einem eigenen Tick (plus einstellbare Verzögerung).
 */
public final class AnchorMacroModule extends Module {
	public enum Mode { CHARGE, CHARGE_EXPLODE }
	public enum Trigger { AUTO, HOLD_USE }

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.CHARGE_EXPLODE));
	private final ModeSetting<Trigger> trigger = add(new ModeSetting<>("trigger", Trigger.HOLD_USE));
	private final BoolSetting safeAnchor = add(new BoolSetting("safe_anchor", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 10, 1));
	/** 0 = automatisch (bevorzugt Totem), sonst fester Hotbar-Slot 1–9 zum Zünden. */
	private final NumberSetting explodeSlot = add(new NumberSetting("explode_slot", 0, 0, 9, 1));

	private int cooldown;
	private BlockPos warnedAbout;

	public AnchorMacroModule() {
		super("anchor_macro", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
		warnedAbout = null;
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (mc().screen != null) {
			return;
		}
		if (trigger.get() == Trigger.HOLD_USE && !mc().options.keyUse.isDown()) {
			return;
		}
		if (!(mc().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos anchor = hit.getBlockPos();
		BlockState state = mc().level.getBlockState(anchor);
		if (!state.is(Blocks.RESPAWN_ANCHOR)) {
			return;
		}

		if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
			charge(hit);
			return;
		}
		if (mode.get() == Mode.CHARGE) {
			return;
		}
		if (safeAnchor.isOn() && !ensureShield(anchor)) {
			return;
		}
		explode(hit);
	}

	private void charge(BlockHitResult hit) {
		int glowstone = InventoryUtil.findHotbar(stack -> stack.is(Items.GLOWSTONE));
		if (glowstone == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.selectHotbar(glowstone);
		if (InteractUtil.useOn(hit)) {
			cooldown = delay.intValue();
		}
	}

	private void explode(BlockHitResult hit) {
		// Mit Glowstone in der Offhand würde der Klick den Anker weiter laden statt zünden
		if (mc().player.getOffhandItem().is(Items.GLOWSTONE)) {
			return;
		}
		int slot = explodeSlot();
		if (slot == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.selectHotbar(slot);
		if (InteractUtil.useOn(hit)) {
			cooldown = delay.intValue();
		}
	}

	private int explodeSlot() {
		int fixed = explodeSlot.intValue();
		if (fixed > 0) {
			ItemStack stack = mc().player.getInventory().getItem(fixed - 1);
			return stack.is(Items.GLOWSTONE) ? InventoryUtil.NOT_FOUND : fixed - 1;
		}
		int totem = InventoryUtil.findHotbar(stack -> stack.is(Items.TOTEM_OF_UNDYING));
		return totem != InventoryUtil.NOT_FOUND ? totem : InventoryUtil.findHotbar(stack -> !stack.is(Items.GLOWSTONE));
	}

	/**
	 * Sorgt für einen Block zwischen Spieler und Anker.
	 *
	 * @return {@code true}, wenn der Schutz steht und gezündet werden darf
	 */
	private boolean ensureShield(BlockPos anchor) {
		LocalPlayer player = mc().player;
		ClientLevel level = mc().level;
		Vec3 center = Vec3.atCenterOf(anchor);
		Direction towardsPlayer = Direction.getApproximateNearest(player.getX() - center.x, 0, player.getZ() - center.z);
		BlockPos shield = anchor.relative(towardsPlayer);
		if (!level.getBlockState(shield).canBeReplaced()) {
			return true; // da steht schon ein Block
		}
		BlockPos floor = shield.below();
		int glowstone = InventoryUtil.findHotbar(stack -> stack.is(Items.GLOWSTONE));
		boolean placeable = glowstone != InventoryUtil.NOT_FOUND
			&& level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
			&& !player.getBoundingBox().intersects(new AABB(shield));
		if (!placeable) {
			if (!anchor.equals(warnedAbout)) {
				warnedAbout = anchor;
				Chat.warn("salami.anchor.no_shield");
			}
			return false;
		}
		InventoryUtil.selectHotbar(glowstone);
		BlockHitResult floorHit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		if (InteractUtil.useOn(floorHit)) {
			cooldown = delay.intValue();
		}
		return false; // im nächsten Tick prüfen, ob der Schutz steht
	}
}
