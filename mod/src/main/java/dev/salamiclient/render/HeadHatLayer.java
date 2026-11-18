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

import java.util.EnumMap;
import java.util.Map;

/**
 * Hüte auf dem Kopf (Cosmetics → Hüte): Krone, Zylinder, Kochmütze, Heiligenschein, Hörner,
 * Katzenohren, Partyhut und das Salami-Barett. Sie folgen dem Kopf (Drehen, Nicken, Schleichen)
 * und rutschen über einen Helm einen Pixel höher.
 *
 * <p>Die Modelle stehen fest im Code, im Pixelmaß des Spielerkopfs (Kopf von y = -8 bis 0, Mitte bei
 * x = z = 0). Gefärbt werden die Würfel über Farbflächen in {@code textures/cosmetics/hats.png}:
 * jede Fläche ist 64 x 32 groß, ein Würfel zeigt mit {@link #color} in eine davon.
 */
public final class HeadHatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/cosmetics/hats.png");
	//? if >=1.21.11 {
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	//?} else
	/*private static final RenderType TYPE = RenderType.entityCutout(TEXTURE);*/
	/** Volle Helligkeit (Heiligenschein leuchtet auch im Dunkeln). */
	private static final int FULL_BRIGHT = 0xF000F0;

	// Farbflächen in hats.png (Reihenfolge wie im Erzeuger-Skript)
	private static final int GOLD = 0;
	private static final int GOLD_DARK = 1;
	private static final int RUBY = 2;
	private static final int SAPPHIRE = 3;
	private static final int BLACK = 4;
	private static final int BAND = 5;
	private static final int WHITE = 6;
	private static final int WHITE_SHADE = 7;
	private static final int HALO = 8;
	private static final int HORN = 9;
	private static final int HORN_TIP = 10;
	private static final int EAR = 11;
	private static final int EAR_INNER = 12;
	private static final int PARTY_BLUE = 13;
	private static final int PARTY_YELLOW = 14;
	private static final int PARTY_POM = 15;
	private static final int SALAMI = 16;
	private static final int SALAMI_NUB = 17;

	private static final Map<HatsModule.Hat, ModelPart> MODELS = new EnumMap<>(HatsModule.Hat.class);

	static {
		MODELS.put(HatsModule.Hat.CROWN, crown());
		MODELS.put(HatsModule.Hat.TOP_HAT, topHat());
		MODELS.put(HatsModule.Hat.CHEF, chef());
		MODELS.put(HatsModule.Hat.HALO, halo());
		MODELS.put(HatsModule.Hat.HORNS, horns());
		MODELS.put(HatsModule.Hat.CAT_EARS, catEars());
		MODELS.put(HatsModule.Hat.PARTY, party());
		MODELS.put(HatsModule.Hat.BERET, beret());
	}

	public HeadHatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/** Würfel-Liste, deren Würfel alle die Farbe {@code swatch} haben. */
	private static CubeListBuilder color(int swatch) {
		return CubeListBuilder.create().texOffs((swatch % 2) * 64, (swatch / 2) * 32);
	}

	private static ModelPart bake(MeshDefinition mesh) {
		return LayerDefinition.create(mesh, 128, 320).bakeRoot();
	}

	private static ModelPart crown() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("band", color(GOLD)
				.addBox(-4.5f, -9.5f, -4.5f, 9f, 2f, 0.75f)
				.addBox(-4.5f, -9.5f, 3.75f, 9f, 2f, 0.75f)
				.addBox(-4.5f, -9.5f, -3.75f, 0.75f, 2f, 7.5f)
				.addBox(3.75f, -9.5f, -3.75f, 0.75f, 2f, 7.5f), PartPose.ZERO);
		CubeListBuilder points = color(GOLD_DARK);
		for (float x : new float[] {-4.5f, -0.5f, 3.5f}) {
			points.addBox(x, -11f, -4.5f, 1f, 1.5f, 0.75f);
			points.addBox(x, -11f, 3.75f, 1f, 1.5f, 0.75f);
		}
		points.addBox(-4.5f, -11f, -0.5f, 0.75f, 1.5f, 1f);
		points.addBox(3.75f, -11f, -0.5f, 0.75f, 1.5f, 1f);
		root.addOrReplaceChild("points", points, PartPose.ZERO);
		root.addOrReplaceChild("ruby", color(RUBY).addBox(-0.75f, -9f, -4.85f, 1.5f, 1f, 0.5f), PartPose.ZERO);
		root.addOrReplaceChild("sapphires", color(SAPPHIRE)
				.addBox(-4.85f, -9f, -0.5f, 0.5f, 1f, 1f)
				.addBox(4.35f, -9f, -0.5f, 0.5f, 1f, 1f), PartPose.ZERO);
		return bake(mesh);
	}

	private static ModelPart topHat() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("brim", color(BLACK).addBox(-5.5f, -8.75f, -5.5f, 11f, 0.75f, 11f), PartPose.ZERO);
		root.addOrReplaceChild("crown", color(BLACK).addBox(-3.75f, -15.25f, -3.75f, 7.5f, 6.5f, 7.5f), PartPose.ZERO);
		root.addOrReplaceChild("band", color(BAND).addBox(-3.9f, -10.25f, -3.9f, 7.8f, 1.5f, 7.8f), PartPose.ZERO);
		return bake(mesh);
	}

	private static ModelPart chef() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("band", color(WHITE_SHADE).addBox(-4.4f, -10f, -4.4f, 8.8f, 2.5f, 8.8f), PartPose.ZERO);
		root.addOrReplaceChild("puff", color(WHITE)
				.addBox(-5f, -14.5f, -5f, 10f, 4.5f, 10f)
				.addBox(-4f, -15.5f, -4f, 8f, 1f, 8f), PartPose.ZERO);
		return bake(mesh);
	}

	private static ModelPart halo() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("ring", color(HALO)
				.addBox(-4f, -12f, -4f, 8f, 0.6f, 1f)
				.addBox(-4f, -12f, 3f, 8f, 0.6f, 1f)
				.addBox(-4f, -12f, -3f, 1f, 0.6f, 6f)
				.addBox(3f, -12f, -3f, 1f, 0.6f, 6f), PartPose.ZERO);
		return bake(mesh);
	}

	private static ModelPart horns() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int side : new int[] {-1, 1}) {
			PartDefinition horn = root.addOrReplaceChild(side < 0 ? "right" : "left", color(HORN)
					.addBox(-1f, -2.5f, -1f, 2f, 2.5f, 2f)
					.addBox(-0.75f, -4.5f, -0.75f, 1.5f, 2f, 1.5f),
				PartPose.offsetAndRotation(side * 2.5f, -7.5f, -1.5f, -0.25f, 0f, side * 0.35f));
			horn.addOrReplaceChild("tip", color(HORN_TIP).addBox(-0.5f, -1.75f, -0.5f, 1f, 1.75f, 1f),
				PartPose.offsetAndRotation(0f, -4.5f, 0f, -0.3f, 0f, side * 0.3f));
		}
		return bake(mesh);
	}

	private static ModelPart catEars() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int side : new int[] {-1, 1}) {
			PartDefinition ear = root.addOrReplaceChild(side < 0 ? "right" : "left", color(EAR)
					.addBox(-1.5f, -1f, -0.5f, 3f, 1f, 1f)
					.addBox(-1f, -2f, -0.5f, 2f, 1f, 1f)
					.addBox(-0.5f, -3f, -0.5f, 1f, 1f, 1f),
				PartPose.offsetAndRotation(side * 2.5f, -8f, -0.5f, 0f, 0f, side * 0.2f));
			ear.addOrReplaceChild("inner", color(EAR_INNER).addBox(-0.75f, -1.75f, -0.65f, 1.5f, 1.5f, 0.2f), PartPose.ZERO);
		}
		return bake(mesh);
	}

	private static ModelPart party() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition cone = mesh.getRoot().addOrReplaceChild("cone", color(PARTY_BLUE)
				.addBox(-2.5f, -1.5f, -2.5f, 5f, 1.5f, 5f)
				.addBox(-1.5f, -4.5f, -1.5f, 3f, 1.5f, 3f)
				.addBox(-0.5f, -7f, -0.5f, 1f, 1f, 1f),
			PartPose.offsetAndRotation(1f, -8f, 0f, 0f, 0f, -0.15f));
		cone.addOrReplaceChild("stripes", color(PARTY_YELLOW)
				.addBox(-2f, -3f, -2f, 4f, 1.5f, 4f)
				.addBox(-1f, -6f, -1f, 2f, 1.5f, 2f), PartPose.ZERO);
		cone.addOrReplaceChild("pom", color(PARTY_POM).addBox(-0.9f, -8.6f, -0.9f, 1.8f, 1.6f, 1.8f), PartPose.ZERO);
		return bake(mesh);
	}

	private static ModelPart beret() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition disc = mesh.getRoot().addOrReplaceChild("disc", color(SALAMI)
				.addBox(-5f, -1.5f, -5f, 10f, 1.5f, 10f),
			PartPose.offsetAndRotation(0.5f, -8f, 0f, -0.05f, 0f, -0.18f));
		disc.addOrReplaceChild("nub", color(SALAMI_NUB).addBox(-0.75f, -2.5f, -0.75f, 1.5f, 1f, 1.5f), PartPose.ZERO);
		return bake(mesh);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		HatsModule.Hat hat = HatsModule.hatOf(state.id);
		ModelPart model = MODELS.get(hat);
		if (model == null) {
			return;
		}
		poseStack.pushPose();
		getParentModel().head.translateAndRotate(poseStack);
		if (!state.headEquipment.isEmpty()) {
			// über den Helm (der steht einen Pixel über)
			poseStack.translate(0f, -1.1f / 16f, 0f);
			poseStack.scale(1.12f, 1.12f, 1.12f);
		}
		int brightness = light;
		if (hat == HatsModule.Hat.HALO) {
			// schwebt und wippt sanft, leuchtet
			poseStack.translate(0f, (float) Math.sin(state.ageInTicks * 0.1f) * 0.5f / 16f, 0f);
			brightness = FULL_BRIGHT;
		}
		collector.submitModelPart(model, poseStack, TYPE, brightness, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}
}
