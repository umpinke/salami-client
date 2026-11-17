/**
 * Freunde und Chat: Aufrufe ins Launcher-Backend (`commands/salami_friends_command.rs`) und die
 * Formen der Server-Antworten (`server/capes/friends.mjs`, README „Freunde und Chat“).
 */
import { invoke } from "@tauri-apps/api/core";

/** Wo ein Freund gerade online ist. */
export type FriendStatus = "game" | "launcher" | null;

export interface Friend {
  uuid: string;
  name: string;
  online: boolean;
  /** Fehlt bei älteren Servern – dann gilt „online“ als im Spiel. */
  status?: FriendStatus;
  since?: string;
}

export interface FriendRequest {
  uuid: string;
  name: string;
  at: string;
}

export interface FriendMedia {
  id: string;
  kind: "image" | "clip";
  mime: string;
  size: number;
  name: string;
  /** Die Datei wurde auf dem Server gelöscht (Kontingent des Absenders). */
  expired?: boolean;
}

export interface FriendMessage {
  id: string;
  from: string;
  to: string;
  text: string;
  at: string;
  media?: FriendMedia;
}

export interface FriendsOverview {
  me: { uuid: string; name: string };
  friends: Friend[];
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
  unread: Record<string, number>;
}

export interface FriendsBackendState {
  available: boolean;
  enabled: boolean;
  connected: boolean;
  signedIn: boolean;
}

/** Live-Ereignisse (`salami-friends-event`). */
export type FriendsEvent =
  | { type: "ready" }
  | { type: "presence"; uuid: string; name: string; online: boolean; status?: FriendStatus }
  | { type: "message"; message: FriendMessage; name: string }
  | { type: "request"; uuid: string; name: string }
  | { type: "friends"; uuid: string; name: string; online: boolean; status?: FriendStatus }
  | { type: "removed"; uuid: string };

export const FRIENDS_EVENT = "salami-friends-event";
export const FRIENDS_CONNECTION_EVENT = "salami-friends-connection";

/** Dateitypen, die sich verschicken lassen (wie `MEDIA_TYPES` im Backend). */
export const IMAGE_EXTENSIONS = ["png", "jpg", "jpeg", "webp"];
export const CLIP_EXTENSIONS = ["mp4", "webm"];

export const FriendsService = {
  state: () => invoke<FriendsBackendState>("salami_friends_state"),
  setEnabled: (enabled: boolean) => invoke<FriendsBackendState>("salami_friends_set_enabled", { enabled }),
  reconnect: () => invoke<void>("salami_friends_reconnect"),
  overview: () => invoke<FriendsOverview>("salami_friends_overview"),
  request: (name: string) =>
    invoke<{ status: "requested" | "friends"; uuid: string; name: string }>("salami_friends_request", { name }),
  respond: (action: "accept" | "deny" | "remove", uuid: string) =>
    invoke<{ status: string }>("salami_friends_respond", { action, uuid }),
  messages: (withUuid: string) =>
    invoke<{ messages: FriendMessage[] }>("salami_friends_messages", { with: withUuid }).then((r) => r.messages),
  send: (to: string, text: string) =>
    invoke<{ message: FriendMessage }>("salami_friends_send", { to, text }).then((r) => r.message),
  read: (withUuid: string) => invoke<void>("salami_friends_read", { with: withUuid }),
  sendFile: (to: string, path: string) =>
    invoke<{ message: FriendMessage }>("salami_friends_send_file", { to, path }).then((r) => r.message),
  /** Lädt die Datei (falls nötig) und gibt ihren Pfad im Launcher-Ordner zurück. */
  media: (id: string, mime: string) => invoke<string>("salami_friends_media", { id, mime }),
};

/** Fehlertext aus einem abgelehnten Befehl (Tauri liefert `{ message }` oder einen String). */
export function errorText(error: unknown): string {
  if (typeof error === "string") return error;
  if (error && typeof error === "object" && "message" in error) return String((error as { message: unknown }).message);
  return String(error);
}
