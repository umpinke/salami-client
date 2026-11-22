package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.friends.Friends;
import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.social.FriendsModule;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Freunde-Menü: links Freundesliste und offene Anfragen (oben ein Feld zum Hinzufügen per
 * Minecraft-Name), rechts der Chat mit dem gewählten Freund. Enter sendet, Tab wechselt
 * zwischen den Eingabefeldern, Esc schließt.
 */
public final class FriendsScreen extends Screen {
	private static final int PAD = 8;
	private static final int LIST_W = 150;
	private static final int ROW = 18;
	private static final int SECTION = 13;
	private static final int FIELD_H = 16;
	private static final int NOTICE_H = 12;
	private static final int BUBBLE_GAP = 4;
	/** Grün für „online“ – auf hellen und dunklen Flächen lesbar. */
	private static final int ONLINE = 0xFF3E9B4F;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** Zuletzt offene Unterhaltung – beim nächsten Öffnen wieder da. */
	private static @Nullable UUID selected;

	private final Screen parent;
	private float uiScale = 1f;
	private final Field addField = new Field(16);
	private final Field chatField = new Field(Friends.MAX_MESSAGE);
	private @Nullable Field focused;
	private double listScroll;
	/** Abstand vom unteren Ende des Chats (0 = neueste Nachricht sichtbar). */
	private double chatScroll;
	private boolean sending;
	private @Nullable UUID confirmRemove;
	private long confirmUntil;

	/** Einfaches Eingabefeld (Cursor immer am Ende). */
	private static final class Field {
		final int max;
		String text = "";

		Field(int max) {
			this.max = max;
		}
	}

	/** Klickbares Element der Liste links. */
	private record Entry(int y, int height, @Nullable String section, Friends.@Nullable Friend friend, Friends.@Nullable Request request) {}

