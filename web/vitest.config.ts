import path from "node:path";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    // Tests call the API by absolute URL; the app default is same-origin ("").
    env: { NEXT_PUBLIC_API_BASE_URL: "http://localhost:8080" },
    // One forked process: the default worker pool ran out of memory with several agents on this PC.
    pool: "forks",
    poolOptions: { forks: { singleFork: true } },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
});
