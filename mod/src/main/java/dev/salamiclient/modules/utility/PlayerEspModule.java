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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ESP: zeigt ausgewählte Entities durch Wände – Spieler, feindliche Mobs, friedliche Mobs und
 * Items, jeweils in eigener Farbe – als Rahmen um die Hitbox oder als Leucht-Umriss
 * (Minecrafts Glowing-Effekt). Steht unter "Utility", weil Sicht durch Wände auf den meisten
 * Servern verboten ist. (ID bleibt {@code player_esp}, damit bestehende Einstellungen erhalten bleiben.)
 */
public final class PlayerEspModule extends Module implements WorldRenderListener {
	public enum Mode { BOX, GLOW, BOTH }

	/** Arten, die angezeigt werden können. */
	private enum Kind { PLAYER, HOSTILE, PASSIVE, ITEM }

	private static PlayerEspModule instance;

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.BOX));
	private final BoolSetting players = add(new BoolSetting("players", true));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFF5555));
	private final BoolSetting hostile = add(new BoolSetting("hostile", false));
	private final ColorSetting hostileColor = add(new ColorSetting("hostile_color", 0xFFFF8C1A));
	private final BoolSetting passive = add(new BoolSetting("passive", false));
	private final ColorSetting passiveColor = add(new ColorSetting("passive_color", 0xFF55FF55));
	private final BoolSetting items = add(new BoolSetting("items", false));
	private final ColorSetting itemColor = add(new ColorSetting("item_color", 0xFFFFE14D));
	private final BoolSetting healthColor = add(new BoolSetting("health_color", false));
	private final NumberSetting range = add(new NumberSetting("range", 64, 8, 256, 8));
	private final NumberSetting lineWidth = add(new NumberSetting("width", 2, 1, 5, 0.5));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", true));
	private final BoolSetting invisible = add(new BoolSetting("invisible", true));

	public PlayerEspModule() {
		super("player_esp", Category.UTILITY);
		instance = this;
		color.showIf(players::isOn);
		hostileColor.showIf(hostile::isOn);
		passiveColor.showIf(passive::isOn);
		itemColor.showIf(items::isOn);
	}

	/** Art des Entities, wenn es angezeigt werden soll; sonst {@code null}. */
	private Kind kind(Entity entity) {
		if (entity instanceof Player player) {
			return players.isOn() && player != mc().player && !player.isSpectator() ? Kind.PLAYER : null;
		}
		if (entity instanceof ItemEntity) {
			return items.isOn() ? Kind.ITEM : null;
		}
		if (entity instanceof Enemy && entity instanceof LivingEntity) {
			return hostile.isOn() ? Kind.HOSTILE : null;
		}
		if (entity instanceof Mob) {
			return passive.isOn() ? Kind.PASSIVE : null;
		}
		return null;
	}

	private boolean shows(Entity entity) {
		return mc().player != null
			&& kind(entity) != null
			&& entity.isAlive()
			&& (invisible.isOn() || !entity.isInvisible())
			&& entity.distanceToSqr(mc().player) <= range.get() * range.get();
	}

	private int colorFor(Entity entity) {
		if (healthColor.isOn() && entity instanceof LivingEntity living) {
			float health = Mth.clamp((living.getHealth() + living.getAbsorptionAmount()) / living.getMaxHealth(), 0f, 1f);
			// rot (leer) → gelb → grün (voll)
			return ARGB.color(255, (int) (255 * Math.min(1f, 2f - 2f * health)), (int) (255 * Math.min(1f, 2f * health)), 64);
		}
		Kind kind = kind(entity);
		if (kind == null) {
			return color.argb();
		}
		return switch (kind) {
			case PLAYER -> color.argb();
			case HOSTILE -> hostileColor.argb();
			case PASSIVE -> passiveColor.argb();
			case ITEM -> itemColor.argb();
		};
	}

	// --- Leucht-Umriss (über Mixins in Minecraft/EntityRenderer) ---

	/** Soll dieses Entity zusätzlich leuchten? */
	public static boolean glows(Entity entity) {
		PlayerEspModule module = instance;
		return module != null && module.isEnabled() && module.mode.get() != Mode.BOX && module.shows(entity);
	}

	/** Umriss-Farbe (RGB) für leuchtende Entities; sonst die Teamfarbe. */
	public static int glowColor(Entity entity, int teamColor) {
		if (glows(entity)) {
			return instance.colorFor(entity) & 0xFFFFFF;
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
		float width = lineWidth.get().floatValue();
		boolean onlyPlayers = !hostile.isOn() && !passive.isOn() && !items.isOn();
		Iterable<? extends Entity> candidates = onlyPlayers ? mc().level.players() : mc().level.entitiesForRendering();
		boolean any = false;
		for (Entity entity : candidates) {
			if (!shows(entity)) {
				continue;
			}
			Vec3 lerped = entity.getPosition(partial);
			AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position()));
			WorldLines.box(context.matrices(), context.consumers(), type, box, colorFor(entity), width);
			any = true;
		}
		if (any) {
			WorldLines.flush(context.consumers(), type);
		}
	}
}