	public FriendsScreen(@Nullable Screen parent) {
		super(Component.translatable("salami.module.friends"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		if (selected != null && Friends.friend(selected) == null && !Friends.friends().isEmpty()) {
			selected = null;
		}
		Friends.view(selected);
		focused = selected != null ? chatField : addField;
	}

	@Override
	public boolean isPauseScreen() {
		return SalamiMenuScreen.pausesGame();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		Friends.view(null);
		Friends.clearNotice();
		super.removed();
	}

	/** Für {@link SalamiClient#isTyping}: die Tastatur gehört den Eingabefeldern. */
	public boolean isTyping() {
		return focused != null;
	}

	// ------------------------------------------------------------------ Layout

	private int uiW() {
		return Math.round(width / uiScale);
	}

	private int uiH() {
		return Math.round(height / uiScale);
	}

	private int[] frame() {
		return SalamiWindow.frame(uiW(), uiH(), 480, 320);
	}

	private int[] backButton() {
		return SalamiWindow.backButton(font, frame());
	}

	/** Linke Spalte unterhalb des Hinzufügen-Felds. */
	private int[] listArea() {
		int[] f = frame();
		int top = SalamiWindow.contentTop(f) + FIELD_H + 6;
		return new int[] {f[0] + PAD, top, f[0] + PAD + LIST_W, f[3] - PAD - NOTICE_H};
	}

	private int[] addFieldBox() {
		int[] f = frame();
		int y = SalamiWindow.contentTop(f);
		return new int[] {f[0] + PAD, y, f[0] + PAD + LIST_W - FIELD_H - 3, y + FIELD_H};
	}

	private int[] addButton() {
		int[] box = addFieldBox();
		return new int[] {box[2] + 3, box[1], box[2] + 3 + FIELD_H, box[3]};
	}

	/** Rechte Seite (Chat). */
	private int[] chatPanel() {
		int[] f = frame();
		return new int[] {f[0] + PAD + LIST_W + PAD, SalamiWindow.contentTop(f), f[2] - PAD, f[3] - PAD - NOTICE_H};
	}

	private int[] chatHeader() {
		int[] p = chatPanel();
		return new int[] {p[0], p[1], p[2], p[1] + 16};
	}

	private int[] removeButton() {
		int[] h = chatHeader();
		String label = I18n.get(isConfirming() ? "salami.friends.remove_confirm" : "salami.friends.remove");
		int w = font.width(label) + 10;
		return new int[] {h[2] - w, h[1] + 1, h[2], h[3] - 1};
	}

	private int[] messagesArea() {
		int[] p = chatPanel();
		return new int[] {p[0], p[1] + 19, p[2], p[3] - FIELD_H - 5};
	}

	private int[] chatFieldBox() {
		int[] p = chatPanel();
		int w = font.width(I18n.get("salami.friends.send")) + 12;
		return new int[] {p[0], p[3] - FIELD_H, p[2] - w - 3, p[3]};
	}

	private int[] sendButton() {
		int[] box = chatFieldBox();
		int[] p = chatPanel();
		return new int[] {box[2] + 3, box[1], p[2], box[3]};
	}

	private int[] enableButton() {
		int[] p = chatPanel();
		String label = I18n.get("salami.friends.enable");
		int w = font.width(label) + 14;
		int cx = (p[0] + p[2]) / 2;
		int y = (p[1] + p[3]) / 2 + 10;
		return new int[] {cx - w / 2, y, cx + w / 2, y + 16};
	}

	private boolean isConfirming() {
		return confirmRemove != null && confirmRemove.equals(selected) && System.currentTimeMillis() < confirmUntil;
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Einträge der linken Liste mit y-Positionen (inkl. Scroll). */
	private List<Entry> entries() {
		int[] a = listArea();
		int y = a[1] - (int) Math.round(listScroll);
		List<Entry> result = new ArrayList<>();
		List<Friends.Request> requests = Friends.requests();
		if (!requests.isEmpty()) {
			result.add(new Entry(y, SECTION, I18n.get("salami.friends.requests", requests.size()), null, null));
			y += SECTION;
			for (Friends.Request request : requests) {
				result.add(new Entry(y, ROW, null, null, request));
				y += ROW;
			}
			y += 4;
		}
		List<Friends.Friend> friends = Friends.friends();
		long online = friends.stream().filter(Friends.Friend::online).count();
		result.add(new Entry(y, SECTION, I18n.get("salami.friends.list", online, friends.size()), null, null));
		y += SECTION;
		for (Friends.Friend friend : friends) {
			result.add(new Entry(y, ROW, null, friend, null));
			y += ROW;
		}
		return result;
	}

	private int listHeight() {
		List<Entry> list = entries();
		if (list.isEmpty()) {
			return 0;
		}
		Entry last = list.get(list.size() - 1);
		return last.y() + last.height() - (listArea()[1] - (int) Math.round(listScroll));
	}

	/** Zwei Knöpfe einer Anfrage: annehmen (nur eingehend) und ablehnen/zurückziehen – je {x0, x1}. */
	private int[][] requestButtons() {
		int[] a = listArea();
		int deny = a[2] - 14;
		int accept = deny - 16;
		return new int[][] {{accept, accept + 14}, {deny, deny + 14}};
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	//?}
		SalamiWindow.backdrop(graphics, width, height, uiScale);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		TextInput.sync(this, isTyping());
		Skin skin = SalamiWindow.skin();
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		if (selected != null && Friends.friend(selected) == null && Friends.status() == Friends.Status.ONLINE && !Friends.friends().isEmpty()) {
			selected = null; // Freundschaft beendet
			Friends.view(null);
		}

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		int[] f = frame();
		SalamiWindow.draw(g, font, f, I18n.get("salami.module.friends"), mx, my, statusWidth() + 14);
		renderStatus(g, skin, f);

		renderAddField(g, skin, mx, my);
		renderList(g, skin, mx, my);
		int sepX = f[0] + PAD + LIST_W + PAD / 2;
		for (int y = SalamiWindow.contentTop(f); y < f[3] - PAD - NOTICE_H; y += 2) {
			g.fill(sepX, y, sepX + 1, y + 1, ink.line());
		}
		renderChat(g, skin, mx, my);

		Component notice = Friends.notice();
		if (notice != null) {
			TextFit.draw(g, font, notice.getString(), f[0] + PAD, f[3] - PAD - 8, f[2] - f[0] - 2 * PAD,
				Friends.noticeIsError() ? ink.warning() : ink.dim());
		}
		g.pose().popMatrix();
	}

	private String statusLabel() {
		return I18n.get("salami.friends.status." + Friends.status().name().toLowerCase(java.util.Locale.ROOT));
	}

	private int statusWidth() {
		return font.width(statusLabel()) + 8;
	}

	private void renderStatus(GuiGraphics g, Skin skin, int[] f) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		Friends.Status status = Friends.status();
		String label = statusLabel();
		int color = switch (status) {
			case ONLINE -> ONLINE;
			case ERROR, NO_SERVER, NO_ACCOUNT -> ink.warning();
			default -> ink.dim();
		};
		int x = f[2] - PAD - font.width(label);
		g.fill(x - 7, f[1] + 10, x - 3, f[1] + 14, color);
		GuiDraw.text(g, font, label, x, f[1] + 8, color, false);
	}

	private void renderAddField(GuiGraphics g, Skin skin, int mx, int my) {
		field(g, skin, addFieldBox(), addField, I18n.get("salami.friends.add_placeholder"));
		int[] add = addButton();
		boolean ready = !addField.text.isBlank();
		Skin.ButtonKind kind = ready ? Skin.ButtonKind.PRIMARY : Skin.ButtonKind.SECONDARY;
		Skin.UiState state = SalamiWindow.state(add, mx, my);
		skin.iconButton(g, add[0], add[1], add[2] - add[0], kind, state);
		Icons.plus(g, add[0] + (add[2] - add[0] - 7) / 2, add[1] + (add[3] - add[1] - 7) / 2 - (kind == Skin.ButtonKind.GHOST ? 0 : 1),
			skin.buttonInk(kind, state));
	}

	private void renderList(GuiGraphics g, Skin skin, int mx, int my) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int[] a = listArea();
		g.enableScissor(a[0] - 2, a[1], a[2] + 2, a[3]);
		int[][] buttons = requestButtons();
		for (Entry entry : entries()) {
			int y = entry.y();
			if (y + entry.height() < a[1] || y > a[3]) {
				continue;
			}
			boolean hovered = mx >= a[0] && mx < a[2] && my >= y && my < y + entry.height() && my >= a[1] && my < a[3];
			if (entry.section() != null) {
				skin.heading(g, font, entry.section(), a[0] + 1, y + 3, ink.dim(), 1f);
				continue;
			}
			if (entry.request() != null) {
				Friends.Request request = entry.request();
				skin.row(g, a[0], y, a[2] - a[0], ROW, false, Skin.UiState.of(hovered));
				int nameRight = request.incoming() ? buttons[0][0] - 3 : buttons[1][0] - 3;
				String suffix = request.incoming() ? "" : " · " + I18n.get("salami.friends.pending");
				TextFit.draw(g, font, request.name() + suffix, a[0] + 5, y + 5, nameRight - a[0] - 6, request.incoming() ? ink.text() : ink.dim());
				if (request.incoming()) {
					smallButton(g, skin, new int[] {buttons[0][0], y + 2, buttons[0][1], y + ROW - 2}, true, mx, my);
				}
				smallButton(g, skin, new int[] {buttons[1][0], y + 2, buttons[1][1], y + ROW - 2}, false, mx, my);
				continue;
			}
			Friends.Friend friend = entry.friend();
			if (friend == null) {
				continue;
			}
			boolean isSelected = friend.uuid().equals(selected);
			skin.row(g, a[0], y, a[2] - a[0], ROW, isSelected, Skin.UiState.of(hovered));
			g.fill(a[0] + 6, y + 7, a[0] + 10, y + 11, friend.online() ? ONLINE : ink.line());
			int unread = Friends.unread(friend.uuid());
			int nameRight = a[2] - 4;
			if (unread > 0) {
				String badge = unread > 99 ? "99+" : String.valueOf(unread);
				skin.badge(g, font, a[2] - 3, y + 4, badge);
				nameRight -= Math.max(10, font.width(badge) + 6) + 3;
			}
			TextFit.draw(g, font, friend.name(), a[0] + 14, y + 5, nameRight - a[0] - 14, friend.online() || isSelected ? ink.text() : ink.dim());
		}
		if (Friends.friends().isEmpty() && Friends.status() == Friends.Status.ONLINE) {
			int y = entries().get(entries().size() - 1).y() + SECTION + 2;
			List<FormattedCharSequence> lines = font.split(Component.translatable("salami.friends.empty"), LIST_W - 4);
			for (int i = 0; i < lines.size(); i++) {
				GuiDraw.text(g, font, lines.get(i), a[0] + 2, y + i * 10, ink.dim(), false);
			}
		}
		g.disableScissor();
	}

	private void renderChat(GuiGraphics g, Skin skin, int mx, int my) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int[] p = chatPanel();
		Friends.Status status = Friends.status();
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (status != Friends.Status.ONLINE && friend == null) {
			String key = "salami.friends.info." + status.name().toLowerCase(java.util.Locale.ROOT);
			Component text = status == Friends.Status.ERROR && Friends.error() != null
				? Component.translatable(key, Friends.error())
				: Component.translatable(key);
			centeredLines(g, ink, text, p, (p[1] + p[3]) / 2 - 10);
			if (status == Friends.Status.OFF) {
				int[] b = enableButton();
				skin.button(g, font, b[0], b[1], b[2] - b[0], b[3] - b[1], I18n.get("salami.friends.enable"), Skin.ButtonKind.PRIMARY,
					SalamiWindow.state(b, mx, my));
			}
			return;
		}
		if (friend == null) {
			centeredLines(g, ink, Component.translatable(Friends.friends().isEmpty() ? "salami.friends.hint_add" : "salami.friends.hint_select"),
				p, (p[1] + p[3]) / 2 - 10);
			return;
		}

		// Kopfzeile: Name, Status, Entfernen
		int[] h = chatHeader();
		g.fill(h[0] + 2, h[1] + 6, h[0] + 6, h[1] + 10, friend.online() ? ONLINE : ink.line());
		skin.heading(g, font, friend.name(), h[0] + 10, h[1] + 4, ink.text(), 1f);
		String state = I18n.get(friend.online() ? "salami.friends.online" : "salami.friends.offline");
		int stateX = h[0] + 14 + skin.headingWidth(font, friend.name(), 1f);
		int[] remove = removeButton();
		TextFit.draw(g, font, state, stateX, h[1] + 4, remove[0] - 4 - stateX, friend.online() ? ONLINE : ink.dim());
		boolean confirming = isConfirming();
		skin.button(g, font, remove[0], remove[1], remove[2] - remove[0], remove[3] - remove[1],
			I18n.get(confirming ? "salami.friends.remove_confirm" : "salami.friends.remove"), Skin.ButtonKind.DANGER,
			confirming ? Skin.UiState.PRESSED : SalamiWindow.state(remove, mx, my));
		skin.divider(g, h[0], h[3] + 1, h[2] - h[0], Skin.Surface.WINDOW);

		renderMessages(g, skin, friend);

		field(g, skin, chatFieldBox(), chatField, I18n.get("salami.friends.message_placeholder", friend.name()));
		boolean canSend = !chatField.text.isBlank() && !sending && Friends.status() == Friends.Status.ONLINE;
		int[] send = sendButton();
		skin.button(g, font, send[0], send[1], send[2] - send[0], send[3] - send[1], I18n.get("salami.friends.send"),
			canSend ? Skin.ButtonKind.PRIMARY : Skin.ButtonKind.SECONDARY, SalamiWindow.state(send, mx, my));
		if (!chatField.text.isEmpty()) {
			String count = chatField.text.length() + "/" + Friends.MAX_MESSAGE;
			int[] box = chatFieldBox();
			if (chatField.text.length() > Friends.MAX_MESSAGE - 40) {
				GuiDraw.text(g, font, count, box[2] - font.width(count) - 3, box[1] - 10, ink.dim(), false);
			}
		}
	}

