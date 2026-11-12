# Java Application CI/CD — Jenkins · Maven · SonarQube · Docker · Helm · Argo CD

A production-oriented CI/CD pipeline for a Spring Boot application.
**Jenkins does CI** (build, test, quality gate, image), **Argo CD does CD** (GitOps sync to Kubernetes).

```
GitHub ──► Jenkins ──► Maven build ──► Unit tests + JaCoCo ──► SonarQube + Quality Gate
                                                                      │
   Kubernetes ◄── Argo CD ◄── Git (helm/values.yaml) ◄── Helm values ◄── Registry ◄── Docker build/scan
```

| Step | Tool | What happens |
|---|---|---|
| 1 | GitHub | Developer pushes code; webhook triggers Jenkins |
| 2 | Maven | `mvn clean verify` compiles, runs JUnit tests, creates `target/java-app.jar` and a JaCoCo coverage report |
| 3 | SonarQube | Static analysis + coverage; pipeline **stops** if the Quality Gate fails |
| 4 | Docker | Builds a non-root JRE image tagged `<build#>-<git-sha>`; Trivy scans it |
| 5 | Registry | Pushes image to Docker Hub (or ECR/GCR/Harbor) |
| 6 | Helm + Git | Jenkins writes the new tag into `helm/java-app/values.yaml` and pushes a `[skip ci]` commit |
| 7 | Argo CD | Detects the commit, renders the Helm chart, rolls out to Kubernetes (zero-downtime rolling update) |

---

## Repository layout

```
java-cicd/
├── pom.xml                         # Spring Boot 3, JaCoCo, Sonar plugin
├── src/main/java/...               # REST app: /, /api/greet, /api/add, /actuator/health
├── src/test/java/...               # JUnit 5 + MockMvc tests
├── Dockerfile                      # runtime image (uses JAR built by Jenkins)
├── Dockerfile.multistage           # self-contained build for local use
├── Jenkinsfile                     # the CI pipeline
├── helm/java-app/                  # Helm chart (Deployment, Service, ConfigMap, Secret, Ingress, HPA, PDB, SA)
├── argocd/application.yaml         # Argo CD Application (auto-sync, prune, self-heal)
├── argocd/repo-secret.yaml         # only for private repos
└── infra/                          # docker-compose for local Jenkins + SonarQube
```

---

## Prerequisites

- A GitHub account + Personal Access Token (classic, `repo` scope)
- A Docker Hub account + access token (or any container registry)
- A Kubernetes cluster (Minikube, Kind, EKS, GKE, AKS…) with `kubectl` access
- Docker on the machine that runs Jenkins

---

## Step 1 — Put the code on GitHub

1. Create an empty repo, e.g. `java-cicd`.
2. Replace the placeholders (search for `your-dockerhub-username` and `your-github-username`):
   - `Jenkinsfile` → `IMAGE_REPO`, `GIT_REPO_URL`
   - `helm/java-app/values.yaml` → `image.repository`
   - `argocd/application.yaml` → `repoURL`
3. Push:
   ```bash
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/java-cicd.git
   git push -u origin main
   ```

## Step 2 — Start Jenkins and SonarQube

Use your existing servers, or run them locally:

```bash
sudo sysctl -w vm.max_map_count=524288        # required by SonarQube (Linux host)
cd infra && docker compose up -d --build
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```
- Jenkins: http://localhost:8080 → unlock, "Install suggested plugins", create admin user
- SonarQube: http://localhost:9000 → login `admin/admin`, set a new password

> On a VM / EC2 instead: install Jenkins (JDK 17), Docker (add `jenkins` user to the `docker` group), and run SonarQube with `docker run -d -p 9000:9000 sonarqube:lts-community`.

## Step 3 — Configure SonarQube

1. **My Account → Security → Generate Token** (type: *Global Analysis*). Copy it.
2. **Administration → Configuration → Webhooks → Create**
   - Name: `jenkins`
   - URL: `http://<jenkins-host>:8080/sonarqube-webhook/` (with docker compose: `http://jenkins:8080/sonarqube-webhook/`)
   - Without this webhook the *Quality Gate* stage waits until it times out.

