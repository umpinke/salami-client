package dev.salamiclient.capes;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.art.CapeDesign;
import dev.salamiclient.capes.art.CapeDesigns;
import dev.salamiclient.capes.art.CapeRaster;
import dev.salamiclient.capes.art.Rgb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.11 {
import com.mojang.blaze3d.textures.FilterMode;
//?}

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * Spielt die gemalten Capes ({@link CapeDesigns}) im Spiel ab.
 *
 * <p>Die Frames werden einmal im Hintergrund gemalt – erst klein, damit sofort etwas zu sehen
 * ist, dann in der gewünschten Auflösung – und kompakt im Arbeitsspeicher gehalten (16 Bit je
 * Pixel, nur der benutzte Ausschnitt). Auf der Grafikkarte liegt je Cape eine Textur. Ein
 * Hintergrund-Thread bereitet den nächsten Frame vor, der Render-Thread lädt ihn nur noch
 * hoch – er rechnet selbst nichts.
 */
public final class AnimatedCapes {
	/** Breite der Textur = 64 × Scale: 16 = 1024 px, 32 = 2048 px, 64 = 4096 px („4K“). */
	public static final int SCALE_HD = 16;
	public static final int SCALE_2K = 32;
	public static final int SCALE_4K = 64;
	/** Erster, sofort fertiger Durchgang. */
	private static final int SCALE_PREVIEW = 8;
	/** Nicht mehr benutzte Capes nach dieser Zeit wieder freigeben. */
	private static final long IDLE_NANOS = 20_000_000_000L;

	private static final Map<String, Entry> ENTRIES = new HashMap<>();
	private static final ExecutorService PAINTER = daemon("Salami Cape Painter");
	private static final ExecutorService PLAYER = daemon("Salami Cape Player");
	/** Malt die Zeilen eines Frames nebeneinander, lässt dem Spiel aber die Hälfte der Kerne. */
	private static final ForkJoinPool ROWS = new ForkJoinPool(Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() / 2)));
	/** 16-Bit-Farbe → Pixel im Format von {@link NativeImage} (ABGR). */
	private static final int[] ABGR = new int[1 << 16];

	static {
		for (int i = 0; i < ABGR.length; i++) {
			int rgb = CapeRaster.rgb((short) i);
			ABGR[i] = 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | rgb >> 16 & 0xFF;
		}
	}

	private AnimatedCapes() {}

	private static ExecutorService daemon(String name) {
		return Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, name);
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		});
	}

	public static boolean has(String name) {
		return CapeDesigns.byName(name) != null;
	}

	/**
	 * Textur für den aktuellen Frame. Nur im Render-Thread aufrufen.
	 *
	 * @param scale    gewünschte Auflösung ({@link #SCALE_HD} …)
	 * @param animated {@code false} = Standbild
	 * @return {@code null}, solange noch nichts gemalt ist (wenige Zehntelsekunden) oder das Motiv unbekannt ist
	 */
	public static ClientAsset.@Nullable Texture texture(String name, int scale, boolean animated) {
		Entry entry = ENTRIES.get(name);
		if (entry == null) {
			CapeDesign design = CapeDesigns.byName(name);
			if (design == null) {
				return null;
			}
			entry = new Entry(design);
			ENTRIES.put(name, entry);
		}
		return entry.texture(scale, animated);
	}

	/** Jeden Client-Tick: Capes freigeben, die niemand mehr trägt. */
	public static void tick() {
		if (ENTRIES.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		for (Iterator<Entry> it = ENTRIES.values().iterator(); it.hasNext(); ) {
			Entry entry = it.next();
			if (now - entry.usedAt > IDLE_NANOS) {
				entry.close();
				it.remove();
			} else {
				entry.endTick();
			}
		}
	}

	/** Für Benchmarks und Tests: Arbeitsspeicher aller gemalten Frames in Bytes. */
	public static long frameBytes() {
		long sum = 0;
		for (Entry entry : ENTRIES.values()) {
			Frames frames = entry.frames;
			if (frames != null) {
				sum += (long) frames.data.length * frames.data[0].length * 2;
			}
		}
		return sum;
	}

	/** Für Tests: Auflösung, in der das Cape gerade gezeigt wird (0 = noch nichts), und der sichtbare Frame. */
	public static int shownScale(String name) {
		Entry entry = ENTRIES.get(name);
		return entry == null || entry.screen == null ? 0 : entry.screen.frames.scale;
	}

	public static int shownFrame(String name) {
		Entry entry = ENTRIES.get(name);
		return entry == null || entry.screen == null ? -1 : entry.screen.shown;
	}

	/** Für Tests: Zeit anhalten und einen bestimmten Frame zeigen ({@code -1} = wieder laufen lassen). */
	public static void freeze(int frame) {
		frozen = frame;
	}

	private static volatile int frozen = -1;

	/**
	 * Für den Leistungstest: {Uploads, ms je Upload (Render-Thread), ms je Frame-Schreiben
	 * (Hintergrund), ms fürs letzte Malen}; setzt die Zähler zurück.
	 */
	public static double[] takeStats() {
		double[] stats = {uploads, uploads == 0 ? 0 : uploadNanos / 1e6 / uploads, fills == 0 ? 0 : fillNanos / 1e6 / fills, paintMillis};
		uploads = 0;
		uploadNanos = 0;
		fills = 0;
		fillNanos = 0;
		return stats;
	}

	private static volatile int uploads;
	private static volatile long uploadNanos;
	private static volatile int fills;
	private static volatile long fillNanos;
	private static volatile long paintMillis;

	/** Fertig gemalte Frames einer Auflösung. */
	private record Frames(CapeDesign design, int scale, boolean animated, short[][] data) {}

	private static final class Entry {
		private final CapeDesign design;
		/** Zuletzt fertig gemalt (vom Maler-Thread gesetzt). */
		private volatile Frames frames;
		private volatile boolean painting;
		/** Größte Auflösung, in der zu groß für den Arbeitsspeicher war. */
		private volatile int tooBig = Integer.MAX_VALUE;
		private Screen screen;
		private Screen incoming;
		private long usedAt = System.nanoTime();
		/** Größter Wunsch in diesem Tick (mehrere Spieler können dasselbe Cape tragen). */
		private int wantScale;
		private int wantNext;
		private boolean wantAnimated;

		Entry(CapeDesign design) {
			this.design = design;
		}

		void endTick() {
			if (wantNext > 0) {
				wantScale = wantNext;
				wantNext = 0;
			}
		}

		ClientAsset.@Nullable Texture texture(int scale, boolean animated) {
			usedAt = System.nanoTime();
			wantNext = Math.max(wantNext, scale);
			wantAnimated = animated;
			if (wantScale == 0) {
				wantScale = scale;
			}
			Frames ready = frames;
			int goal = Math.min(wantScale, tooBig);
			if (goal <= 0) {
				return null;
			}
			if (!painting && (ready == null || ready.scale != goal || ready.animated != animated)) {
				painting = true;
				int next = ready == null ? Math.min(goal, SCALE_PREVIEW) : goal;
				PAINTER.execute(() -> paint(next, animated));
			}
			if (ready == null) {
				return null;
			}
			Screen target = incoming != null ? incoming : screen;
			if (target == null || target.frames != ready) {
				if (incoming != null) {
					incoming.close();
				}
				incoming = target = new Screen(ready);
			}
			int count = ready.data.length;
			int frozenAt = frozen;
			int wanted = count == 1 ? 0
				: frozenAt >= 0 ? frozenAt % count
				: (int) (System.currentTimeMillis() / design.frameMs % count);
			if (target.ready) {
				target.upload();
				if (target == incoming) {
					if (screen != null) {
						screen.close();
					}
					screen = incoming;
					incoming = null;
				}
			}
			if (!target.busy && !target.ready && target.shown != wanted) {
				target.busy = true;
				Screen fill = target;
				PLAYER.execute(() -> fill.fill(wanted));
			}
			return screen == null ? null : screen.asset;
		}

		/** Maler-Thread. */
		private void paint(int scale, boolean animated) {
			try {
				int count = animated ? design.frames : 1;
				int height = CapeRaster.height(scale);
				short[][] data = new short[count][CapeRaster.width(scale) * height];
				long start = System.nanoTime();
				for (int f = 0; f < count; f++) {
					// Standbild: nicht Frame 0, dort sind Glanzstreifen und Blitze gerade außer Sicht
					int frame = animated ? f : design.frames / 3;
					short[] out = data[f];
					ROWS.submit(() -> IntStream.range(0, height).parallel()
						.forEach(row -> CapeRaster.render(design, scale, frame, out, row, row + 1, new Rgb()))).join();
				}
				frames = new Frames(design, scale, animated, data);
				paintMillis = (System.nanoTime() - start) / 1_000_000;
				SalamiClient.LOG.debug("Cape {} gemalt: {} px breit, {} Frames, {} ms", design.name, 64 * scale, count,
					(System.nanoTime() - start) / 1_000_000);
			} catch (OutOfMemoryError e) {
				// zu wenig Arbeitsspeicher für diese Auflösung: bei der bisherigen bleiben
				tooBig = scale / 2;
				SalamiClient.LOG.warn("Cape {}: zu wenig Arbeitsspeicher für {} px, bleibe bei {} px", design.name, 64 * scale, 64 * tooBig);
			} catch (Throwable e) {
				tooBig = 0;
				SalamiClient.LOG.warn("Cape {} konnte nicht gemalt werden", design.name, e);
			} finally {
				painting = false;
			}
		}

		void close() {
			if (incoming != null) {
				incoming.close();
			}
			if (screen != null) {
				screen.close();
			}
			incoming = screen = null;
			frames = null;
		}
	}

	/**
	 * Die Textur eines Capes in einer Auflösung samt Zwischenspeicher für den benutzten Ausschnitt:
	 * der Spieler-Thread schreibt den nächsten Frame hinein, der Render-Thread lädt ihn hoch.
	 */
	private static final class Screen {
		private static int serial;

		private final Frames frames;
		private final Identifier id;
		private final CapeTexture texture;
		private final ClientAsset.Texture asset;
		private final int width;
		private final int height;
		/** Ausschnitt als RGBA; durchsichtige Stellen bleiben für immer 0. Nur unter {@code synchronized} benutzen. */
		private ByteBuffer buffer;
		/** Frame in der Textur. */
		private int shown = -1;
		private volatile int filled = -1;
		private volatile boolean busy;
		private volatile boolean ready;

		Screen(Frames frames) {
			this.frames = frames;
			width = CapeRaster.width(frames.scale);
			height = CapeRaster.height(frames.scale);
			id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/" + frames.design.name + "/" + serial++);
			texture = new CapeTexture(frames.design.name, CapeRaster.LAYOUT_W * frames.scale, CapeRaster.LAYOUT_H * frames.scale);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			asset = new ClientAsset.ResourceTexture(id, id);
			buffer = MemoryUtil.memCalloc(width * height * 4);
		}

		/** Spieler-Thread: Frame in den Zwischenspeicher schreiben. */
		synchronized void fill(int frame) {
			try {
				if (buffer == null) {
					return;
				}
				long start = System.nanoTime();
				int scale = frames.scale;
				long base = MemoryUtil.memAddress(buffer);
				short[] data = frames.data[frame];
				for (int cy = 0; cy < CapeRaster.UNITS_H; cy++) {
					for (int cx = 0; cx < CapeRaster.UNITS_W; cx++) {
						if (!CapeRaster.opaque(cx, cy)) {
							continue;
						}
						for (int y = cy * scale; y < (cy + 1) * scale; y++) {
							int from = y * width + cx * scale;
							long at = base + from * 4L;
							for (int i = 0; i < scale; i++) {
								MemoryUtil.memPutInt(at + i * 4L, ABGR[data[from + i] & 0xFFFF]);
							}
						}
					}
				}
				fillNanos += System.nanoTime() - start;
				fills++;
				filled = frame;
				ready = true;
			} finally {
				busy = false;
			}
		}

		/** Render-Thread: geschriebenen Frame in die Textur laden – nur den benutzten Ausschnitt. */
		synchronized void upload() {
			if (buffer == null) {
				return;
			}
			long start = System.nanoTime();
			//? if >=26.2 {
			/*RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), buffer, 0, 0, 0, 0, width, height);
			*///?} else {
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), buffer, NativeImage.Format.RGBA, 0, 0, 0, 0, width, height);
			//?}
			uploadNanos += System.nanoTime() - start;
			uploads++;
			shown = filled;
			ready = false;
		}

		void close() {
			synchronized (this) {
				if (buffer != null) {
					MemoryUtil.memFree(buffer);
					buffer = null;
				}
			}
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	/**
	 * Leere (durchsichtige) Textur im Cape-Layout. Weich gefiltert: bei 2K/4K sind die
	 * Texturpixel kleiner als die Bildschirmpixel, sonst flimmert es.
	 */
	private static final class CapeTexture extends DynamicTexture {
		CapeTexture(String name, int width, int height) {
			super(() -> "salami cape " + name, width, height, true);
			// einmal leer hochladen, danach kommt nur noch der benutzte Ausschnitt; das große Bild selbst
			// wird nicht mehr gebraucht. Ein winziges bleibt stehen statt keinem: in 26.3 schließen close()
			// und setPixels() das Bild ohne Prüfung – ohne Bild brach close() mit einer NullPointerException
			// ab, bevor die Textur auf der Grafikkarte frei war (jeder Wechsel von Cape oder Auflösung ließ
			// so bis zu 32 MB Grafikspeicher liegen).
			upload();
			setPixels(new NativeImage(1, 1, false));
			//? if >=1.21.11 {
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			//?} else {
			/*setFilter(true, false);
			*///?}
		}
	}
}
