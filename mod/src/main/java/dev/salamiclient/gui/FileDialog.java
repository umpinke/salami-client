package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
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

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * „Datei öffnen“-Dialog des Systems für eine Dateiendung (z. B. PNG fürs Cosmetics-Menü).
 * Die gewählte Datei kommt im Spiel-Thread an; abbrechen ruft nichts auf.
 */
public final class FileDialog {
	/** Nur ein Dateidialog gleichzeitig. */
	private static final AtomicBoolean OPEN = new AtomicBoolean();

	private FileDialog() {}

	//? if >=26.3 {
	/*// Ab 26.3 über SDL: der Dialog läuft asynchron und meldet sich über einen Rückruf.
	public static void open(String title, String filterName, String extension, @Nullable Path suggested, Consumer<Path> onChosen) {
		if (!OPEN.compareAndSet(false, true)) {
			return;
		}
		ByteBuffer name = MemoryUtil.memUTF8(filterName);
		ByteBuffer pattern = MemoryUtil.memUTF8(extension);
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.name(name).pattern(pattern);
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, files, filter) -> {
			Path path = null;
			try {
				long first = files == 0L ? 0L : MemoryUtil.memGetAddress(files);
				if (first != 0L) {
					path = Path.of(MemoryUtil.memUTF8(first));
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
				OPEN.set(false);
				if (chosenPath != null) {
					onChosen.accept(chosenPath);
				}
			});
		});
		long window = Minecraft.getInstance().getWindow().handle();
		SDLDialog.SDL_ShowOpenFileDialog(callback[0], 0L, window, filters, suggested != null ? suggested.toString() : null, false);
	}
	*///?} else {
	// Der Dialog blockiert, bis er geschlossen wird – deshalb in einem eigenen Thread.
	public static void open(String title, String filterName, String extension, @Nullable Path suggested, Consumer<Path> onChosen) {
		if (!OPEN.compareAndSet(false, true)) {
			return;
		}
		Thread thread = new Thread(() -> {
			Path path = null;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filter = stack.mallocPointer(1);
				filter.put(stack.UTF8("*." + shellSafe(extension))).flip();
				String chosen = TinyFileDialogs.tinyfd_openFileDialog(shellSafe(title), safeSuggestion(suggested),
					filter, shellSafe(filterName), false);
				if (chosen != null) {
					path = Path.of(chosen);
				}
			} catch (Throwable t) {
				SalamiClient.LOG.error("File dialog failed", t);
			} finally {
				OPEN.set(false);
			}
			if (path != null) {
				Path chosenPath = path;
				Minecraft.getInstance().execute(() -> onChosen.accept(chosenPath));
			}
		}, "Salami file dialog");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * tinyfd baut auf Linux (zenity/kdialog) eine Shell-Befehlszeile aus Titel und Filternamen und prüft
	 * dabei nur Anführungszeichen – Backticks oder {@code $(…)} würden ausgeführt (CVE-2023-47104, in der
	 * mit 1.21.10/1.21.11 ausgelieferten tinyfd-Version noch offen). Die Texte kommen aus den Sprachdateien,
	 * die ein Server-Ressourcenpaket überschreiben kann. Deshalb bleiben nur Buchstaben, Ziffern und
	 * harmlose Satzzeichen übrig.
	 */
	static String shellSafe(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder safe = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			safe.append(Character.isLetterOrDigit(c) || " .,:()-_+".indexOf(c) >= 0 ? c : ' ');
		}
		return safe.toString().trim();
	}

	/** Vorgeschlagener Pfad nur, wenn er auf Linux/macOS keine Shell-Sonderzeichen enthält. */
	private static @Nullable String safeSuggestion(@Nullable Path suggested) {
		if (suggested == null) {
			return null;
		}
		String path = suggested.toString();
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
		if (!windows) {
			for (int i = 0; i < path.length(); i++) {
				if ("`$\\\"'!;&|<>".indexOf(path.charAt(i)) >= 0 || Character.isISOControl(path.charAt(i))) {
					return null;
				}
			}
		}
		return path;
	}
	//?}
}