## Step 4 — Configure Jenkins

**Plugins** (Manage Jenkins → Plugins; pre-installed if you used `infra/`):
Pipeline, Git, GitHub, Docker Pipeline, SonarQube Scanner, JUnit, Credentials Binding, Timestamper, Workspace Cleanup.

**Tools** (Manage Jenkins → Tools):
- JDK → name **`jdk17`** → install automatically (Adoptium 17)
- Maven → name **`maven3`** → install automatically (3.9.x)

**Credentials** (Manage Jenkins → Credentials → System → Global → Add):

| ID | Kind | Value |
|---|---|---|
| `dockerhub-creds` | Username with password | Docker Hub user + access token |
| `github-creds` | Username with password | GitHub user + PAT |
| `sonar-token` | Secret text | SonarQube token from Step 3 |

**SonarQube server** (Manage Jenkins → System → SonarQube servers):
- Name **`sonarqube`**, URL `http://<sonar-host>:9000` (docker compose: `http://sonarqube:9000`), token `sonar-token`.

## Step 5 — Create the pipeline job

1. **New Item → Multibranch Pipeline** (or *Pipeline* with "Pipeline script from SCM").
2. Branch source: GitHub / Git → your repo URL, credentials `github-creds`.
3. Script path: `Jenkinsfile`.
4. GitHub webhook: repo **Settings → Webhooks → Add** → `http://<jenkins-host>:8080/github-webhook/`, content type `application/json`, "Just the push event".
   (Jenkins must be reachable from the internet — use a public IP or a tunnel like ngrok for local setups.)

## Step 6 — Install Argo CD on Kubernetes

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server

# UI access
kubectl -n argocd port-forward svc/argocd-server 8443:443        # → https://localhost:8443
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo               # user: admin
```

## Step 7 — Register the application with Argo CD

```bash
# private repo only:
kubectl apply -f argocd/repo-secret.yaml      # after filling in your PAT (don't commit it)

kubectl apply -f argocd/application.yaml
```
Argo CD now watches `helm/java-app` on `main` and deploys to namespace `java-app`.

If your registry repo is private, also create a pull secret and uncomment `imagePullSecrets` in `values.yaml`:
```bash
kubectl create ns java-app
kubectl -n java-app create secret docker-registry regcred \
  --docker-server=docker.io --docker-username=<user> --docker-password=<token>
```

## Step 8 — Run it end-to-end

1. Change something (e.g. the message in `AppController.java`) and push to `main`.
2. Watch Jenkins: Build & Test → SonarQube → Quality Gate → Docker → Push → Update Helm values.
3. Jenkins commits `ci: deploy java-app <tag> [skip ci]`. That commit triggers a build that is immediately marked **NOT_BUILT** — this is intended and prevents an infinite loop.
4. Argo CD shows *OutOfSync → Syncing → Healthy* (it polls every ~3 min; click **Refresh** to speed it up).
5. Verify:
   ```bash
   kubectl -n java-app get pods,svc,hpa
   kubectl -n java-app port-forward svc/java-app 8081:80
   curl http://localhost:8081/                     # shows the new version tag
   curl "http://localhost:8081/api/greet?name=Argo"
   ```

**Rollback:** revert the `values.yaml` commit in Git (`git revert <sha>`) — Argo CD rolls back automatically. Or use *History and Rollback* in the Argo CD UI (temporarily disable auto-sync first).

---

## Configuration & secrets

| What | Where | Notes |
|---|---|---|
| Non-sensitive app config | `config:` in `values.yaml` -> **ConfigMap** -> env vars (`envFrom`) | Changing a value rolls the pods automatically (checksum annotation) |
| Sensitive app config | `secret:` in `values.yaml` -> **Secret** -> env vars (`envFrom`) | `secret.data` holds a **placeholder only** |
| Real credentials | Create the Secret outside Git, set `secret.existingSecret` | Never commit real values |
| Jenkins credentials | Jenkins Credentials store (`dockerhub-creds`, `github-creds`, `sonar-token`) | Injected with `withCredentials`; nothing hardcoded in the `Jenkinsfile` |
| Registry pull | `imagePullSecrets` (only for a private registry) | Created with `kubectl`, not stored in Git |

Using a real secret without committing it:

```bash
kubectl -n java-app create secret generic java-app-secrets --from-literal=APP_API_KEY='<value>'
# then in helm/java-app/values.yaml:  secret.existingSecret: java-app-secrets   (commit + push)
```

Security controls already in the chart: non-root user (UID 1001), `readOnlyRootFilesystem`, all capabilities dropped,
`allowPrivilegeEscalation: false`, service-account token not mounted, dedicated ServiceAccount, Trivy scan in Jenkins.
Jenkins never runs `kubectl` - it only commits to Git; Argo CD deploys.

## Application URL (exposure)

The chart supports both options required by the task. Pick one in `values.yaml`, commit, and push:

```yaml
# Option A - LoadBalancer (Docker Desktop / cloud)
service:
  type: LoadBalancer
  port: 80
