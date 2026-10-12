package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.util.Chat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Registriert alle Module, schaltet sie (mit Safe-Mode-Prüfung) um und verteilt
 * Tick-/HUD-Aufrufe ausschließlich an aktive Module.
 */
public final class ModuleManager {
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Class<?>, Module> byClass = new LinkedHashMap<>();
	/** Gecachte Liste aktiver Module; wird nur beim Umschalten neu gebaut. */
	private List<Module> active = List.of();
	/** Tasten, die im letzten Tick gedrückt waren (Flankenerkennung für Modul-Keybinds). */
	private final Set<Integer> keysDownLastTick = new HashSet<>();
	private Runnable changeListener = () -> {};
	private BooleanSupplier safeModeActive = () -> false;
	private boolean utilityWarningShown;

	public <M extends Module> M register(M module) {
		if (byId.putIfAbsent(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate module id: " + module.id());
		}
		byClass.put(module.getClass(), module);
		module.bindChangeHook(() -> changeListener.run());
		return module;
	}

	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	public void setSafeModeCheck(BooleanSupplier check) {
		this.safeModeActive = check;
	}

	// --- Abfragen ---

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(byId.values()));
	}

	public List<Module> byCategory(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module module : byId.values()) {
			if (module.category() == category) {
				result.add(module);
			}
		}
		return result;
	}

	public Optional<Module> get(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public <M extends Module> M get(Class<M> type) {
		Module module = byClass.get(type);
		if (module == null) {
			throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
		}
		return type.cast(module);
	}

	// --- Umschalten ---

	/**
	 * Schaltet ein Modul an/aus. Utility-Module lassen sich bei aktivem Safe Mode
	 * nicht einschalten.
	 *
	 * @return {@code true}, wenn der gewünschte Zustand jetzt gilt
	 */
	public boolean setEnabled(Module module, boolean enabled) {
		if (module.isEnabled() == enabled) {
			return true;
		}
		if (enabled && module.category().isRestricted()) {
			if (safeModeActive.getAsBoolean()) {
				Chat.warn("salami.safe_mode.blocked");
				return false;
			}
			if (!utilityWarningShown) {
				utilityWarningShown = true;
				Chat.warn("salami.category.utility.warning");
			}
		}
		applySafely(module, enabled);
		rebuildActive();
		changeListener.run();
		return true;
	}

	public boolean toggle(Module module) {
		return setEnabled(module, !module.isEnabled());
	}

	/** Schaltet alle Module einer Kategorie aus und liefert deren Anzahl. */
	public int disableCategory(Category category) {
		int count = 0;
		for (Module module : byId.values()) {
			if (module.category() == category && module.isEnabled()) {
				applySafely(module, false);
				count++;
			}
		}
		if (count > 0) {
			rebuildActive();
			changeListener.run();
		}
		return count;
	}

	/** Setzt den Zustand ohne Prüfungen – nur für das Laden der Config. */
	public void restoreState(Module module, boolean enabled) {
		applySafely(module, enabled);
		rebuildActive();
	}

	// --- Laufzeit ---

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			keysDownLastTick.clear();
			return;
		}
		if (client.screen == null) {
			handleKeybinds(client);
		}
		for (Module module : active) {
			try {
				module.onTick();
			} catch (Throwable t) {
				crash(module, "tick", t);
			}
		}
	}

	public void renderHud(GuiGraphics graphics, DeltaTracker delta) {
		for (Module module : active) {
			try {
				module.onHud(graphics, delta);
			} catch (Throwable t) {
				crash(module, "hud", t);
			}
		}
	}

	private void handleKeybinds(Minecraft client) {
		Set<Integer> downNow = new HashSet<>();
		for (Module module : byId.values()) {
			int key = module.keybind.key();
			if (!module.keybind.isBound()) {
				continue;
			}
			if (InputConstants.isKeyDown(client.getWindow(), key)) {
				downNow.add(key);
				if (!keysDownLastTick.contains(key)) {
					toggle(module);
				}
			}
		}
		keysDownLastTick.clear();
		keysDownLastTick.addAll(downNow);
	}

	private void applySafely(Module module, boolean enabled) {
		try {
			module.applyEnabled(enabled);
		} catch (Throwable t) {
			SalamiClient.LOG.error("Module '{}' failed to {}", module.id(), enabled ? "enable" : "disable", t);
		}
	}

	/** Ein fehlerhaftes Modul wird abgeschaltet, statt das Spiel abstürzen zu lassen. */
	private void crash(Module module, String phase, Throwable t) {
		SalamiClient.LOG.error("Module '{}' threw in {} – disabling it", module.id(), phase, t);
		applySafely(module, false);
		rebuildActive();
		Chat.warn("salami.module.crashed", Chat.translated(module.translationKey()));
	}

	private void rebuildActive() {
		List<Module> list = new ArrayList<>();
		for (Module module : byId.values()) {
			if (module.isEnabled()) {
				list.add(module);
			}
		}
		active = List.copyOf(list);
	}
}
