import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "./",        // VERY IMPORTANT for Electron
  plugins: [react()],
  server: {
    port: 5173
  }
});