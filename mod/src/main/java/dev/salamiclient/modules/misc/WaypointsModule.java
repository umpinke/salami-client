package dev.salamiclient.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.WaypointsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.ActionSetting;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.util.Chat;
import dev.salamiclient.util.Projection;
import dev.salamiclient.waypoints.Waypoint;
import dev.salamiclient.waypoints.WaypointStore;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Waypoints: Wegpunkte pro Server/Welt setzen (Taste, Standard B), automatisch beim Tod,
 * mit Strahl in der Welt sowie Name und Entfernung auf dem Bildschirm.
 */
public final class WaypointsModule extends Module implements WorldRenderListener {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final KeybindSetting addKey = add(new KeybindSetting("add_key", GLFW.GLFW_KEY_B));
	private final KeybindSetting menuKey = add(new KeybindSetting("menu_key", KeybindSetting.NONE));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFB23A3A));
	private final BoolSetting deathPoints = add(new BoolSetting("death_points", true));
	private final BoolSetting beams = add(new BoolSetting("beams", true));
	private final BoolSetting throughWalls = add(new BoolSetting("through_walls", true));
	private final ActionSetting manage = add(new ActionSetting("manage", this::openScreen));

	private WaypointStore store;
	private boolean addWasDown;
	private boolean menuWasDown;
	private boolean wasDead;
	private final float[] screenPos = new float[2];

	public WaypointsModule() {
		super("waypoints", Category.MISC);
	}

	public WaypointStore store() {
		if (store == null) {
			store = new WaypointStore(FabricLoader.getInstance().getConfigDir().resolve(SalamiClient.MOD_ID).resolve("waypoints.json"));
		}
		return store;
	}

	private void openScreen() {
		mc().setScreen(new WaypointsScreen(this, mc().screen));
	}

	/** Neuer Wegpunkt an der aktuellen Position (vom Bildschirm und per Taste). */
	public void addHere(String name, int argb) {
		var player = mc().player;
		if (player == null) {
			return;
		}
		BlockPos pos = player.blockPosition();
		Waypoint waypoint = new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), player.level().dimension().identifier().toString(), argb);
		if (store().add(waypoint)) {
			Chat.info("salami.module.waypoints.added", waypoint.name, pos.getX(), pos.getY(), pos.getZ());
		} else {
			Chat.warn("salami.module.waypoints.full");
		}
	}

	@Override
	public void onTick() {
		var mc = mc();
		boolean addDown = mc.screen == null && addKey.isBound() && InputConstants.isKeyDown(mc.getWindow(), addKey.key());
		if (addDown && !addWasDown) {
			addHere(String.format(Locale.ROOT, "%s %d", I18n.get("salami.module.waypoints.default_name"),
				store().current().size() + 1), color.argb());
		}
		addWasDown = addDown;

		boolean menuDown = mc.screen == null && menuKey.isBound() && InputConstants.isKeyDown(mc.getWindow(), menuKey.key());
		if (menuDown && !menuWasDown) {
			openScreen();
		}
		menuWasDown = menuDown;

		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && deathPoints.isOn()) {
			addHere(I18n.get("salami.module.waypoints.death_name", LocalTime.now().format(TIME)), 0xFF8A8A95);
		}
		wasDead = dead;
	}

	private String dimension() {
		return mc().level.dimension().identifier().toString();
	}

	@Override
	public void renderWorld(WorldRenderContext context) {
		if (!beams.isOn() || mc().level == null) {
			return;
		}
		RenderType type = throughWalls.isOn() ? WorldLines.THROUGH_WALLS : WorldLines.VISIBLE;
		String dimension = dimension();
		int minY = mc().level.getMinY();
		int maxY = mc().level.getMaxY();
		for (Waypoint waypoint : store().current()) {
			if (!waypoint.visible || !waypoint.dimension.equals(dimension)) {
				continue;
			}
			AABB beam = new AABB(waypoint.x + 0.45, minY, waypoint.z + 0.45, waypoint.x + 0.55, maxY, waypoint.z + 0.55);
			WorldLines.box(context.matrices(), context.consumers(), type, beam, waypoint.color, 2f);
			WorldLines.box(context.matrices(), context.consumers(), type,
				new AABB(waypoint.x, waypoint.y, waypoint.z, waypoint.x + 1, waypoint.y + 1, waypoint.z + 1), waypoint.color, 2f);
		}
		WorldLines.flush(context.consumers(), type);
	}

	@Override
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (mc().level == null || mc().options.hideGui || !Projection.begin()) {
			return;
		}
		String dimension = dimension();
		Vec3 eyes = mc().player.getEyePosition();
		var font = mc().font;
		for (Waypoint waypoint : store().current()) {
			if (!waypoint.visible || !waypoint.dimension.equals(dimension)) {
				continue;
			}
			Vec3 at = waypoint.center().add(0, 1.2, 0);
			if (!Projection.toGui(at, graphics.guiWidth(), graphics.guiHeight(), screenPos)) {
				continue;
			}
			String text = String.format(Locale.ROOT, "%s (%dm)", waypoint.name, Math.round(eyes.distanceTo(waypoint.center())));
			int w = font.width(text);
			int x = Math.round(screenPos[0]) - w / 2;
			int y = Math.round(screenPos[1]) - 5;
			graphics.fill(x - 2, y - 2, x + w + 2, y + 10, 0x90000000);
			graphics.fill(x - 2, y - 2, x - 1, y + 10, waypoint.color | 0xFF000000);
			graphics.drawString(font, text, x, y, 0xFFFFFFFF, false);
		}
	}
}
