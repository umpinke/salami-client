package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.util.Chat;
import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.util.Keys;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	/** Launcher-Schalter „Kampf-Module erlauben“; aus = alle {@link Module#needsCombat()} fehlen. */
	private boolean combatAllowed = true;
	private boolean utilityWarningShown;
	/** Gemessene Zeit der Module (für den Benchmark-Test und zur Fehlersuche). */
	private long hudNanos;
	private long hudFrames;
	private long tickNanos;
	private long tickCount;
	private long worldNanos;
	private long worldFrames;
	/** Zeit pro Modul in ns {HUD, Welt, Tick}; nur im Benchmark an, sonst {@code null}. */
	private Map<Module, long[]> profile;

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

	/** Kampf-Module erlauben oder komplett ausblenden; ausgeblendete werden sofort abgeschaltet. */
	public void setCombatAllowed(boolean allowed) {
		combatAllowed = allowed;
		if (!allowed) {
			int count = 0;
			for (Module module : byId.values()) {
				if (module.needsCombat() && module.isEnabled()) {
					applySafely(module, false);
					count++;
				}
			}
			if (count > 0) {
				rebuildActive();
				changeListener.run();
			}
		}
	}

	public boolean combatAllowed() {
		return combatAllowed;
	}

	/** Ob das Modul im Spiel vorkommt (in GUIs gelistet, per Taste schaltbar). */
	public boolean isAvailable(Module module) {
		return combatAllowed || !module.needsCombat();
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
		if (enabled && !isAvailable(module)) {
			return false;
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

	/** Setzt den Zustand ohne Safe-Mode-Prüfung – nur für das Laden der Config. */
	public void restoreState(Module module, boolean enabled) {
		applySafely(module, enabled && isAvailable(module));
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
		long start = System.nanoTime();
		for (Module module : active) {
			long moduleStart = profile != null ? System.nanoTime() : 0L;
			try {
				module.onTick();
			} catch (Throwable t) {
				crash(module, "tick", t);
			}
			record(module, 2, moduleStart);
		}
		tickNanos += System.nanoTime() - start;
		tickCount++;
	}

	public void renderHud(GuiGraphics graphics, DeltaTracker delta) {
		long start = System.nanoTime();
		for (Module module : active) {
			long moduleStart = profile != null ? System.nanoTime() : 0L;
			try {
				module.onHud(graphics, delta);
			} catch (Throwable t) {
				crash(module, "hud", t);
			}
			record(module, 0, moduleStart);
		}
		hudNanos += System.nanoTime() - start;
		hudFrames++;
	}

	/** Vor jedem Angriff (siehe {@link AttackListener}). */
	public void beforeAttack(Entity target) {
		for (Module module : active) {
			if (module instanceof AttackListener listener) {
				try {
					listener.beforeAttack(target);
				} catch (Throwable t) {
					crash(module, "attack", t);
				}
			}
		}
	}

	/** Weltrendering (siehe {@link WorldRenderListener}). */
	public void renderWorld(WorldContext context) {
		long start = System.nanoTime();
		renderWorldListeners(context);
		worldNanos += System.nanoTime() - start;
		worldFrames++;
	}

	private void renderWorldListeners(WorldContext context) {
		for (Module module : active) {
			if (module instanceof WorldRenderListener listener) {
				long moduleStart = profile != null ? System.nanoTime() : 0L;
				try {
					listener.renderWorld(context);
				} catch (Throwable t) {
					crash(module, "render", t);
				}
				record(module, 1, moduleStart);
			}
		}
	}

	// --- Messung ---

	/** Setzt die Zeitmessung zurück. */
	public void resetTimings() {
		hudNanos = hudFrames = tickNanos = tickCount = worldNanos = worldFrames = 0;
		if (profile != null) {
			profile.clear();
		}
	}

	/** Zeit pro Modul mitschreiben (Benchmark); kostet sonst nichts. */
	public void setProfiling(boolean on) {
		profile = on ? new HashMap<>() : null;
	}

	/** Gemessene Zeit pro Modul seit {@link #resetTimings()} in ns: {HUD, Welt, Tick}. */
	public Map<Module, long[]> profile() {
		return profile == null ? Map.of() : Collections.unmodifiableMap(profile);
	}

	/** Anzahl der Aufrufe seit {@link #resetTimings()}: {HUD-Frames, Welt-Frames, Ticks}. */
	public long[] profileCounts() {
		return new long[] {hudFrames, worldFrames, tickCount};
	}

	private void record(Module module, int slot, long start) {
		if (profile != null) {
			profile.computeIfAbsent(module, m -> new long[3])[slot] += System.nanoTime() - start;
		}
	}

	/** Durchschnittliche Zeit pro Frame im HUD aller Module (ms). */
	public double averageHudMillis() {
		return hudFrames == 0 ? 0 : hudNanos / 1_000_000.0 / hudFrames;
	}

	/** Durchschnittliche Zeit pro Frame beim Zeichnen in die Welt (ms). */
	public double averageWorldMillis() {
		return worldFrames == 0 ? 0 : worldNanos / 1_000_000.0 / worldFrames;
	}

	/** Durchschnittliche Zeit pro Client-Tick aller Module (ms). */
	public double averageTickMillis() {
		return tickCount == 0 ? 0 : tickNanos / 1_000_000.0 / tickCount;
	}

	private void handleKeybinds(Minecraft client) {
		Set<Integer> downNow = new HashSet<>();
		for (Module module : byId.values()) {
			int key = module.keybind.key();
			if (!module.keybind.isBound() || !isAvailable(module)) {
				continue;
			}
			if (Keys.isDown(key)) {
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
