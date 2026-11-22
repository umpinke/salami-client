package dev.salamiclient.gui.skin;

import dev.salamiclient.SalamiClient;

import java.util.Map;

/**
 * Welcher {@link Skin} gilt. Der Launcher übergibt den im Launcher gewählten Look beim Start als
 * {@code -Dsalami.look=theke|werkbank|salumeria}; ohne Angabe (anderer Launcher, Entwicklung) gilt
 * die Theke. Die Gametests setzen den Look über {@code -PsalamiLook=…}.
 *
 * <p>Jeder Look liegt in seinem eigenen Paket ({@code gui.skin.theke.ThekeSkin} usw.) und wird hier
 * nur über den Klassennamen geladen – ein neuer Look braucht keine Änderung an dieser Datei, solange
 * er dem Schema folgt.
 */
public final class Skins {
	public static final String DEFAULT = "theke";

	private static final Map<String, String> CLASSES = Map.of(
		"theke", "dev.salamiclient.gui.skin.theke.ThekeSkin",
		"werkbank", "dev.salamiclient.gui.skin.werkbank.WerkbankSkin",
		"salumeria", "dev.salamiclient.gui.skin.salumeria.SalumeriaSkin"
	);

	private static Skin current;

	private Skins() {}

	/** Der Look dieser Sitzung. */
	public static Skin current() {
		if (current == null) {
			current = create(System.getProperty("salami.look", DEFAULT));
		}
		return current;
	}

	/** Look wechseln (Gametests). */
	public static void use(String id) {
		current = create(id);
	}

	private static Skin create(String id) {
		Skin skin = instantiate(CLASSES.get(id));
		if (skin == null && !DEFAULT.equals(id)) {
			SalamiClient.LOG.warn("Look '{}' unbekannt oder nicht verfügbar – nehme '{}'", id, DEFAULT);
			skin = instantiate(CLASSES.get(DEFAULT));
		}
		if (skin == null) {
			throw new IllegalStateException("Salami-Look '" + DEFAULT + "' fehlt");
		}
		return skin;
	}

	private static Skin instantiate(String className) {
		if (className == null) {
			return null;
		}
		try {
			return (Skin) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | ClassCastException e) {
			return null;
		}
	}
}
