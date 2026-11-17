package dev.salamiclient.minime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Skins für Mini Mes: Minecraft-Name → Skin-Textur. Fragt Mojang (Name → UUID → Profil mit
 * Textur-Adresse), lädt das Bild von textures.minecraft.net und registriert es als Textur.
 * Ergebnisse bleiben im Speicher; nicht gefundene Namen werden erst nach ein paar Minuten
 * erneut gefragt. Alle Aufrufe im Render- bzw. Client-Thread.
 */
public final class MiniMeSkins {
	/** Geladener Skin: Textur und ob das schmale Armmodell („Alex“) gilt. */
	public record Skin(Identifier texture, boolean slim) {}

	public enum State { LOADING, READY, NOT_FOUND }

	public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long RETRY_MS = 5 * 60_000L;
	private static final int MAX_SKIN_BYTES = 1024 * 1024;
	private static final int MAX_CACHED = 64;

	private static final class Entry {
		volatile State state = State.LOADING;
		@Nullable Skin skin;
		long failedAt;
	}

	private static final Map<String, Entry> CACHE = new HashMap<>();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Salami Mini Me Skins");
		thread.setDaemon(true);
		return thread;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private MiniMeSkins() {}

	/** Skin zum Namen oder {@code null}, solange er lädt oder es den Namen nicht gibt. */
	public static @Nullable Skin get(String name) {
		Entry entry = entry(name);
		return entry == null ? null : entry.skin;
	}

	/** Ladezustand für das Cosmetics-Menü. */
	public static State state(String name) {
		Entry entry = entry(name);
		return entry == null ? State.NOT_FOUND : entry.state;
	}

	private static @Nullable Entry entry(String name) {
		if (name == null || !NAME.matcher(name).matches()) {
			return null;
		}
		String key = name.toLowerCase(Locale.ROOT);
		Entry entry = CACHE.get(key);
		long now = System.currentTimeMillis();
		if (entry != null && !(entry.state == State.NOT_FOUND && now - entry.failedAt > RETRY_MS)) {
			return entry;
		}
		if (CACHE.size() >= MAX_CACHED) {
			release();
		}
		Entry fresh = new Entry();
		CACHE.put(key, fresh);
		LOADER.execute(() -> load(key, fresh));
		return fresh;
	}

	/** Alles vergessen (Texturen freigeben), z. B. wenn der Speicher voll ist. */
	private static void release() {
		Minecraft mc = Minecraft.getInstance();
		for (Entry entry : CACHE.values()) {
			if (entry.skin != null) {
				mc.getTextureManager().release(entry.skin.texture());
			}
		}
		CACHE.clear();
	}

