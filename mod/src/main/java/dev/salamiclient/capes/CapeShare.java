package dev.salamiclient.capes;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Was andere Salami-Spieler vom eigenen Cape sehen sollen: ein mitgeliefertes Cape,
 * ein eigenes Bild (wird erst nach Prüfung durch einen Admin sichtbar) oder keins.
 * {@code key} unterscheidet die Zustände, damit nur Änderungen neu geteilt werden.
 */
public record CapeShare(String key, @Nullable String builtin, byte @Nullable [] png) {
	public static final CapeShare NONE = new CapeShare("none", null, null);

	public static CapeShare builtin(String name) {
		return new CapeShare("builtin:" + name, name, null);
	}

	public static CapeShare png(byte[] data) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
			return new CapeShare("png:" + HexFormat.of().formatHex(hash), null, data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	JsonElement toJson() {
		JsonObject json = new JsonObject();
		if (builtin != null) {
			json.addProperty("builtin", builtin);
			return json;
		}
		if (png != null) {
			json.addProperty("png", Base64.getEncoder().encodeToString(png));
			return json;
		}
		return JsonNull.INSTANCE;
	}
}
