import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  server: {
    port: 3000,
    host: true,
    // Dev requests go to /api on the same origin and are proxied to the gateway, so the
    // browser never makes a cross-origin call and CORS stays out of the dev loop.
    proxy: {
      '/api': {
        target: process.env.GATEWAY_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
