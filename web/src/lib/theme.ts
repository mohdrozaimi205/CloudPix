export type Theme = "system" | "light" | "dark";
const KEY = "cloudpix.theme";

export const getTheme = (): Theme => (localStorage.getItem(KEY) as Theme) || "system";

export function applyTheme() {
  const t = getTheme();
  const dark = t === "dark" || (t === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
}

export function setTheme(t: Theme) {
  localStorage.setItem(KEY, t);
  applyTheme();
}

matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
