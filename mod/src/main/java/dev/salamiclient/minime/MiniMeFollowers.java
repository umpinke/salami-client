package dev.salamiclient.minime;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.cosmetics.FollowerSim;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mini Mes, die ihrem Spieler hinterherlaufen (Platz „Folgt dir“). Wie sie laufen, steht in
 * {@link FollowerSim}; hier steht, wer eines hat und wie es aussieht. Gezeichnet in der Welt (nicht
 * am Spielermodell), damit man sein eigenes auch in der Ich-Perspektive sieht. Hält außerdem die
 * Modelle für die anderen Plätze bereit.
 */
public final class MiniMeFollowers {
	/** Größe gegenüber einem Spieler. */
	private static final float SCALE = 0.4f;
	/** Halbe Breite (mit Armen) und Höhe der Figur in Blöcken, für die Kollision. */
	private static final double HALF_WIDTH = 0.2;
	private static final double HEIGHT = 0.75;
	/** Plätze um den Spieler zum Auftauchen: {vorne, rechts} in Blöcken, der erste freie gewinnt (bevorzugt schräg hinter ihm). */
	private static final double[][] SPOTS = {
		{-1.3, 0.7}, {-1.3, -0.7}, {-1.5, 0}, {0, 1.2}, {0, -1.2}, {-2.5, 0}, {1.5, 0}, {0, 0},
	};

	private static final FollowerSim SIM = new FollowerSim(HALF_WIDTH, HEIGHT, SCALE, SPOTS);
	/** Ein Modell pro Spieler und Armform (siehe {@link MiniMeModel}). */
	private static final Map<UUID, MiniMeModel> MODELS = new HashMap<>();
	private static final Map<UUID, Boolean> MODEL_SLIM = new HashMap<>();

	private MiniMeFollowers() {}

	/** Modell für das Mini Me dieses Spielers (wird bei geänderter Armform neu gebaut). */
	public static MiniMeModel model(UUID player, boolean slim) {
		MiniMeModel model = MODELS.get(player);
		if (model == null || MODEL_SLIM.get(player) != slim) {
			model = new MiniMeModel(slim);
			MODELS.put(player, model);
			MODEL_SLIM.put(player, slim);
		}
		return model;
	}

	/** Füße des hinterherlaufenden Mini Mes dieses Spielers (für Tests) oder {@code null}. */
	public static @Nullable Vec3 positionOf(UUID player) {
		FollowerSim.Follower f = SIM.get(player);
		return f == null ? null : new Vec3(f.x, f.y, f.z);
	}

	public static void clear() {
		SIM.clear();
		MODELS.clear();
		MODEL_SLIM.clear();
	}

	/** Jeden Client-Tick: Mini Mes nachziehen, verschwundene Spieler vergessen. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		for (Player player : mc.level.players()) {
			UUID uuid = player.getUUID();
			MiniMeModule.Wish wish = MiniMeModule.of(player);
			if (wish != null) {
				present.add(uuid);
			}
			if (wish == null || wish.place() != MiniMeModule.Place.FOLLOW) {
				SIM.remove(uuid);
				continue;
			}
			SIM.follow(mc.level, player).what = wish.name();
		}
		SIM.retain(present);
		MODELS.keySet().retainAll(present);
		MODEL_SLIM.keySet().retainAll(present);
	}

	/** Alle hinterherlaufenden Mini Mes zeichnen. */
	public static void render(WorldContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (SIM.isEmpty() || mc.level == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = WorldModels.camera();
		PoseStack poseStack = context.matrices();
		for (Map.Entry<UUID, FollowerSim.Follower> entry : SIM.all().entrySet()) {
			FollowerSim.Follower f = entry.getValue();
			MiniMeSkins.Skin skin = MiniMeSkins.get(f.what);
			Identifier texture = skin != null ? skin.texture() : DefaultPlayerSkin.getDefaultTexture();
			MiniMeModel model = model(entry.getKey(), skin != null && skin.slim());
			double x = f.prevX + (f.x - f.prevX) * partial;
			double y = f.prevY + (f.y - f.prevY) * partial;
			double z = f.prevZ + (f.z - f.prevZ) * partial;
			float yaw = f.prevYaw + (((f.yaw - f.prevYaw) % 360f + 540f) % 360f - 180f) * partial;
			// Phase und Stärke der Schritte überblenden, sonst ruckeln die Beine im Takt der Ticks
			model.pose(MiniMeModel.Pose.WALK, f.walk(partial), f.amount(partial), mc.level.getGameTime() + partial);

			BlockPos at = BlockPos.containing(x, y + 0.5, z);
			int light = mc.level.getBrightness(LightLayer.BLOCK, at) << 4 | mc.level.getBrightness(LightLayer.SKY, at) << 20;
			poseStack.pushPose();
			poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
			// wie bei Minecrafts Wesen: drehen, umklappen, auf die Füße stellen
			poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180f - yaw)));
			poseStack.scale(-SCALE, -SCALE, SCALE);
			poseStack.translate(0f, -1.501f, 0f);
			WorldModels.model(context, model.root, WorldModels.entityType(texture), light);
			poseStack.popPose();
		}
	}
}
