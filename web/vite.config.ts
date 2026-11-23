import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Laluan relatif: sama berfungsi di GitHub Pages (/cloudpix/) dan Cloudflare Pages (/)
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: { target: "es2020", sourcemap: false, chunkSizeWarningLimit: 900 },
});
