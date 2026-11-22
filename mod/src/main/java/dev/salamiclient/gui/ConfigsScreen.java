package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
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
	private static final int HEADER = 24;
	private static final int PAD = 10;
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
		int w = Math.min(uiW - 16, 300);
		int h = Math.min(uiH - 16, HEADER + 30 + Action.values().length * (ACTION_H + ACTION_GAP) + 22);
		int x0 = (uiW - w) / 2;
		int y0 = (uiH - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] backButton() {
		int[] f = frame();
		int w = font.width(I18n.get("salami.mods.back")) + 12;
		return new int[] {f[0] + 4, f[1] + 5, f[0] + 4 + w, f[1] + 19};
	}

	private int[] actionRect(Action action) {
		int[] f = frame();
		int top = f[1] + HEADER + 30 + action.ordinal() * (ACTION_H + ACTION_GAP);
		return new int[] {f[0] + PAD, top, f[2] - PAD, top + ACTION_H};
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			// ohne Welt (Titelbildschirm, Mod Menu): Panorama dahinter wie in Minecrafts eigenen Menüs
			//? if >=26.1 {
			/*super.extractBackground(graphics, mouseX, mouseY, partialTick);
			*///?} else
			super.renderBackground(graphics, mouseX, mouseY, partialTick);
		}
		graphics.fill(0, 0, width, height, 0xAA000000);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);

		int[] f = frame();
		int fw = f[2] - f[0];
		int fh = f[3] - f[1];
		GuiDraw.rounded(g, f[0] + 1, f[1] + 3, fw, fh, 6, 0x50000000);
		GuiDraw.rounded(g, f[0], f[1], fw, fh, 6, theme.panel() | 0xFF000000);
		GuiDraw.roundedOutline(g, f[0], f[1], fw, fh, 6, theme.outline());
		g.fill(f[0] + 1, f[1] + HEADER - 1, f[2] - 1, f[1] + HEADER, theme.outline());

		int[] back = backButton();
		boolean backHover = inside(back, mx, my);
		GuiDraw.rounded(g, back[0], back[1], back[2] - back[0], back[3] - back[1], 3, backHover ? theme.hover() | 0x20000000 : 0);
		GuiDraw.text(g, font, I18n.get("salami.mods.back"), back[0] + 6, back[1] + 3, backHover ? theme.headerText() : theme.textDim(), false);
		GuiDraw.centeredText(g, font, title.getString(), (f[0] + f[2]) / 2, f[1] + 8, theme.headerText());

		List<FormattedCharSequence> hint = font.split(Component.translatable("salami.configs.hint"), fw - 2 * PAD);
		for (int i = 0; i < Math.min(2, hint.size()); i++) {
			GuiDraw.text(g, font, hint.get(i), f[0] + PAD, f[1] + HEADER + 6 + i * 10, theme.textDim(), false);
		}

		for (Action action : Action.values()) {
			int[] r = actionRect(action);
			boolean hovered = inside(r, mx, my);
			boolean danger = action == Action.RESET;
			int edge = hovered ? (danger ? theme.warning() : theme.accent()) : theme.outline();
			GuiDraw.rounded(g, r[0], r[1], r[2] - r[0], r[3] - r[1], 4, 0xFF120F10);
			if (hovered) {
				GuiDraw.rounded(g, r[0], r[1], r[2] - r[0], r[3] - r[1], 4, theme.hover());
			}
			GuiDraw.roundedOutline(g, r[0], r[1], r[2] - r[0], r[3] - r[1], 4, edge);
			int labelColor = danger ? theme.warning() : hovered ? theme.headerText() : theme.text();
			GuiDraw.text(g, font, GuiDraw.ellipsize(font, I18n.get(action.key), r[2] - r[0] - 24), r[0] + 8, r[1] + 5, labelColor, false);
			GuiDraw.text(g, font, GuiDraw.ellipsize(font, I18n.get(action.descKey), r[2] - r[0] - 24), r[0] + 8, r[1] + 16, theme.textDim(), false);
			GuiDraw.text(g, font, ">", r[2] - 12 + (hovered ? 2 : 0), r[1] + 10, hovered ? edge : theme.outline(), false);
		}

		if (!status.getString().isEmpty()) {
			String line = GuiDraw.ellipsize(font, status.getString(), fw - 2 * PAD);
			GuiDraw.centeredText(g, font, line, (f[0] + f[2]) / 2, f[3] - 16, statusError ? theme.warning() : theme.accent());
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