# -> http://localhost/  (Docker Desktop)  or  http://<EXTERNAL-IP>/  (cloud)

# Option B - Ingress (requires an ingress controller, e.g. ingress-nginx)
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: java-app.local        # add "127.0.0.1 java-app.local" to your hosts file
      paths:
        - path: /
          pathType: Prefix
```

Quick check without changing anything: `kubectl -n java-app port-forward svc/java-app 8081:80` -> http://localhost:8081/

## Zero-downtime deployment & rollback (demo)

The Deployment uses `RollingUpdate` with `maxSurge: 1`, `maxUnavailable: 0`, plus readiness/liveness/startup probes, so a new
pod must be Ready before an old pod is removed.

> **Important:** every commit you make by hand that triggers Jenkins would build a *new* image and overwrite your rollback.
> Put `[skip ci]` in the commit message of any manual GitOps commit (the pipeline's Checkout stage skips those).

**A. Deploy a new version (normal path)**
1. Change code, push to `main`. Jenkins builds, tests, scans, pushes `<build#>-<sha>` and commits the new tag.
2. Watch the rollout:
   ```bash
   kubectl -n java-app rollout status deploy/java-app
   kubectl -n java-app get pods -w
   kubectl -n argocd get application java-app
   curl http://localhost:8081/        # shows the new version (APP_VERSION)
   ```
3. Keep `while true; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/actuator/health; sleep 1; done`
   running in another terminal to show there is no downtime (all `200`).

**B. Simulate an unsuccessful deployment**
1. In `helm/java-app/values.yaml` set `image.tag: "does-not-exist"`, then
   `git commit -am "demo: bad image [skip ci]" && git push`.
2. Argo CD syncs; the new pod goes to `ImagePullBackOff`/`ErrImagePull` but the **old pods keep serving traffic**
   (`maxUnavailable: 0`). Evidence: `kubectl -n java-app get pods` and `kubectl -n java-app describe pod <new-pod>`.

**C. Roll back to the previous working version (GitOps way - recommended)**
```bash
git revert --no-edit HEAD            # restores the previous image tag
git commit --amend -m "rollback: restore previous image [skip ci]"
git push
kubectl -n java-app rollout status deploy/java-app
kubectl -n java-app rollout history deploy/java-app
```
Alternative: Argo CD UI -> app -> **History and Rollback** (turn off auto-sync first, otherwise `selfHeal` re-applies Git).
`kubectl rollout undo` also works, but with `selfHeal: true` Argo CD reverts it to match Git, so use Git or the Argo UI.

## Monitoring & logging

Uses Kubernetes built-ins + metrics-server (required by the HPA) + Argo CD health. Prometheus metrics are also exposed at `/actuator/prometheus`.

