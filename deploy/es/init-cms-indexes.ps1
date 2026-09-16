$ErrorActionPreference = 'Stop'
$esUrl = 'http://localhost:9200'
$indexes = @{
  'cms-standard-index' = 'cms-standard-index.json'
  'cms-material-index' = 'cms-material-index.json'
  'cms-process-index'  = 'cms-process-index.json'
  'cms-quota-index'    = 'cms-quota-index.json'
  'cms-policy-index'   = 'cms-policy-index.json'
}

$dir = $PSScriptRoot
foreach ($entry in $indexes.GetEnumerator()) {
  $body = Get-Content -LiteralPath (Join-Path $dir $entry.Value) -Raw -Encoding UTF8
  try {
    Invoke-RestMethod -Method Put -Uri "$esUrl/$($entry.Key)" -ContentType 'application/json' -Body $body | Out-Null
    Write-Host "created: $($entry.Key)"
  } catch {
    Write-Host "skip/failed: $($entry.Key) - $($_.Exception.Message)"
  }
}
