import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("nginx deployment config", () => {
  it("serves the Vite SPA with immutable asset caching and a non-cached shell", () => {
    const source = readSource("../nginx.conf");

    expect(source).toContain("try_files $uri $uri/ /index.html;");
    expect(source).toContain("location = /index.html");
    expect(source).toContain('add_header Cache-Control "no-store" always;');
    expect(source).toContain("location /assets/");
    expect(source).toContain('add_header Cache-Control "public, max-age=31536000, immutable" always;');
    expect(source).toContain("try_files $uri =404;");
  });

  it("enables gzip for launch assets and adds baseline browser security headers", () => {
    const source = readSource("../nginx.conf");

    expect(source).toContain("gzip on;");
    expect(source).toContain("gzip_types");
    expect(source).toContain("application/javascript");
    expect(source).toContain("text/css");
    expect(source).toContain('add_header X-Content-Type-Options "nosniff" always;');
    expect(source).toContain('add_header X-Frame-Options "SAMEORIGIN" always;');
    expect(source).toContain('add_header Referrer-Policy "strict-origin-when-cross-origin" always;');
    expect(source).toContain("Content-Security-Policy");
    expect(source).toContain("connect-src 'self' http: https:");
    expect(source).toContain("form-action 'self' http: https:");
  });

  it("builds the Docker image reproducibly before serving with nginx", () => {
    const dockerfile = readSource("../Dockerfile");
    const dockerignore = readSource("../.dockerignore");

    expect(dockerfile).toContain("FROM node:");
    expect(dockerfile).toContain("AS build");
    expect(dockerfile).toContain("COPY package.json package-lock.json ./");
    expect(dockerfile).toContain("RUN npm ci");
    expect(dockerfile).toContain("RUN npm run build");
    expect(dockerfile).toContain("FROM nginx:");
    expect(dockerfile).toContain("COPY nginx.conf /etc/nginx/conf.d/default.conf");
    expect(dockerfile).toContain("COPY --from=build /app/dist/ /usr/share/nginx/html/");
    expect(dockerfile).not.toContain("COPY dist/ /usr/share/nginx/html/");
    expect(dockerfile).not.toContain("VITE_SHOW_AUTH_TOKEN_PANEL");
    expect(dockerignore).toContain("dist");
    expect(dockerignore).toContain(".env");
  });
});
