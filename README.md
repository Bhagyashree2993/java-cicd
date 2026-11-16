# Habit Tracker – CI/CD with Jenkins, Maven, SonarQube, Docker, Helm & Argo CD

A Spring Boot **Habit Tracker** (web UI + REST API on **port 8080**) delivered by a
production-style pipeline: **Jenkins does CI, Argo CD does CD (GitOps)**.

```
GitHub → Jenkins → Maven build/test → SonarQube + Quality Gate → Docker multi-stage build
      → Trivy scan → container smoke test → Docker Hub (versioned tag)
      → Jenkins updates helm/habit-tracker/values.yaml in Git → Argo CD → Kubernetes
```

## The application
- Create habits, mark them done each day, see current and longest streaks and today's completion rate
- `GET/POST /api/habits`, `POST|DELETE /api/habits/{id}/check-in`, `DELETE /api/habits/{id}`, `GET /api/stats`, `GET /api/info`
- `POST /api/admin/reset` protected by an admin token from a Kubernetes **Secret**
- Settings (environment, greeting, demo data, log level) from a Kubernetes **ConfigMap**
- Health probes `/actuator/health/liveness|readiness`, metrics `/actuator/prometheus`
- 22 unit/integration tests (JUnit 5, MockMvc), coverage with JaCoCo

## Repository layout
```
pom.xml                    Maven build (Spring Boot 3, Java 17, JaCoCo, Sonar)
src/                       application code, web UI (src/main/resources/static), tests
Dockerfile                 multi-stage: Maven build stage -> JRE Alpine runtime, non-root
.dockerignore
Jenkinsfile                CI pipeline (no kubectl - GitOps only)
helm/habit-tracker/        reusable chart: Deployment, Service, ConfigMap, Secret, HPA, PDB,
                           NetworkPolicy, ServiceAccount, optional Ingress, helm test
argocd/application.yaml    Argo CD Application (auto-sync, prune, self-heal)
monitoring/                metrics-server patch for Docker Desktop
docs/OPERATIONS.md         logging, monitoring, rollback, troubleshooting, recovery runbook
infra/                     docker-compose for local Jenkins (port 8090) + SonarQube (9000)
demo/                      PowerShell scripts: evidence collection and GitOps rollback demo
```

## Versioning
Image tag = `<pom version>-<Jenkins build number>-<git commit>`, e.g. `1.0.0-12-a1b2c3d`,
plus `latest`. The tag is also shown by the app at `/api/info` and in the UI header.

## Run locally
```bash
mvn clean verify && java -jar target/habit-tracker.jar          # http://localhost:8080
docker build -t habit-tracker:local . && docker run -p 8080:8080 habit-tracker:local
```

## Deploy (once)
```powershell
kubectl apply -f argocd/application.yaml
kubectl -n argocd get applications          # habit-tracker  Synced  Healthy
kubectl -n habit-tracker get svc habit-tracker   # LoadBalancer on port 8080 -> http://127.0.0.1:8080
```
After that every push to `main` is built by Jenkins and deployed by Argo CD automatically.

See **docs/OPERATIONS.md** for logs, monitoring, rollback and recovery.
