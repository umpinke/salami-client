package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Salami Capes: ein eigenes Cape (und auf Wunsch dieselbe Textur auf den Elytren) für
 * den eigenen Spieler. {@code LAUNCHER} nimmt das im Launcher gewählte Cape
 * ({@code -Dsalami.cape=builtin:<name> | <pfad> | none}) – beim Start aus dem Launcher
 * ist das immer die Ausgangseinstellung; daneben die mitgelieferten
 * Salami-Designs und {@code CUSTOM} = {@code config/salami/cape.png} (Cape-Layout 64x32
 * oder ein Vielfaches).
 *
 * <p>Nur für dich sichtbar – andere Spieler sehen ihr eigenes bzw. dein Mojang-Cape.
 */
public final class CapesModule extends Module {
	public enum Cape { LAUNCHER, SALAMI, PEPPERONI, BOARD, CUSTOM }

	/** Vom Launcher übergebene Auswahl (siehe {@code salami_capes.rs}). */
	private static final String LAUNCHER_PROPERTY = "salami.cape";

	private static final Identifier CUSTOM_TEXTURE = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/custom");
	private static CapesModule instance;

	private final ModeSetting<Cape> cape = add(new ModeSetting<>("cape", Cape.LAUNCHER));
	private final BoolSetting elytra = add(new BoolSetting("elytra", true));

	/** Geladenes eigenes Cape ({@code null} = keins oder Fehler). */
	private ClientAsset.Texture custom;
	private boolean customTried;
	private final Map<Cape, ClientAsset.Texture> builtIn = new EnumMap<>(Cape.class);
	private PlayerSkin lastBase;
	private PlayerSkin lastResult;
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

	/** Für {@code AbstractClientPlayerMixin}: Skin des Spielers, ggf. mit Salami-Cape. */
	public static PlayerSkin apply(UUID player, PlayerSkin skin) {
		CapesModule module = instance;
		if (module == null || !module.isEnabled() || !player.equals(mc().getUser().getProfileId())) {
			return skin;
		}
		ClientAsset.Texture texture = module.texture();
		if (texture == null) {
			return skin;
		}
		// getSkin() läuft jedes Frame – Ergebnis wiederverwenden, solange sich nichts ändert
		ClientAsset.Texture elytraTexture = module.elytra.isOn() ? texture : skin.elytra();
		PlayerSkin cached = module.lastResult;
		if (cached == null || module.lastBase != skin || !cached.cape().equals(texture) || cached.elytra() != elytraTexture) {
			module.lastBase = skin;
			module.lastResult = new PlayerSkin(skin.body(), texture, elytraTexture, skin.model(), skin.secure());
		}
		return module.lastResult;
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
			return customFrom(customCapeFile());
		}
		return builtIn(selected);
	}

	private ClientAsset.Texture builtIn(Cape selected) {
		return builtIn.computeIfAbsent(selected, c -> new ClientAsset.ResourceTexture(
			Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/" + c.name().toLowerCase(Locale.ROOT))));
	}

	/** {@code null}/leer = Spiel ohne Launcher gestartet → Salami-Cape. */
	private ClientAsset.Texture fromLauncher(String selection) {
		if (selection == null || selection.isBlank()) {
			return builtIn(Cape.SALAMI);
		}
		if (selection.equals("none")) {
			return null;
		}
		if (selection.startsWith("builtin:")) {
			try {
				Cape named = Cape.valueOf(selection.substring("builtin:".length()).toUpperCase(Locale.ROOT));
				return named == Cape.LAUNCHER || named == Cape.CUSTOM ? builtIn(Cape.SALAMI) : builtIn(named);
			} catch (IllegalArgumentException unknown) {
				return builtIn(Cape.SALAMI);
			}
		}
		return customFrom(Path.of(selection));
	}

	private ClientAsset.Texture customFrom(Path file) {
		if (!customTried) {
			customTried = true;
			custom = loadCustom(file);
		}
		return custom;
	}

	private static ClientAsset.Texture loadCustom(Path file) {
		if (!Files.isRegularFile(file)) {
			Chat.warn("salami.capes.custom_missing", file.toString());
			return null;
		}
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);
			if (image.getWidth() != image.getHeight() * 2) {
				image.close();
				Chat.warn("salami.capes.custom_invalid");
				return null;
			}
			mc().getTextureManager().register(CUSTOM_TEXTURE, new DynamicTexture(() -> "salami custom cape", image));
			return new ClientAsset.ResourceTexture(CUSTOM_TEXTURE, CUSTOM_TEXTURE);
		} catch (IOException e) {
			SalamiClient.LOG.warn("Eigenes Cape konnte nicht geladen werden: {}", file, e);
			Chat.warn("salami.capes.custom_invalid");
			return null;
		}
	}
}
