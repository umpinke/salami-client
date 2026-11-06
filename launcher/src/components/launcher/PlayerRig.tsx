"use client";

import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { SkinViewer } from './SkinViewer';
import { WebGLBoundary } from '../WebGLBoundary';
import { SkinViewer3D, isWebGLAvailable } from '../../lib/skin3d';
import { useActiveSkinTexture } from '../../hooks/useActiveSkinTexture';
import { useSkinPreview } from '../../hooks/useSkinPreview';
import { useWindowFocus } from '../../hooks/useWindowFocus';
import { useMinecraftAuthStore } from '../../store/minecraft-auth-store';
import { useQualitySettingsStore } from '../../store/quality-settings-store';

const NO_ACCOUNT_SKIN_URL = "/skins/steve.png";
const RIG_SHADOW = 'drop-shadow(5px 10px 5px rgba(0,0,0,0.75))';

export const PLAYER_RIG_WIDTH = 225;
export const PLAYER_RIG_HEIGHT = 450;

/** Die 3D-Fläche ist größer als die Figur, damit Namensschild und Armschwung nicht abgeschnitten werden. */
const VIEW_WIDTH = 340;
const VIEW_HEIGHT = 560;
/** So groß, dass die Figur etwa PLAYER_RIG_HEIGHT hoch ist. */
const VIEW_ZOOM = 0.75;

const viewStyle: React.CSSProperties = {
  position: "absolute",
  left: "50%",
  top: "50%",
  transform: "translate(-50%, -50%)",
  width: `${VIEW_WIDTH}px`,
  height: `${VIEW_HEIGHT}px`,
  pointerEvents: "none",
  filter: RIG_SHADOW,
};

const flatStyle: React.CSSProperties = {
  position: "absolute",
  inset: 0,
  width: "100%",
  height: "100%",
  pointerEvents: "none",
  filter: RIG_SHADOW,
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

/** Spielerfigur auf der Startseite: 3D mit Leerlauf-Animation, sonst ein Standbild. */
export function PlayerRig({ playerName, showNametag = true }: PlayerRigProps) {
  const { t } = useTranslation();
  const activeAccount = useMinecraftAuthStore((state) => state.activeAccount);
  const { textureUrl, variant, loading: skinLoading } = useActiveSkinTexture();
  const wants3d = useQualitySettingsStore((s) => s.cosmeticRenderer3d);
  const animated = wants3d && isWebGLAvailable();
  const isWindowFocused = useWindowFocus();
  const hasAccount = !!activeAccount;
  const loading = useMinLoading(hasAccount && skinLoading, 450);

  const rigTextureUrl = hasAccount ? (textureUrl ?? NO_ACCOUNT_SKIN_URL) : NO_ACCOUNT_SKIN_URL;
  const rigVariant = hasAccount ? variant : "slim";
  const rigNametag = hasAccount
    ? (playerName && showNametag ? playerName.toString() : null)
    : t('auth.noAccountNametag');

  const { url: stillUrl } = useSkinPreview(!animated && !loading, {
    textureUrl: rigTextureUrl,
    variant: rigVariant,
    nametag: rigNametag,
    width: PLAYER_RIG_WIDTH,
    height: PLAYER_RIG_HEIGHT,
    dpr: 1.5,
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
      {animated ? (
        <WebGLBoundary label="player rig" fallback={stillRig}>
          {!loading && (
            <SkinViewer3D
              textureUrl={rigTextureUrl}
              variant={rigVariant}
              nametag={rigNametag}
              animation="idle"
              zoom={VIEW_ZOOM}
              draggable={false}
              paused={!isWindowFocused}
              className="bg-transparent"
              style={viewStyle}
            />
          )}
        </WebGLBoundary>
      ) : (
        stillRig
      )}
    </div>
  );
}
