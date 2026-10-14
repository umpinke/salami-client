package dev.salamiclient.modules.utility;

import dev.salamiclient.mixin.MinecraftAccessor;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.CrystalUtil;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crystal Macro: Solange du mit einem End-Kristall in der Haupthand die Benutzen-Taste
 * hältst, setzt das Makro Kristalle auf das anvisierte Obsidian/Grundgestein und
 * zerstört anvisierte Kristalle.
 *
 * <ul>
 *   <li>Zerstören mit einer zufälligen Rate zwischen Min- und Max-CPS.</li>
 *   <li><b>Loot-Schutz:</b> liegen Elytren, Schwerter oder Rüstung in der Nähe, wird
 *       nichts gesetzt oder gesprengt.</li>
 *   <li><b>Stop on Kill:</b> stirbt ein Spieler in der Nähe, pausiert das Makro kurz.</li>
 *   <li><b>Obsidian setzen:</b> zielst du auf einen anderen Block, setzt das Makro erst
 *       Obsidian aus der Hotbar und gleich danach den Kristall darauf.</li>
 * </ul>
 */
public final class CrystalMacroModule extends Module {
	/** Umkreis, in dem herumliegender Loot das Sprengen verhindert. */
	private static final double LOOT_RADIUS = 10;
	/** Hält Vanillas Dauer-Rechtsklick nach einem gesetzten Kristall an. */
	private static final int SUPPRESS_RIGHT_CLICK_TICKS = 4;

	private final NumberSetting placeDelay = add(new NumberSetting("place_delay", 0, 0, 20, 1));
	private final NumberSetting minCps = add(new NumberSetting("min_cps", 8, 1, 20, 1));
	private final NumberSetting maxCps = add(new NumberSetting("max_cps", 12, 1, 20, 1));
	private final BoolSetting lootProtect = add(new BoolSetting("loot_protect", true));
	private final BoolSetting stopOnKill = add(new BoolSetting("stop_on_kill", true));
	private final NumberSetting stopTime = add(new NumberSetting("stop_time", 3, 0, 15, 0.5));
	private final NumberSetting stopRange = add(new NumberSetting("stop_range", 18, 4, 48, 1));
	private final BoolSetting placeObsidian = add(new BoolSetting("place_obsidian", false));
	private final NumberSetting obsidianDelay = add(new NumberSetting("obsidian_delay", 0, 0, 20, 1));
	private final NumberSetting obsidianCooldown = add(new NumberSetting("obsidian_cooldown", 1, 0, 10, 0.5));

	private int placeCooldown;
	private int breakCooldown;
	private int nextBreakDelay = 1;
	private int obsidianWait;
	private int obsidianCooldownTicks;
	/** Klick, mit dem gleich Obsidian gesetzt wird ({@code null} = kein Obsidian-Schritt offen). */
	private BlockHitResult pendingObsidian;
	private int pauseTicks;
	/** Spieler (Entity-IDs), deren Tod schon eine Pause ausgelöst hat. */
	private final Set<Integer> knownDead = new HashSet<>();

	public CrystalMacroModule() {
		super("crystal_macro", Category.UTILITY);
		stopTime.showIf(stopOnKill::isOn);
		stopRange.showIf(stopOnKill::isOn);
		obsidianDelay.showIf(placeObsidian::isOn);
		obsidianCooldown.showIf(placeObsidian::isOn);
	}

	@Override
	protected void onEnable() {
		reset();
	}

	/** Läuft das Makro gerade (Benutzen-Taste gehalten, Kristall in der Hand)? Auto D-Tap hält sich dann raus. */
	public boolean isRunning() {
		return isEnabled()
			&& mc().screen == null
			&& mc().options.keyUse.isDown()
			&& mc().player != null
			&& mc().player.getMainHandItem().is(Items.END_CRYSTAL);
	}

	private void reset() {
		placeCooldown = 0;
		breakCooldown = 0;
		obsidianWait = 0;
		obsidianCooldownTicks = 0;
		pendingObsidian = null;
		pauseTicks = 0;
		knownDead.clear();
	}

