# Deployment & rollback demo (requirement 12), fully GitOps - no kubectl apply/rollout undo.
#   A) deploy a new version  (config change -> rolling update)
#   B) deploy a bad version  (nonexistent image tag -> new pod fails, old pods keep serving)
#   C) roll back via git revert
# A health-check loop runs the whole time to prove there is no downtime.
# Every commit carries [skip ci] so Jenkins does not rebuild. Output is saved to evidence\12-deployment-rollback\.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File demo\rollback-demo.ps1
param([string]$Url = 'http://127.0.0.1:8080/actuator/health/readiness')

$ErrorActionPreference = 'Stop'
$values = 'helm/habit-tracker/values.yaml'
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path evidence\12-deployment-rollback | Out-Null
$probeLog = Join-Path (Get-Location) "evidence\12-deployment-rollback\health-probe-$ts.csv"
Start-Transcript -Path (Join-Path (Get-Location) "evidence\12-deployment-rollback\rollback-demo-$ts.txt") | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Step($m) { Write-Host "`n==================== $m ====================" -ForegroundColor Cyan }
function Run($cmd) { Write-Host "`n> $cmd" -ForegroundColor DarkGray; Invoke-Expression $cmd 2>&1 | Out-Host }
function Edit-Values($pattern, $replacement) {
  $s = [System.IO.File]::ReadAllText($values)
  $re = New-Object System.Text.RegularExpressions.Regex($pattern)
  if (-not $re.IsMatch($s)) { throw "Pattern not found in values.yaml: $pattern" }
  [System.IO.File]::WriteAllText($values, $re.Replace($s, $replacement, 1), $utf8)
}
function Push-Change($msg) {
  git add $values
  git commit -m "$msg [skip ci]"
  if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
  git pull --rebase origin main
  if ($LASTEXITCODE -ne 0) { throw 'git pull --rebase failed - resolve it, then re-run' }
  git push origin HEAD:main
  if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
  Run 'kubectl -n argocd annotate application habit-tracker argocd.argoproj.io/refresh=hard --overwrite'
}

