package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.CapeShare;
import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.PngInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Salami Capes: ein eigenes Cape (und auf Wunsch dieselbe Textur auf den Elytren) für
 * den eigenen Spieler. {@code LAUNCHER} nimmt das im Launcher gewählte Cape
 * ({@code -Dsalami.cape=builtin:<name> | <pfad> | none}) – beim Start aus dem Launcher
 * ist das immer die Ausgangseinstellung; daneben die mitgelieferten
 * Salami-Designs und {@code CUSTOM} = {@code config/salami/cape.png} (Cape-Layout 64x32
 * oder ein Vielfaches).
 *
 * <p>Andere Salami-Spieler sehen dein Cape über den Cape-Server ({@link SharedCapes}):
 * mitgelieferte sofort, eigene Bilder erst nach Freigabe durch einen Admin. Spieler ohne
 * Salami Client sehen weiter dein Mojang-Cape.
 */
public final class CapesModule extends Module {
	public enum Cape { LAUNCHER, SALAMI, PEPPERONI, BOARD, CUSTOM }

	/** Vom Launcher übergebene Auswahl (siehe {@code salami_capes.rs}). */
	private static final String LAUNCHER_PROPERTY = "salami.cape";

	private static final Identifier CUSTOM_TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/custom");
	/** Eigene Capes: nur PNG bis 4 MiB, Breite = 2 × Höhe, 64–2048 px breit in 64er-Schritten (wie im Launcher). */
	private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
	private static final int MIN_WIDTH = 64;
	private static final int MAX_WIDTH = 2048;
	private static CapesModule instance;

	private final ModeSetting<Cape> cape = add(new ModeSetting<>("cape", Cape.LAUNCHER));
	private final BoolSetting elytra = add(new BoolSetting("elytra", true));
	private final BoolSetting others = add(new BoolSetting("others", true));

	/** Geladenes eigenes Cape ({@code null} = keins oder Fehler). */
	private ClientAsset.Texture custom;
	private boolean customTried;
	/** Bild des eigenen Capes fürs Teilen ({@code NONE}, wenn es nicht geladen werden konnte). */
	private CapeShare customShare = CapeShare.NONE;
	/** Was andere vom eigenen Cape sehen sollen – gesetzt von {@link #texture()}. */
	private CapeShare share = CapeShare.NONE;
	private final Map<Cape, ClientAsset.Texture> builtIn = new EnumMap<>(Cape.class);
	/** getSkin() läuft jedes Frame für jeden Spieler – Ergebnis je Spieler wiederverwenden. */
	private final Map<UUID, SkinCache> skins = new HashMap<>();
	/** Zuletzt übernommene Launcher-Auswahl (siehe {@link #texture()}). */
	private String appliedLauncherSelection;

	public CapesModule() {
		super("capes", Category.VISUAL, true);
		instance = this;
		cape.onChange(value -> customTried = false);
	}

