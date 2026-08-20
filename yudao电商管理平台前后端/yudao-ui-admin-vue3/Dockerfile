FROM node:22-alpine AS build

WORKDIR /app

RUN npm install --global pnpm@8.15.9

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

ARG VITE_BASE_URL=https://api.vanzhome.com
ARG VITE_API_URL=/admin-api
ARG VITE_BASE_PATH=/admin/
ARG VITE_FURNITURE_WEB_URL=https://www.vanzhome.com
ARG VITE_MALL_H5_DOMAIN=https://www.vanzhome.com

ENV VITE_BASE_URL=${VITE_BASE_URL}
ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
ENV VITE_FURNITURE_WEB_URL=${VITE_FURNITURE_WEB_URL}
ENV VITE_MALL_H5_DOMAIN=${VITE_MALL_H5_DOMAIN}

RUN pnpm build:prod && test -s dist-prod/index.html

FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist-prod/ /usr/share/nginx/html/admin/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