try {
  Step 'Pre-checks'
  if (-not (Test-Path $values)) { throw 'Run this from the repo root (the folder containing helm\ and Jenkinsfile).' }
  if ((kubectl config current-context) -ne 'docker-desktop') { throw 'kubectl context is not docker-desktop.' }
  if (git status --porcelain --untracked-files=no) { throw 'You have uncommitted changes to tracked files. Commit or stash them first.' }
  git pull --rebase origin main
  Run 'kubectl -n habit-tracker get pods'
  Run 'kubectl -n argocd get application habit-tracker'
  $sync = kubectl -n argocd get application habit-tracker -o "jsonpath={.status.sync.status}"
  if ($sync -ne 'Synced') { throw "Argo CD sync status is '$sync', expected Synced. Fix Argo CD first (kubectl -n argocd describe application habit-tracker)." }
  Write-Host "Checking application URL: $Url"
  $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 10 $Url
  if ($code -ne '200') { throw "Application URL $Url returned '$code' (expected 200). Fix access first (kubectl -n habit-tracker get svc habit-tracker) or pass -Url." }
  $good = ([regex]::Match([System.IO.File]::ReadAllText($values), '(?m)^\s*tag:\s*"([^"]*)"')).Groups[1].Value
  Write-Host "Current good image tag: $good" -ForegroundColor Green

  Step 'Start health-check loop (1 request/second)'
  $job = Start-Job -ArgumentList $Url, $probeLog -ScriptBlock {
    param($u, $log)
    while ($true) {
      $c = curl.exe -s -o NUL -w "%{http_code}" --max-time 3 $u
      if (-not $c) { $c = 'ERR' }
      Add-Content -Path $log -Value ("{0},{1}" -f (Get-Date -Format o), $c)
      Start-Sleep -Seconds 1
    }
  }
  Start-Sleep -Seconds 5

  Step 'A) Deploy a NEW version (LOG_LEVEL INFO -> DEBUG triggers a rolling update)'
  $curLevel = ([regex]::Match([System.IO.File]::ReadAllText($values), 'LOG_LEVEL:\s*(\w+)')).Groups[1].Value
  $newLevel = if ($curLevel -eq 'INFO') { 'DEBUG' } else { 'INFO' }
  Edit-Values 'LOG_LEVEL:\s*\w+' "LOG_LEVEL: $newLevel"
  Push-Change "demo: new version - set LOG_LEVEL=$newLevel"
  Start-Sleep -Seconds 20
  Run 'kubectl -n habit-tracker rollout status deploy/habit-tracker --timeout=240s'
  Run 'kubectl -n habit-tracker get pods'
  Run 'kubectl -n habit-tracker exec deploy/habit-tracker -- printenv LOG_LEVEL'
  Run 'kubectl -n argocd get application habit-tracker'

  Step 'B) Deploy a BAD version (image tag that does not exist)'
  Edit-Values '(?m)^(\s*tag:\s*)"[^"]*"' '$1"does-not-exist"'
  Push-Change 'demo: bad version - nonexistent image tag'
  Write-Host 'Waiting up to 150s for Argo CD to apply the bad tag and the new pod to fail...'
  $seen = $false
  for ($i = 0; $i -lt 30 -and -not $seen; $i++) {
    Start-Sleep -Seconds 5
    $reasons = kubectl -n habit-tracker get pods -o "jsonpath={.items[*].status.containerStatuses[*].state.waiting.reason}"
    if ($reasons -match 'ImagePullBackOff|ErrImagePull') { $seen = $true }
  }
  if (-not $seen) { Write-Host 'WARNING: no failing pod appeared, so Argo CD did NOT apply the bad tag. This run is not valid evidence for the bad-deploy step. Check: kubectl -n argocd describe application habit-tracker' -ForegroundColor Yellow }
  Run 'kubectl -n habit-tracker get pods'
  Run 'kubectl -n habit-tracker rollout status deploy/habit-tracker --timeout=10s'
  Run 'kubectl -n habit-tracker get events --sort-by=.lastTimestamp'
  Run 'kubectl -n argocd get application habit-tracker'

  Step 'C) ROLL BACK to the previous working version (git revert)'
  git revert --no-edit HEAD
  if ($LASTEXITCODE -ne 0) { throw 'git revert failed' }
  git commit --amend -m 'rollback: restore previous working image [skip ci]'
  git pull --rebase origin main
  if ($LASTEXITCODE -ne 0) { throw 'git pull --rebase failed' }
  git push origin HEAD:main
  if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
  Run 'kubectl -n argocd annotate application habit-tracker argocd.argoproj.io/refresh=hard --overwrite'
  Start-Sleep -Seconds 20
  Run 'kubectl -n habit-tracker rollout status deploy/habit-tracker --timeout=240s'
  Run 'kubectl -n habit-tracker get pods'
  Run 'kubectl -n habit-tracker rollout history deploy/habit-tracker'
  Run 'kubectl -n argocd get application habit-tracker'
  Run 'git log --oneline -5'
  Write-Host "`nImage tag should be back to: $good" -ForegroundColor Green
  Run 'kubectl -n habit-tracker get deploy habit-tracker -o jsonpath="{.spec.template.spec.containers[0].image}"'
}
finally {
  if ($job) { Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue }
  if (Test-Path $probeLog) {
    $rows = Get-Content $probeLog
    $bad = $rows | Where-Object { $_ -notmatch ',200$' }
    Step 'Availability summary'
    Write-Host ("Health checks: {0} total, {1} not HTTP 200" -f $rows.Count, @($bad).Count)
    if (@($bad).Count -gt 0) { $bad | Select-Object -First 10 | Out-Host }
    Write-Host "Probe log: $probeLog"
  }
  Stop-Transcript | Out-Null
}
