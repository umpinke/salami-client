package dev.salamiclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.capes.AnimatedCapes;
import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.friends.Friends;
import dev.salamiclient.friends.FriendsCommand;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.FriendsScreen;
import dev.salamiclient.gui.SalamiMenuScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.gui.ModSettingsScreen;
import dev.salamiclient.gui.ModsScreen;
import dev.salamiclient.modules.hud.CpsModule;
import dev.salamiclient.modules.hud.ClockModule;
import dev.salamiclient.modules.hud.SpeedometerModule;
import dev.salamiclient.modules.hud.UptimeModule;
import dev.salamiclient.modules.hud.DayCounterModule;
import dev.salamiclient.modules.hud.TpsModule;
import dev.salamiclient.modules.hud.ResourcePackDisplayModule;
import dev.salamiclient.modules.hud.JumpResetModule;
import dev.salamiclient.modules.misc.AutoTextModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.modules.misc.NoAdvancementModule;
import dev.salamiclient.modules.misc.AutoReconnectModule;
import dev.salamiclient.modules.misc.ConfirmDisconnectModule;
import dev.salamiclient.modules.misc.PanicDisconnectModule;
import dev.salamiclient.modules.misc.ChatTweaksModule;
import dev.salamiclient.modules.misc.TabTweaksModule;
import dev.salamiclient.modules.misc.WaypointsModule;
import dev.salamiclient.modules.social.FriendsModule;
import dev.salamiclient.modules.client.BorderlessFullscreenModule;
import dev.salamiclient.gui.WaypointsScreen;
import dev.salamiclient.modules.visual.FreeLookModule;
import dev.salamiclient.modules.visual.FovChangerModule;
import dev.salamiclient.modules.visual.NoFogModule;
import dev.salamiclient.modules.visual.TimeChangerModule;
import dev.salamiclient.modules.visual.WeatherChangerModule;
import dev.salamiclient.modules.visual.ClearBackgroundModule;
import dev.salamiclient.modules.visual.ItemPhysicsModule;
import dev.salamiclient.modules.visual.GlintColorizerModule;
import dev.salamiclient.modules.visual.ShinyPotsModule;
import dev.salamiclient.modules.visual.ArrowTrailModule;
import dev.salamiclient.modules.visual.TntTimerModule;
import dev.salamiclient.modules.visual.ItemModelModule;
import dev.salamiclient.modules.visual.LightLevelModule;
import dev.salamiclient.modules.visual.HealthIndicatorsModule;
import dev.salamiclient.modules.visual.ColorSaturationModule;
import dev.salamiclient.modules.visual.MotionBlurModule;
import dev.salamiclient.modules.visual.OldAnimationsModule;
import dev.salamiclient.modules.hud.ScoreboardModule;
import dev.salamiclient.modules.hud.TitlesModule;
import dev.salamiclient.modules.hud.KeystrokesModule;
import dev.salamiclient.modules.hud.PotionStatusModule;
import dev.salamiclient.modules.hud.ReachDisplayModule;
import dev.salamiclient.modules.hud.ShieldStatusModule;
import dev.salamiclient.modules.hud.ToggleSprintModule;
import dev.salamiclient.modules.visual.FullBrightModule;
import dev.salamiclient.modules.visual.HideArmorModule;
import dev.salamiclient.modules.visual.HitColorModule;
import dev.salamiclient.modules.visual.HitboxModule;
import dev.salamiclient.modules.visual.LowFireModule;
import dev.salamiclient.modules.visual.NameTagsModule;
import dev.salamiclient.modules.visual.NoHurtCamModule;
import dev.salamiclient.modules.visual.SideShieldModule;
import dev.salamiclient.modules.visual.ZoomModule;
import dev.salamiclient.mixin.RecipeBookComponentAccessor;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.client.SalamiThemeModule;
import dev.salamiclient.modules.misc.KitsModule;
import dev.salamiclient.modules.visual.ArmorHudModule;
import dev.salamiclient.modules.visual.BlockOutlineModule;
import dev.salamiclient.modules.visual.ComboCounterModule;
import dev.salamiclient.modules.visual.CrosshairModule;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.modules.visual.ItemCounterModule;
import dev.salamiclient.modules.visual.LiveTrackerModule;
import dev.salamiclient.modules.visual.LogoOverlayModule;
import dev.salamiclient.modules.visual.ParticlesModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.modules.visual.HatsModule;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.render.MiniMeLayer;
import dev.salamiclient.render.SantaHatLayer;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.modules.visual.WatermarkModule;
import dev.salamiclient.theme.Theme;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
//?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Einstiegspunkt von salami-core.
 *
 * <p>Baut Modul-System, Config und Keybinds auf und hängt sich in die
 * Fabric-Events (Tick, HUD, Server-Beitritt, Beenden).
 */
