package dev.salamiclient.emotes;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Emotes: kurze Animationen des Spielermodells. Die Namen ({@link #id()}) kennt auch der Cape-Server
 * ({@code server/capes/server.mjs}, {@code EMOTES}) – neue dort nachtragen.
 *
 * @param durationMs Länge; 0 = läuft, bis du dich bewegst (höchstens {@link #MAX_LOOP_MS})
 */
public enum Emote {
	WAVE(2_400),
	CHEER(2_400),
	CLAP(2_600),
	DANCE(0),
	FACEPALM(2_600),
	POINT(2_200),
	SHRUG(1_800),
	TPOSE(0);

	/** Endlose Emotes (Tanzen, T-Pose) hören spätestens nach dieser Zeit auf. */
	public static final long MAX_LOOP_MS = 15_000L;

	private final long durationMs;

	Emote(long durationMs) {
		this.durationMs = durationMs;
	}

	/** Länge in ms; endlose Emotes haben {@link #MAX_LOOP_MS}. */
	public long durationMs() {
		return durationMs > 0 ? durationMs : MAX_LOOP_MS;
	}

	public boolean loops() {
		return durationMs == 0;
	}

	/** Name für Server und Übersetzungen ({@code wave}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "salami.emote." + id();
	}

	/** Emote zu einem Namen oder {@code null} (unbekannt, z. B. von einem neueren Client). */
	public static @Nullable Emote of(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Emote emote : values()) {
			if (emote.id().equals(id)) {
				return emote;
			}
		}
		return null;
	}
}
