package dev.salamiclient.util;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Anmeldung bei den Salami-Servern (Capes, Emotes, Freunde) wie beim Betreten eines Minecraft-Servers: der Server
 * schickt eine serverId, das Spiel meldet sie bei Mojang an ({@code joinServer}), der Server fragt dort nach.
 *
 * <p>Das Spiel unterschreibt nur serverIds im eigenen Format {@code salami-<32–64 Hex-Ziffern>}. Ein echter
 * Minecraft-Server-Hash besteht nur aus Hex-Ziffern (und höchstens einem Minus vorne), sieht also nie so aus – ein
 * übernommener Salami-Server kann das Spiel damit nicht die Anmeldung für einen fremden Minecraft-Server
 * unterschreiben lassen.
 */
public final class AuthChallenge {
	private static final Pattern SERVER_ID = Pattern.compile("salami-[0-9a-f]{32,64}");

	/** Der Server hat keine oder eine fremde serverId geschickt – es wurde nichts bei Mojang angemeldet. */
	public static final class RefusedException extends IllegalStateException {
		RefusedException(String message) {
			super(message);
		}
	}

	private AuthChallenge() {}

	/** {@code true}, wenn diese serverId vom Salami-Server stammen kann (und kein Minecraft-Server-Hash ist). */
	public static boolean valid(@Nullable String serverId) {
		return serverId != null && SERVER_ID.matcher(serverId).matches();
	}

	/** Gibt die serverId zurück, wenn das Spiel sie unterschreiben darf; sonst {@link RefusedException}. */
	public static String require(@Nullable String serverId) {
		if (serverId == null) {
			throw new RefusedException("Server hat keine serverId geschickt");
		}
		if (!valid(serverId)) {
			// Inhalt nicht ins Log: kann beliebig lang sein
			throw new RefusedException("Server hat eine fremde serverId geschickt (" + serverId.length()
				+ " Zeichen), Anmeldung bei Mojang verweigert");
		}
		return serverId;
	}
}
