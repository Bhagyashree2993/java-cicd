# Habit Tracker – Operations Runbook

How the running application is secured, monitored, logged, rolled back and recovered.
All commands are for PowerShell on the Docker Desktop Kubernetes cluster.
Namespace: `habit-tracker`. Argo CD application: `habit-tracker`.

---

## 1. Access the application (port 8080)

The Service is type **LoadBalancer** on port **8080**; Docker Desktop publishes it on the laptop:
```powershell
kubectl -n habit-tracker get svc habit-tracker      # EXTERNAL-IP localhost, PORT 8080
```
Open **http://127.0.0.1:8080** (web UI). Jenkins runs on http://127.0.0.1:8090.
Fallback if the LoadBalancer is not published: `kubectl -n habit-tracker port-forward svc/habit-tracker 8081:8080`
and use http://127.0.0.1:8081.

| URL | Shows |
|---|---|
| `/` | Habit Tracker web UI (habits, streaks, version, pod name) |
| `/api/habits` | All habits as JSON |
| `/api/stats` | Totals and today's completion rate |
| `/api/info` | App version (= image tag), environment, greeting (ConfigMap) and pod name |
| `/actuator/health/liveness`, `/actuator/health/readiness` | Probe endpoints |
| `/actuator/prometheus` | Prometheus metrics |

---

## 2. Health and deployment status

```powershell
kubectl -n argocd get applications                 # Synced / Healthy
kubectl -n habit-tracker get deploy,rs,pods,svc,hpa,pdb,cm,secret,networkpolicy
kubectl -n habit-tracker rollout status deploy/habit-tracker
kubectl -n habit-tracker describe deploy habit-tracker   # strategy, probes, limits, image
```

---

## 3. Logging

```powershell
kubectl -n habit-tracker logs deploy/habit-tracker --tail=50                 # one pod
kubectl -n habit-tracker logs -l app.kubernetes.io/name=habit-tracker --prefix --tail=20   # all pods
kubectl -n habit-tracker logs -f deploy/habit-tracker                        # follow live
kubectl -n habit-tracker logs <pod> --previous                               # logs of a crashed container
```
The app logs every action with timestamp, level, thread and logger, for example
`Created habit id=4 name='Meditate'` and `Check-in habit id=4 newCompletion=true streak=1`.
Log level is set from the ConfigMap (`LOG_LEVEL`).

---

## 4. Monitoring

**Metrics endpoint:** `/actuator/prometheus` (JVM memory, GC, HTTP request counts and latency).
Pods carry `prometheus.io/scrape` annotations so a Prometheus server can scrape them.

**Resource usage and autoscaling (metrics-server):**
```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --patch-file monitoring/metrics-server-patch.yaml
kubectl -n kube-system rollout status deploy/metrics-server
# wait ~1 minute, then:
kubectl top nodes
kubectl -n habit-tracker top pods
kubectl -n habit-tracker get hpa          # TARGETS now shows real CPU %, e.g. cpu: 2%/70%
```

---

## 5. Rollback

**Option A – GitOps rollback (recommended).** Git is the source of truth, so revert the deploy commit:
```powershell
git pull
git log --oneline -5 -- helm/habit-tracker/values.yaml      # find "ci: deploy habit-tracker <tag>"
git revert --no-edit <commit-sha>
git push
```
Argo CD syncs within ~3 minutes (or click **Refresh**) and the previous image tag is deployed.
Check with `kubectl -n habit-tracker get deploy habit-tracker -o jsonpath="{.spec.template.spec.containers[0].image}"`.

**Option B – Argo CD UI.** Application → **History and Rollback** → pick an earlier revision → **Rollback**.
(Argo CD temporarily turns off auto-sync for a manual rollback; turn it back on afterwards.)

**Option C – Kubernetes rollout history** (emergency only; Argo CD self-heal will put Git's version back):
```powershell
kubectl -n habit-tracker rollout history deploy/habit-tracker
kubectl -n habit-tracker rollout undo deploy/habit-tracker
```

---

## 6. Troubleshooting

| Symptom | Check | Typical fix |
|---|---|---|
| Pod `ImagePullBackOff` | `kubectl -n habit-tracker describe pod <pod>` (Events) | Wrong image tag/repo in values.yaml, or private registry without `imagePullSecrets` |
| Pod `CrashLoopBackOff` | `kubectl -n habit-tracker logs <pod> --previous` | Bad config in ConfigMap/Secret; fix in Git |
| Pod `Running` but `0/1 READY` | `kubectl describe pod` → readiness probe failures | App still starting, or failing health check |
| Argo CD `OutOfSync` / `Degraded` | Argo CD UI → app → events / diff | Fix the chart or values in Git; Argo CD retries automatically |
| `Pending` pods | `kubectl describe pod` → "Insufficient memory" | Reduce replicas/requests or free cluster memory |

---

## 7. Recovery / self-healing demonstrations

```powershell
# 1) Kubernetes replaces a killed pod (Deployment keeps the replica count)
kubectl -n habit-tracker get pods
kubectl -n habit-tracker delete pod <one-pod-name>
kubectl -n habit-tracker get pods -w          # a new pod appears and becomes READY 1/1

# 2) Argo CD self-heal undoes manual drift
kubectl -n habit-tracker scale deploy habit-tracker --replicas=1
kubectl -n argocd get applications            # briefly OutOfSync, then Synced again
kubectl -n habit-tracker get deploy habit-tracker   # back to the replica count from Git / HPA

# 3) Zero-downtime rolling update (maxSurge 1, maxUnavailable 0)
kubectl -n habit-tracker rollout status deploy/habit-tracker
kubectl -n habit-tracker get rs               # old ReplicaSet scaled to 0, new one running
```

---

## 8. Security controls

| Area | Control |
|---|---|
| Code quality | SonarQube analysis + Quality Gate blocks the pipeline on failure |
| Image vulnerabilities | Trivy scan of every image (HIGH/CRITICAL report) |
| Image | Multi-stage build, minimal JRE Alpine runtime, `.dockerignore`, non-root UID 1001 |
| Pod | `runAsNonRoot`, read-only root filesystem, all Linux capabilities dropped, no privilege escalation, seccomp `RuntimeDefault`, no service-account token mounted |
| Network | NetworkPolicy allows inbound traffic only on port 8080 (enforced when the cluster's network plugin supports NetworkPolicy) |
| Secrets | CI credentials stored in Jenkins Credentials; app admin token in a Kubernetes Secret (`X-Admin-Token` for `/api/admin/reset`). In production use Sealed Secrets / External Secrets instead of a value in Git |
| Deployment | Jenkins never runs `kubectl`; only Argo CD (pull-based GitOps) changes the cluster |