| What to monitor | Command |
|---|---|
| Pod status | `kubectl -n java-app get pods -o wide` |
| Pod restarts | `kubectl -n java-app get pods` (RESTARTS column) |
| CPU / memory | `kubectl top pods -n java-app` and `kubectl -n java-app get hpa java-app` |
| Application logs | `kubectl -n java-app logs deploy/java-app --tail=100` (add `-f` to follow, `--previous` after a crash) |
| Why a pod is unhealthy | `kubectl -n java-app describe pod <pod>` and `kubectl -n java-app get events --sort-by=.lastTimestamp` |
| Rollout state | `kubectl -n java-app rollout status deploy/java-app` |
| Argo CD health / sync | `kubectl -n argocd get application java-app` (or the Argo CD UI) |

Troubleshooting examples to include as evidence:
- **Degraded HPA** - `kubectl -n java-app describe hpa java-app` showing `FailedGetResourceMetric` until metrics-server was installed; fixed, `TARGETS` now shows `cpu: 2%/70%`.
- **ImagePullBackOff** - from the bad-tag demo above; found with `describe pod` / events.

## Submission checklist

| Requirement | Evidence to attach |
|---|---|
| 1-2 Java app, Maven | Jenkins console: `mvn clean verify` + test results; app URL |
| 3 SonarQube | Project dashboard + Quality Gate "Passed" screenshot |
| 4 Docker | `Dockerfile`, `.dockerignore`, `docker run` / `docker images` output |
| 5 Registry | Docker Hub tags page showing `<build#>-<sha>` |
| 6-7 Helm / Kubernetes | `helm/java-app/`, `kubectl -n java-app get deploy,po,svc,cm,secret,hpa,pdb` (do not show secret values) |
| 8 Argo CD | UI or `kubectl -n argocd get application java-app` -> `Synced` / `Healthy`; a Git change being auto-synced |
| 9-10 Jenkins -> Argo CD | `Jenkinsfile`, pipeline stage view, the `ci: deploy java-app <tag> [skip ci]` commit |
| 11 Security | Jenkins credentials page (IDs only), `securityContext` in values, no secrets in Git |
| 12 Rollback | Sections A-C above: rollout, bad deploy, rollback outputs |
| 13 Monitoring | Output of the commands in the monitoring table |

---

## Run locally (no Jenkins)

```bash
mvn clean verify                     # build + tests + coverage report (target/site/jacoco)
java -jar target/java-app.jar        # http://localhost:8080
docker build -f Dockerfile.multistage -t java-app:local . && docker run -p 8080:8080 java-app:local
helm lint helm/java-app && helm template java-app helm/java-app
```

---

## Production notes

- **Separate config repo:** many teams keep Helm values in a separate `java-cicd-gitops` repo so app commits and deploy commits don't mix. To switch, clone that repo in the "Update Helm values" stage and point `argocd/application.yaml` at it.
- **Environments:** add `values-dev.yaml` / `values-prod.yaml` and one Argo CD Application per environment; promote by copying the tag between files via pull request.
- **Security:** non-root container, read-only root filesystem, all Linux capabilities dropped, no SA token mounted, Trivy image scan (set `--exit-code 1` to make it blocking).
- **Reliability:** startup/liveness/readiness probes on Spring Boot Actuator, rolling update with `maxUnavailable: 0`, HPA (2–5 replicas, needs metrics-server), PodDisruptionBudget, graceful shutdown.
- **Observability:** Prometheus metrics at `/actuator/prometheus` with scrape annotations.
- **Alternative:** Argo CD Image Updater can replace the Jenkins "Update Helm values" stage if you prefer the cluster to pick up new tags itself.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Quality Gate stage hangs | SonarQube webhook to `/sonarqube-webhook/` missing or unreachable |
| `docker: permission denied` in Jenkins | Add `jenkins` user to `docker` group and restart Jenkins |
| `git push` 403 from Jenkins | PAT lacks `repo` scope, or wrong `github-creds` |
| Pods in `ImagePullBackOff` | Wrong `image.repository`, or private registry without `imagePullSecrets` |
| Pods `CrashLoopBackOff` with read-only FS errors | Make sure the `/tmp` emptyDir volume is present (it is by default) |
| SonarQube container exits | Run `sysctl -w vm.max_map_count=524288` on the host |
| Pipeline loops forever | Keep `[skip ci]` in the CI commit message (checked in the Checkout stage) |
