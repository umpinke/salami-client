import { invoke } from "@tauri-apps/api/core";
import i18n from "../i18n/i18n";

// Friends see this text in Discord, so it goes out in the launcher's language.
const STATE_KEYS: Record<string, string> = {
  "Idling": "discord.idling",
  "Browsing Mods": "discord.browsing_mods",
  "Browsing Modpacks": "discord.browsing_modpacks",
  "Browsing Resource Packs": "discord.browsing_resource_packs",
  "Browsing Shaders": "discord.browsing_shaders",
  "Browsing Data Packs": "discord.browsing_data_packs",
  "Browsing Skins": "discord.browsing_skins",
  "Browsing Clips": "discord.browsing_clips",
  "Viewing a Mod": "discord.viewing_mod",
  "Editing a Profile": "discord.editing_profile",
  "Managing Profiles": "discord.managing_profiles",
  "Configuring Settings": "discord.configuring_settings",
};

export function setDiscordState(state: string) {
  const key = STATE_KEYS[state];
  const text = key ? i18n.t(key) : state;
  invoke("set_discord_state", { stateType: text }).catch(() => {});
}
