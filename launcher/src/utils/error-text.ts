/**
 * Readable text for any error. Tauri commands reject with a CommandError object
 * (`{ message, kind, … }`), and `String(error)` turns that into "[object Object]".
 */
export function errorText(error: unknown): string {
  let text: string;
  if (typeof error === "string") text = error;
  else if (error && typeof error === "object" && "message" in error) {
    text = String((error as { message: unknown }).message);
  } else if (error === undefined || error === null) text = "";
  else {
    text = String(error);
    if (text === "[object Object]") {
      try {
        text = JSON.stringify(error);
      } catch {
        // keep the generic text
      }
    }
  }
  return text.replace(/^Other error: /, "");
}
