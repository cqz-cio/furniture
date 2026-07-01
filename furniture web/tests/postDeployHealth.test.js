import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  buildPostDeployChecks,
  parsePostDeployHealthArgs,
  runPostDeployHealthCheck,
} from "../scripts/post-deploy-health-check.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const scriptPath = new URL("../scripts/post-deploy-health-check.mjs", import.meta.url);

const response = (status, body = "", headers = {}) => ({
  status,
  ok: status >= 200 && status < 300,
  headers: {
    get: (key) => headers[key.toLowerCase()] || "",
  },
  text: async () => body,
});

describe("post-deploy health check", () => {
  it("exposes a repeatable post-deploy health command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:deploy:health"]).toBe("node scripts/post-deploy-health-check.mjs");
    expect(existsSync(scriptPath)).toBe(true);
  });

  it("parses base URL and optional page paths", () => {
    expect(parsePostDeployHealthArgs(["--base-url", "https://shop.example.com", "--path", "/sale"])).toMatchObject({
      baseUrl: "https://shop.example.com",
      paths: ["/sale"],
    });
    expect(parsePostDeployHealthArgs(["--base-url=https://shop.example.com", "--path=/checkout"])).toMatchObject({
      baseUrl: "https://shop.example.com",
      paths: ["/checkout"],
    });
  });

  it("builds launch-critical checks for shell, pages, fallback, and assets", () => {
    const checks = buildPostDeployChecks({
      baseUrl: "https://shop.example.com",
      paths: ["/", "/sofas-plp"],
      assetPath: "/assets/index-abc.js",
    });

    expect(checks.map((check) => check.name)).toEqual([
      "app-shell",
      "page:/",
      "page:/sofas-plp",
      "spa-fallback",
      "asset-cache",
    ]);
    expect(checks.at(-1).url).toBe("https://shop.example.com/assets/index-abc.js");
  });

  it("passes when deployed storefront headers and SPA behavior are healthy", async () => {
    const calls = [];
    const fetchImpl = async (url) => {
      calls.push(url);
      if (url.endsWith("/")) {
        return response(
          200,
          '<html><head><script type="module" src="/assets/index-abc.js"></script></head><body><div id="app"></div></body></html>',
          {
            "cache-control": "no-store",
            "x-content-type-options": "nosniff",
            "x-frame-options": "SAMEORIGIN",
            "content-security-policy": "default-src 'self'",
          },
        );
      }
      if (url.endsWith("/sofas-plp") || url.endsWith("/checkout") || url.includes("__oakved_spa_health__")) {
        return response(200, '<html><body><div id="app"></div></body></html>', { "cache-control": "no-store" });
      }
      if (url.endsWith("/assets/index-abc.js")) {
        return response(200, "console.log('ok')", {
          "cache-control": "public, max-age=31536000, immutable",
          "content-encoding": "gzip",
        });
      }
      throw new Error(`unexpected url ${url}`);
    };

    const result = await runPostDeployHealthCheck(
      { baseUrl: "https://shop.example.com", paths: ["/sofas-plp", "/checkout"] },
      fetchImpl,
    );

    expect(result).toMatchObject({ ok: true, errors: [] });
    expect(calls).toContain("https://shop.example.com/assets/index-abc.js");
  });

  it("fails when cache headers are unsafe for launch", async () => {
    const fetchImpl = async (url) => {
      if (url.endsWith("/")) {
        return response(200, '<script type="module" src="/assets/index-abc.js"></script>', {
          "cache-control": "public, max-age=31536000",
          "x-content-type-options": "nosniff",
          "x-frame-options": "SAMEORIGIN",
          "content-security-policy": "default-src 'self'",
        });
      }
      if (url.includes("__oakved_spa_health__")) return response(200, "<html></html>");
      if (url.endsWith("/assets/index-abc.js")) return response(200, "", { "cache-control": "no-cache" });
      return response(200, "<html></html>");
    };

    const result = await runPostDeployHealthCheck({ baseUrl: "https://shop.example.com" }, fetchImpl);

    expect(result.ok).toBe(false);
    expect(result.errors.join("\n")).toContain("app shell must be served with no-store");
    expect(result.errors.join("\n")).toContain("asset must be served with immutable long cache");
  });
});
