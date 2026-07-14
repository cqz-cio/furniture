import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const DEFAULT_PATHS = ["/", "/sofas-plp", "/checkout", "/account/wishlist"];
const DEFAULT_FALLBACK_PATH = "/__oakved_spa_health__/deep/link";

export const parsePostDeployHealthArgs = (argv = [], env = process.env) => {
  const options = {
    baseUrl: env.LAUNCH_HEALTH_BASE_URL || "",
    paths: [],
    assetPath: "",
    fallbackPath: DEFAULT_FALLBACK_PATH,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--base-url") {
      options.baseUrl = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--base-url=")) {
      options.baseUrl = arg.slice("--base-url=".length);
    } else if (arg === "--path") {
      options.paths.push(argv[index + 1] || "");
      index += 1;
    } else if (arg.startsWith("--path=")) {
      options.paths.push(arg.slice("--path=".length));
    } else if (arg === "--asset-path") {
      options.assetPath = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--asset-path=")) {
      options.assetPath = arg.slice("--asset-path=".length);
    } else if (arg === "--fallback-path") {
      options.fallbackPath = argv[index + 1] || DEFAULT_FALLBACK_PATH;
      index += 1;
    } else if (arg.startsWith("--fallback-path=")) {
      options.fallbackPath = arg.slice("--fallback-path=".length);
    }
  }

  if (!options.paths.length) options.paths = [...DEFAULT_PATHS];
  return options;
};

const normalizePath = (path) => {
  const trimmed = String(path || "").trim();
  if (!trimmed) return "/";
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
};

const normalizeBaseUrl = (baseUrl) => String(baseUrl || "").trim().replace(/\/$/, "");

const buildUrl = (baseUrl, path) => `${normalizeBaseUrl(baseUrl)}${normalizePath(path)}`;

export const buildPostDeployChecks = (options = {}) => {
  const baseUrl = normalizeBaseUrl(options.baseUrl);
  const paths = options.paths?.length ? options.paths : DEFAULT_PATHS;
  const checks = [
    {
      name: "app-shell",
      type: "app-shell",
      url: buildUrl(baseUrl, "/"),
    },
    ...paths.map((path) => ({
      name: `page:${normalizePath(path)}`,
      type: "page",
      url: buildUrl(baseUrl, path),
    })),
    {
      name: "spa-fallback",
      type: "spa-fallback",
      url: buildUrl(baseUrl, options.fallbackPath || DEFAULT_FALLBACK_PATH),
    },
  ];

  if (options.assetPath) {
    checks.push({
      name: "asset-cache",
      type: "asset",
      url: buildUrl(baseUrl, options.assetPath),
    });
  }

  return checks;
};

const header = (response, key) => String(response.headers?.get?.(key) || "").toLowerCase();

const hasNoStore = (value) => value.includes("no-store") || value.includes("no-cache");

const hasImmutableCache = (value) => value.includes("max-age=31536000") && value.includes("immutable");

const discoverAssetPath = (html) => {
  const match = String(html || "").match(/(?:src|href)=["']([^"']*\/assets\/[^"']+\.(?:js|css))["']/i);
  return match?.[1] || "";
};

const checkStatus = (errors, check, response) => {
  if (!response.ok) errors.push(`${check.name} expected HTTP 2xx, received ${response.status}`);
};

const checkAppShellHeaders = (errors, response) => {
  if (!hasNoStore(header(response, "cache-control"))) errors.push("app shell must be served with no-store or no-cache");
  if (!header(response, "x-content-type-options").includes("nosniff")) errors.push("app shell must include X-Content-Type-Options=nosniff");
  if (!header(response, "x-frame-options").includes("sameorigin")) errors.push("app shell must include X-Frame-Options=SAMEORIGIN");
  if (!header(response, "content-security-policy")) errors.push("app shell must include a Content-Security-Policy header");
};

const checkAssetHeaders = (errors, response) => {
  if (!hasImmutableCache(header(response, "cache-control"))) {
    errors.push("asset must be served with immutable long cache");
  }
  const contentEncoding = header(response, "content-encoding");
  if (contentEncoding && !["gzip", "br", "zstd"].some((encoding) => contentEncoding.includes(encoding))) {
    errors.push("asset content-encoding must be gzip, br, or zstd when present");
  }
};

export const runPostDeployHealthCheck = async (options = {}, fetchImpl = fetch) => {
  const errors = [];
  const checked = [];
  const baseUrl = normalizeBaseUrl(options.baseUrl);
  if (!baseUrl) {
    return {
      ok: false,
      errors: ["--base-url or LAUNCH_HEALTH_BASE_URL is required."],
      checked,
    };
  }

  const appShellCheck = buildPostDeployChecks({ ...options, baseUrl, paths: [] })[0];
  const appShellResponse = await fetchImpl(appShellCheck.url, { headers: { "Accept-Encoding": "gzip, br" } });
  const appShellHtml = await appShellResponse.text().catch(() => "");
  checkStatus(errors, appShellCheck, appShellResponse);
  checkAppShellHeaders(errors, appShellResponse);
  checked.push(appShellCheck);

  const assetPath = options.assetPath || discoverAssetPath(appShellHtml);
  const checks = buildPostDeployChecks({
    ...options,
    baseUrl,
    assetPath,
  }).filter((check) => check.name !== "app-shell");

  if (!assetPath) errors.push("app shell must reference at least one /assets/ .js or .css file");

  for (const check of checks) {
    const response = await fetchImpl(check.url, { headers: { "Accept-Encoding": "gzip, br" } });
    checkStatus(errors, check, response);
    if (check.type === "asset") checkAssetHeaders(errors, response);
    checked.push(check);
  }

  return {
    ok: errors.length === 0,
    errors,
    checked,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parsePostDeployHealthArgs(process.argv.slice(2));
  runPostDeployHealthCheck(options)
    .then((result) => {
      if (result.ok) {
        console.log(`Post-deploy health check passed: ${result.checked.length} check(s)`);
        result.checked.forEach((check) => console.log(`- ${check.name}: ${check.url}`));
      } else {
        console.error("Post-deploy health check failed:");
        result.errors.forEach((error) => console.error(`error: ${error}`));
        process.exitCode = 1;
      }
    })
    .catch((error) => {
      console.error(error);
      process.exitCode = 1;
    });
}
