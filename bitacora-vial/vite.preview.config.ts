import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';

/**
 * A one-off build config for a self-contained, single-HTML-file bundle you can open
 * directly in a browser (or publish as a Claude Artifact) to click through the app without
 * any local tooling. Skips the PWA/service-worker plugin from vite.config.ts on purpose —
 * service workers don't register reliably inside a sandboxed preview iframe, and this build
 * is for trying the app out, not for testing installability.
 */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: 'dist-preview',
    emptyOutDir: true,
  },
});
