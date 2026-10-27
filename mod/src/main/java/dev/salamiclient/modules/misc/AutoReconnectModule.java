package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Auto Reconnect: nach einem Verbindungsabbruch ein „Neu verbinden“-Knopf, optional mit
 * Countdown, der sich selbst wieder verbindet. Nur für Server, nicht für Einzelspieler-Welten
 * oder Realms.
 *
 * <p>Ziel ist immer der Server, mit dem du dich zuletzt selbst verbinden wolltest (gemerkt beim
 * Verbindungsaufbau, nicht erst beim Beitritt) – nie ein älterer. Automatisch höchstens
 * {@link #MAX_AUTO_ATTEMPTS}-mal mit wachsender Wartezeit, und nie nach Bann, Kick durch einen
 * Operator oder „von einem anderen Ort eingeloggt“ (sonst werfen sich zwei Clients ewig raus).
 */
public final class AutoReconnectModule extends Module {
	private static final int MAX_AUTO_ATTEMPTS = 3;
	/** So lange verbunden gilt als stabil – danach zählen die Versuche wieder von vorn. */
	private static final long STABLE_MILLIS = 30_000;

	private static AutoReconnectModule instance;
	/** Server der letzten selbst gestarteten Verbindung; {@code null} = nichts anbieten. */
	private static ServerData lastServer;
	/** Neu-Verbinden-Versuche seit der letzten stabilen Verbindung. */
	private static int attempts;
	/** Zeitpunkt des Beitritts, 0 = (noch) nicht beigetreten. */
	private static long connectedAt;
	/** {@code true}, während dieses Modul selbst die Verbindung startet. */
	private static boolean reconnecting;

	private final BoolSetting auto = add(new BoolSetting("auto", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 5, 2, 30, 1));

	public AutoReconnectModule() {
		super("auto_reconnect", Category.MISC);
		instance = this;
	}

	/** Für {@code ConnectScreenMixin}: jeder Verbindungsaufbau zu einem Server. */
	public static void onConnectStart(ServerData server) {
		if (!reconnecting) {
			attempts = 0; // neue, selbst gewählte Verbindung
		}
		lastServer = server != null && server.type() != ServerData.Type.REALM ? server : null;
		connectedAt = 0;
	}

	/** Einzelspieler oder Realms: nichts zum Neu-Verbinden anbieten. */
	public static void forget() {
		lastServer = null;
		attempts = 0;
		connectedAt = 0;
	}

	/** Bei jedem Beitritt (siehe {@code SalamiClient}). */
	public static void onJoin(ServerData current) {
		if (current == null || current.type() == ServerData.Type.REALM) {
			forget();
			return;
		}
		connectedAt = System.currentTimeMillis();
	}

	/** Für {@code DisconnectedScreenMixin}: Knopf oder {@code null}, wenn nicht passend. */
	public static Button createButton(Component reason) {
		if (instance == null || !instance.isEnabled() || lastServer == null) {
			return null;
		}
		if (connectedAt > 0) {
			if (System.currentTimeMillis() - connectedAt >= STABLE_MILLIS) {
				attempts = 0;
			}
			connectedAt = 0;
		}
		boolean automatic = instance.auto.isOn() && attempts < MAX_AUTO_ATTEMPTS && !refusesAutomatic(reason);
		// 1., 2., 3. Versuch: einfache, doppelte, vierfache Wartezeit
		long deadline = automatic ? System.currentTimeMillis() + (instance.delay.intValue() * 1000L << attempts) : -1;
		return new ReconnectButton(lastServer, deadline);
	}

	/** Gründe, bei denen automatisches Neu-Verbinden nur schadet. */
	static boolean refusesAutomatic(Component reason) {
		if (reason != null && reason.getContents() instanceof TranslatableContents translatable) {
			String key = translatable.getKey();
			return key.startsWith("multiplayer.disconnect.banned")
				|| key.equals("multiplayer.disconnect.duplicate_login")
				|| key.equals("multiplayer.disconnect.kicked");
		}
		return false;
	}

	private static void reconnect(ServerData server) {
		Minecraft mc = Minecraft.getInstance();
		attempts++;
		reconnecting = true;
		try {
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
		} finally {
			reconnecting = false;
		}
	}

	/** Knopf mit Countdown im Text; verbindet beim Klick oder wenn der Countdown abläuft. */
	//? if >=1.21.11 {
	private static final class ReconnectButton extends Button.Plain {
	//?} else
	/*private static final class ReconnectButton extends Button {*/
		private final ServerData server;
		private final long deadline;
		private boolean done;

		ReconnectButton(ServerData server, long deadline) {
			super(0, 0, 200, 20, Component.empty(), button -> ((ReconnectButton) button).fire(), DEFAULT_NARRATION);
			this.server = server;
			this.deadline = deadline;
			updateLabel();
		}

		private void fire() {
			if (!done) {
				done = true;
				reconnect(server);
			}
		}

		private void updateLabel() {
			if (deadline < 0) {
				setMessage(Component.translatable("salami.module.auto_reconnect.button"));
				return;
			}
			long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
			setMessage(Component.literal(I18n.get("salami.module.auto_reconnect.countdown", seconds)));
		}

		@Override
		//? if >=26.1 {
		/*protected void extractContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		*///?} else if >=1.21.11 {
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		//?} else
		/*protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {*/
			updateLabel();
			if (deadline >= 0 && !done && System.currentTimeMillis() >= deadline) {
				// nicht mitten im Zeichnen den Bildschirm wechseln – im nächsten Durchlauf verbinden
				Minecraft.getInstance().execute(this::fire);
			}
			//? if >=26.1 {
			/*super.extractContents(graphics, mouseX, mouseY, partialTick);
			*///?} else if >=1.21.11 {
			super.renderContents(graphics, mouseX, mouseY, partialTick);
			//?} else
			/*super.renderWidget(graphics, mouseX, mouseY, partialTick);*/
		}
	}
}
