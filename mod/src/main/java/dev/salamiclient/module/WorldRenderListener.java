package dev.salamiclient.module;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Für Module, die in die 3D-Welt zeichnen (Linien, Boxen). Aufruf am Ende des Haupt-Renderdurchgangs. */
public interface WorldRenderListener {
	void renderWorld(WorldRenderContext context);
}
