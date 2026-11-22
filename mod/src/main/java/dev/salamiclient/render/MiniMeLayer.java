package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.minime.MiniMeFollowers;
import dev.salamiclient.minime.MiniMeModel;
import dev.salamiclient.minime.MiniMeSkins;
import dev.salamiclient.modules.visual.MiniMeModule;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Mini Me am Spielermodell: an der linken Hand hängend, auf der rechten Schulter oder auf dem
 * Kopf sitzend. Folgt dem jeweiligen Körperteil (die Hand trägt es mit, der Kopf dreht es mit), bleibt
 * an Hand und Kopf aber aufrecht ({@link ModelAnchor#upright}). Das hinterherlaufende Mini Me
 * zeichnet {@link MiniMeFollowers} in der Welt.
 */
public final class MiniMeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Größe gegenüber dem Spieler, wenn das Mini Me an ihm sitzt oder hängt. */
	private static final float SCALE = 0.28f;

	public MiniMeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(state.id);
		if (!(entity instanceof Player player)) {
			return;
		}
		MiniMeModule.Wish wish = MiniMeModule.of(player);
		if (wish == null || wish.place() == MiniMeModule.Place.FOLLOW || wish.place() == MiniMeModule.Place.NONE) {
			return;
		}
		if (wish.place() == MiniMeModule.Place.SHOULDER && state.parrotOnRightShoulder != null) {
			return; // dort sitzt schon ein Papagei
		}
		MiniMeSkins.Skin skin = MiniMeSkins.get(wish.name());
		Identifier texture = skin != null ? skin.texture() : DefaultPlayerSkin.getDefaultTexture();
		boolean slim = skin != null && skin.slim();
		MiniMeModel model = MiniMeFollowers.model(player.getUUID(), slim);
		PlayerModel parent = getParentModel();

		poseStack.pushPose();
		switch (wish.place()) {
			case HAND -> {
				// linke Hand (dort ist meist nichts): Handende 10 Pixel unter der Schulter. Es hängt aufrecht
				// daran, statt jeden Armschwung mitzumachen.
				ModelAnchor.upright(poseStack, parent, parent.leftArm, 1f, 10.5f, 0f, parent.body, 0f);
				poseStack.scale(SCALE, SCALE, SCALE);
				float[] hand = MiniMeModel.hangingHand(slim);
				poseStack.translate(-hand[0] / 16f, -hand[1] / 16f, -hand[2] / 16f);
				model.pose(MiniMeModel.Pose.HANG, 0f, 0f, state.ageInTicks);
			}
			case SHOULDER -> {
				// rechte Schulter (links schwebt ggf. die Weihnachtsmütze), Hüfte auf der Schulter
				ModelAnchor.to(poseStack, parent, parent.body);
				poseStack.translate(-6.8f / 16f, -0.2f / 16f, 0f);
				poseStack.scale(SCALE, SCALE, SCALE);
				poseStack.translate(0f, -12f / 16f, 0f);
				model.pose(MiniMeModel.Pose.SIT, 0f, 0f, state.ageInTicks);
			}
			case HEAD -> {
				// oben auf dem Kopf, dreht sich mit dem Kopf – kippt beim Nicken aber nur ein wenig mit
				ModelAnchor.upright(poseStack, parent, parent.head, 0f, -8.6f, 0f, parent.head, BuddyLayer.HEAD_TILT);
				poseStack.scale(SCALE, SCALE, SCALE);
				poseStack.translate(0f, -12f / 16f, 0f);
				model.pose(MiniMeModel.Pose.SIT, 0f, 0f, state.ageInTicks);
			}
			default -> {
				poseStack.popPose();
				return;
			}
		}
		collector.submitModelPart(model.root, poseStack, WorldModels.entityType(texture), light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}
}
