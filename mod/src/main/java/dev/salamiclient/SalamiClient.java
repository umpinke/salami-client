package dev.salamiclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.client.SalamiThemeModule;
import dev.salamiclient.modules.utility.AnchorMacroModule;
import dev.salamiclient.modules.utility.AutoCrystalModule;
import dev.salamiclient.modules.utility.AutoTotemModule;
import dev.salamiclient.modules.utility.DoubleHandTotemModule;
import dev.salamiclient.modules.utility.GhostSpearModule;
import dev.salamiclient.modules.utility.HoverTotemModule;
import dev.salamiclient.modules.misc.KitsModule;
import dev.salamiclient.modules.utility.AttributeSwapModule;
import dev.salamiclient.modules.utility.AutoElytraModule;
import dev.salamiclient.modules.utility.AutoMaceModule;
import dev.salamiclient.modules.utility.BlockEspModule;
import dev.salamiclient.modules.utility.PlayerEspModule;
import dev.salamiclient.modules.utility.TriggerBotModule;
import dev.salamiclient.modules.utility.AimAssistModule;
import dev.salamiclient.modules.utility.BreachSwapModule;
import dev.salamiclient.modules.utility.SpeedLungeModule;
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
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.modules.visual.WatermarkModule;
import dev.salamiclient.theme.Theme;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private KeyMapping easyLeaveKey;

	public static SalamiClient get() {
		return instance;
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	@Override
	public void onInitializeClient() {
		instance = this;

		modules = new ModuleManager();
		registerModules();
		modules.setSafeModeCheck(() -> modules.get(SafeModeModule.class).isEnabled());

		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), modules);
		config.load();
		enforceSafeModeAfterLoad();
		applyLauncherTheme();
		modules.setChangeListener(config::markDirty);

		registerKeybinds();
		registerEvents();

		LOG.info("{} {} angeschnitten – {} Module geladen", NAME, version(), modules.all().size());
	}

	private void registerModules() {
		// Client
		modules.register(new SalamiThemeModule());
		modules.register(new SafeModeModule());
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
		// Misc
		modules.register(new KitsModule());
		// Utility (Kampf-Automatisierung – von Safe Mode / Easy Leave gesperrt)
		modules.register(new AutoTotemModule());
		modules.register(new HoverTotemModule());
		modules.register(new DoubleHandTotemModule());
		modules.register(new AutoCrystalModule());
		modules.register(new AnchorMacroModule());
		modules.register(new GhostSpearModule());
		modules.register(new BreachSwapModule());
		modules.register(new AttributeSwapModule());
		modules.register(new AutoMaceModule());
		modules.register(new AutoElytraModule());
		modules.register(new SpeedLungeModule());
		modules.register(new BlockEspModule());
		modules.register(new PlayerEspModule());
		modules.register(new TriggerBotModule());
		modules.register(new AimAssistModule());
	}

	private void registerKeybinds() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.salami.clickgui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		easyLeaveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.salami.easy_leave", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, category));
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
				vanilla.render(graphics, delta);
			}
		});
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> modules.get(BlockOutlineModule.class).render(context, state));
		WorldRenderEvents.END_MAIN.register(modules::renderWorld);
		WorldLines.init();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
			modules.get(SafeModeModule.class).onJoin(client.getCurrentServer()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.saveNow());
		// Keybinds feuern nur ohne offenes Menü – Easy Leave soll aber auch im
		// Inventar, Chat oder in der ClickGUI sofort greifen.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (easyLeaveKey.matches(event) && !isTyping(s)) {
					easyLeave();
				}
			}));
	}

	private void onTick(Minecraft client) {
		while (clickGuiKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ClickGuiScreen(modules, config));
			}
		}
		while (easyLeaveKey.consumeClick()) {
			easyLeave();
		}
		modules.tick(client);
		config.tick();
	}

	/** In Textfeldern (Chat, Schilder, Suche) oder beim Tastenbelegen gehört die Taste dem Feld. */
	private static boolean isTyping(Screen screen) {
		return screen instanceof ChatScreen
			|| screen.getFocused() instanceof EditBox
			|| (screen instanceof ClickGuiScreen gui && gui.wasCapturingKey());
	}

	/** Easy Leave: alle Kampfmodule sofort aus und gesperrt (Safe Mode). */
	public void easyLeave() {
		modules.get(SafeModeModule.class).engage(SafeModeModule.Reason.EASY_LEAVE);
	}

	/** Falls die Config inkonsistent ist (Safe Mode an + Utility an), gewinnt Safe Mode. */
	private void enforceSafeModeAfterLoad() {
		if (modules.get(SafeModeModule.class).isEnabled()) {
			modules.disableCategory(Category.UTILITY);
		}
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

	public KeyMapping easyLeaveKey() {
		return easyLeaveKey;
	}

	/** Aktives Farbschema. */
	public Theme theme() {
		return modules.get(SalamiThemeModule.class).isEnabled() ? Theme.SALAMI : Theme.DEFAULT;
	}
}
