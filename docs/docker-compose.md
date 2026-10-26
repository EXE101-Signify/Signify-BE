# Signify Docker Compose

This Compose setup is for local integration. Run commands from the backend repository root. The frontend source is the separate Git repository at `../signify-fe/Signify-FE`.

## Configuration

Copy `.env.example` to `.env.compose.local` and fill it with local or test credentials. Always pass `--env-file .env.compose.local`; the backend repository's existing `.env` is for local Spring execution and must not be reused implicitly for Compose. Both files are gitignored. Do not print `docker compose config` without `--quiet` when it contains secrets.

Set the following:

- `SIGNIFY_COMPOSE_DB_PASSWORD`, `SIGNIFY_COMPOSE_JWT_SECRET` (at least 32 random UTF-8 bytes), and `SIGNIFY_COMPOSE_JWT_REFRESH_ENCRYPTION_KEY` (Base64 of exactly 32 random bytes).
- `SIGNIFY_COMPOSE_SMTP_MAIL_USERNAME`, `SIGNIFY_COMPOSE_SMTP_MAIL_PASSWORD`, and the three `SIGNIFY_COMPOSE_PAYOS_*` values. The application requires these at startup, even when the corresponding features are not exercised.
- `SIGNIFY_COMPOSE_CORS_ALLOWED_ORIGINS` as the browser frontend origin, and `SIGNIFY_COMPOSE_PUBLIC_API_URL` as the browser-visible backend URL. For the default loopback ports, these are respectively `http://127.0.0.1:13000` and `http://127.0.0.1:18080`. If either host port changes, update both values and rebuild the frontend.
- Storage settings for the selected provider. The Compose default is `r2`; it needs bucket, HTTPS endpoint, access key ID, and secret access key. Source code reads `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`. For `s3`, provide S3 bucket/region and AWS credentials instead. No object storage service is created by Compose.
- `SIGNIFY_COMPOSE_AI_BASE_URL` when the separate sign-prediction API is available. The frontend Gemini translation endpoint uses `SIGNIFY_COMPOSE_GEMINI_API_KEY`; without it, that endpoint returns HTTP 503.

The `SIGNIFY_COMPOSE_*` prefix prevents Compose from silently consuming the backend's existing Neon-related `.env` variables. Do not put real credentials in `.env.example` or build arguments.

## Local PostgreSQL and Redis

```powershell
Copy-Item .env.example .env.compose.local
# Fill .env.compose.local with local/test values.
docker compose --env-file .env.compose.local -f compose.yaml -f compose.local.yaml -f compose.redis.yaml config --quiet
docker compose --env-file .env.compose.local -f compose.yaml -f compose.local.yaml -f compose.redis.yaml up -d --build --wait
docker compose --env-file .env.compose.local -f compose.yaml -f compose.local.yaml -f compose.redis.yaml ps
docker compose --env-file .env.compose.local -f compose.yaml -f compose.local.yaml -f compose.redis.yaml logs -f backend frontend
docker compose --env-file .env.compose.local -f compose.yaml -f compose.local.yaml -f compose.redis.yaml down
```

`down` preserves the named PostgreSQL volume. Do not use `down -v` for normal operation. PostgreSQL and Redis have no published host ports. The two HTTP ports bind only to `127.0.0.1` and can be changed with `SIGNIFY_COMPOSE_BACKEND_HOST_PORT` and `SIGNIFY_COMPOSE_FRONTEND_HOST_PORT`.

The local PostgreSQL image is `postgres:17-alpine`. Liquibase runs during Spring Boot startup; do not run migrations separately. Redis stores OTPs and presence data. Its listener retries after Redis outages, so Redis is not a strict startup dependency for REST, but OTP/presence behavior degrades without it.

## External PostgreSQL or Redis

For Neon or another external PostgreSQL, omit `compose.local.yaml` and set `SIGNIFY_COMPOSE_DB_URL` to its JDBC URL plus `SIGNIFY_COMPOSE_DB_USERNAME` and `SIGNIFY_COMPOSE_DB_PASSWORD`. No production database is needed for image builds. To keep Redis local, include `compose.redis.yaml`; to use external Redis, omit it and set `SIGNIFY_COMPOSE_REDIS_HOST`, `SIGNIFY_COMPOSE_REDIS_PORT`, and optionally `SIGNIFY_COMPOSE_REDIS_PASSWORD`.

