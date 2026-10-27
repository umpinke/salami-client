package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.render.GuiDraw;
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
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
*///?} else {
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
//?}

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
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	//?}
		Theme theme = SalamiClient.get().theme();
		int top = height / 2 - 36;
		GuiDraw.centeredText(graphics, font, title, width / 2, top - 34, theme.headerText());
		GuiDraw.centeredText(graphics, font, I18n.get("salami.configs.hint"), width / 2, top - 20, theme.textDim());
		GuiDraw.centeredText(graphics, font, status, width / 2, top + 72, statusError ? theme.warning() : theme.accent());
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

	// Öffnet den Dateidialog des Systems; die gewählte Datei wird im Spiel-Thread verarbeitet.
	//? if >=26.3 {
	/*// Ab 26.3 über SDL: der Dialog läuft asynchron und meldet sich über einen Rückruf.
	private void chooseFile(boolean save, Consumer<Path> onChosen) {
		if (!DIALOG_OPEN.compareAndSet(false, true)) {
			return;
		}
		ByteBuffer name = MemoryUtil.memUTF8("JSON");
		ByteBuffer pattern = MemoryUtil.memUTF8("json");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.name(name).pattern(pattern);
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, files, filter) -> {
			Path path = null;
			try {
				long first = files == 0L ? 0L : MemoryUtil.memGetAddress(files);
				if (first != 0L) {
					String chosen = MemoryUtil.memUTF8(first);
					path = Path.of(save && !chosen.toLowerCase(Locale.ROOT).endsWith(".json") ? chosen + ".json" : chosen);
				}
			} catch (Throwable t) {
				SalamiClient.LOG.error("File dialog failed", t);
			}
			Path chosenPath = path;
			// Speicher erst im Spiel-Thread freigeben – der Rückruf läuft gerade noch
			Minecraft.getInstance().execute(() -> {
				callback[0].free();
				filters.free();
				MemoryUtil.memFree(name);
				MemoryUtil.memFree(pattern);
				DIALOG_OPEN.set(false);
				if (chosenPath != null) {
					onChosen.accept(chosenPath);
				}
			});
		});
		long window = Minecraft.getInstance().getWindow().handle();
		String suggested = defaultFile().toString();
		if (save) {
			SDLDialog.SDL_ShowSaveFileDialog(callback[0], 0L, window, filters, suggested);
		} else {
			SDLDialog.SDL_ShowOpenFileDialog(callback[0], 0L, window, filters, suggested, false);
		}
	}
	*///?} else {
	// Der Dialog blockiert, bis er geschlossen wird – deshalb in einem eigenen Thread.
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
	//?}
}