	public static Path customCapeFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(SalamiClient.MOD_ID).resolve("cape.png");
	}

	@Override
	protected void onEnable() {
		customTried = false;
	}

	private record SkinCache(PlayerSkin base, PlayerSkin result) {}

	/** Für {@code AbstractClientPlayerMixin}: Skin des Spielers, ggf. mit Salami-Cape. */
	public static PlayerSkin apply(UUID player, PlayerSkin skin) {
		CapesModule module = instance;
		if (module == null || !module.isEnabled()) {
			return skin;
		}
		ClientAsset.Texture texture;
		if (player.equals(mc().getUser().getProfileId())) {
			texture = module.texture();
		} else if (module.others.isOn()) {
			// Andere Salami-Spieler: Cape vom Cape-Server (nur mitgelieferte und freigegebene)
			texture = SharedCapes.capeOf(player, module::builtInNamed);
		} else {
			texture = null;
		}
		if (texture == null) {
			return skin;
		}
		// getSkin() läuft jedes Frame – Ergebnis wiederverwenden, solange sich nichts ändert
		ClientAsset.Texture elytraTexture = module.elytra.isOn() ? texture : skin.elytra();
		SkinCache cached = module.skins.get(player);
		if (cached == null || cached.base() != skin || !cached.result().cape().equals(texture) || cached.result().elytra() != elytraTexture) {
			if (module.skins.size() > 512) {
				module.skins.clear();
			}
			cached = new SkinCache(skin, new PlayerSkin(skin.body(), texture, elytraTexture, skin.model(), skin.secure()));
			module.skins.put(player, cached);
		}
		return cached.result();
	}

	/**
	 * Für {@link SharedCapes}: was andere Salami-Spieler von dir sehen sollen.
	 * {@code null} = noch unklar (keine Welt geladen).
	 */
	public static @Nullable CapeShare sharedState() {
		CapesModule module = instance;
		if (module == null || mc().player == null) {
			return null;
		}
		if (!module.isEnabled()) {
			return CapeShare.NONE;
		}
		module.texture();
		return module.share;
	}

	private ClientAsset.Texture texture() {
		// Aus dem Launcher gestartet: dessen Auswahl gilt ab Spielstart – auch wenn in der
		// Config noch ein anderes Cape steht (sonst sieht man immer z. B. das Salami-Cape).
		// Im Spiel umstellen geht danach weiterhin, bis zum nächsten Start.
		String launcher = System.getProperty(LAUNCHER_PROPERTY);
		if (launcher != null && !launcher.isBlank() && !launcher.equals(appliedLauncherSelection)) {
			appliedLauncherSelection = launcher;
			cape.set(Cape.LAUNCHER);
		}
		Cape selected = cape.get();
		if (selected == Cape.LAUNCHER) {
			return fromLauncher(System.getProperty(LAUNCHER_PROPERTY));
		}
		if (selected == Cape.CUSTOM) {
			return customFrom(CapesModule::customCapeFile);
		}
		return own(selected);
	}

	/** Mitgeliefertes Cape als eigenes Cape (wird so auch geteilt). */
	private ClientAsset.Texture own(Cape selected) {
		String name = selected.name().toLowerCase(Locale.ROOT);
		if (!name.equals(share.builtin())) {
			share = CapeShare.builtin(name);
		}
		return builtIn(selected);
	}

	/** Mitgeliefertes Cape eines anderen Spielers ({@code "salami"} …), unbekannte Namen → {@code null}. */
	private ClientAsset.@Nullable Texture builtInNamed(String name) {
		return switch (name) {
			case "salami" -> builtIn(Cape.SALAMI);
			case "pepperoni" -> builtIn(Cape.PEPPERONI);
			case "board" -> builtIn(Cape.BOARD);
			default -> null;
		};
	}

	private ClientAsset.Texture builtIn(Cape selected) {
		return builtIn.computeIfAbsent(selected, c -> new ClientAsset.ResourceTexture(
			Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/" + c.name().toLowerCase(Locale.ROOT))));
	}

	/** {@code null}/leer = Spiel ohne Launcher gestartet → Salami-Cape. */
	private ClientAsset.Texture fromLauncher(String selection) {
		if (selection == null || selection.isBlank()) {
			return own(Cape.SALAMI);
		}
		if (selection.equals("none")) {
			share = CapeShare.NONE;
			return null;
		}
		if (selection.startsWith("builtin:")) {
			try {
				Cape named = Cape.valueOf(selection.substring("builtin:".length()).toUpperCase(Locale.ROOT));
				return named == Cape.LAUNCHER || named == Cape.CUSTOM ? own(Cape.SALAMI) : own(named);
			} catch (IllegalArgumentException unknown) {
				return own(Cape.SALAMI);
			}
		}
		return customFrom(() -> launcherPath(selection));
	}

	/** Pfad aus {@code -Dsalami.cape}; ungültig oder Netzwerkpfad → {@code null}. */
	private static Path launcherPath(String selection) {
		// \\server\share bzw. //server/share: Netzwerkpfade werden nie angefasst (kein SMB-Zugriff)
		if (selection.length() >= 2 && isSeparator(selection.charAt(0)) && isSeparator(selection.charAt(1))) {
			SalamiClient.LOG.warn("Eigenes Cape abgelehnt: Netzwerkpfade werden nicht geladen");
			return null;
		}
		try {
			return Path.of(selection);
		} catch (InvalidPathException e) {
			SalamiClient.LOG.warn("Eigenes Cape abgelehnt: ungültiger Pfad");
			return null;
		}
	}

	private static boolean isSeparator(char c) {
		return c == '\\' || c == '/';
	}

	/** Lädt das eigene Cape einmal pro Auswahl (der Pfad wird erst dann ermittelt). */
	private ClientAsset.Texture customFrom(Supplier<Path> file) {
		if (!customTried) {
			customTried = true;
			customShare = CapeShare.NONE;
			Path path = file.get();
			custom = path == null ? null : loadCustom(path);
		}
		share = customShare;
		return custom;
	}

	private ClientAsset.Texture loadCustom(Path file) {
		// In Log und Chat nie den vollen Pfad – er enthält den Windows-Benutzernamen
		String name = String.valueOf(file.getFileName());
		NativeImage image = null;
		try {
			if (!Files.isRegularFile(file)) {
				Chat.warn("salami.capes.custom_missing", shownPath(file));
				return null;
			}
			if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
				return reject(name, "keine .png-Datei");
			}
			if (Files.size(file) > MAX_FILE_BYTES) {
				return reject(name, "größer als 4 MiB");
			}
			byte[] data;
			try (InputStream in = Files.newInputStream(file)) {
				data = in.readNBytes(MAX_FILE_BYTES + 1);
			}
			if (data.length > MAX_FILE_BYTES) {
				return reject(name, "größer als 4 MiB");
			}
			// Signatur und Maße aus dem PNG-Kopf prüfen, bevor das Bild dekodiert wird
			PngInfo info = PngInfo.fromBytes(data);
			if (!validCapeSize(info.width(), info.height())) {
				return reject(name, "ungültige Größe " + info.width() + "x" + info.height());
			}
			image = NativeImage.read(data);
			if (!validCapeSize(image.getWidth(), image.getHeight())) {
				return reject(name, "ungültige Größe " + image.getWidth() + "x" + image.getHeight());
			}
			mc().getTextureManager().register(CUSTOM_TEXTURE, new DynamicTexture(() -> "salami custom cape", image));
			image = null; // gehört jetzt der Textur
			customShare = CapeShare.png(data);
			return new ClientAsset.ResourceTexture(CUSTOM_TEXTURE, CUSTOM_TEXTURE);
		} catch (Exception e) {
			// Exception statt nur IOException: ein kaputtes Bild darf das Spiel nie abstürzen lassen
			SalamiClient.LOG.warn("Eigenes Cape {} konnte nicht geladen werden: {}", name, describe(e));
			Chat.warn("salami.capes.custom_invalid");
			return null;
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}

	/** Cape-Layout: Breite = 2 × Höhe, 64–2048 px breit, Vielfaches von 64. */
	public static boolean validCapeSize(int width, int height) {
		return width == height * 2 && width >= MIN_WIDTH && width <= MAX_WIDTH && width % MIN_WIDTH == 0;
	}

	private static ClientAsset.Texture reject(String name, String reason) {
		SalamiClient.LOG.warn("Eigenes Cape {} abgelehnt: {}", name, reason);
		Chat.warn("salami.capes.custom_invalid");
		return null;
	}

	/** Pfad für die Chat-Meldung: relativ zum Spielordner, sonst nur der Dateiname. */
	private static String shownPath(Path file) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		if (file.isAbsolute() && file.startsWith(gameDir)) {
			return gameDir.relativize(file).toString();
		}
		return String.valueOf(file.getFileName());
	}

	/** Fehlertext ohne Pfad (Dateisystem-Fehlermeldungen enthalten den vollen Pfad). */
	private static String describe(Exception e) {
		if (e instanceof FileSystemException fs) {
			return fs.getClass().getSimpleName() + (fs.getReason() != null ? ": " + fs.getReason() : "");
		}
		return e.toString();
	}
}
