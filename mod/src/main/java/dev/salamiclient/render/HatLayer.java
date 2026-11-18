package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.modules.visual.HatsModule;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
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
 * <p>Die Würfel haben kein eigenes Bild: eine weiße Textur, die Farbe steht an jeder Fläche.
 */
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/cosmetics/white.png");
	// Ohne Rückseiten-Auslese: gespiegelte Hüte (andere Schulter) haben die Flächen andersherum.
	// Ab 26.1 heißt genau das „entityCutout“ (mit Auslese: entityCutoutCull).
	//? if >=26.1 {
	/*private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	*///?} else if >=1.21.11 {
	private static final RenderType TYPE = RenderTypes.entityCutoutNoCull(TEXTURE);
	//?} else
	/*private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);*/
	/** Volle Helligkeit (leuchtende Hüte, auch im Dunkeln). */
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Abstand von der Körpermitte (in Spieler-Pixeln): über der Außenkante der Schulter, frei vom Kopf. */
	private static final float SHOULDER_X = 6.5f;
	/** So weit (in Spieler-Pixeln) schwebt ein Schulter-Hut über der Schulter. */
	private static final float SHOULDER_FLOAT = 1f;

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
			getParentModel().body.translateAndRotate(poseStack);
			poseStack.translate((leftFree ? 1f : -1f) * SHOULDER_X / 16f, (-SHOULDER_FLOAT - bob) / 16f, 0f);
		} else {
			getParentModel().head.translateAndRotate(poseStack);
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
		int brightness = hat.glow() ? FULL_BRIGHT : light;
		boolean flip = mirror;
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> hat.emit(pose, buffer, flip, brightness));
		poseStack.popPose();
	}
}
