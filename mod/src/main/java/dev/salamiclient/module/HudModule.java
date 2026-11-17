package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Basis für verschiebbare HUD-Elemente (Overlay-System).
 *
 * <p>Position als Anteil der Bildschirmgröße (0..1, linke obere Ecke des Kastens samt Innenabstand),
 * damit sie bei jeder Auflösung und GUI-Skalierung stimmt. Verschoben und vergrößert wird im
 * HUD-Editor; die Positions-Settings sind in den Menüs ausgeblendet.
 *
 * <p>Aussehen: standardmäßig der gemeinsame HUD-Stil aus dem Modul „Oberfläche“ (Clean, Minimal
 * oder Klassisch). Mit „Eigener Stil“ bekommt das Element eigenen Hintergrund, Eckenradius und
 * Textschatten; mit „Eigene Farben“ eigene Akzent- und Textfarbe statt der Theme-Farben.
 */
public abstract class HudModule extends Module {
	public static final double MIN_SCALE = 0.5;
	public static final double MAX_SCALE = 4.0;

	public final NumberSetting x;
	public final NumberSetting y;
	public final NumberSetting scale;
	private final BoolSetting ownStyle;
	private final BoolSetting background;
	private final ColorSetting backgroundColor;
	private final NumberSetting cornerRadius;
	private final BoolSetting textShadow;
	private final BoolSetting customColors;
	private final ColorSetting accentColor;
	private final ColorSetting textColor;

	/** Stil des gerade gezeichneten Frames (für {@link #shadow()} usw. während {@link #render}). */
	private HudStyle frameStyle;

	protected HudModule(String id, Category category, boolean enabledByDefault, double defaultX, double defaultY) {
		super(id, category, enabledByDefault);
		this.x = add(new NumberSetting("x", defaultX, 0, 1, 0.001));
		this.y = add(new NumberSetting("y", defaultY, 0, 1, 0.001));
		x.hide();
		y.hide();
		this.scale = add(new NumberSetting("scale", 1.0, MIN_SCALE, MAX_SCALE, 0.05));
		scale.appearance();

		ownStyle = add(new BoolSetting("own_style", false));
		ownStyle.appearance();
		background = add(new BoolSetting("background", defaultBackground()));
		background.appearance().showIf(ownStyle::isOn);
		backgroundColor = add(new ColorSetting("background_color", 0x80000000));
		backgroundColor.appearance().showIf(() -> ownStyle.isOn() && background.isOn());
		cornerRadius = add(new NumberSetting("corner_radius", 3, 0, GuiDraw.MAX_RADIUS, 1));
		cornerRadius.appearance().showIf(() -> ownStyle.isOn() && background.isOn());
		if (!usesBackground()) {
			background.hide();
			backgroundColor.hide();
			cornerRadius.hide();
		}
		textShadow = add(new BoolSetting("text_shadow", defaultShadow()));
		textShadow.appearance().showIf(ownStyle::isOn);
		customColors = add(new BoolSetting("custom_colors", false));
		customColors.appearance();
		accentColor = add(new ColorSetting("accent_color", 0xFFB23A3A));
		accentColor.appearance().showIf(customColors::isOn);
		textColor = add(new ColorSetting("text_color", 0xFFFFFFFF));
		textColor.appearance().showIf(customColors::isOn);
		if (!usesText()) {
			textShadow.hide();
			customColors.hide();
			accentColor.hide();
			textColor.hide();
		}
		if (!usesBackground() && !usesText()) {
			ownStyle.hide();
		}
	}

	// --- Was das Element unterstützt (Konstanten, werden schon im Konstruktor gefragt) ---

	/** Hintergrund bei „Eigener Stil“ standardmäßig an? */
	protected boolean defaultBackground() {
		return true;
	}

	/** Textschatten bei „Eigener Stil“ standardmäßig an? */
	protected boolean defaultShadow() {
		return false;
	}

	/** Gibt es überhaupt einen Hintergrund? */
	protected boolean usesBackground() {
		return true;
	}

	/** Zeichnet das Element seinen Hintergrund selbst (z. B. Keystrokes pro Taste)? */
	protected boolean drawsOwnBackground() {
		return false;
	}

	/**
	 * Braucht das Element Innenabstand um den Inhalt, wenn die Basis den Hintergrund zeichnet?
	 * {@code false}, wenn der Inhalt den Rand schon enthält (einzeilige Textanzeigen).
	 */
	protected boolean padded() {
		return true;
	}

	/** Zeigt das Element Text (sonst keine Text-Einstellungen)? */
	protected boolean usesText() {
		return true;
	}

	// --- Stil und Farben für die Unterklassen ---

	/** Aktueller Stil: eigener oder der gemeinsame aus „Oberfläche“. */
	public final HudStyle style() {
		if (!ownStyle.isOn()) {
			HudStyle global = GuiScaleModule.hudStyle();
			if (!usesBackground() && global.hasBackground()) {
				return new HudStyle(0, global.radius(), 0, true, global.accentLabels(), 0);
			}
			return global;
		}
		boolean bg = usesBackground() && background.isOn();
		return new HudStyle(bg ? backgroundColor.argb() : 0, cornerRadius.intValue(), 0, textShadow.isOn(), false, bg ? 3 : 0);
	}

