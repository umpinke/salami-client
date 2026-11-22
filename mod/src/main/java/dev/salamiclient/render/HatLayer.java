package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.modules.visual.HatsModule;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Quaternionf;

/**
 * Zeichnet den Hut eines Spielers (Cosmetics → Hüte). Welche Hüte es gibt und wie sie aussehen,
 * steht nicht hier, sondern als Daten im Katalog ({@link dev.salamiclient.cosmetics.HatCatalog}) –
 * diese Klasse kennt nur die zwei Plätze:
 *
 * <ul>
 * <li>Kopf: der Hut folgt dem Kopf (Drehen, Nicken, Schleichen) und rutscht über einen Helm einen
 * Pixel höher.</li>
 * <li>Schulter: der Hut schwebt links neben dem Kopf über der Schulter und folgt dem Oberkörper.
 * Hockt links ein Papagei, schwebt er rechts (gespiegelt), mit zwei Papageien bleibt er weg.</li>
 * </ul>
 *
 * <p>Gezeichnet werden die Würfel von {@link BoxRender}. Hüte, die es auch als Buddy gibt, zeichnet
 * für den eigenen Spieler {@link BuddyLayer} – hier kommen sie nur noch von älteren Spielversionen an.
 */
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Abstand von der Körpermitte (in Spieler-Pixeln): über der Außenkante der Schulter, frei vom Kopf. */
	static final float SHOULDER_X = 6.5f;
	/** So weit (in Spieler-Pixeln) schwebt ein Schulter-Hut über der Schulter. */
	static final float SHOULDER_FLOAT = 1f;

	public HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		Hat hat = HatsModule.hatOf(state.id);
		if (hat == null) {
			return;
		}
		// sanftes Schweben: etwa alle drei Sekunden auf und ab, langsam ein wenig pendeln
		float bob = (float) Math.sin(state.ageInTicks * 0.1f) * hat.bob();
		float sway = (float) Math.sin(state.ageInTicks * 0.07f) * hat.sway();
		boolean mirror = false;
		poseStack.pushPose();
		if (hat.anchor() == Hat.Anchor.SHOULDER) {
			boolean leftFree = state.parrotOnLeftShoulder == null;
			if (!leftFree && state.parrotOnRightShoulder != null) {
				poseStack.popPose();
				return;
			}
			mirror = !leftFree;
			ModelAnchor.to(poseStack, getParentModel(), getParentModel().body);
			poseStack.translate((leftFree ? 1f : -1f) * SHOULDER_X / 16f, (-SHOULDER_FLOAT - bob) / 16f, 0f);
		} else {
			ModelAnchor.to(poseStack, getParentModel(), getParentModel().head);
			if (!state.headEquipment.isEmpty()) {
				// über den Helm (der steht einen Pixel über)
				poseStack.translate(0f, -1.1f / 16f, 0f);
				poseStack.scale(1.12f, 1.12f, 1.12f);
			}
			poseStack.translate(0f, -bob / 16f, 0f);
		}
		if (sway != 0f) {
			poseStack.mulPose(new Quaternionf().rotationY(sway));
		}
		// die Flächen stehen in Spieler-Pixeln
		float scale = hat.scale() / 16f;
		poseStack.scale(scale, scale, scale);
		BoxRender.submit(collector, poseStack, hat.model(), mirror, hat.glow() ? BoxRender.FULL_BRIGHT : light);
		poseStack.popPose();
	}
}
