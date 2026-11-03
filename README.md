# Signify backend CI/CD

This repository is one Spring Boot 4.1.1 application, built with Gradle 9.7.1 and Java 17. It exposes port 8080 and needs PostgreSQL, Redis, SMTP, PayOS and object storage at runtime. The AI HTTP service is optional by configuration, but its default `localhost:8000` does **not** point to another container on the VPS. Liquibase runs database migrations when the application starts.

The Signify Premium payment flow, REST contract, renewal rules and preproduction checks are documented in [doc/payment-subscription.md](doc/payment-subscription.md). The backend requires both call participants to have an active paid subscription.

## Pipeline

`.github/workflows/workflow.yml` tests and builds a JAR for pushes to `main`, pull requests into `main`, and manual runs. Only successful `main` push/manual runs build and push `linux/amd64` images to Docker Hub. Images are tagged `latest` and with the full Git commit SHA. The deploy step sets Dokploy's Docker image to the SHA tag through its documented API, then requests a deployment. It checks that the commit is still the head of `main` before deployment. GitHub Actions reports that Dokploy accepted the request; inspect Dokploy for the final deployment status.

The Docker image repository name is `IMAGE_REPOSITORY` near the top of the workflow. Its default is `signify-be`.

## GitHub configuration

Create these under **Settings → Secrets and variables → Actions**:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `DOCKERHUB_TOKEN` | Docker Hub access token with permission to push `signify-be` |
| Secret | `DOKPLOY_URL` | HTTPS origin of Dokploy, such as `https://dokploy.example.com` (no path) |
| Secret | `DOKPLOY_API_KEY` | Dokploy API key with permission to update and deploy the application |
| Variable | `DOCKERHUB_USERNAME` | Docker Hub namespace/username owning the image repository |
| Variable | `DOCKERHUB_REGISTRY` | `docker.io` |
| Variable | `DOKPLOY_APPLICATION_ID` | ID of the Dokploy Application created below |

The Docker Hub repository `DOCKERHUB_USERNAME/signify-be` must exist. Pull requests never access the deploy credentials. GitHub Actions does not need runtime secrets such as database passwords.

## Dokploy setup

1. Create a **Project → Environment → Application**. In the application's **General** tab, choose **Docker** as source and set the image to `docker.io/<DOCKERHUB_USERNAME>/signify-be:latest` for the first manual deployment. The workflow replaces this with the commit SHA tag on subsequent deployments. Disable Docker Hub auto deploy/webhook for this application so that a `latest` push does not start a separate deployment.
2. For a private Docker Hub repository, configure registry access in Dokploy with the Docker Hub username and a **read-only** Docker Hub token. Dokploy's Docker source supports username/password/token fields; its Registry settings can also store credentials. This token is distinct from the GitHub push token if desired.
3. Provide the Neon PostgreSQL database and Redis reachable from the application container. `docker-compose.yml` defines Redis but no PostgreSQL service; set `DB_URL` to the actual Neon JDBC URL and `REDIS_HOST` to the reachable Redis service. Back up Neon before schema changes and never point a staging test at production data.
4. In Dokploy's **Environment** tab, set the runtime variables documented in `.env.example`. Required application values are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SMTP_MAIL_USERNAME`, `SMTP_MAIL_PASSWORD`, `JWT_SECRET`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` and `PAYOS_WEBHOOK_URL`. Configure reachable HTTPS PayOS URLs; register the public webhook endpoint `POST /api/payments/webhooks/payos` on the PayOS payment channel, since setting `PAYOS_WEBHOOK_URL` alone does not register it. For R2 storage, set `STORAGE_PROVIDER=r2`, `R2_BUCKET`, `R2_ENDPOINT`, `R2_ACCESS_KEY`, and `R2_SECRET_KEY`; the application uses those exact R2 names. Configure `CORS_ALLOWED_ORIGINS` for the frontend and set `SIGNIFY_AI_BASE_URL` to a reachable address if the AI service is used. Set a non-default `JWT_REFRESH_ENCRYPTION_KEY` if refresh tokens should survive restarts/key changes according to your auth policy. Use real secret values only in Dokploy, never in Git.
5. Set the application's container port to **8080**. Configure its domain, HTTPS and any needed WebSocket routing in Dokploy. Deploy once manually to verify network access and environment values. Copy the Application ID to the GitHub variable, generate the Dokploy API key, and set the GitHub secrets above.
6. Push to `main` or run the workflow manually **on `main`**. The workflow's deploy job uses the Dokploy `application.update` endpoint to set `dockerImage` and `application.deploy` to start deployment. A manual run from another branch performs CI only.

`docker-compose.yml` remains a local/Compose option and builds the application from source; the GitHub Actions pipeline deploys the Dokploy Docker Application described above. Configure PostgreSQL and Redis separately in Dokploy for this deployment mode.

## Verification and troubleshooting

- In GitHub **Actions**, check that `ci` passes tests and `publish_and_deploy` pushes both tags. For a pull request, the publish/deploy job should be skipped. On Docker Hub, confirm the SHA tag exists. In Dokploy, confirm the application's Docker image ends with that same SHA and the deployment record succeeds.
- If Gradle fails, inspect the `Test` or `Build executable JAR` step and the test report under `build/reports/tests/test`. Java 17 and the checked-in Gradle wrapper must be used. Tests use H2 and do not require production credentials by default.
- Before enabling a production deployment, test payment confirmation, duplicate webhooks, renewal, call authorization and Liquibase on a separate PostgreSQL staging database. The existing unit tests and H2 setup do not prove PayOS delivery or PostgreSQL locking behavior. A successful return URL redirect does not confirm payment.
- If Docker login/push fails, check Docker Hub username, repository name, token write permission and registry value. A Docker build failure is separate from the Gradle CI build and appears in the Buildx step.
- If Dokploy returns 401/403, check its API key and permissions. For 400/404, check the Application ID and that the source is Docker. If the deploy request succeeds but the container fails, inspect Dokploy deployment logs for registry pull authorization, PostgreSQL/Redis connectivity, Liquibase errors, and missing runtime variables. For a private image, check Dokploy's read token.
- `linux/amd64` assumes an x86-64 VPS. If the VPS is ARM64, change `platforms` to `linux/arm64` after verifying the base image and dependencies, then rebuild. A successful GitHub deploy step confirms API acceptance, not application health; confirm the service responds on its domain.

Dokploy references: [Docker image source](https://docs.dokploy.com/docs/core/Docker), [Application API](https://docs.dokploy.com/docs/api/application), [Docker Hub registry credentials](https://docs.dokploy.com/docs/core/registry/dockerhub).