	private HudStyle currentStyle() {
		return frameStyle != null ? frameStyle : style();
	}

	/** Hervorhebungen (z. B. hohe Combo, „Bereit“). */
	public final int accentColor() {
		return customColors.isOn() ? accentColor.argb() : SalamiClient.get().theme().accent();
	}

	/** Normaler Text und Werte. */
	public final int textColor() {
		return customColors.isOn() ? textColor.argb() : SalamiClient.get().theme().text();
	}

	/**
	 * Bezeichnungen vor Werten („FPS“, „Ping“): ruhig und etwas blasser als der Wert – oder in der
	 * Akzentfarbe (Stil „Klassisch“, Einstellung „Bezeichnungen in Akzentfarbe“, eigene Farben).
	 */
	public final int labelColor() {
		if (customColors.isOn() || currentStyle().accentLabels()) {
			return accentColor();
		}
		return GuiDraw.fade(textColor(), 0.62F);
	}

	/** Nebensächlicher Text (etwas blasser als {@link #textColor()}). */
	public final int dimColor() {
		if (!customColors.isOn()) {
			return SalamiClient.get().theme().textDim();
		}
		return GuiDraw.fade(textColor.argb(), 0.7F);
	}

	/** Hintergrundfarbe oder 0, wenn das Element keinen Hintergrund hat. */
	public final int backgroundColor() {
		return currentStyle().background();
	}

	public final boolean shadow() {
		return currentStyle().shadow();
	}

	/** Eckenradius des aktuellen Stils (für Elemente, die Flächen selbst zeichnen). */
	public final int cornerRadius() {
		return currentStyle().radius();
	}

	/** Fläche im aktuellen Stil (Hintergrund, Ecken, Rand) – z. B. eine Taste der Keystrokes. */
	protected final void panel(GuiGraphics graphics, int x, int y, int w, int h, int color) {
		HudStyle style = currentStyle();
		GuiDraw.rounded(graphics, x, y, w, h, style.radius(), color);
		if (style.border() != 0) {
			GuiDraw.roundedOutline(graphics, x, y, w, h, style.radius(), style.border());
		}
	}

	/** Breite/Höhe des Inhalts in unskalierten GUI-Pixeln (ohne Innenabstand). */
	public abstract int width();

	public abstract int height();

	/** Innenabstand um den Inhalt im aktuellen Stil. */
	private int padding(HudStyle style) {
		return padded() && usesBackground() && !drawsOwnBackground() && style.hasBackground() ? style.padding() : 0;
	}

	/** Breite des ganzen Kastens (Inhalt + Innenabstand) in unskalierten GUI-Pixeln. */
	public final int boxWidth() {
		return width() + 2 * padding(style());
	}

	public final int boxHeight() {
		return height() + 2 * padding(style());
	}

	/** Zeichnet den Inhalt bei (0,0) – Verschiebung, Skalierung und Hintergrund übernimmt {@link #onHud}. */
	protected abstract void render(GuiGraphics graphics, DeltaTracker delta);

	/** Ob das Element gerade etwas anzeigt (Editor zeigt trotzdem einen Rahmen). */
	public boolean hasContent() {
		return true;
	}

	/** Eigene Größe × globale HUD-Größe (Modul „Oberfläche“). */
	public float effectiveScale() {
		return scale.get().floatValue() * GuiScaleModule.hudScale();
	}

	public int screenX(int guiWidth) {
		int w = Math.round(boxWidth() * effectiveScale());
		return Math.max(0, Math.min(guiWidth - w, (int) Math.round(x.get() * guiWidth)));
	}

	public int screenY(int guiHeight) {
		int h = Math.round(boxHeight() * effectiveScale());
		return Math.max(0, Math.min(guiHeight - h, (int) Math.round(y.get() * guiHeight)));
	}

	public void moveTo(int guiX, int guiY, int guiWidth, int guiHeight) {
		x.set((double) guiX / guiWidth);
		y.set((double) guiY / guiHeight);
	}

	/** Position und Größe auf die Standardwerte (HUD-Editor: Taste R). */
	public void resetLayout() {
		x.reset();
		y.reset();
		scale.reset();
	}

	@Override
	public final void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (!hasContent()) {
			return;
		}
		HudStyle style = style();
		frameStyle = style;
		try {
			float s = effectiveScale();
			int pad = padding(style);
			graphics.pose().pushMatrix();
			graphics.pose().translate(screenX(graphics.guiWidth()), screenY(graphics.guiHeight()));
			graphics.pose().scale(s, s);
			if (usesBackground() && !drawsOwnBackground() && style.hasBackground()) {
				panel(graphics, 0, 0, width() + 2 * pad, height() + 2 * pad, style.background());
			}
			graphics.pose().translate(pad, pad);
			render(graphics, delta);
			graphics.pose().popMatrix();
		} finally {
			frameStyle = null;
		}
	}
}
