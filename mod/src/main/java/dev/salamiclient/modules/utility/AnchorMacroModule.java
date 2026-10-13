package dev.salamiclient.modules.utility;

import dev.salamiclient.mixin.MinecraftAccessor;
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
 * <p>Ein Rechtsklick auf den Anker (bzw. bei {@code AUTO} das Anvisieren) startet die
 * Sequenz. Danach bleibt das Makro an diesem Anker, bis er explodiert ist – auch wenn
 * der Glowstone-Schutz jetzt im Fadenkreuz steht oder die Taste losgelassen wurde.
 * Jeder Schritt passiert in einem eigenen Tick (plus einstellbare Verzögerung).
 */
public final class AnchorMacroModule extends Module {
	public enum Mode { CHARGE, CHARGE_EXPLODE }
	public enum Trigger { AUTO, HOLD_USE }

	/** Abbruch, falls der Anker nach so vielen Ticks noch steht (z. B. Server lehnt Klicks ab). */
	private static final int MAX_SEQUENCE_TICKS = 40;
	/** Hält Vanillas Dauer-Rechtsklick an, solange die Sequenz läuft. */
	private static final int SUPPRESS_RIGHT_CLICK_TICKS = 4;

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.CHARGE_EXPLODE));
	private final ModeSetting<Trigger> trigger = add(new ModeSetting<>("trigger", Trigger.HOLD_USE));
	private final BoolSetting safeAnchor = add(new BoolSetting("safe_anchor", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 10, 1));
	/** 0 = automatisch (bevorzugt Totem), sonst fester Hotbar-Slot 1–9 zum Zünden. */
	private final NumberSetting explodeSlot = add(new NumberSetting("explode_slot", 0, 0, 9, 1));
	private final BoolSetting switchBack = add(new BoolSetting("switch_back", true));

	private int cooldown;
	private BlockPos warnedAbout;
	private boolean warnedSneaking;

	/** Anker, an dem die Sequenz gerade läuft ({@code null} = keine). */
	private BlockPos target;
	/** Klickpunkt auf dem Anker vom Start der Sequenz. */
	private BlockHitResult targetHit;
	private int sequenceTicks;
	private int previousSlot;
	private boolean detonated;

	public AnchorMacroModule() {
		super("anchor_macro", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
		warnedAbout = null;
		target = null;
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	@Override
	public void onTick() {
		if (mc().screen != null) {
			target = null;
			return;
		}
		if (target == null && !start()) {
			return;
		}
		((MinecraftAccessor) mc()).salami$setRightClickDelay(SUPPRESS_RIGHT_CLICK_TICKS);
		if (!stillRunning()) {
			finish();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		// Schleichen mit Item in der Hand: Minecraft benutzt dann das Item statt des Ankers
		// (Glowstone würde als Block gesetzt) – abwarten, bis nicht mehr geschlichen wird.
		if (mc().player.isSecondaryUseActive()) {
			if (!warnedSneaking) {
				warnedSneaking = true;
				Chat.warn("salami.anchor.sneaking");
			}
			return;
		}
		step();
	}

	/** Startet eine Sequenz am anvisierten Anker, wenn der Auslöser aktiv ist. */
	private boolean start() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (trigger.get() == Trigger.HOLD_USE && !mc().options.keyUse.isDown()) {
			return false;
		}
		if (!(mc().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockState state = mc().level.getBlockState(hit.getBlockPos());
		if (!state.is(Blocks.RESPAWN_ANCHOR)) {
			return false;
		}
		if (mode.get() == Mode.CHARGE && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
			return false; // nichts zu tun
		}
		target = hit.getBlockPos();
		targetHit = hit;
		sequenceTicks = 0;
		previousSlot = InventoryUtil.selectedHotbar();
		detonated = false;
		warnedSneaking = false;
		return true;
	}

	private boolean stillRunning() {
		return mc().level.getBlockState(target).is(Blocks.RESPAWN_ANCHOR)
			&& mc().player.isWithinBlockInteractionRange(target, 0)
			&& sequenceTicks++ < MAX_SEQUENCE_TICKS;
	}

	private void finish() {
		if (detonated && switchBack.isOn() && previousSlot != InventoryUtil.selectedHotbar()) {
			InventoryUtil.selectHotbar(previousSlot);
		}
		target = null;
	}

	private void step() {
		BlockState state = mc().level.getBlockState(target);
		BlockHitResult hit = hitOnTarget();
		if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
			if (!charge(hit)) {
				target = null; // kein Glowstone in der Hotbar
			}
			return;
		}
		if (mode.get() == Mode.CHARGE) {
			target = null;
			return;
		}
		if (safeAnchor.isOn()) {
			Shield shield = ensureShield(target);
			if (shield == Shield.IMPOSSIBLE) {
				target = null;
				return;
			}
			if (shield == Shield.PLACING) {
				return; // im nächsten Tick prüfen, ob der Schutz steht
			}
		}
		if (!explode(hit)) {
			target = null;
		}
	}

	/** Der aktuelle Klickpunkt auf dem Anker – oder der vom Start, falls jetzt etwas anderes im Fadenkreuz ist. */
	private BlockHitResult hitOnTarget() {
		if (mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
			return hit;
		}
		return targetHit;
	}

	private boolean charge(BlockHitResult hit) {
		int glowstone = InventoryUtil.findHotbar(stack -> stack.is(Items.GLOWSTONE));
		if (glowstone == InventoryUtil.NOT_FOUND) {
			return false;
		}
		InventoryUtil.selectHotbar(glowstone);
		if (InteractUtil.useOn(hit)) {
			cooldown = delay.intValue();
		}
		return true;
	}

	private boolean explode(BlockHitResult hit) {
		// Mit Glowstone in der Offhand würde der Klick den Anker weiter laden statt zünden
		if (mc().player.getOffhandItem().is(Items.GLOWSTONE)) {
			return false;
		}
		int slot = explodeSlot();
		if (slot == InventoryUtil.NOT_FOUND) {
			return false;
		}
		InventoryUtil.selectHotbar(slot);
		if (InteractUtil.useOn(hit)) {
			detonated = true;
			cooldown = delay.intValue();
		}
		return true;
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

	private enum Shield { READY, PLACING, IMPOSSIBLE }

	/** Sorgt für einen Block zwischen Spieler und Anker. */
	private Shield ensureShield(BlockPos anchor) {
		LocalPlayer player = mc().player;
		ClientLevel level = mc().level;
		Vec3 center = Vec3.atCenterOf(anchor);
		Direction towardsPlayer = Direction.getApproximateNearest(player.getX() - center.x, 0, player.getZ() - center.z);
		BlockPos shield = anchor.relative(towardsPlayer);
		if (!level.getBlockState(shield).canBeReplaced()) {
			return Shield.READY; // da steht schon ein Block
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
			return Shield.IMPOSSIBLE;
		}
		InventoryUtil.selectHotbar(glowstone);
		BlockHitResult floorHit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		if (InteractUtil.useOn(floorHit)) {
			cooldown = delay.intValue();
		}
		return Shield.PLACING;
	}
}
