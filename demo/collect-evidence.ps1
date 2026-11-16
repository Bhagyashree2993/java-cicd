# Captures monitoring/logging evidence (requirement 13) into evidence\13-monitoring-logging\monitoring-<time>.txt
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File demo\collect-evidence.ps1
$ts  = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path evidence\13-monitoring-logging | Out-Null
$out = Join-Path (Get-Location) "evidence\13-monitoring-logging\monitoring-$ts.txt"
$utf8 = New-Object System.Text.UTF8Encoding($false)

$cmds = @(
  'kubectl config current-context',
  'kubectl -n habit-tracker get pods -o wide',
  'kubectl -n habit-tracker get deploy,rs,svc,cm,secret,hpa,pdb',
  'kubectl top pods -n habit-tracker',
  'kubectl -n habit-tracker get hpa habit-tracker',
  'kubectl -n habit-tracker describe hpa habit-tracker',
  'kubectl -n habit-tracker logs deploy/habit-tracker --tail=50',
  'kubectl -n habit-tracker get events --sort-by=.lastTimestamp',
  'kubectl -n habit-tracker rollout status deploy/habit-tracker',
  'kubectl -n habit-tracker rollout history deploy/habit-tracker',
  'kubectl -n argocd get application habit-tracker',
  'curl.exe -s http://127.0.0.1:8080/actuator/health/readiness',
  'curl.exe -s http://127.0.0.1:8080/api/info',
  'curl.exe -s http://127.0.0.1:8080/api/stats',
  'curl.exe -s http://127.0.0.1:8080/api/habits'
)
foreach ($c in $cmds) {
  $text = (Invoke-Expression $c 2>&1 | Out-String)
  $block = "`r`n===== $c =====`r`n$text"
  [System.IO.File]::AppendAllText($out, $block, $utf8)
  Write-Host $block
}
Write-Host "`nSaved to $out" -ForegroundColor Green
