import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import tseslint from "typescript-eslint";

const httpFrameworks = ["fastify", "fastify/*", "@fastify/*", "fastify-type-provider-zod"];

export default defineConfig(
  { ignores: ["dist", "node_modules", "coverage"] },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      parserOptions: { projectService: { allowDefaultProject: ["eslint.config.js"] }, tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      "@typescript-eslint/restrict-template-expressions": ["error", { allowNumber: true }],
      "@typescript-eslint/no-unnecessary-condition": "off",
    },
  },
  {
    // Layering: business rules and data access never depend on the HTTP framework or layer.
    files: ["src/modules/**/*.service.ts", "src/modules/**/*.repository.ts", "src/modules/**/merge.ts", "src/modules/**/entities.ts",
      "src/modules/**/storage.ts", "src/modules/**/passwords.ts", "src/modules/**/tokens.ts", "src/shared/**"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [
        { group: httpFrameworks, message: "Services, repositories and shared code must not depend on the HTTP framework." },
        { group: ["**/http/**"], message: "Services, repositories and shared code must not depend on the HTTP layer." },
      ] }],
    },
  },
  {
    // Repositories are the bottom layer: SQL only, no business services.
    files: ["src/modules/**/*.repository.ts"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [
        { group: httpFrameworks, message: "Repositories must not depend on the HTTP framework." },
        { group: ["**/http/**"], message: "Repositories must not depend on the HTTP layer." },
        { group: ["**/*.service.js"], message: "Repositories must not depend on services." },
      ] }],
    },
  },
  {
    // Module boundaries: a service reaches its own repository (./x.repository.js) and the shared
    // users table; other modules' data goes through their services.
    files: ["src/modules/**/*.service.ts"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [
        { group: httpFrameworks, message: "Services must not depend on the HTTP framework." },
        { group: ["**/http/**"], message: "Services must not depend on the HTTP layer." },
        { group: ["../*/*.repository.js", "!../users/users.repository.js"],
          message: "Use the owning module's service instead of its repository." },
      ] }],
    },
  },
  {
    // Background jobs orchestrate services; they never touch SQL directly.
    files: ["src/jobs/**"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [
        { group: ["**/*.repository.js"], message: "Jobs must go through services, not repositories." },
        { group: ["**/shared/db.js"], message: "Jobs must not run SQL." },
      ] }],
    },
  },
  {
    // Fastify route plugins are async by contract even when they only register routes.
    files: ["src/**/*.routes.ts", "src/http/health.ts"],
    rules: { "@typescript-eslint/require-await": "off" },
  },
  {
    files: ["test/**"],
    rules: {
      "@typescript-eslint/no-unsafe-member-access": "off",
      "@typescript-eslint/no-unsafe-assignment": "off",
      "@typescript-eslint/no-unsafe-argument": "off",
      "@typescript-eslint/no-non-null-assertion": "off",
    },
  },
);
