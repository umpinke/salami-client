package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hitbox Expand – für die eigene Testwelt. Zwei getrennte Teile:
 * <ul>
 *   <li><b>Anzeige</b> (rein lokal, überall): zeichnet die um „Vergrößerung“ erweiterte
 *       Hitbox von Gegnern als Rahmen.</li>
 *   <li><b>Fadenkreuz</b> (ändert die Spielmechanik, nur im Einzelspieler): das Fadenkreuz
 *       trifft Gegner schon, wenn es die vergrößerte Hitbox berührt. Dafür wird nur
 *       {@code Entity.getPickRadius()} auf dem Client erhöht (siehe {@code EntityMixin}) –
 *       Server- und Entity-Daten bleiben unverändert, Treffer laufen normal über den Server.
 *       Auf fremden Servern ist dieser Teil immer aus.</li>
 * </ul>
 */
public final class HitboxExpandModule extends Module implements WorldRenderListener {
	/** Größere Werte fände die Fadenkreuz-Suche von Minecraft (1 Block Rand) nicht mehr zuverlässig. */
	private static final double MAX_EXPAND = 1.0;
	private static final double DISPLAY_RANGE = 32;

	private static HitboxExpandModule instance;

	private final NumberSetting expand = add(new NumberSetting("expand", 0.3, 0.05, MAX_EXPAND, 0.05));
	private final BoolSetting players = add(new BoolSetting("players", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", true));
	private final BoolSetting affectAim = add(new BoolSetting("affect_aim", true));
	private final BoolSetting display = add(new BoolSetting("display", true));
	private final ColorSetting color = add(new ColorSetting("color", 0xFF55FFFF));

	/** Hinweis „nur im Einzelspieler“ einmal pro Server zeigen. */
	private boolean warnedOnServer;

	public HitboxExpandModule() {
		super("hitbox_expand", Category.UTILITY);
		instance = this;
		color.showIf(display::isOn);
	}

	@Override
	protected void onEnable() {
		warnedOnServer = false;
	}

	@Override
	public void onTick() {
		boolean ownWorld = mc().hasSingleplayerServer();
		if (ownWorld) {
			warnedOnServer = false;
		} else if (affectAim.isOn() && !warnedOnServer) {
			warnedOnServer = true;
			Chat.warn("salami.hitbox_expand.own_world_only");
		}
	}

	private TargetUtil.Filter filter() {
		return new TargetUtil.Filter(players.isOn(), mobs.isOn(), true);
	}

	/**
	 * Für {@code EntityMixin}: zusätzlicher Pick-Radius dieses Entities. Nur auf dem Client
	 * (der Einzelspieler-Server teilt sich die Klassen mit dem Client!) und nur in der eigenen Welt.
	 */
	public static float extraPickRadius(Entity entity) {
		HitboxExpandModule module = instance;
		if (module == null || !module.isEnabled() || !module.affectAim.isOn() || !entity.level().isClientSide()) {
			return 0;
		}
		if (!Minecraft.getInstance().hasSingleplayerServer() || !module.filter().accepts(entity)) {
			return 0;
		}
		return module.expand.get().floatValue();
	}

	@Override
	public void renderWorld(WorldContext context) {
		if (!display.isOn() || mc().level == null || mc().player == null) {
			return;
		}
		float partial = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		TargetUtil.Filter filter = filter();
		double inflate = expand.get();
		boolean any = false;
		for (Entity entity : mc().level.entitiesForRendering()) {
			if (!filter.accepts(entity) || entity.distanceToSqr(mc().player) > DISPLAY_RANGE * DISPLAY_RANGE) {
				continue;
			}
			Vec3 lerped = entity.getPosition(partial);
			AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position())).inflate(inflate);
			WorldLines.box(context, WorldLines.VISIBLE, box, color.argb(), 1.5f);
			any = true;
		}
		if (any) {
			WorldLines.flush(context, WorldLines.VISIBLE);
		}
	}
}
