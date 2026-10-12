import { motion } from "framer-motion";
import { useMemo } from "react";
import { useQualitySettingsStore } from "../../store/quality-settings-store";
import { SalamiSlice } from "./SalamiSlice";

/**
 * Langsam fallende Salamischeiben hinter dem Inhalt (Vorlage: features/snow-effect).
 * Anzahl richtet sich nach der Qualitätsstufe; liegt unter dem UI, damit nichts verdeckt wird.
 */
export function SalamiFall() {
  const { qualityLevel } = useQualitySettingsStore();

  const slices = useMemo(() => {
    const count = qualityLevel === "low" ? 8 : qualityLevel === "high" ? 24 : 14;
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      size: Math.random() * 26 + 22,
      delay: Math.random() * 20,
      duration: Math.random() * 14 + 22,
      rotation: Math.random() * 360,
      spin: (Math.random() > 0.5 ? 1 : -1) * (180 + Math.random() * 180),
      opacity: Math.random() * 0.25 + 0.35,
    }));
  }, [qualityLevel]);

  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden">
      {slices.map((s) => (
        <motion.div
          key={s.id}
          className="absolute"
          initial={{ top: "-8%", left: `${s.x}%`, rotate: s.rotation, opacity: 0 }}
          animate={{ top: "108%", rotate: s.rotation + s.spin, opacity: [0, s.opacity, s.opacity, 0] }}
          transition={{ duration: s.duration, delay: s.delay, repeat: Infinity, ease: "linear" }}
          style={{ filter: "drop-shadow(0 4px 6px rgba(0,0,0,0.35))" }}
        >
          <SalamiSlice size={s.size} />
        </motion.div>
      ))}
    </div>
  );
}
