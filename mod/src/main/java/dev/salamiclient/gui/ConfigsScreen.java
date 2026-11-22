package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Configs: alle Einstellungen als JSON-Datei exportieren, wieder importieren oder auf
 * Standard zurücksetzen. Export und Import öffnen den Dateidialog des Systems.
 *
 * <p>Fenster im Stil der übrigen Salami-Menüs (Rahmen, „Zurück“, Größe aus dem Modul „Oberfläche“);
 * Zeichnen und Klicken rechnen in denselben skalierten Koordinaten.
 */
public final class ConfigsScreen extends Screen {
	private static final int PAD = SalamiWindow.PAD;
	private static final int ACTION_H = 28;
	private static final int ACTION_GAP = 5;
	/** Nur ein Dateidialog gleichzeitig. */
	private static final AtomicBoolean DIALOG_OPEN = new AtomicBoolean();

	/** Die drei Aktionen in Anzeige-Reihenfolge. */
	private enum Action {
		EXPORT("salami.configs.export", "salami.configs.export.desc"),
		IMPORT("salami.configs.import", "salami.configs.import.desc"),
		RESET("salami.configs.reset", "salami.configs.reset.desc");

		final String key;
		final String descKey;

		Action(String key, String descKey) {
			this.key = key;
			this.descKey = descKey;
		}
	}

	private final Screen parent;
	private Component status = Component.empty();
	private boolean statusError;
	private float uiScale = 1f;

	public ConfigsScreen(Screen parent) {
		super(Component.translatable("salami.configs.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	// ------------------------------------------------------------------ Layout

	private int[] frame() {
		int uiW = Math.round(width / uiScale);
		int uiH = Math.round(height / uiScale);
		return SalamiWindow.frame(uiW, uiH, 300, SalamiWindow.HEADER + 30 + Action.values().length * (ACTION_H + ACTION_GAP) + 30);
	}

	private int[] backButton() {
		return SalamiWindow.backButton(font, frame());
	}

	private int[] actionRect(Action action) {
		int[] f = frame();
		int top = SalamiWindow.contentTop(f) + 24 + action.ordinal() * (ACTION_H + ACTION_GAP);
		return new int[] {f[0] + PAD, top, f[2] - PAD, top + ACTION_H};
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	//?}
		SalamiWindow.backdrop(graphics, width, height, uiScale);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		Skin skin = SalamiWindow.skin();
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		int[] f = frame();
		int fw = f[2] - f[0];
		SalamiWindow.draw(g, font, f, title.getString(), mx, my, 0);

		List<FormattedCharSequence> hint = font.split(Component.translatable("salami.configs.hint"), fw - 2 * PAD);
		for (int i = 0; i < Math.min(2, hint.size()); i++) {
			GuiDraw.text(g, font, hint.get(i), f[0] + PAD, SalamiWindow.contentTop(f) + i * 10, ink.dim(), false);
		}

		for (Action action : Action.values()) {
			int[] r = actionRect(action);
			boolean hovered = inside(r, mx, my);
			boolean danger = action == Action.RESET;
			skin.row(g, r[0], r[1], r[2] - r[0], r[3] - r[1], false, Skin.UiState.of(hovered));
			Skin.Ink card = skin.ink(Skin.Surface.CARD);
			int labelColor = danger ? card.warning() : hovered ? card.accent() : card.text();
			int room = r[2] - r[0] - 26;
			TextFit.draw(g, font, I18n.get(action.key), r[0] + 8, r[1] + 5, room, labelColor);
			TextFit.draw(g, font, I18n.get(action.descKey), r[0] + 8, r[1] + 16, room, card.dim());
			int arrowX = r[2] - 12 + (hovered ? 2 : 0);
			GuiDraw.text(g, font, ">", arrowX, r[1] + 10, hovered ? labelColor : card.line(), false);
		}

		if (!status.getString().isEmpty()) {
			List<FormattedCharSequence> lines = font.split(status, fw - 2 * PAD);
			int y = f[3] - 8 - lines.size() * 10;
			for (FormattedCharSequence line : lines) {
				GuiDraw.text(g, font, line, (f[0] + f[2] - font.width(line)) / 2, y, statusError ? ink.warning() : ink.accent(), false);
				y += 10;
			}
		}

		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (inside(backButton(), x, y)) {
				onClose();
				return true;
			}
			for (Action action : Action.values()) {
				if (inside(actionRect(action), x, y)) {
					run(action);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void run(Action action) {
		switch (action) {
			case EXPORT -> exportConfig();
			case IMPORT -> importConfig();
			case RESET -> confirmReset();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (SalamiMenuScreen.closeOnMenuKey(minecraft, event, false)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return SalamiMenuScreen.pausesGame();
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
				report("salami.configs.export_failed", true);
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
				report("salami.configs.import_failed", true);
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
