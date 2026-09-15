param(
    [string]$ElasticsearchUrl = "http://127.0.0.1:9200"
)

$ErrorActionPreference = "Stop"
$templatePath = Join-Path $PSScriptRoot "cms-law-index-template.json"

Invoke-RestMethod -Method Put `
    -Uri "$ElasticsearchUrl/_index_template/txgy-cms-law-v1" `
    -ContentType "application/json" `
    -InFile $templatePath | Out-Null

$indexName = "txgy-cms-law-v1-000001"
$indexExists = $true
try {
    Invoke-WebRequest -Method Head -Uri "$ElasticsearchUrl/$indexName" | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        $indexExists = $false
    } else {
        throw
    }
}

if (-not $indexExists) {
    $body = '{"aliases":{"txgy-cms-law-write":{"is_write_index":true}}}'
    Invoke-RestMethod -Method Put -Uri "$ElasticsearchUrl/$indexName" `
        -ContentType "application/json" -Body $body | Out-Null
}

Write-Output "Elasticsearch 法规索引已准备：$indexName"
