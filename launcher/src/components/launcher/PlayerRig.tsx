"use client";

import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { SkinViewer } from './SkinViewer';
import { useActiveSkinTexture } from '../../hooks/useActiveSkinTexture';
import { useSkinPreview } from '../../hooks/useSkinPreview';
import { useMinecraftAuthStore } from '../../store/minecraft-auth-store';

const NO_ACCOUNT_SKIN_URL = "/skins/steve.png";
/** Weicher Schatten nach unten statt eines harten Schlagschattens. */
const RIG_SHADOW = 'drop-shadow(0 14px 12px rgba(0,0,0,0.45))';

export const PLAYER_RIG_WIDTH = 225;
export const PLAYER_RIG_HEIGHT = 450;

const flatStyle: React.CSSProperties = {
  position: "absolute",
  inset: 0,
  width: "100%",
  height: "100%",
  pointerEvents: "none",
  filter: RIG_SHADOW,
  // das Bild ist dreifach aufgelöst gerendert und wird geglättet verkleinert – saubere Kanten
  imageRendering: "auto",
};

interface PlayerRigProps {
  playerName: string | null | undefined;
  /** Namensschild über dem Kopf (Salami-Startseite zeigt den Namen daneben). */
  showNametag?: boolean;
}

function useMinLoading(active: boolean, minMs: number): boolean {
  const [done, setDone] = useState(!active);
  const startRef = useRef(Date.now());
  useEffect(() => {
    if (active) {
      startRef.current = Date.now();
      setDone(false);
      return;
    }
    if (done) return;
    const remaining = Math.max(0, minMs - (Date.now() - startRef.current));
    const id = setTimeout(() => setDone(true), remaining);
    return () => clearTimeout(id);
  }, [active, minMs, done]);
  return active || !done;
}

/** Spielerfigur auf der Startseite: ein Standbild des Skins, ohne Animation. */
export function PlayerRig({ playerName, showNametag = true }: PlayerRigProps) {
  const { t } = useTranslation();
  const activeAccount = useMinecraftAuthStore((state) => state.activeAccount);
  const { textureUrl, variant, loading: skinLoading } = useActiveSkinTexture();
  const hasAccount = !!activeAccount;
  const loading = useMinLoading(hasAccount && skinLoading, 450);

  const rigTextureUrl = hasAccount ? (textureUrl ?? NO_ACCOUNT_SKIN_URL) : NO_ACCOUNT_SKIN_URL;
  const rigVariant = hasAccount ? variant : "slim";
  const rigNametag = hasAccount
    ? (playerName && showNametag ? playerName.toString() : null)
    : t('auth.noAccountNametag');

  const { url: stillUrl } = useSkinPreview(!loading, {
    textureUrl: rigTextureUrl,
    variant: rigVariant,
    nametag: rigNametag,
    width: PLAYER_RIG_WIDTH,
    height: PLAYER_RIG_HEIGHT,
    dpr: 3,
    fallbackUrl: null,
  });

  const stillRig = stillUrl ? (
    <SkinViewer
      skinUrl={stillUrl}
      playerName={playerName?.toString()}
      width={PLAYER_RIG_WIDTH}
      height={PLAYER_RIG_HEIGHT}
      className="bg-transparent"
      style={flatStyle}
    />
  ) : null;

  return (
    <div
      className="relative flex-shrink-0"
      style={{ width: `${PLAYER_RIG_WIDTH}px`, height: `${PLAYER_RIG_HEIGHT}px` }}
    >
      {stillRig}
    </div>
  );
}
