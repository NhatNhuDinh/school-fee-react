# Frontend image: the Vite build served by nginx, which also proxies /api and
# /ws to the backend container. Build context is the repository root, built by
# 174's own Docker (BuildKit).
FROM node:24-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
# The layer is reused until the lockfile changes; when it does, the npm cache
# mount keeps the packages that did not change, so only new ones download.
RUN --mount=type=cache,id=edupay-npm,target=/root/.npm,sharing=locked \
    npm ci --no-audit --no-fund --prefer-offline
COPY frontend/ .
# Vite bakes this into the bundle, and live.ts parses it with `new URL`, so it
# has to be absolute: the public origin plus /api.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL NODE_OPTIONS=--max-old-space-size=2048
# The two halves of `npm run build` (tsc && refine build), run side by side:
# they need nothing from each other. The image is still refused if tsc finds
# an error, since the RUN waits for it. The type check is incremental: its
# .tsbuildinfo (tsconfig.json puts it in node_modules/.tmp) lives in a cache
# mount kept between builds. Keep in step with "build" in frontend/package.json.
RUN --mount=type=cache,id=edupay-tsc,target=/src/node_modules/.tmp,sharing=locked \
    test -n "$VITE_API_URL" \
 && { npx tsc --incremental & tsc=$!; npx refine build && wait $tsc; }

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
