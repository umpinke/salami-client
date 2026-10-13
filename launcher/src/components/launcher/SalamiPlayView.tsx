import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import type { Profile } from "../../types/profile";
import { MainLaunchButton } from "./MainLaunchButton";
import { PlayerRig } from "./PlayerRig";

interface LaunchVersion {
  id: string;
  label: string;
  icon?: string;
  isCustom?: boolean;
  profileId?: string;
}

interface SalamiPlayViewProps {
  profiles: Profile[];
  selectedProfile: Profile | null;
  versions: LaunchVersion[];
  onVersionChange: (profileId: string) => void;
  playerName: string | null | undefined;
  outline?: { strength: number; thickness: number; sensitivity: number };
}

const RECENT_COUNT = 4;
const LOADER_NAMES: Record<string, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
};

/**
 * Startseite im Salami-Layout: links Profil, Start-Knopf und zuletzt gespielte Profile,
 * rechts die Spielfigur – statt NoRisks zentrierter Figur mit Knopf darüber.
 */
export function SalamiPlayView({ profiles, selectedProfile, versions, onVersionChange, playerName, outline }: SalamiPlayViewProps) {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor);

  const recent = useMemo(
    () =>
      [...profiles]
        .sort((a, b) => (b.last_played ?? "").localeCompare(a.last_played ?? ""))
        .slice(0, RECENT_COUNT),
    [profiles],
  );

  const loaderName = selectedProfile ? LOADER_NAMES[selectedProfile.loader] ?? selectedProfile.loader : "";

  return (
    <div className="relative z-10 grid h-full w-full grid-cols-[minmax(360px,44%)_1fr] items-center gap-6 px-10">
      {/* z-20: die Dreh-Ebene der Spielfigur ist größer als ihre Spalte und läge sonst über dem Panel */}
      <section className="relative z-20 flex flex-col gap-6 rounded-3xl border border-white/10 bg-black/50 p-8 shadow-[0_20px_60px_rgba(0,0,0,0.45)] backdrop-blur-md">
        <div>
          <p className="text-sm uppercase tracking-[0.3em] text-white/70">
            {playerName ? t("salami.play.greeting", { name: playerName }) : t("salami.play.greeting_anon")}
          </p>
          <h1 className="mt-2 break-words font-minecraft text-5xl leading-tight text-white text-shadow">
            {selectedProfile?.name ?? t("salami.play.no_profile")}
          </h1>
          {selectedProfile && (
            <p className="mt-2 flex items-center gap-2 text-base text-white/70">
              <Icon icon="solar:box-bold" className="h-5 w-5" style={{ color: accent.value }} />
              {t("salami.play.version", { version: selectedProfile.game_version, loader: loaderName })}
            </p>
          )}
        </div>

        {versions.length > 0 && (
          <MainLaunchButton
            defaultVersion={selectedProfile?.id ?? versions[0]?.id}
            onVersionChange={onVersionChange}
            versions={versions}
            selectedVersionLabel={selectedProfile?.name}
            mainButtonWidth="w-full"
            maxWidth="440px"
            mainButtonHeight="h-16"
          />
        )}

        {recent.length > 1 && (
          <div>
            <div className="mb-2 text-xs uppercase tracking-[0.2em] text-white/45">{t("salami.play.recent")}</div>
            <div className="flex max-w-[440px] flex-wrap gap-2">
              {recent.map((profile) => {
                const active = profile.id === selectedProfile?.id;
                return (
                  <button
                    key={profile.id}
                    type="button"
                    onClick={() => onVersionChange(profile.id)}
                    className={cn(
                      "flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm transition-colors",
                      active ? "text-white" : "border-white/10 bg-black/30 text-white/75 hover:bg-white/10 hover:text-white",
                    )}
                    style={active ? { borderColor: accent.value, backgroundColor: `${accent.value}44` } : undefined}
                  >
                    <span className="max-w-[160px] truncate">{profile.name}</span>
                    <span className="text-xs text-white/45">{profile.game_version}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <section className="relative flex h-full items-center justify-center">
        <div className="w-full max-w-[460px]">
          <PlayerRig playerName={playerName} outline={outline} showNametag={false} />
        </div>
      </section>
    </div>
  );
}
