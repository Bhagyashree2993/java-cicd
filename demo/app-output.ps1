# Application output of the given Habit Tracker (HabitApp) running in Kubernetes on port 8080.
# Creates habits, marks completions, reads streaks/stats and shows error handling, and saves
# everything to evidence\app-output\app-output-<time>.txt.
#
# Run from the repo root while this is running in another window:
#   kubectl -n habit-tracker port-forward svc/habit-tracker 8080:8080
# then:
#   powershell -ExecutionPolicy Bypass -File demo\app-output.ps1
# (port-forward talks to one pod, so all calls see the same in-memory data)
param([string]$Base = 'http://127.0.0.1:8080')

$ErrorActionPreference = 'Stop'
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path evidence\app-output | Out-Null
Start-Transcript -Path (Join-Path (Get-Location) "evidence\app-output\app-output-$ts.txt") | Out-Null

function Show($title, $obj) {
  Write-Host "`n=== $title ===" -ForegroundColor Cyan
  Write-Host ($obj | ConvertTo-Json -Depth 5)
}

try {
  Write-Host "Habit Tracker application output - $Base - $(Get-Date)" -ForegroundColor Green

  $page = Invoke-WebRequest -UseBasicParsing "$Base/"
  $title = [regex]::Match($page.Content, '<title>(.*?)</title>').Groups[1].Value
  Write-Host "`n=== GET / (dashboard) ===" -ForegroundColor Cyan
  Write-Host ("HTTP {0}  title: {1}" -f $page.StatusCode, $title)

  $water = Invoke-RestMethod -Method Post -Uri "$Base/api/habits" -ContentType 'application/json' `
           -Body '{"name":"Drink water","description":"8 glasses a day","frequency":"DAILY"}'
  Show 'POST /api/habits  (create "Drink water")' $water

  $read = Invoke-RestMethod -Method Post -Uri "$Base/api/habits" -ContentType 'application/json' `
          -Body '{"name":"Read 10 pages","description":"before bed","frequency":"DAILY"}'
  Show 'POST /api/habits  (create "Read 10 pages")' $read

  $gym = Invoke-RestMethod -Method Post -Uri "$Base/api/habits" -ContentType 'application/json' `
         -Body '{"name":"Gym","description":"strength training","frequency":"WEEKLY"}'
  Show 'POST /api/habits  (create "Gym", WEEKLY)' $gym

  $d2 = (Get-Date).AddDays(-2).ToString('yyyy-MM-dd')
  $d1 = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd')
  Invoke-RestMethod -Method Post -Uri "$Base/api/habits/$($water.id)/complete?date=$d2" | Out-Null
  Invoke-RestMethod -Method Post -Uri "$Base/api/habits/$($water.id)/complete?date=$d1" | Out-Null
  $done = Invoke-RestMethod -Method Post -Uri "$Base/api/habits/$($water.id)/complete"
  Show "POST /api/habits/$($water.id)/complete  ($d2, $d1 and today -> 3-day streak)" $done

  Invoke-RestMethod -Method Post -Uri "$Base/api/habits/$($read.id)/complete" | Out-Null

  Show "GET /api/habits/$($water.id)/stats" (Invoke-RestMethod -Uri "$Base/api/habits/$($water.id)/stats")

  $upd = Invoke-RestMethod -Method Put -Uri "$Base/api/habits/$($gym.id)" -ContentType 'application/json' `
         -Body '{"name":"Gym 3x a week","description":"strength training","frequency":"WEEKLY"}'
  Show "PUT /api/habits/$($gym.id)  (update)" $upd

  Show 'GET /api/habits  (all habits)' (Invoke-RestMethod -Uri "$Base/api/habits")

  Write-Host "`n=== GET /api/habits/9999  (error handling) ===" -ForegroundColor Cyan
  try { Invoke-RestMethod -Uri "$Base/api/habits/9999" | Out-Null }
  catch { Write-Host ("HTTP {0}: {1}" -f [int]$_.Exception.Response.StatusCode, $_.ErrorDetails.Message) }

  Write-Host "`nOpen $Base in the browser to see these habits on the dashboard." -ForegroundColor Green
}
finally {
  Stop-Transcript | Out-Null
}
