package dev.salamiclient.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Rechnet Weltpositionen in GUI-Koordinaten um (für HUD-Elemente über Entities).
 *
 * <p>Die Projektionsmatrix wird jedes Frame per Mixin aus {@code GameRenderer#renderLevel}
 * übernommen (inkl. FOV-Effekten und View-Bobbing), die Blickrichtung kommt von der Kamera.
 */
public final class Projection {
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static boolean captured;

	// Wiederverwendete Objekte: keine Allokation pro Aufruf im Render-Pfad
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Quaternionf ROTATION = new Quaternionf();
	private static final Vector4f TMP = new Vector4f();

	private Projection() {}

	/** Aufgerufen vom GameRendererMixin mit der finalen Projektionsmatrix des Frames. */
	public static void capture(Matrix4f projection) {
		PROJECTION.set(projection);
		captured = true;
	}

	/** Bereitet die Umrechnung für das aktuelle Frame vor; {@code false}, wenn (noch) nicht möglich. */
	public static boolean begin() {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (!captured || !camera.isInitialized()) {
			return false;
		}
		camera.rotation().conjugate(ROTATION);
		VIEW_PROJECTION.set(PROJECTION).rotate(ROTATION);
		return true;
	}

	/**
	 * Projiziert eine Weltposition. Schreibt GUI-x/-y nach {@code out[0..1]}.
	 *
	 * @return {@code false}, wenn der Punkt hinter der Kamera liegt
	 */
	public static boolean toGui(Vec3 world, int guiWidth, int guiHeight, float[] out) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		TMP.set((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f);
		VIEW_PROJECTION.transform(TMP);
		if (TMP.w <= 0.05f) {
			return false;
		}
		float ndcX = TMP.x / TMP.w;
		float ndcY = TMP.y / TMP.w;
		out[0] = (ndcX + 1f) * 0.5f * guiWidth;
		out[1] = (1f - ndcY) * 0.5f * guiHeight;
		return true;
	}
}