public final class SalamiClient implements ClientModInitializer {
	public static final String MOD_ID = "salami";
	public static final String NAME = "Salami Client";
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	/** JVM-Property, über die der Launcher den Salami-Modus mitgibt. */
	public static final String THEME_PROPERTY = "salami.theme";

	private static SalamiClient instance;

	private ModuleManager modules;
	private ConfigManager config;
	private KeyMapping clickGuiKey;

	public static SalamiClient get() {
		return instance;
	}

	private static String version;

	/** Version von salami-core (einmal gelesen – die Watermark fragt jeden Tick). */
	public static String version() {
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		}
		return version;
	}

	@Override
	public void onInitializeClient() {
		instance = this;

		modules = new ModuleManager();
		registerModules();

		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), modules);
		config.load();
		applyLauncherTheme();
		modules.setChangeListener(config::markDirty);

		registerKeybinds();
		registerEvents();

		LOG.info("{} {} angeschnitten – {} Module geladen", NAME, version(), modules.all().size());
	}

	private void registerModules() {
		// Client
		modules.register(new SalamiThemeModule());
		modules.register(new GuiScaleModule());
		modules.register(new BorderlessFullscreenModule());
		// Visual (Overlay-System: alle HudModule sind im HUD-Editor verschiebbar)
		modules.register(new WatermarkModule());
		modules.register(new LiveTrackerModule());
		modules.register(new CrosshairModule());
		modules.register(new BlockOutlineModule());
		modules.register(new ArmorHudModule());
		modules.register(new ItemCounterModule());
		modules.register(new InfoHudModule());
		modules.register(new ComboCounterModule());
		modules.register(new LogoOverlayModule());
		modules.register(new ParticlesModule());
		modules.register(new CapesModule());
		modules.register(new HatsModule());
		modules.register(new MiniMeModule());
		// Mods (Batch A): Optik
		modules.register(new SideShieldModule());
		modules.register(new HideArmorModule());
		modules.register(new LowFireModule());
		modules.register(new NoHurtCamModule());
		modules.register(new HitColorModule());
		modules.register(new HitboxModule());
		modules.register(new FullBrightModule());
		modules.register(new ZoomModule());
		modules.register(new NameTagsModule());
		// Mods (Batch A): HUD
		modules.register(new KeystrokesModule());
		modules.register(new CpsModule());
		modules.register(new PotionStatusModule());
		modules.register(new ReachDisplayModule());
		modules.register(new ShieldStatusModule());
		// Mods (Batch B): Optik
		modules.register(new FreeLookModule());
		modules.register(new FovChangerModule());
		modules.register(new NoFogModule());
		modules.register(new TimeChangerModule());
		modules.register(new WeatherChangerModule());
		modules.register(new ClearBackgroundModule());
		modules.register(new ItemPhysicsModule());
		modules.register(new GlintColorizerModule());
		modules.register(new ShinyPotsModule());
		modules.register(new ArrowTrailModule());
		modules.register(new TntTimerModule());
		modules.register(new ItemModelModule());
		modules.register(new LightLevelModule());
		modules.register(new HealthIndicatorsModule());
		modules.register(new ColorSaturationModule());
		modules.register(new MotionBlurModule());
		modules.register(new OldAnimationsModule());
		// Mods (Batch B): Vanilla-HUD anpassen
		modules.register(new ScoreboardModule());
		modules.register(new TitlesModule());
		// Mods (Batch C): HUD-Infos
		modules.register(new ClockModule());
		modules.register(new SpeedometerModule());
		modules.register(new UptimeModule());
		modules.register(new DayCounterModule());
		modules.register(new TpsModule());
		modules.register(new ResourcePackDisplayModule());
		modules.register(new JumpResetModule());
		// Misc
		modules.register(new KitsModule());
		modules.register(new ToggleSprintModule());
		// Mods (Batch C): Chat, Verbindung, Wegpunkte
		modules.register(new AutoTextModule());
		modules.register(new StreamerModeModule());
		modules.register(new NoAdvancementModule());
		modules.register(new AutoReconnectModule());
		modules.register(new ConfirmDisconnectModule());
		modules.register(new PanicDisconnectModule());
		modules.register(new ChatTweaksModule());
		modules.register(new TabTweaksModule());
		modules.register(new WaypointsModule());
		// Social
		modules.register(new FriendsModule());
	}

	private void registerKeybinds() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.salami.clickgui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
	}

	private void registerEvents() {
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "modules"), modules::renderHud);
		// Custom Crosshair ersetzt das Vanilla-Fadenkreuz nur, solange das Modul aktiv ist
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			CrosshairModule crosshair = modules.get(CrosshairModule.class);
			if (crosshair.isEnabled() && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
				crosshair.render(graphics);
			} else {
				//? if >=26.1 {
				/*vanilla.extractRenderState(graphics, delta);
				*///?} else
				vanilla.render(graphics, delta);
			}
		});
		//? if >=26.1 {
		/*LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> modules.get(BlockOutlineModule.class).render(worldContext(context), state));
		LevelRenderEvents.END_MAIN.register(context -> modules.renderWorld(worldContext(context)));
		*///?} else {
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> modules.get(BlockOutlineModule.class).render(worldContext(context), state));
		WorldRenderEvents.END_MAIN.register(context -> modules.renderWorld(worldContext(context)));
		//?}
		WorldLines.init();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			TpsModule.reset();
			AutoReconnectModule.onJoin(client.getCurrentServer());
		});
		// Kits: ein halb angewendetes Kit nicht in die nächste Verbindung/Welt mitnehmen
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> modules.get(KitsModule.class).cancel());
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> modules.get(KitsModule.class).cancel());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.saveNow());
		// Freunde: /salami friends und /salami msg <freund> <text>
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> FriendsCommand.register(dispatcher));
		// Hüte (Cosmetics): Weihnachtsmütze auf der Schulter aller Spieler-Modelle
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new SantaHatLayer(avatar));
				helper.register(new MiniMeLayer(avatar));
			}
		});
	}

	/** Fabrics Welt-Render-Kontext der jeweiligen Version als {@link WorldContext} für die Module. */
	//? if >=26.2 {
	/*private static WorldContext worldContext(LevelRenderContext context) {
		return new WorldContext(context.poseStack(), context.submitNodeCollector());
	}
	*///?} else if >=26.1 {
	/*private static WorldContext worldContext(LevelRenderContext context) {
		return new WorldContext(context.poseStack(), context.bufferSource());
	}
	*///?} else {
	private static WorldContext worldContext(WorldRenderContext context) {
		return new WorldContext(context.matrices(), context.consumers());
	}
	//?}

	private void onTick(Minecraft client) {
		while (clickGuiKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new SalamiMenuScreen(modules, config));
			}
		}
		modules.tick(client);
		SharedCapes.tick(client);
		Friends.tick(client);
		AnimatedCapes.tick();
		config.tick();
	}

	/** In Textfeldern (Chat, Schilder, Suche) oder beim Tastenbelegen gehört die Taste dem Feld. */
	public static boolean isTyping(Screen screen) {
		return screen instanceof ChatScreen
			|| screen instanceof AbstractSignEditScreen
			|| screen.getFocused() instanceof EditBox
			|| hasFocusedTextField(screen)
			|| (screen instanceof ClickGuiScreen gui && gui.wasCapturingKey())
			// Mods-Menü: Tippen geht immer in die Suche
			|| screen instanceof ModsScreen
			|| (screen instanceof ModSettingsScreen settings && settings.wasCapturingKey())
			|| (screen instanceof WaypointsScreen waypoints && waypoints.isEditing())
			|| (screen instanceof FriendsScreen friends && friends.isTyping())
			|| (screen instanceof CosmeticsScreen cosmetics && cosmetics.isTyping());
	}

	/** Sucht ein fokussiertes Textfeld im Screen – auch Creative-Suche, Buch und Rezeptbuch-Suche. */
	private static boolean hasFocusedTextField(ContainerEventHandler parent) {
		for (GuiEventListener child : parent.children()) {
			if (child instanceof RecipeBookComponent<?> book) {
				EditBox search = ((RecipeBookComponentAccessor) book).salami$getSearchBox();
				if (book.isVisible() && search != null && search.isVisible() && search.isFocused()) {
					return true;
				}
			} else if (child instanceof EditBox box) {
				if (box.isVisible() && box.isFocused()) {
					return true;
				}
			} else if (child instanceof MultiLineEditBox box) {
				if (box.isFocused()) {
					return true;
				}
			} else if (child instanceof ContainerEventHandler nested && hasFocusedTextField(nested)) {
				return true;
			}
		}
		return false;
	}

	/** Configs-Menü: Config aus einer Datei übernehmen und speichern. */
	public void importConfig(Path file) throws IOException {
		config.importFrom(file);
		config.saveNow();
	}

	/** Configs-Menü: alles auf Standard zurücksetzen und speichern. */
	public void resetConfig() {
		config.resetToDefaults();
		config.saveNow();
	}

	/** Brücke zum Launcher: {@code -Dsalami.theme=salami|default} überschreibt die Config. */
	private void applyLauncherTheme() {
		String fromLauncher = System.getProperty(THEME_PROPERTY);
		if (fromLauncher == null) {
			return;
		}
		boolean salami = fromLauncher.equalsIgnoreCase("salami");
		modules.restoreState(modules.get(SalamiThemeModule.class), salami);
		LOG.info("Theme vom Launcher: {}", salami ? "Salami" : "Standard");
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public KeyMapping clickGuiKey() {
		return clickGuiKey;
	}

	/** Aktives Farbschema. */
	public Theme theme() {
		Theme base = modules.get(SalamiThemeModule.class).isEnabled() ? Theme.SALAMI : Theme.DEFAULT;
		return GuiScaleModule.customize(base);
	}
}
