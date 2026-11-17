package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.visual.HatsModule;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * Weihnachtsmütze über der Schulter (Cosmetics → Hüte): eine kleine Mütze, die links neben dem
 * Kopf über der Schulter schwebt, sanft auf und ab wippt und leicht hin und her pendelt. Hockt
 * links ein Papagei, schwebt sie rechts, mit zwei Papageien bleibt sie weg. Folgt dem Oberkörper,
 * also auch beim Schleichen. Das Modell ist fest im Code (keine eigene Modell-Datei).
 */
public final class SantaHatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/cosmetics/santa_hat.png");
	//? if >=1.21.11 {
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	//?} else
	/*private static final RenderType TYPE = RenderType.entityCutout(TEXTURE);*/

	/** Größe gegenüber dem Modell in Spieler-Pixeln – eine kleine Mütze. */
	private static final float SCALE = 0.5f;
	/** So weit (in Spieler-Pixeln) schwebt die Krempe über der Schulter. */
	private static final float FLOAT = 2f;
	/** Abstand von der Körpermitte (in Spieler-Pixeln): über der Außenkante der Schulter, frei vom Kopf. */
	private static final float SHOULDER_X = 8f;

	private final ModelPart left = build(true);
	private final ModelPart right = build(false);

	public SantaHatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/**
	 * Mütze im Pixelmaß des Spielermodells (y nach oben negativ), Krempe unten bei y = 0: Fellkrempe,
	 * zwei rote Stufen, die nach außen gebogene Spitze und der Bommel. Verkleinert und platziert
	 * wird sie erst beim Zeichnen.
	 */
	private static ModelPart build(boolean leftShoulder) {
		float side = leftShoulder ? 1f : -1f;
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition hat = mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-2.5f, -2f, -2.5f, 5f, 2f, 5f)
				.texOffs(0, 8).addBox(-2f, -4f, -2f, 4f, 2f, 4f)
				.texOffs(0, 16).addBox(-1.5f, -6f, -1.5f, 3f, 2f, 3f),
			PartPose.offset(0f, 0f, 0f));
		hat.addOrReplaceChild("tip", CubeListBuilder.create()
				.texOffs(16, 16).addBox(-1f, -2.5f, -1f, 2f, 2.5f, 2f)
				.texOffs(20, 0).addBox(-1.25f, -4.5f, -1.25f, 2.5f, 2.5f, 2.5f),
			PartPose.offsetAndRotation(0f, -5.5f, 0f, 0f, 0f, side * 0.55f));
		return LayerDefinition.create(mesh, 64, 32).bakeRoot();
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !HatsModule.wearsSanta(state.id)) {
			return;
		}
		boolean leftFree = state.parrotOnLeftShoulder == null;
		if (!leftFree && state.parrotOnRightShoulder != null) {
			return;
		}
		float side = leftFree ? 1f : -1f;
		// sanftes Schweben: etwa alle drei Sekunden auf und ab, langsamer hin und her pendeln
		float bob = (float) Math.sin(state.ageInTicks * 0.1f) * 0.6f;
		float sway = (float) Math.sin(state.ageInTicks * 0.07f) * 0.25f;
		poseStack.pushPose();
		getParentModel().body.translateAndRotate(poseStack);
		poseStack.translate(side * SHOULDER_X / 16f, (-FLOAT - bob) / 16f, 0f);
		poseStack.mulPose(new Quaternionf().rotationY(sway));
		poseStack.scale(SCALE, SCALE, SCALE);
		collector.submitModelPart(leftFree ? left : right, poseStack, TYPE, light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}
}