	private static void load(String key, Entry entry) {
		try {
			JsonObject profile = json("https://api.mojang.com/users/profiles/minecraft/" + key);
			String id = profile == null ? null : string(profile, "id");
			if (id == null || !id.matches("[0-9a-fA-F]{32}")) {
				fail(entry);
				return;
			}
			JsonObject session = json("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
			String skinUrl = null;
			boolean slim = false;
			JsonElement properties = session == null ? null : session.get("properties");
			if (properties instanceof JsonArray array) {
				for (JsonElement property : array) {
					if (property.isJsonObject() && "textures".equals(string(property.getAsJsonObject(), "name"))) {
						String decoded = new String(Base64.getDecoder().decode(string(property.getAsJsonObject(), "value")), StandardCharsets.UTF_8);
						JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
						JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
						if (skin != null) {
							skinUrl = string(skin, "url");
							JsonObject metadata = skin.getAsJsonObject("metadata");
							slim = metadata != null && "slim".equals(string(metadata, "model"));
						}
					}
				}
			}
			if (skinUrl == null) {
				fail(entry); // Spieler ohne eigenen Skin: Standard-Skin zeigt der Renderer
				return;
			}
			URI uri = URI.create(skinUrl.replace("http://", "https://"));
			// Nur Mojangs Textur-Server – keine fremden Adressen aus einem Profil laden
			if (uri.getHost() == null || !uri.getHost().endsWith(".minecraft.net")) {
				fail(entry);
				return;
			}
			HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
			byte[] data;
			try (InputStream in = response.body()) {
				data = in.readNBytes(MAX_SKIN_BYTES + 1);
			}
			if (response.statusCode() != 200 || data.length > MAX_SKIN_BYTES) {
				fail(entry);
				return;
			}
			boolean slimModel = slim;
			Minecraft.getInstance().execute(() -> register(key, entry, data, slimModel));
		} catch (IOException | RuntimeException e) {
			SalamiClient.LOG.debug("Mini-Me-Skin für {} nicht geladen: {}", key, e.toString());
			fail(entry);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail(entry);
		}
	}

	private static void register(String key, Entry entry, byte[] data, boolean slim) {
		NativeImage image = null;
		try {
			image = NativeImage.read(data);
			if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
				image.close();
				fail(entry);
				return;
			}
			boolean legacy = image.getHeight() == 32;
			if (legacy) {
				image = legacy(image);
			}
			// wie Minecraft: Grundschicht deckend, bei alten Skins eine ganz deckende
			// Hutschicht durchsichtig (sonst trägt z. B. Notch einen schwarzen Kasten)
			setNoAlpha(image, 0, 0, 32, 16);
			if (legacy) {
				notchTransparency(image, 32, 0, 64, 32);
			}
			setNoAlpha(image, 0, 16, 64, 32);
			setNoAlpha(image, 16, 48, 48, 64);
			Identifier id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "minime/" + key);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "salami mini me " + key, image));
			image = null; // gehört jetzt der Textur
			entry.skin = new Skin(id, slim);
			entry.state = State.READY;
		} catch (IOException | RuntimeException e) {
			SalamiClient.LOG.debug("Mini-Me-Skin für {} ungültig: {}", key, e.toString());
			fail(entry);
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}

	/** Alte 64x32-Skins: auf 64x64 erweitern, linkes Bein und linker Arm gespiegelt wie rechts (wie Minecraft). */
	private static NativeImage legacy(NativeImage old) {
		NativeImage image = new NativeImage(64, 64, true);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				image.setPixel(x, y, y < 32 ? old.getPixel(x, y) : 0);
			}
		}
		old.close();
		// Bein: oben, unten, vier Seiten
		mirrorCopy(image, 4, 16, 16, 32, 4, 4);
		mirrorCopy(image, 8, 16, 16, 32, 4, 4);
		mirrorCopy(image, 0, 20, 24, 32, 4, 12);
		mirrorCopy(image, 4, 20, 16, 32, 4, 12);
		mirrorCopy(image, 8, 20, 8, 32, 4, 12);
		mirrorCopy(image, 12, 20, 16, 32, 4, 12);
		// Arm
		mirrorCopy(image, 44, 16, -8, 32, 4, 4);
		mirrorCopy(image, 48, 16, -8, 32, 4, 4);
		mirrorCopy(image, 40, 20, 0, 32, 4, 12);
		mirrorCopy(image, 44, 20, -8, 32, 4, 12);
		mirrorCopy(image, 48, 20, -16, 32, 4, 12);
		mirrorCopy(image, 52, 20, -8, 32, 4, 12);
		return image;
	}

	/** Rechteck waagrecht gespiegelt um (dx, dy) versetzt kopieren. */
	private static void mirrorCopy(NativeImage image, int x0, int y0, int dx, int dy, int w, int h) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setPixel(x0 + dx + x, y0 + dy + y, image.getPixel(x0 + w - 1 - x, y0 + y));
			}
		}
	}

	private static void setNoAlpha(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
			}
		}
	}

	/** Ist der Bereich nirgends durchsichtig, wird er ganz durchsichtig (Minecrafts „Notch-Trick“). */
	private static void notchTransparency(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				if ((image.getPixel(x, y) >>> 24) < 128) {
					return;
				}
			}
		}
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				image.setPixel(x, y, 0);
			}
		}
	}

	private static void fail(Entry entry) {
		entry.failedAt = System.currentTimeMillis();
		entry.state = State.NOT_FOUND;
	}

	private static @Nullable JsonObject json(String url) throws IOException, InterruptedException {
		HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			return null;
		}
		JsonElement parsed = JsonParser.parseString(response.body());
		return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
	}

	private static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
