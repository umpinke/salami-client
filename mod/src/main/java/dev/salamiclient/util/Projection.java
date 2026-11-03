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
	/** Näher an der Kameraebene (oder dahinter) gilt ein Punkt als nicht projizierbar. */
	private static final float MIN_DEPTH = 0.05f;

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
		return toGui(world, (float) guiWidth, (float) guiHeight, out);
	}

	/** Wie {@link #toGui(Vec3, int, int, float[])} mit exakter (nicht gerundeter) GUI-Größe. */
	public static boolean toGui(Vec3 world, float guiWidth, float guiHeight, float[] out) {
		clip(world);
		if (TMP.w <= MIN_DEPTH) {
			return false;
		}
		float ndcX = TMP.x / TMP.w;
		float ndcY = TMP.y / TMP.w;
		out[0] = (ndcX + 1f) * 0.5f * guiWidth;
		out[1] = (1f - ndcY) * 0.5f * guiHeight;
		return true;
	}

	/**
	 * Wie {@link #toGui}, schreibt zusätzlich nach {@code out[2]}, wie viele GUI-Pixel ein Block an
	 * dieser Stelle groß ist – für Anzeigen, die wie Namensschilder zur Kamera gedreht sind.
	 */
	public static boolean toGuiScaled(Vec3 world, float guiWidth, float guiHeight, float[] out) {
		if (!toGui(world, guiWidth, guiHeight, out)) {
			return false;
		}
		// m11 = 1 / tan(FOV/2): ein Block in Tiefe w ist m11 / w der halben Bildhöhe
		out[2] = PROJECTION.m11() * 0.5f * guiHeight / TMP.w;
		return true;
	}

	/**
	 * Für Linien zu einem Punkt, der auch hinter der Kamera liegen darf. Vor der Kamera: die
	 * projizierte Position (wie {@link #toGui}, auch außerhalb des Bildes). Dahinter: ein Punkt weit
	 * jenseits des Bildschirmrands in der Richtung, in die man sich drehen müsste.
	 */
	public static void toGuiOrEdge(Vec3 world, float guiWidth, float guiHeight, float[] out) {
		if (toGui(world, guiWidth, guiHeight, out)) {
			return;
		}
		// x/y im Clip-Raum zeigen auch hinter der Kamera in die richtige Richtung – nur das Teilen
		// durch das negative w würde sie spiegeln
		float dx = TMP.x;
		float dy = -TMP.y;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 1e-4f) {
			dx = 0f; // genau hinter der Kamera: nach unten
			dy = 1f;
			length = 1f;
		}
		float reach = guiWidth + guiHeight; // sicher außerhalb des Bildes
		out[0] = guiWidth * 0.5f + dx / length * reach;
		out[1] = guiHeight * 0.5f + dy / length * reach;
	}

	/** Weltposition → Clip-Raum (Ergebnis in {@link #TMP}). */
	private static void clip(Vec3 world) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		TMP.set((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f);
		VIEW_PROJECTION.transform(TMP);
	}
}
