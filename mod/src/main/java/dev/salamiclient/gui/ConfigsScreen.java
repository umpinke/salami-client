package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.theme.Theme;
import dev.salamiclient.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Configs: alle Einstellungen als JSON-Datei exportieren, wieder importieren oder auf
 * Standard zurücksetzen. Export und Import öffnen den Dateidialog des Systems.
 */
public final class ConfigsScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	/** Nur ein Dateidialog gleichzeitig. */
	private static final AtomicBoolean DIALOG_OPEN = new AtomicBoolean();

	private final Screen parent;
	private Component status = Component.empty();
	private boolean statusError;

	public ConfigsScreen(Screen parent) {
		super(Component.translatable("salami.configs.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = height / 2 - 36;
		addRenderableWidget(Button.builder(Component.translatable("salami.configs.export"), b -> exportConfig())
			.bounds(x, y, BUTTON_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("salami.configs.import"), b -> importConfig())
			.bounds(x, y + 24, BUTTON_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("salami.configs.reset"), b -> confirmReset())
			.bounds(x, y + 48, BUTTON_WIDTH, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(x, y + 84, BUTTON_WIDTH, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		Theme theme = SalamiClient.get().theme();
		int top = height / 2 - 36;
		graphics.drawCenteredString(font, title, width / 2, top - 34, theme.headerText());
		graphics.drawCenteredString(font, I18n.get("salami.configs.hint"), width / 2, top - 20, theme.textDim());
		graphics.drawCenteredString(font, status, width / 2, top + 72, statusError ? theme.warning() : theme.accent());
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static Path defaultFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("salami-config.json");
	}

	private void exportConfig() {
		chooseFile(true, path -> {
			try {
				SalamiClient.get().config().exportTo(path);
				report("salami.configs.exported", false, path.getFileName());
			} catch (IOException e) {
				SalamiClient.LOG.error("Config export to {} failed", path, e);
				report("salami.configs.failed", true, e.getMessage());
			}
		});
	}

	private void importConfig() {
		chooseFile(false, path -> {
			try {
				SalamiClient.get().importConfig(path);
				report("salami.configs.imported", false, path.getFileName());
			} catch (IOException e) {
				SalamiClient.LOG.warn("Config import from {} failed", path, e);
				report("salami.configs.failed", true, e.getMessage());
			}
		});
	}

	private void confirmReset() {
		minecraft.setScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					SalamiClient.get().resetConfig();
					report("salami.configs.reset_done", false);
				}
				minecraft.setScreen(this);
			},
			Component.translatable("salami.configs.reset"),
			Component.translatable("salami.configs.reset.question")));
	}

	/** Meldung im Menü und im Chat (falls das Menü inzwischen zu ist). */
	private void report(String key, boolean error, Object... args) {
		status = Component.translatable(key, args);
		statusError = error;
		if (error) {
			Chat.warn(key, args);
		} else {
			Chat.info(key, args);
		}
	}

	/**
	 * Öffnet den Dateidialog des Systems in einem eigenen Thread (er blockiert, bis er
	 * geschlossen wird); die gewählte Datei wird im Spiel-Thread verarbeitet.
	 */
	private void chooseFile(boolean save, Consumer<Path> onChosen) {
		if (!DIALOG_OPEN.compareAndSet(false, true)) {
			return;
		}
		String dialogTitle = I18n.get(save ? "salami.configs.export" : "salami.configs.import");
		String suggested = defaultFile().toString();
		Thread thread = new Thread(() -> {
			Path path = null;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filter = stack.mallocPointer(1);
				filter.put(stack.UTF8("*.json")).flip();
				String chosen = save
					? TinyFileDialogs.tinyfd_saveFileDialog(dialogTitle, suggested, filter, "JSON")
					: TinyFileDialogs.tinyfd_openFileDialog(dialogTitle, suggested, filter, "JSON", false);
				if (chosen != null) {
					path = Path.of(save && !chosen.toLowerCase(Locale.ROOT).endsWith(".json") ? chosen + ".json" : chosen);
				}
			} catch (Throwable t) {
				SalamiClient.LOG.error("File dialog failed", t);
			} finally {
				DIALOG_OPEN.set(false);
			}
			if (path != null) {
				Path chosenPath = path;
				Minecraft.getInstance().execute(() -> onChosen.accept(chosenPath));
			}
		}, "Salami config dialog");
		thread.setDaemon(true);
		thread.start();
	}
}
