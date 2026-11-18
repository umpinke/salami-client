package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.cosmetics.BuddyFollowers;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.modules.visual.BuddyModule;
import dev.salamiclient.modules.visual.HatsModule;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Buddy am Spielermodell (Cosmetics → Buddys): hängt an der linken Hand, schwebt über der linken
 * Schulter oder steht auf dem Kopf. Den hinterherlaufenden Buddy zeichnet {@link BuddyFollowers} in
 * der Welt.
 *
 * <ul>
 * <li>Schulter: wie ein Schulter-Hut – hockt links ein Papagei, schwebt er rechts (gespiegelt), mit
 * zwei Papageien bleibt er weg.</li>
 * <li>Kopf: steht oben auf dem Kopf oder auf dem Hut, den der Spieler trägt.</li>
 * <li>Hand: baumelt unter der linken Hand; hängt dort schon ein Mini Me, unter der rechten.</li>
 * </ul>
 */
public final class BuddyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public BuddyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
		BuddyModule.Wish wish = BuddyModule.of(player);
		if (wish == null || wish.place() == BuddyModule.Place.FOLLOW) {
			return;
		}
		Hat buddy = wish.buddy();
		PlayerModel parent = getParentModel();
		boolean mirror = false;
		poseStack.pushPose();
		switch (wish.place()) {
			case SHOULDER -> {
				boolean leftFree = state.parrotOnLeftShoulder == null;
				if (!leftFree && state.parrotOnRightShoulder != null) {
					poseStack.popPose();
					return;
				}
				mirror = !leftFree;
				// sanftes Schweben: etwa alle drei Sekunden auf und ab, langsam ein wenig pendeln
				float bob = (float) Math.sin(state.ageInTicks * 0.1f) * buddy.bob();
				parent.body.translateAndRotate(poseStack);
				poseStack.translate((leftFree ? 1f : -1f) * HatLayer.SHOULDER_X / 16f, (-HatLayer.SHOULDER_FLOAT - bob) / 16f, 0f);
				float sway = (float) Math.sin(state.ageInTicks * 0.07f) * buddy.sway();
				if (sway != 0f) {
					poseStack.mulPose(new Quaternionf().rotationY(sway));
				}
			}
			case HEAD -> {
				parent.head.translateAndRotate(poseStack);
				float top = -8f;
				Hat hat = HatsModule.hatOf(state.id);
				if (hat != null && hat.anchor() == Hat.Anchor.HEAD) {
					top = Math.min(top, hat.model().top() * hat.scale());
				}
				if (!state.headEquipment.isEmpty()) {
					top -= 1.1f; // über den Helm (der steht einen Pixel über)
				}
				poseStack.translate(0f, top / 16f, 0f);
			}
			case HAND -> {
				// unter dem Handende (10 Pixel unter der Schulter), baumelt leicht hin und her
				MiniMeModule.Wish mini = MiniMeModule.of(player);
				boolean left = mini == null || mini.place() != MiniMeModule.Place.HAND;
				(left ? parent.leftArm : parent.rightArm).translateAndRotate(poseStack);
				poseStack.translate((left ? 1f : -1f) / 16f, 10.3f / 16f, 0f);
				poseStack.mulPose(new Quaternionf().rotationZ((float) Math.sin(state.ageInTicks * 0.1f) * 0.12f));
				// der Nullpunkt des Buddys ist unten: um seine Höhe nach unten, dann hängt er an seinem Scheitel
				poseStack.translate(0f, -buddy.model().top() * buddy.scale() / 16f, 0f);
			}
			default -> {
				poseStack.popPose();
				return;
			}
		}
		// die Flächen stehen in Spieler-Pixeln
		float scale = buddy.scale() / 16f;
		poseStack.scale(scale, scale, scale);
		BoxRender.submit(collector, poseStack, buddy.model(), mirror, buddy.glow() ? BoxRender.FULL_BRIGHT : light);
		poseStack.popPose();
	}
}
