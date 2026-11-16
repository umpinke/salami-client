import dev.salamiclient.capes.art.CapeDesign;
import dev.salamiclient.capes.art.CapeDesigns;
import dev.salamiclient.capes.art.CapeRaster;
import dev.salamiclient.capes.art.Rgb;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Schreibt die gemalten Capes der Mod ({@code mod/…/capes/art}) als Bilder heraus – für die
 * Vorschau im Launcher und zum Ansehen beim Entwerfen. Aufruf über
 * {@code python branding/generate_capes.py}, das übersetzt und startet diese Datei.
 *
 * <pre>
 * CapeExport frames &lt;ordner&gt; &lt;scale&gt; &lt;spalten&gt; &lt;scale klein&gt; &lt;spalten klein&gt;
 *     je Cape name.png (ein Frame, ganzes Layout), name.frames.png (alle Frames als Raster,
 *     nur der benutzte Ausschnitt), name.face.png (alle Frames, nur die Außenseite, klein)
 *     sowie capes.json mit Frame-Zahl und Tempo
 * CapeExport sheet  &lt;ordner&gt; &lt;scale&gt; [name]      Übersicht: Außenseite zu mehreren Zeitpunkten
 * CapeExport time   &lt;scale&gt;                       Rechenzeit je Cape messen
 * </pre>
 */
public final class CapeExport {
	public static void main(String[] args) throws IOException {
		switch (args[0]) {
			case "frames" -> frames(new File(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
			case "sheet" -> sheet(new File(args[1]), Integer.parseInt(args[2]), args.length > 3 ? args[3] : null);
			case "time" -> time(Integer.parseInt(args[1]));
			default -> throw new IllegalArgumentException(args[0]);
		}
	}

	private static short[] render(CapeDesign design, int scale, int frame) {
		int h = CapeRaster.height(scale);
		short[] out = new short[CapeRaster.width(scale) * h];
		IntStream.range(0, h).parallel().forEach(row -> CapeRaster.render(design, scale, frame, out, row, row + 1, new Rgb()));
		return out;
	}

	/** Ein Frame in ein Bild kopieren; (ox, oy) = linke obere Ecke des Ausschnitts im Bild. */
	private static void draw(BufferedImage image, short[] frame, int scale, int ox, int oy) {
		int w = CapeRaster.width(scale);
		int h = CapeRaster.height(scale);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (CapeRaster.opaque(x / scale, y / scale)) {
					image.setRGB(ox + x, oy + y, 0xFF000000 | CapeRaster.rgb(frame[y * w + x]));
				}
			}
		}
	}

	private static void frames(File dir, int scale, int columns, int faceScale, int faceColumns) throws IOException {
		dir.mkdirs();
		StringBuilder json = new StringBuilder();
		for (CapeDesign design : CapeDesigns.all()) {
			int w = CapeRaster.width(scale);
			int h = CapeRaster.height(scale);
			BufferedImage atlas = new BufferedImage(w * columns, h * rows(design, columns), BufferedImage.TYPE_INT_ARGB);
			int faceW = 10 * faceScale;
			int faceH = 16 * faceScale;
			BufferedImage faces = new BufferedImage(faceW * faceColumns, faceH * rows(design, faceColumns), BufferedImage.TYPE_INT_RGB);
			for (int f = 0; f < design.frames; f++) {
				short[] frame = render(design, scale, f);
				draw(atlas, frame, scale, f % columns * w, f / columns * h);
				if (f == design.frames / 3) {
					// Standbild: nicht Frame 0, dort sind Glanzstreifen und Blitze gerade außer Sicht
					BufferedImage still = new BufferedImage(CapeRaster.LAYOUT_W * scale, CapeRaster.LAYOUT_H * scale, BufferedImage.TYPE_INT_ARGB);
					draw(still, frame, scale, 0, 0);
					ImageIO.write(still, "png", new File(dir, design.name + ".png"));
				}
				// nur die Außenseite, für die kleinen Karten der Galerie
				BufferedImage small = new BufferedImage(CapeRaster.LAYOUT_W * faceScale, CapeRaster.LAYOUT_H * faceScale, BufferedImage.TYPE_INT_ARGB);
				draw(small, render(design, faceScale, f), faceScale, 0, 0);
				faces.getGraphics().drawImage(small.getSubimage(faceScale, faceScale, faceW, faceH),
					f % faceColumns * faceW, f / faceColumns * faceH, null);
			}
			ImageIO.write(atlas, "png", new File(dir, design.name + ".frames.png"));
			ImageIO.write(faces, "png", new File(dir, design.name + ".face.png"));
			json.append(json.isEmpty() ? "" : ",\n")
				.append("    \"").append(design.name).append("\": {\"frames\": ").append(design.frames)
				.append(", \"frameMs\": ").append(design.frameMs).append("}");
			System.out.println(design.name);
		}
		String head = "{\n  \"sheet\": {\"scale\": " + scale + ", \"columns\": " + columns + "},\n"
			+ "  \"face\": {\"scale\": " + faceScale + ", \"columns\": " + faceColumns + "},\n  \"capes\": {\n";
		java.nio.file.Files.writeString(new File(dir, "capes.json").toPath(), head + json + "\n  }\n}\n");
	}

	private static int rows(CapeDesign design, int columns) {
		return (design.frames + columns - 1) / columns;
	}

	private static void sheet(File dir, int scale, String only) throws IOException {
		dir.mkdirs();
		int shots = 4;
		int faceW = 10 * scale;
		int faceH = 16 * scale;
		int wingW = 12 * scale;
		int wingH = 20 * scale;
		for (CapeDesign design : CapeDesigns.all()) {
			if (only != null && !only.equals(design.name)) {
				continue;
			}
			BufferedImage sheet = new BufferedImage((faceW + 8) * shots + wingW + 8, wingH, BufferedImage.TYPE_INT_RGB);
			for (int i = 0; i < shots; i++) {
				short[] frame = render(design, scale, design.frames * i / shots);
				BufferedImage full = new BufferedImage(CapeRaster.LAYOUT_W * scale, CapeRaster.LAYOUT_H * scale, BufferedImage.TYPE_INT_ARGB);
				draw(full, frame, scale, 0, 0);
				sheet.getGraphics().drawImage(full.getSubimage(scale, scale, faceW, faceH), i * (faceW + 8), 0, null);
				if (i == 1) {
					sheet.getGraphics().drawImage(full.getSubimage(34 * scale, 2 * scale, wingW, wingH), shots * (faceW + 8), 0, null);
				}
			}
			ImageIO.write(sheet, "png", new File(dir, design.name + ".png"));
		}
	}

	private static void time(int scale) {
		for (CapeDesign design : CapeDesigns.all()) {
			render(design, scale, 0);
			long start = System.nanoTime();
			for (int f = 0; f < design.frames; f++) {
				render(design, scale, f);
			}
			System.out.printf("%-8s %2d Frames  %6.2f s%n", design.name, design.frames, (System.nanoTime() - start) / 1e9);
		}
	}
}
