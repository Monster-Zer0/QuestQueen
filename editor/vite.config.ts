import { defineConfig } from "vite";

export default defineConfig({
  root: ".",
  publicDir: "public",
  server: { port: 5173 },
  // Two pages while the Living Book (book.html) grows to parity with the classic editor (index.html).
  build: { rollupOptions: { input: { index: "index.html", book: "book.html" } } },
});