	/** Eine Nachricht, umgebrochen; {@code y} wird beim Zeichnen gesetzt. */
	private record Bubble(Friends.Message message, boolean mine, List<FormattedCharSequence> lines, int width, int height) {}

	private List<Bubble> bubbles(int areaWidth) {
		List<Friends.Message> messages = Friends.conversation(selected);
		List<Bubble> result = new ArrayList<>();
		if (messages == null) {
			return result;
		}
		int maxWidth = Math.max(60, areaWidth * 3 / 4);
		UUID me = Friends.me();
		for (Friends.Message message : messages) {
			List<FormattedCharSequence> lines = font.split(Component.literal(message.text()), maxWidth - 8);
			int w = 0;
			for (FormattedCharSequence line : lines) {
				w = Math.max(w, font.width(line));
			}
			result.add(new Bubble(message, message.from().equals(me), lines, w + 8, lines.size() * 10 + 4));
		}
		return result;
	}

	private int messagesHeight(List<Bubble> list) {
		int total = 0;
		for (Bubble bubble : list) {
			total += bubble.height() + BUBBLE_GAP;
		}
		return total;
	}

	private void renderMessages(GuiGraphics g, Skin skin, Friends.Friend friend) {
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		Skin.Ink card = skin.ink(Skin.Surface.CARD);
		int[] m = messagesArea();
		List<Bubble> list = bubbles(m[2] - m[0]);
		if (Friends.conversation(friend.uuid()) == null) {
			GuiDraw.text(g, font, I18n.get("salami.friends.loading"), (m[0] + m[2] - font.width(I18n.get("salami.friends.loading"))) / 2,
				(m[1] + m[3]) / 2, ink.dim(), false);
			return;
		}
		if (list.isEmpty()) {
			centeredLines(g, ink, Component.translatable("salami.friends.no_messages", friend.name()), m, (m[1] + m[3]) / 2 - 5);
			return;
		}
		int total = messagesHeight(list);
		int visible = m[3] - m[1];
		chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, total - visible)));
		// Unten ausrichten: neueste Nachricht am unteren Rand
		int y = m[3] - total + (int) Math.round(chatScroll);
		g.enableScissor(m[0], m[1], m[2], m[3]);
		for (Bubble bubble : list) {
			int top = y;
			y += bubble.height() + BUBBLE_GAP;
			if (top + bubble.height() < m[1] || top > m[3]) {
				continue;
			}
			int x = bubble.mine() ? m[2] - bubble.width() - 2 : m[0] + 2;
			int textColor;
			if (bubble.mine()) {
				GuiDraw.rounded(g, x, top, bubble.width(), bubble.height(), 2, ink.accent());
				textColor = ink.onAccent();
			} else {
				skin.surface(g, Skin.Surface.CARD, x, top, bubble.width(), bubble.height());
				textColor = card.text();
			}
			for (int i = 0; i < bubble.lines().size(); i++) {
				GuiDraw.text(g, font, bubble.lines().get(i), x + 4, top + 3 + i * 10, textColor, false);
			}
			String time = TIME.format(Instant.ofEpochMilli(bubble.message().at()).atZone(ZoneId.systemDefault()));
			int timeX = bubble.mine() ? x - font.width(time) - 4 : x + bubble.width() + 4;
			GuiDraw.text(g, font, time, timeX, top + bubble.height() - 9, ink.dim(), false);
		}
		g.disableScissor();
	}

	private void centeredLines(GuiGraphics g, Skin.Ink ink, Component text, int[] area, int y) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(40, area[2] - area[0] - 20));
		int cx = (area[0] + area[2]) / 2;
		int top = y - (lines.size() - 1) * 5;
		for (int i = 0; i < lines.size(); i++) {
			GuiDraw.text(g, font, lines.get(i), cx - font.width(lines.get(i)) / 2, top + i * 10, ink.dim(), false);
		}
	}

	private void field(GuiGraphics g, Skin skin, int[] r, Field field, String placeholder) {
		Skin.Ink ink = skin.ink(Skin.Surface.INPUT);
		boolean isFocused = focused == field;
		skin.input(g, r[0], r[1], r[2] - r[0], r[3] - r[1], isFocused);
		int inner = r[2] - r[0] - 8;
		if (field.text.isEmpty()) {
			TextFit.draw(g, font, placeholder, r[0] + 4, r[1] + 4, inner, ink.dim());
		} else {
			// Ende sichtbar halten: von hinten abschneiden
			String shown = font.plainSubstrByWidth(field.text, inner, true);
			GuiDraw.text(g, font, shown, r[0] + 4, r[1] + 4, ink.text(), false);
		}
		if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = r[0] + 4 + Math.min(font.width(field.text), inner);
			g.fill(cx, r[1] + 3, cx + 1, r[3] - 3, ink.text());
		}
	}

	/** Annehmen (Haken) oder Ablehnen (Kreuz) einer Anfrage. */
	private void smallButton(GuiGraphics g, Skin skin, int[] r, boolean accept, int mx, int my) {
		Skin.ButtonKind kind = accept ? Skin.ButtonKind.SECONDARY : Skin.ButtonKind.DANGER;
		Skin.UiState state = SalamiWindow.state(r, mx, my);
		skin.iconButton(g, r[0], r[1], r[2] - r[0], kind, state);
		int color = accept && state != Skin.UiState.HOVER ? ONLINE : skin.buttonInk(kind, state);
		if (accept) {
			Icons.check(g, r[0] + (r[2] - r[0] - 7) / 2, r[1] + (r[3] - r[1] - 5) / 2 - 1, color);
		} else {
			Icons.cross(g, r[0] + (r[2] - r[0] - 5) / 2, r[1] + (r[3] - r[1] - 5) / 2, color);
		}
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		if (inside(backButton(), x, y)) {
			onClose();
			return true;
		}
		if (inside(addFieldBox(), x, y)) {
			focused = addField;
			return true;
		}
		if (inside(addButton(), x, y)) {
			submitAdd();
			return true;
		}
		if (clickList(x, y)) {
			return true;
		}
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (friend == null) {
			if (Friends.status() == Friends.Status.OFF && inside(enableButton(), x, y)) {
				FriendsModule module = FriendsModule.instance();
				if (module != null) {
					SalamiClient.get().modules().setEnabled(module, true);
				}
			}
			return true;
		}
		if (inside(removeButton(), x, y)) {
			if (isConfirming()) {
				confirmRemove = null;
				Friends.remove(friend.uuid());
			} else {
				confirmRemove = friend.uuid();
				confirmUntil = System.currentTimeMillis() + 4_000L;
			}
			return true;
		}
		if (inside(chatFieldBox(), x, y)) {
			focused = chatField;
			return true;
		}
		if (inside(sendButton(), x, y)) {
			submitMessage();
			return true;
		}
		return true;
	}

	private boolean clickList(double x, double y) {
		int[] a = listArea();
		if (!inside(a, x, y)) {
			return false;
		}
		int[][] buttons = requestButtons();
		for (Entry entry : entries()) {
			if (y < entry.y() || y >= entry.y() + entry.height()) {
				continue;
			}
			if (entry.request() != null) {
				Friends.Request request = entry.request();
				if (request.incoming() && x >= buttons[0][0] && x < buttons[0][1]) {
					Friends.accept(request.uuid());
				} else if (x >= buttons[1][0] && x < buttons[1][1]) {
					Friends.deny(request.uuid());
				}
			} else if (entry.friend() != null) {
				select(entry.friend().uuid());
			}
			return true;
		}
		return true;
	}

	private void select(UUID friend) {
		if (!friend.equals(selected)) {
			selected = friend;
			chatScroll = 0;
			chatField.text = "";
			confirmRemove = null;
		}
		Friends.view(friend);
		focused = chatField;
	}

	private void submitAdd() {
		String name = addField.text.trim();
		if (!name.isEmpty()) {
			Friends.addFriend(name);
			addField.text = "";
		}
	}

	private void submitMessage() {
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (friend == null || sending || chatField.text.isBlank()) {
			return;
		}
		String text = chatField.text;
		sending = true;
		Friends.send(friend.uuid(), text, sent -> {
			sending = false;
			if (sent && chatField.text.equals(text)) {
				chatField.text = "";
				chatScroll = 0;
			}
		});
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double x = mouseX / uiScale;
		double y = mouseY / uiScale;
		if (inside(listArea(), x, y)) {
			int[] a = listArea();
			listScroll = Math.max(0, Math.min(listScroll - scrollY * ROW * 2, Math.max(0, listHeight() - (a[3] - a[1]))));
		} else {
			chatScroll += scrollY * 20; // in render() begrenzt
			chatScroll = Math.max(0, chatScroll);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (SalamiMenuScreen.closeOnMenuKey(minecraft, event, isTyping())) {
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			return super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_TAB) {
			focused = focused == addField && selected != null ? chatField : addField;
			return true;
		}
		if (focused == null) {
			return super.keyPressed(event);
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (focused == addField) {
					submitAdd();
				} else {
					submitMessage();
				}
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (!focused.text.isEmpty()) {
					if (event.hasControlDown()) {
						// Strg + Rück: letztes Wort
						String trimmed = focused.text.stripTrailing();
						int space = trimmed.lastIndexOf(' ');
						focused.text = space < 0 ? "" : trimmed.substring(0, space + 1);
					} else {
						focused.text = focused.text.substring(0, focused.text.offsetByCodePoints(focused.text.length(), -1));
					}
				}
			}
			case InputConstants.KEY_V -> {
				if (event.hasControlDown()) {
					paste(minecraft.keyboardHandler.getClipboard());
				}
			}
			default -> {
				// Zeichen kommen über charTyped
			}
		}
		return true;
	}

	private void paste(String clip) {
		StringBuilder clean = new StringBuilder();
		clip.codePoints()
			.filter(c -> c >= 32 && c != 127 && c != 0xA7)
			.forEach(clean::appendCodePoint);
		append(clean.toString());
	}

	private void append(String text) {
		if (focused == null) {
			return;
		}
		String next = focused.text + text;
		if (focused == addField) {
			// Minecraft-Namen: nur Buchstaben, Ziffern, _
			next = next.replaceAll("[^A-Za-z0-9_]", "");
		}
		if (next.length() > focused.max) {
			next = next.substring(0, focused.max);
		}
		focused.text = next;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focused != null) {
			if (event.isAllowedChatCharacter()) {
				append(event.codepointAsString());
			}
			return true;
		}
		return super.charTyped(event);
	}
}
