package dev.salamiclient.theme;

import dev.salamiclient.SalamiClient;
import net.minecraft.resources.Identifier;

/**
 * Tauscht Vanilla-Texturen gegen Salami-Texturen, solange der Salami-Modus aktiv ist.
 *
 * <p>Wird aus Mixins pro Frame aufgerufen – daher nur billige Prüfungen, keine
 * Allokation im Normalfall. Kein Resourcepack-Reload nötig: Die Sprites liegen
 * unter {@code assets/salami/textures/gui/sprites} und landen automatisch im GUI-Atlas.
 */
public final class SalamiTextures {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/title/salami.png");
	private static final Identifier EDITION = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/title/edition.png");
	private static final Identifier BUTTON = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "widget/button");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "widget/button_disabled");

	private SalamiTextures() {}

	private static boolean active() {
		SalamiClient client = SalamiClient.get();
		return client != null && client.modules() != null && client.theme() == Theme.SALAMI;
	}

	/** Titelbild-Logo (auch das "Minceraft"-Easter-Egg). */
	public static Identifier logo(Identifier original) {
		return active() ? LOGO : original;
	}

	/** Untertitel unter dem Logo ("Java Edition" -> "CLIENT"). */
	public static Identifier edition(Identifier original) {
		return active() ? EDITION : original;
	}

	/** Button-Hintergründe (normal / hervorgehoben / deaktiviert). */
	public static Identifier buttonSprite(Identifier original) {
		if (!active() || !"minecraft".equals(original.getNamespace())) {
			return original;
		}
		return switch (original.getPath()) {
			case "widget/button" -> BUTTON;
			case "widget/button_highlighted" -> BUTTON_HIGHLIGHTED;
			case "widget/button_disabled" -> BUTTON_DISABLED;
			default -> original;
		};
	}
}
