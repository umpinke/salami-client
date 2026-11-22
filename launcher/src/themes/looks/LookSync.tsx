import { useEffect } from "react";
import { useLook } from "./registry";

/** Setzt `<html data-look="…">`, woran die Regeln in `themes/looks/<id>/look.css` hängen. */
export function LookSync() {
  const look = useLook();
  useEffect(() => {
    document.documentElement.dataset.look = look.id;
  }, [look.id]);
  return null;
}