	@Override
	public void onTick() {
		if (mc().screen != null) {
			reset();
			return;
		}
		if (placeCooldown > 0) placeCooldown--;
		if (breakCooldown > 0) breakCooldown--;
		if (obsidianWait > 0) obsidianWait--;
		if (obsidianCooldownTicks > 0) obsidianCooldownTicks--;
		if (pauseTicks > 0) pauseTicks--;
		if (stopOnKill.isOn()) {
			checkKills();
			if (pauseTicks > 0) {
				return;
			}
		}

		LocalPlayer player = mc().player;
		if (player.isUsingItem()) {
			return;
		}
		if (!mc().options.keyUse.isDown()) {
			pendingObsidian = null;
			obsidianCooldownTicks = 0;
			return;
		}
		if (pendingObsidian != null) {
			if (obsidianWait == 0) {
				finishObsidian();
			}
			return;
		}
		if (!player.getMainHandItem().is(Items.END_CRYSTAL)) {
			return;
		}
		HitResult hit = mc().hitResult;
		if (hit instanceof EntityHitResult entityHit) {
			breakCrystal(entityHit.getEntity());
		} else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			place(blockHit);
		}
	}

	/** Stop on Kill: ein neu gestorbener Spieler in Reichweite startet die Pause. */
	private void checkKills() {
		LocalPlayer self = mc().player;
		AABB area = self.getBoundingBox().inflate(stopRange.get());
		for (Player other : mc().level.getEntitiesOfClass(Player.class, area, p -> p != self && p.isDeadOrDying())) {
			if (knownDead.add(other.getId())) {
				pauseTicks = Math.max(pauseTicks, (int) Math.round(stopTime.get() * 20));
			}
		}
		// Nach dem Respawn hat der Spieler dieselbe ID – dann darf sein nächster Tod wieder zählen
		knownDead.removeIf(id -> !(mc().level.getEntity(id) instanceof LivingEntity living) || !living.isDeadOrDying());
	}

	private void place(BlockHitResult hit) {
		if (placeCooldown > 0) {
			return;
		}
		BlockPos base = hit.getBlockPos();
		if (!CrystalUtil.isCrystalBase(mc().level.getBlockState(base))) {
			if (placeObsidian.isOn() && obsidianCooldownTicks == 0) {
				startObsidian(hit);
			}
			return;
		}
		if (CrystalUtil.canPlaceOn(base) && !lootNearby(base.above())) {
			placeCrystal(hit);
		}
	}

	private void placeCrystal(BlockHitResult hit) {
		if (InteractUtil.useOn(hit)) {
			((MinecraftAccessor) mc()).salami$setRightClickDelay(SUPPRESS_RIGHT_CLICK_TICKS);
		}
		placeCooldown = placeDelay.intValue();
	}

	/** Wechselt auf Obsidian; gesetzt wird nach der eingestellten Wartezeit. */
	private void startObsidian(BlockHitResult hit) {
		int slot = InventoryUtil.findHotbar(stack -> stack.is(Items.OBSIDIAN));
		if (slot == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.selectHotbar(slot);
		pendingObsidian = hit;
		obsidianWait = obsidianDelay.intValue();
	}

	/** Setzt das Obsidian, wechselt zurück auf Kristalle und setzt gleich einen darauf. */
	private void finishObsidian() {
		BlockHitResult hit = pendingObsidian;
		pendingObsidian = null;
		obsidianCooldownTicks = (int) Math.round(obsidianCooldown.get() * 20);
		// Slot neu suchen: ein anderes Modul kann inzwischen gewechselt haben
		int obsidian = InventoryUtil.findHotbar(stack -> stack.is(Items.OBSIDIAN));
		int crystal = InventoryUtil.findHotbar(stack -> stack.is(Items.END_CRYSTAL));
		if (obsidian == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.selectHotbar(obsidian);
		InteractUtil.useOn(hit);
		if (crystal == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.selectHotbar(crystal);
		// Das neue Obsidian steht auf dem Client schon (Vorhersage) – der Server setzt es vor dem Kristall
		BlockPos base = hit.getBlockPos().relative(hit.getDirection());
		if (CrystalUtil.canPlaceOn(base) && !lootNearby(base.above())) {
			placeCrystal(CrystalUtil.topOf(base));
		}
	}

	private void breakCrystal(Entity entity) {
		if (breakCooldown > 0 || !(entity instanceof EndCrystal crystal) || !crystal.isAlive()) {
			return;
		}
		if (lootNearby(crystal.blockPosition())) {
			return;
		}
		InteractUtil.attack(crystal);
		breakCooldown = nextBreakDelay;
		rollBreakDelay();
	}

	/** Nächste Pause zwischen zwei Schlägen: zufällige Rate zwischen Min- und Max-CPS. */
	private void rollBreakDelay() {
		double min = Math.min(minCps.get(), maxCps.get());
		double max = Math.max(minCps.get(), maxCps.get());
		double cps = min + (max - min) * ThreadLocalRandom.current().nextDouble();
		nextBreakDelay = Math.max(1, (int) (20 / cps));
	}

	private boolean lootNearby(BlockPos pos) {
		if (!lootProtect.isOn()) {
			return false;
		}
		AABB area = new AABB(pos).inflate(LOOT_RADIUS);
		return !mc().level.getEntitiesOfClass(ItemEntity.class, area, item -> isValuable(item.getItem())).isEmpty();
	}

	private static boolean isValuable(ItemStack stack) {
		return stack.is(Items.ELYTRA)
			|| stack.is(ItemTags.SWORDS)
			|| stack.is(ItemTags.HEAD_ARMOR)
			|| stack.is(ItemTags.CHEST_ARMOR)
			|| stack.is(ItemTags.LEG_ARMOR)
			|| stack.is(ItemTags.FOOT_ARMOR);
	}
}
