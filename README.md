# Habit Tracker (HabitApp) – CI/CD with Jenkins, Maven, SonarQube, Docker, Helm & Argo CD

The application is the **given Habit Tracker code** from
https://github.com/Vennilavanguvi/HabitApp (`src/`, `pom.xml`, `sonar-project.properties`,
copied unchanged). It is a Spring Boot 3.3 / **Java 21** REST API with a dashboard, served on **port 8080**,
storing habits in memory (no database).

This repository adds the delivery pipeline around it: **Jenkins does CI, Argo CD does CD (GitOps)**.

```
GitHub → Jenkins → Maven build/test (JDK 21) → SonarQube + Quality Gate → Docker multi-stage build
      → Trivy scan → container smoke test (calls the API on 8080) → Docker Hub (versioned tag)
      → Jenkins updates helm/habit-tracker/values.yaml in Git → Argo CD → Kubernetes
```

## The application (given code)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Dashboard: add habits, mark today done, stats, delete |
| GET | `/api/habits` | List all habits |
| GET | `/api/habits/{id}` | Get one habit |
| POST | `/api/habits` | Create `{"name","description","frequency":"DAILY"|"WEEKLY"}` |
| PUT | `/api/habits/{id}` | Update name/description/frequency |
| DELETE | `/api/habits/{id}` | Delete |
| PATCH | `/api/habits/{id}/active` | Activate/deactivate `{"active":false}` |
| POST | `/api/habits/{id}/complete` | Mark complete today (or `?date=YYYY-MM-DD`) |
| DELETE | `/api/habits/{id}/complete` | Unmark |
| GET | `/api/habits/{id}/stats` | Streaks + last-7-day completion rate |

**Added for CI only (no application code changed):** `src/test/.../HabitApiTest.java` – 8 API tests
(create, streaks, stats, update, deactivate, delete/404, validation, dashboard) next to the given
`contextLoads` test.

## What the pipeline adds
| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage: `maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre-alpine` runtime, non-root UID 1001, HEALTHCHECK on `/api/habits` |
| `.dockerignore` | Keeps the build context to `pom.xml` + `src` |
| `Jenkinsfile` | Checkout → Build & Unit Test → SonarQube → Quality Gate → Docker Build → Trivy → Container Smoke Test → Push (with retries) → Update Helm values. **No kubectl.** |
| `helm/habit-tracker/` | Reusable chart: Deployment (rolling update, startup/readiness/liveness probes, requests/limits, non-root), Service (port 8080), ConfigMap, Secret, HPA, PDB, NetworkPolicy, ServiceAccount, optional Ingress, `helm test` |
| `argocd/application.yaml` | Argo CD Application: auto-sync, prune, self-heal |
| `docs/OPERATIONS.md` | Logging, monitoring, rollback, troubleshooting, recovery runbook |
| `demo/` | `app-output.ps1` (application output), `collect-evidence.ps1`, `rollback-demo.ps1` |
| `infra/` | docker-compose for local Jenkins (port 8090) + SonarQube (9000) |

Probes use the app's own endpoints because the given code has no Spring Actuator:
startup/readiness → `GET /api/habits`, liveness → `GET /`.

## Versioning
Image tag = `<pom version>-<Jenkins build>-<git commit>`, e.g. `0.0.1-SNAPSHOT-7-a1b2c3d`, plus `latest`.

## Run locally
```bash
mvn clean package && java -jar target/habit-tracker.jar           # http://localhost:8080
docker build -t habit-tracker:local . && docker run -p 8080:8080 habit-tracker:local
```

## Deploy (once)
```powershell
kubectl apply -f argocd/application.yaml
kubectl -n argocd get applications                                  # habit-tracker  Synced  Healthy
kubectl -n habit-tracker port-forward svc/habit-tracker 8080:8080   # http://127.0.0.1:8080
```
Every push to `main` is then built by Jenkins and deployed by Argo CD automatically.
