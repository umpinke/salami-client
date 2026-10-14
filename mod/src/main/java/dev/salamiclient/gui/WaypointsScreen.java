package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.misc.WaypointsModule;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.theme.Theme;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.waypoints.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Wegpunkte der aktuellen Welt verwalten: umbenennen (Klick auf den Namen), Farbe (Klick auf das
 * Farbfeld), ein-/ausblenden, löschen und neue an der eigenen Position anlegen.
 */
public final class WaypointsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int ROW = 20;
	private static final int PAD = 10;

	private final WaypointsModule module;
	private final Screen parent;
	private float uiScale = 1f;
	private double scroll;
	private Waypoint editing;
	private String editBuffer = "";

	public WaypointsScreen(WaypointsModule module, Screen parent) {
		super(Component.translatable("salami.module.waypoints"));
		this.module = module;
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		commit();
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		commit();
		super.removed();
	}

	private void commit() {
		if (editing != null) {
			String name = Waypoint.clean(editBuffer);
			if (!name.isEmpty()) {
				editing.name = name;
			}
			editing = null;
			module.store().save();
		}
	}

	// ------------------------------------------------------------------ Layout

	private int[] frame() {
		int uiW = Math.round(width / uiScale);
		int uiH = Math.round(height / uiScale);
		int w = Math.min(uiW - 16, 380);
		int h = Math.min(uiH - 16, 300);
		int x0 = (uiW - w) / 2;
		int y0 = (uiH - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] backButton() {
		int[] f = frame();
		return new int[] {f[0] + 4, f[1] + 5, f[0] + 4 + font.width(I18n.get("salami.mods.back")) + 12, f[1] + 19};
	}

	private int[] addButton() {
		int[] f = frame();
		int w = font.width(I18n.get("salami.module.waypoints.add_here")) + 12;
		return new int[] {f[2] - 4 - w, f[1] + 5, f[2] - 4, f[1] + 19};
	}

	private int[] content() {
		int[] f = frame();
		return new int[] {f[0] + PAD, f[1] + HEADER + 6, f[2] - PAD, f[3] - PAD};
	}

	private List<Waypoint> waypoints() {
		return module.store().current();
	}

	private int rowY(int index) {
		return content()[1] + index * ROW - (int) Math.round(scroll);
	}

	/** Felder einer Zeile: Farbe, Name, Sichtbar, Löschen – je {x0, x1}. */
	private int[][] cells() {
		int[] c = content();
		int delete = c[2] - 16;
		int visible = delete - 22;
		return new int[][] {{c[0], c[0] + 14}, {c[0] + 18, c[0] + 140}, {visible, visible + 18}, {delete, c[2]}};
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	private void clampScroll() {
		int[] c = content();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, waypoints().size() * ROW - (c[3] - c[1]))));
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xAA000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		int[] f = frame();
		g.fill(f[0], f[1], f[2], f[3], theme.panel());
		g.fill(f[0], f[1], f[2], f[1] + HEADER - 2, theme.header());
		g.renderOutline(f[0], f[1], f[2] - f[0], f[3] - f[1], theme.outline());
		g.drawCenteredString(font, I18n.get("salami.module.waypoints"), (f[0] + f[2]) / 2, f[1] + 8, theme.headerText());
		button(g, theme, backButton(), I18n.get("salami.mods.back"), mx, my);
		button(g, theme, addButton(), I18n.get("salami.module.waypoints.add_here"), mx, my);

		int[] c = content();
		List<Waypoint> list = waypoints();
		if (list.isEmpty()) {
			g.drawCenteredString(font, I18n.get("salami.module.waypoints.empty"), (c[0] + c[2]) / 2, c[1] + 10, theme.textDim());
		}
		g.enableScissor(c[0], c[1], c[2], c[3]);
		Vec3 eyes = minecraft.player != null ? minecraft.player.getEyePosition() : Vec3.ZERO;
		int[][] cells = cells();
		for (int i = 0; i < list.size(); i++) {
			Waypoint waypoint = list.get(i);
			int y = rowY(i);
			if (y + ROW < c[1] || y > c[3]) {
				continue;
			}
			if (my >= y && my < y + ROW && mx >= c[0] && mx < c[2]) {
				g.fill(c[0] - 4, y, c[2] + 4, y + ROW, theme.hover());
			}
			g.fill(cells[0][0], y + 4, cells[0][1], y + 16, waypoint.color | 0xFF000000);
			boolean isEditing = editing == waypoint;
			String name = isEditing ? editBuffer : waypoint.name;
			g.drawString(font, font.plainSubstrByWidth(name, cells[1][1] - cells[1][0], isEditing), cells[1][0], y + 6,
				isEditing ? theme.accent() : waypoint.visible ? theme.text() : theme.textDim(), false);
			if (isEditing && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = cells[1][0] + Math.min(font.width(name), cells[1][1] - cells[1][0]);
				g.fill(cx, y + 5, cx + 1, y + 15, theme.text());
			}
			long distance = Math.round(eyes.distanceTo(waypoint.center()));
			String coords = StreamerModeModule.hidesCoords()
				? distance + "m"
				: String.format(Locale.ROOT, "%d %d %d  %dm", waypoint.x, waypoint.y, waypoint.z, distance);
			g.drawString(font, coords, cells[1][1] + 6, y + 6, theme.textDim(), false);
			g.drawString(font, waypoint.visible ? "◉" : "○", cells[2][0] + 4, y + 6, waypoint.visible ? theme.accent() : theme.textDim(), false);
			g.drawString(font, "x", cells[3][0] + 5, y + 6, theme.warning(), false);
		}
		g.disableScissor();
		g.pose().popMatrix();
	}

	private void button(GuiGraphics g, Theme theme, int[] r, String label, int mx, int my) {
		boolean hovered = inside(r, mx, my);
		g.fill(r[0], r[1], r[2], r[3], hovered ? theme.accent() : theme.panel());
		g.drawString(font, label, r[0] + 6, r[1] + 3, hovered ? 0xFF000000 : theme.headerText(), false);
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		commit();
		if (inside(backButton(), x, y)) {
			onClose();
			return true;
		}
		if (inside(addButton(), x, y)) {
			module.addHere(I18n.get("salami.module.waypoints.default_name") + " " + (waypoints().size() + 1), ColorSetting.PALETTE[waypoints().size() % ColorSetting.PALETTE.length]);
			return true;
		}
		int[] c = content();
		if (y < c[1] || y >= c[3]) {
			return super.mouseClicked(event, doubleClick);
		}
		List<Waypoint> list = waypoints();
		int[][] cells = cells();
		for (int i = 0; i < list.size(); i++) {
			int rowY = rowY(i);
			if (y < rowY || y >= rowY + ROW) {
				continue;
			}
			Waypoint waypoint = list.get(i);
			if (x >= cells[0][0] && x < cells[0][1]) {
				waypoint.color = nextColor(waypoint.color, event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
				module.store().save();
			} else if (x >= cells[1][0] && x < cells[1][1]) {
				editing = waypoint;
				editBuffer = waypoint.name;
			} else if (x >= cells[2][0] && x < cells[2][1]) {
				waypoint.visible = !waypoint.visible;
				module.store().save();
			} else if (x >= cells[3][0] && x < cells[3][1]) {
				module.store().remove(waypoint);
				clampScroll();
			}
			return true;
		}
		return true;
	}

	private static int nextColor(int current, int direction) {
		int index = 0;
		for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
			if (ColorSetting.PALETTE[i] == current) {
				index = i;
				break;
			}
		}
		return ColorSetting.PALETTE[Math.floorMod(index + direction, ColorSetting.PALETTE.length)];
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * ROW * 2;
		clampScroll();
		return true;
	}

	/** Während des Umbenennens gehört die Tastatur dem Namen. */
	public boolean isEditing() {
		return editing != null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editing != null) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit();
				case GLFW.GLFW_KEY_ESCAPE -> editing = null;
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!editBuffer.isEmpty()) {
						editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editing != null) {
			if (event.isAllowedChatCharacter() && editBuffer.length() < Waypoint.MAX_NAME) {
				editBuffer += event.codepointAsString();
			}
			return true;
		}
		return super.charTyped(event);
	}
}
