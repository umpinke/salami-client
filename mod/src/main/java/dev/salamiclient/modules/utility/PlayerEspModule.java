package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Player ESP: zeigt andere Spieler durch Wände – als Rahmen um die Hitbox oder als
 * Leucht-Umriss (Minecrafts Glowing-Effekt). Steht unter "Utility", weil Sicht durch
 * Wände auf den meisten Servern verboten ist.
 */
public final class PlayerEspModule extends Module implements WorldRenderListener {
	public enum Mode { BOX, GLOW, BOTH }

	private static PlayerEspModule instance;

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.BOX));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFF5555));
	private final BoolSetting healthColor = add(new BoolSetting("health_color", false));
	private final NumberSetting range = add(new NumberSetting("range", 64, 8, 256, 8));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", true));
	private final BoolSetting invisible = add(new BoolSetting("invisible", true));

	public PlayerEspModule() {
		super("player_esp", Category.UTILITY);
		instance = this;
	}

	private boolean shows(Entity entity) {
		return entity instanceof Player player
			&& player != mc().player
			&& !player.isSpectator()
			&& (invisible.isOn() || !player.isInvisible())
			&& mc().player != null
			&& player.distanceToSqr(mc().player) <= range.get() * range.get();
	}

	private int colorFor(Player player) {
		if (!healthColor.isOn()) {
			return color.argb();
		}
		float health = Mth.clamp((player.getHealth() + player.getAbsorptionAmount()) / player.getMaxHealth(), 0f, 1f);
		// rot (leer) → gelb → grün (voll)
		return ARGB.color(255, (int) (255 * Math.min(1f, 2f - 2f * health)), (int) (255 * Math.min(1f, 2f * health)), 64);
	}

	// --- Leucht-Umriss (über Mixins in Minecraft/EntityRenderer) ---

	/** Soll dieses Entity zusätzlich leuchten? */
	public static boolean glows(Entity entity) {
		PlayerEspModule module = instance;
		return module != null && module.isEnabled() && module.mode.get() != Mode.BOX && module.shows(entity);
	}

	/** Umriss-Farbe (RGB) für leuchtende Spieler; sonst die Teamfarbe. */
	public static int glowColor(Entity entity, int teamColor) {
		if (glows(entity) && entity instanceof Player player) {
			return instance.colorFor(player) & 0xFFFFFF;
		}
		return teamColor;
	}

	// --- Rahmen ---

	@Override
	public void renderWorld(WorldRenderContext context) {
		if (mode.get() == Mode.GLOW || mc().level == null) {
			return;
		}
		float partial = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		RenderType type = throughWalls.isOn() ? WorldLines.THROUGH_WALLS : WorldLines.VISIBLE;
		boolean any = false;
		for (Player player : mc().level.players()) {
			if (!shows(player)) {
				continue;
			}
			Vec3 lerped = player.getPosition(partial);
			AABB box = player.getBoundingBox().move(lerped.subtract(player.position()));
			WorldLines.box(context.matrices(), context.consumers(), type, box, colorFor(player), 2f);
			any = true;
		}
		if (any) {
			WorldLines.flush(context.consumers(), type);
		}
	}
}
