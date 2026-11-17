/**
 * Zustand des Freunde-Reiters: Liste, Anfragen, Gespräche und Ungelesen-Zähler. Der Speicher
 * hört auf die Live-Ereignisse des Backends und bleibt so aktuell, auch wenn der Reiter zu ist
 * (für den Zähler in der Navigation).
 */
import { listen } from "@tauri-apps/api/event";
import { create } from "zustand";
import {
  FRIENDS_CONNECTION_EVENT,
  FRIENDS_EVENT,
  FriendsService,
  errorText,
  type Friend,
  type FriendMessage,
  type FriendRequest,
  type FriendsEvent,
} from "./friends-service";

interface FriendsState {
  /** Zustand des Backends geladen? */
  ready: boolean;
  available: boolean;
  enabled: boolean;
  connected: boolean;
  signedIn: boolean;
  /** Letzter Fehler beim Laden der Liste (z. B. Server nicht erreichbar). */
  error: string | null;
  loading: boolean;

  me: { uuid: string; name: string } | null;
  friends: Friend[];
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
  unread: Record<string, number>;

  /** Gewählter Freund und ob der Reiter gerade offen ist (dann zählt nichts als ungelesen). */
  activeUuid: string | null;
  tabOpen: boolean;
  /** Verläufe pro Freund, älteste zuerst. */
  conversations: Record<string, FriendMessage[]>;

  init: () => Promise<void>;
  setEnabled: (enabled: boolean) => Promise<void>;
  refresh: () => Promise<void>;
  setTabOpen: (open: boolean) => void;
  select: (uuid: string | null) => Promise<void>;
  sendText: (text: string) => Promise<void>;
  sendFile: (path: string) => Promise<void>;
  request: (name: string) => Promise<"requested" | "friends">;
  respond: (action: "accept" | "deny" | "remove", uuid: string) => Promise<void>;
}

/** online zuerst, dann nach Namen – wie die Liste des Servers */
const sortFriends = (friends: Friend[]) =>
  [...friends].sort((a, b) => Number(b.online) - Number(a.online) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));

const appendMessage = (list: FriendMessage[] | undefined, message: FriendMessage) =>
  list?.some((m) => m.id === message.id) ? list : [...(list ?? []), message];

let initialized = false;

export const useFriendsStore = create<FriendsState>((set, get) => {
  function onEvent(event: FriendsEvent) {
    const state = get();
    switch (event.type) {
      case "ready":
      case "request":
      case "friends":
        void state.refresh();
        break;
      case "removed": {
        const { [event.uuid]: _gone, ...conversations } = state.conversations;
        set({ conversations, activeUuid: state.activeUuid === event.uuid ? null : state.activeUuid });
        void state.refresh();
        break;
      }
      case "presence":
        set({
          friends: sortFriends(
            state.friends.map((f) =>
              f.uuid === event.uuid ? { ...f, name: event.name, online: event.online, status: event.status ?? null } : f,
            ),
          ),
        });
        break;
      case "message": {
        const { message } = event;
        const mine = message.from === state.me?.uuid;
        const other = mine ? message.to : message.from;
        const conversations = state.conversations[other]
          ? { ...state.conversations, [other]: appendMessage(state.conversations[other], message) }
          : state.conversations;
        const watching = state.tabOpen && state.activeUuid === other;
        if (mine || watching) {
          set({ conversations });
          if (!mine) void FriendsService.read(other).catch(() => undefined);
        } else {
          set({ conversations, unread: { ...state.unread, [other]: (state.unread[other] ?? 0) + 1 } });
        }
        break;
      }
    }
  }

  return {
    ready: false,
    available: false,
    enabled: false,
    connected: false,
    signedIn: false,
    error: null,
    loading: false,
    me: null,
    friends: [],
    incoming: [],
    outgoing: [],
    unread: {},
    activeUuid: null,
    tabOpen: false,
    conversations: {},

    async init() {
      if (!initialized) {
        initialized = true;
        void listen<FriendsEvent>(FRIENDS_EVENT, (e) => onEvent(e.payload));
        void listen<boolean>(FRIENDS_CONNECTION_EVENT, (e) => set({ connected: e.payload }));
      }
      try {
        const backend = await FriendsService.state();
        set({ ...backend, ready: true });
        if (backend.enabled && backend.signedIn) await get().refresh();
      } catch (error) {
        set({ ready: true, error: errorText(error) });
      }
    },

    async setEnabled(enabled) {
      const backend = await FriendsService.setEnabled(enabled);
      if (enabled) {
        set({ ...backend, error: null });
        await get().refresh();
      } else {
        set({
          ...backend,
          me: null,
          friends: [],
          incoming: [],
          outgoing: [],
          unread: {},
          conversations: {},
          activeUuid: null,
          error: null,
        });
      }
    },

    async refresh() {
      if (!get().enabled) return;
      set({ loading: true });
      try {
        const overview = await FriendsService.overview();
        const activeUuid = get().activeUuid;
        set({
          me: overview.me,
          friends: sortFriends(overview.friends),
          incoming: overview.incoming,
          outgoing: overview.outgoing,
          unread: overview.unread,
          signedIn: true,
          error: null,
          loading: false,
          activeUuid: activeUuid && overview.friends.some((f) => f.uuid === activeUuid) ? activeUuid : null,
        });
      } catch (error) {
        set({ error: errorText(error), loading: false });
      }
    },

    setTabOpen(open) {
      set({ tabOpen: open });
      const { activeUuid, unread } = get();
      if (open && activeUuid && unread[activeUuid]) void get().select(activeUuid);
    },

    async select(uuid) {
      set({ activeUuid: uuid });
      if (!uuid) return;
      // Verlauf holen setzt auf dem Server auch den Ungelesen-Zähler zurück.
      const messages = await FriendsService.messages(uuid);
      const { [uuid]: _read, ...unread } = get().unread;
      set({ conversations: { ...get().conversations, [uuid]: messages }, unread });
    },

    async sendText(text) {
      const to = get().activeUuid;
      const trimmed = text.trim();
      if (!to || !trimmed) return;
      const message = await FriendsService.send(to, trimmed);
      set({ conversations: { ...get().conversations, [to]: appendMessage(get().conversations[to], message) } });
    },

    async sendFile(path) {
      const to = get().activeUuid;
      if (!to) return;
      const message = await FriendsService.sendFile(to, path);
      set({ conversations: { ...get().conversations, [to]: appendMessage(get().conversations[to], message) } });
    },

    async request(name) {
      const result = await FriendsService.request(name);
      await get().refresh();
      return result.status;
    },

    async respond(action, uuid) {
      await FriendsService.respond(action, uuid);
      if (action === "remove") {
        const { [uuid]: _gone, ...conversations } = get().conversations;
        set({ conversations, activeUuid: get().activeUuid === uuid ? null : get().activeUuid });
      }
      await get().refresh();
    },
  };
});

/** Ungelesene Nachrichten plus offene Anfragen an mich – für den Zähler in der Navigation. */
export const selectFriendsBadge = (state: FriendsState) =>
  Object.values(state.unread).reduce((sum, n) => sum + n, 0) + state.incoming.length;
