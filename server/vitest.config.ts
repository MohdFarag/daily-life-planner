import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Each test file gets its own database schema, so files can run in parallel.
    fileParallelism: true,
    testTimeout: 20_000,
    hookTimeout: 30_000,
  },
});
