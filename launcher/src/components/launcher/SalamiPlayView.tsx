import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import type { Profile } from "../../types/profile";
import { hexAlpha, SURFACE } from "../ui/design-system";
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
 * Startseite des Salami Client: links offen gesetzter Text (Begrüßung, Profil, Start-Knopf,
 * zuletzt gespielt), rechts die Spielfigur auf einer Bühne mit der Spielversion als
 * Schattenriss dahinter – ohne Kasten und ohne NoRisks mittige Figur mit Knopf darunter.
 */
export function SalamiPlayView({ profiles, selectedProfile, versions, onVersionChange, playerName }: SalamiPlayViewProps) {
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
    <div className="relative z-10 grid h-full w-full grid-cols-[minmax(400px,46%)_1fr] items-center">
      {/* z-20: die Dreh-Ebene der Spielfigur ist größer als ihre Spalte und läge sonst über dem Text */}
      <section className="relative z-20 flex flex-col gap-7 pl-14 pr-6">
        <div>
          <p className="flex items-center gap-2.5 text-base text-white/60">
            <span className="h-[3px] w-7" style={{ backgroundColor: accent.value }} />
            {playerName ? t("salami.play.greeting", { name: playerName }) : t("salami.play.greeting_anon")}
          </p>
          <h1 className="mt-3 break-words font-minecraft text-[4.25rem] leading-[1.02] text-white">
            {selectedProfile?.name ?? t("salami.play.no_profile")}
          </h1>
          {selectedProfile && (
            <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
              <span className="border px-3 py-1 text-white/80" style={{ backgroundColor: SURFACE.base, borderColor: SURFACE.line }}>
                Minecraft {selectedProfile.game_version}
              </span>
              <span
                className="border px-3 py-1"
                style={{ backgroundColor: hexAlpha(accent.value, 0.14), borderColor: hexAlpha(accent.value, 0.35), color: accent.light || "#fff" }}
              >
                {loaderName}
              </span>
            </div>
          )}
        </div>

        {versions.length > 0 && (
          <MainLaunchButton
            defaultVersion={selectedProfile?.id ?? versions[0]?.id}
            onVersionChange={onVersionChange}
            versions={versions}
            selectedVersionLabel={selectedProfile?.name}
            mainButtonWidth="w-full"
            maxWidth="400px"
            mainButtonHeight="h-[68px]"
          />
        )}

        {recent.length > 1 && (
          <div>
            <div className="mb-2.5 text-sm text-white/40">{t("salami.play.recent")}</div>
            <div className="flex max-w-[520px] flex-wrap gap-2">
              {recent.map((profile) => {
                const active = profile.id === selectedProfile?.id;
                return (
                  <button
                    key={profile.id}
                    type="button"
                    onClick={() => onVersionChange(profile.id)}
                    className={cn(
                      "flex items-center gap-2.5 border py-2 pl-2 pr-3.5 text-sm transition-colors",
                      active ? "text-white" : "text-white/70 hover:text-white",
                    )}
                    style={{
                      backgroundColor: active ? SURFACE.raised : SURFACE.base,
                      borderColor: active ? SURFACE.lineStrong : SURFACE.line,
                    }}
                  >
                    <span
                      className="flex h-7 w-7 flex-shrink-0 items-center justify-center text-xs uppercase"
                      style={{
                        borderRadius: "calc(var(--border-radius) * 0.6)",
                        backgroundColor: active ? accent.value : SURFACE.raised,
                        color: "#fff",
                      }}
                    >
                      {profile.name.trim().charAt(0) || "?"}
                    </span>
                    <span className="flex flex-col items-start leading-tight">
                      <span className="max-w-[150px] truncate">{profile.name}</span>
                      <span className="text-[11px] text-white/40">{profile.game_version}</span>
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <section className="relative flex h-full items-center justify-center overflow-hidden">
        {selectedProfile && (
          <span
            className="salami-ghost-text pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-[62%] whitespace-nowrap font-minecraft text-[13rem] leading-none"
            aria-hidden="true"
          >
            {selectedProfile.game_version}
          </span>
        )}
        {/* Bühne: flache Scheibe unter den Füßen */}
        <div
          className="pointer-events-none absolute left-1/2 top-1/2 h-[90px] w-[440px] -translate-x-1/2 translate-y-[168px]"
          style={{
            borderRadius: "50%",
            border: `1px solid ${hexAlpha(accent.value, 0.35)}`,
            backgroundImage: `radial-gradient(closest-side, ${hexAlpha(accent.value, 0.3)}, ${hexAlpha(accent.value, 0.06)} 70%, transparent)`,
            boxShadow: `0 0 60px -10px ${hexAlpha(accent.value, 0.5)}`,
          }}
          aria-hidden="true"
        />
        <div className="relative">
          <PlayerRig playerName={playerName} showNametag={false} />
        </div>
      </section>
    </div>
  );
}
