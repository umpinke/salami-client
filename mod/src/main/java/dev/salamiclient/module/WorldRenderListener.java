package dev.salamiclient.module;

import dev.salamiclient.render.WorldContext;

/** Für Module, die in die 3D-Welt zeichnen (Linien, Boxen). Aufruf am Ende des Haupt-Renderdurchgangs. */
public interface WorldRenderListener {
	void renderWorld(WorldContext context);
}
