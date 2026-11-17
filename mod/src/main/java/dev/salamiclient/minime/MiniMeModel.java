package dev.salamiclient.minime;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Spielermodell für ein Mini Me: dieselben Würfel und Textur-Plätze wie Minecrafts Spieler
 * (64x64-Skin, breite oder schmale Arme, äußere Schicht als Kind jedes Körperteils). Jedes
 * Mini Me hat sein eigenes Modell – Minecraft zeichnet eingereichte Teile erst später, ein
 * geteiltes Modell hätte dann bei allen dieselbe Haltung.
 */
public final class MiniMeModel {
	/** Haltung: stehen/laufen, sitzen (Schulter, Kopf) oder an der Hand hängen. */
	public enum Pose { WALK, SIT, HANG }

	public final ModelPart root;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public MiniMeModel(boolean slim) {
		root = build(slim);
		head = root.getChild("head");
		body = root.getChild("body");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	private static ModelPart build(boolean slim) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation hat = new CubeDeformation(0.5f);
		CubeDeformation outer = new CubeDeformation(0.25f);

		PartDefinition head = root.addOrReplaceChild("head",
			CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -8f, -4f, 8f, 8f, 8f), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4f, -8f, -4f, 8f, 8f, 8f, hat), PartPose.ZERO);

		PartDefinition body = root.addOrReplaceChild("body",
			CubeListBuilder.create().texOffs(16, 16).addBox(-4f, 0f, -2f, 8f, 12f, 4f), PartPose.ZERO);
		body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32).addBox(-4f, 0f, -2f, 8f, 12f, 4f, outer), PartPose.ZERO);

		float armWidth = slim ? 3f : 4f;
		PartDefinition rightArm = root.addOrReplaceChild("right_arm",
			CubeListBuilder.create().texOffs(40, 16).addBox(slim ? -2f : -3f, -2f, -2f, armWidth, 12f, 4f), PartPose.offset(-5f, slim ? 2.5f : 2f, 0f));
		rightArm.addOrReplaceChild("right_sleeve",
			CubeListBuilder.create().texOffs(40, 32).addBox(slim ? -2f : -3f, -2f, -2f, armWidth, 12f, 4f, outer), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm",
			CubeListBuilder.create().texOffs(32, 48).addBox(-1f, -2f, -2f, armWidth, 12f, 4f), PartPose.offset(5f, slim ? 2.5f : 2f, 0f));
		leftArm.addOrReplaceChild("left_sleeve",
			CubeListBuilder.create().texOffs(48, 48).addBox(-1f, -2f, -2f, armWidth, 12f, 4f, outer), PartPose.ZERO);

		PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
			CubeListBuilder.create().texOffs(0, 16).addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(-1.9f, 12f, 0f));
		rightLeg.addOrReplaceChild("right_pants",
			CubeListBuilder.create().texOffs(0, 32).addBox(-2f, 0f, -2f, 4f, 12f, 4f, outer), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
			CubeListBuilder.create().texOffs(16, 48).addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(1.9f, 12f, 0f));
		leftLeg.addOrReplaceChild("left_pants",
			CubeListBuilder.create().texOffs(0, 48).addBox(-2f, 0f, -2f, 4f, 12f, 4f, outer), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	/**
	 * Haltung setzen.
	 *
	 * @param walk   Laufphase (wächst mit der zurückgelegten Strecke)
	 * @param amount wie stark gelaufen wird, 0 = steht
	 * @param age    Zeit in Ticks (für leichte Bewegung im Stand)
	 */
	public void pose(Pose pose, float walk, float amount, float age) {
		root.getAllParts().forEach(ModelPart::resetPose);
		float idle = (float) Math.sin(age * 0.067f) * 0.05f;
		rightArm.zRot = 0.05f + idle;
		leftArm.zRot = -0.05f - idle;
		switch (pose) {
			case WALK -> {
				float swing = (float) Math.cos(walk * 0.6662f) * amount;
				rightLeg.xRot = swing * 1.4f;
				leftLeg.xRot = -swing * 1.4f;
				rightArm.xRot = -swing;
				leftArm.xRot = swing;
				head.xRot = 0.1f * amount;
			}
			case SIT -> {
				// wie beim Reiten: Beine nach vorne, leicht gespreizt, Hände auf den Knien
				rightLeg.xRot = -1.41f;
				rightLeg.yRot = 0.31f;
				leftLeg.xRot = -1.41f;
				leftLeg.yRot = -0.31f;
				rightArm.xRot = -0.63f;
				leftArm.xRot = -0.63f;
				// Beine baumeln ein wenig
				rightLeg.xRot += (float) Math.sin(age * 0.12f) * 0.12f;
				leftLeg.xRot += (float) Math.sin(age * 0.12f + 1.6f) * 0.12f;
			}
			case HANG -> {
				// hält sich mit dem rechten Arm fest, der Rest baumelt
				rightArm.xRot = Mth.PI;
				rightArm.zRot = 0f;
				leftArm.zRot = -0.25f - idle;
				float dangle = (float) Math.sin(age * 0.1f) * 0.25f;
				rightLeg.xRot = dangle;
				leftLeg.xRot = -dangle;
				body.zRot = 0f;
			}
		}
	}

	/** Position der rechten Hand bei {@link Pose#HANG} (Modell-Pixel), damit man das Mini Me daran aufhängen kann. */
	public static float[] hangingHand(boolean slim) {
		// Schulter (-5, 2 bzw. 2.5), Arm 10 Pixel lang nach oben gedreht; Mitte der Hand
		return new float[] {slim ? -5.5f : -6f, (slim ? 2.5f : 2f) - 9.5f, 0f};
	}
}
