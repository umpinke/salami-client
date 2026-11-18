package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.Emotes;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Requisiten eines Emotes am Spielermodell: der Schneeball in der Hand vor dem Wurf, die Glocke, das
 * Geschenk. Jede hängt an einem Körperteil und bewegt sich mit ihm, solange das Emote sie zeigt.
 * Geworfenes und Verwandlungen zeichnet {@link dev.salamiclient.emotes.EmoteEffects} in der Welt.
 */
public final class EmotePropLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public EmotePropLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !Emotes.anyPlaying()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(state.id);
		if (!(entity instanceof Player player)) {
			return;
		}
		Emotes.Active active = Emotes.activeOf(player);
		if (active == null || active.emote().props().isEmpty()) {
			return;
		}
		PlayerModel parent = getParentModel();
		for (Emote.Prop prop : active.emote().props()) {
			if (!prop.visibleAt(active.seconds())) {
				continue;
			}
			ModelPart limb = switch (prop.on()) {
				case HEAD -> parent.head;
				case BODY -> parent.body;
				case RIGHT_ARM -> parent.rightArm;
				case LEFT_ARM -> parent.leftArm;
				case RIGHT_LEG -> parent.rightLeg;
				case LEFT_LEG -> parent.leftLeg;
			};
			poseStack.pushPose();
			limb.translateAndRotate(poseStack);
			poseStack.translate(prop.at()[0] / 16f, prop.at()[1] / 16f, prop.at()[2] / 16f);
			poseStack.mulPose(new Quaternionf().rotationZYX(prop.rot()[2], prop.rot()[1], prop.rot()[0]));
			// die Flächen stehen in Spieler-Pixeln; die Requisite wächst mit dem Einblenden des Emotes
			float scale = prop.scale() * active.weight() / 16f;
			poseStack.scale(scale, scale, scale);
			BoxRender.submit(collector, poseStack, prop.model(), false, light);
			poseStack.popPose();
		}
	}
}
