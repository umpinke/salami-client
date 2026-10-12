import { SALAMI_COLORS } from "./salami-theme";

/** Fettaugen in Prozent-Koordinaten (cx, cy, rx, ry) – fest, damit jede Scheibe gleich aussieht. */
const FAT_SPOTS: Array<[number, number, number, number]> = [
  [32, 30, 6, 5], [58, 24, 5, 4], [70, 44, 6, 5], [44, 50, 7, 5],
  [26, 58, 5, 4], [56, 66, 6, 5], [38, 76, 5, 4], [72, 70, 4, 4],
  [50, 38, 3, 3], [64, 56, 3, 3],
];

const PEPPER: Array<[number, number]> = [[40, 36], [62, 34], [34, 46], [52, 58], [66, 62], [46, 70]];

/** Eine Salamischeibe als SVG – für Hintergrund-Effekte, Spinner und Deko. */
export function SalamiSlice({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      className={className}
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <circle cx="50" cy="50" r="48" fill="#3C1614" />
      <circle cx="50" cy="50" r="45" fill={SALAMI_COLORS.casing} />
      <circle cx="50" cy="50" r="40" fill={SALAMI_COLORS.red} />
      {FAT_SPOTS.map(([cx, cy, rx, ry], i) => (
        <ellipse key={i} cx={cx} cy={cy} rx={rx} ry={ry} fill={SALAMI_COLORS.fat} />
      ))}
      {PEPPER.map(([cx, cy], i) => (
        <circle key={i} cx={cx} cy={cy} r="1.6" fill={SALAMI_COLORS.pepper} />
      ))}
      <ellipse cx="38" cy="32" rx="20" ry="12" fill="white" opacity="0.08" />
    </svg>
  );
}