The backend reads Redis settings from `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` in `application.yaml`. Compose maps its `SIGNIFY_COMPOSE_REDIS_*` values to those names. It does not bind `SPRING_DATA_REDIS_*`. In the local Redis overlay, the service name `redis` resolves on the Compose `data` network, and the backend waits for its `PING` health check. Running only `compose.yaml` does not create Redis; its default hostname `redis` therefore fails DNS resolution unless an external Redis with that hostname is reachable on the backend's network.

For Dokploy, first determine whether Redis is a service in the same Compose project or is managed separately. If it is a separate Dokploy application/project, do not assume its container name `redis` resolves in this project's network. Attach both services to a shared Docker network or use the Redis endpoint/hostname Dokploy exposes to this application. Set Compose interpolation variables `SIGNIFY_COMPOSE_REDIS_HOST`, `SIGNIFY_COMPOSE_REDIS_PORT` (normally `6379`), and `SIGNIFY_COMPOSE_REDIS_PASSWORD` in Dokploy; if deploying the backend as a standalone Dockerfile Application, set `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` directly instead. Configure the password on the Redis service as well when authentication is enabled. Never use `localhost` for Redis in another container.

Redis holds expiring OTPs and presence leases. Those values are transient and presence is reconciled from the database, so the current local Redis overlay intentionally has no persistent volume. A Redis restart can clear presence leases and cause OTP verification to fall back to the current backend instance's in-memory store; it does not clear PostgreSQL data.

```powershell
docker compose --env-file .env.compose.local -f compose.yaml -f compose.redis.yaml up -d --build
# Or use only compose.yaml when both PostgreSQL and Redis are external.
```

The browser cannot resolve Docker service names. `VITE_API_BASE_URL` is embedded during frontend build from `SIGNIFY_COMPOSE_PUBLIC_API_URL`; the WebSocket URL is derived from that URL at `/ws/chat`. Changing it requires rebuilding the frontend image.

## Network and health

`frontend` and `backend` share the project-scoped `app` network. `backend`, `postgres`, and `redis` use the internal `data` network. Only the backend has both networks so it can contact external services. Local `depends_on: service_healthy` waits for PostgreSQL and Redis checks. Frontend health checks HTTP `/`; backend checks that its HTTP server responds. The backend has no application health endpoint, so its check shows HTTP liveness rather than database/Redis readiness.

The application uses WebSocket/STOMP at `/ws/chat` for chat and WebRTC signaling. With a public domain, use HTTPS for frontend and backend, allow WebSocket Upgrade and suitable idle timeouts at the reverse proxy, and set `SIGNIFY_COMPOSE_PUBLIC_API_URL` to the public HTTPS API URL. The browser then uses WSS. Media travels between peers; signaling success does not prove video connectivity. Test with two authenticated browsers, inspect the WebSocket `CONNECT`/`CONNECTED` frames, then verify offer, answer and ICE candidates and a selected candidate pair in the browser's WebRTC diagnostics. Configure real STUN/TURN infrastructure if direct peer connectivity fails; this Compose setup does not create TURN credentials or expose media ports.

## Dokploy

Dokploy supports Docker Compose and Docker Stack as distinct modes. This local Compose file refers to a sibling frontend Git checkout, which a deployment cloned from only the backend repository does not contain. Deploy the backend and frontend as separate Dokploy Dockerfile Applications from their respective Git repositories, with container ports 8080 and 3000. Provide backend values as runtime environment variables. Provide frontend `VITE_API_BASE_URL` and optional `VITE_WEBRTC_ICE_SERVERS` as build-time arguments, and `GEMINI_API_KEY` at runtime. Use Dokploy-managed PostgreSQL/Redis or external services and configure the backend's DB/Redis hostnames accordingly. If deploying a single Dokploy Compose application later, publish both images to a registry or make both build contexts available in one source; choose Compose mode for `build`, not Stack.

## Database migration status

Changeset 024 converts the existing `admin_audit_logs.metadata` TEXT column from changeset 017 to JSONB. It preserves valid JSON values, converts empty strings to NULL, and stops the migration on invalid JSON rather than discarding it. The previous Liquibase 5.0.3 checksum is accepted for databases where the original changeset was already recorded.

Changeset 030 adds the nullable `notifications.deduplication_key` column and unique constraint required by the current JPA entity.
