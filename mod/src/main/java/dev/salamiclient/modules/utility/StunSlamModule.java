package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.KeyTrigger;
import dev.salamiclient.util.TargetUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Stun Slam (Sattela-Kombo): In der Luft erst mit der Axt zuschlagen – das schaltet einen
 * Schild aus – und kurz danach mit dem Streitkolben nachsetzen. Danach geht es zurück zum
 * vorherigen Slot. Axt und Streitkolben müssen in der Hotbar liegen.
 *
 * <p>Auslöser (nur in der Luft): dein eigener Schlag, die Auslöse-Taste oder – mit
 * „Automatisch“ – ein Spieler in Reichweite.
 */
public final class StunSlamModule extends Module implements AttackListener {
	/** Mindestabstand zwischen zwei automatischen Kombos (Ticks). */
	private static final int AUTO_INTERVAL = 8;
	private static final TargetUtil.Filter PLAYERS = new TargetUtil.Filter(true, false, true);

	private final BoolSetting auto = add(new BoolSetting("auto", true));
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", KeybindSetting.NONE));
	/** Ticks zwischen Axt und Streitkolben (0 = im selben Tick). */
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 6, 1));
	private final NumberSetting reach = add(new NumberSetting("reach", 4.2, 2, 6, 0.1));
	private final BoolSetting shieldOnly = add(new BoolSetting("shield_only", false));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);
	/** Zählt die eigenen Ticks; Zeitpunkte der Kombo beziehen sich darauf. */
	private int ticks;
	/** Tick des Streitkolben-Schlags; -1 = keine Kombo aktiv. */
	private int slamAt = -1;
	private int sinceCombo = AUTO_INTERVAL;
	private Player target;
	private int previousSlot = InventoryUtil.NOT_FOUND;
	/** Eigene Schläge lösen keine neue Kombo aus. */
	private boolean ownAttack;

	public StunSlamModule() {
		super("stun_slam", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		finish();
	}

	@Override
	public void beforeAttack(Entity entity) {
		// Dein eigener Schlag in der Luft wird zum Axt-Schlag der Kombo
		if (ownAttack || slamAt >= 0 || mc().player.onGround() || !(entity instanceof Player player) || !PLAYERS.accepts(player)) {
			return;
		}
		if (shieldOnly.isOn() && !player.isBlocking()) {
			return;
		}
		// Angriffe kommen vor dem Modul-Tick: der Schlag zählt schon zum nächsten onTick
		begin(player, ticks + 1);
	}

	@Override
	public void onTick() {
		ticks++;
		if (sinceCombo < AUTO_INTERVAL) {
			sinceCombo++;
		}
		boolean pressed = trigger.pressed();
		if (mc().player.onGround() || mc().screen != null) {
			finish();
			return;
		}
		if (slamAt >= 0) {
			if (ticks >= slamAt) {
				slam();
			}
			return;
		}
		Player found = findTarget();
		if (found == null) {
			return;
		}
		boolean autoReady = auto.isOn() && sinceCombo >= AUTO_INTERVAL && (!shieldOnly.isOn() || found.isBlocking());
		if ((pressed || autoReady) && begin(found, ticks)) {
			attack(found);
			if (ticks >= slamAt) {
				slam();
			}
		}
	}

	/** Wechselt auf die Axt und merkt sich Ziel, Ausgangs-Slot und den Tick des zweiten Schlags. */
	private boolean begin(Player player, int now) {
		int axe = InventoryUtil.findHotbar(stack -> stack.is(ItemTags.AXES));
		int mace = InventoryUtil.findHotbar(stack -> stack.is(Items.MACE));
		if (axe == InventoryUtil.NOT_FOUND || mace == InventoryUtil.NOT_FOUND) {
			return false;
		}
		target = player;
		previousSlot = InventoryUtil.selectedHotbar();
		InventoryUtil.selectHotbar(axe);
		slamAt = now + delay.intValue();
		return true;
	}

	/** Zweiter Schlag mit dem Streitkolben, danach zurück. */
	private void slam() {
		int mace = InventoryUtil.findHotbar(stack -> stack.is(Items.MACE));
		Player victim = target != null && target.isAlive() && inReach(target, reach.get() + 0.5) ? target : findTarget();
		if (mace != InventoryUtil.NOT_FOUND && victim != null) {
			InventoryUtil.selectHotbar(mace);
			attack(victim);
		}
		sinceCombo = 0;
		finish();
	}

	private void attack(Player victim) {
		ownAttack = true;
		try {
			InteractUtil.attack(victim);
		} finally {
			ownAttack = false;
		}
	}

	private void finish() {
		if (previousSlot != InventoryUtil.NOT_FOUND && mc().player != null) {
			InventoryUtil.selectHotbar(previousSlot);
		}
		previousSlot = InventoryUtil.NOT_FOUND;
		target = null;
		slamAt = -1;
	}

	/** Spieler im Fadenkreuz, sonst der nächste Spieler in Reichweite. */
	private Player findTarget() {
		double range = reach.get();
		if (mc().hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player aimed
			&& PLAYERS.accepts(aimed) && inReach(aimed, range + 0.5)) {
			return aimed;
		}
		Player best = null;
		double bestDistance = range * range;
		for (Player other : mc().level.players()) {
			double distance = mc().player.distanceToSqr(other);
			if (PLAYERS.accepts(other) && distance <= bestDistance) {
				best = other;
				bestDistance = distance;
			}
		}
		return best;
	}

	private boolean inReach(Player other, double range) {
		return mc().player.distanceToSqr(other) <= range * range;
	}
}
