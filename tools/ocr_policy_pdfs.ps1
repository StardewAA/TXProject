param([Parameter(Mandatory=$true)][string]$Directory, [Parameter(Mandatory=$true)][string]$PdfToPpm)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder,Windows.Foundation,ContentType=WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine,Windows.Foundation,ContentType=WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
function Await($Operation, [Type]$ResultType) {
    $task = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $task.Wait()
    return $task.Result
}
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if (-not $engine) { throw 'Chinese OCR is unavailable' }
foreach ($name in @('oleObject1','oleObject2','oleObject4')) {
    $pdf = Join-Path $Directory ($name + '.pdf')
    $imageDir = Join-Path $Directory ($name + '-pages')
    $null = New-Item -ItemType Directory -Path $imageDir -Force
    & $PdfToPpm -scale-to 2400 -png $pdf (Join-Path $imageDir 'page')
    if ($LASTEXITCODE -ne 0) { throw "Rendering failed: $pdf" }
    $results = @()
    foreach ($png in Get-ChildItem -LiteralPath $imageDir -Filter '*.png' | Sort-Object Name) {
        $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($png.FullName)) ([Windows.Storage.StorageFile])
        $stream = Await ($file.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
        $bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
        $ocr = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
        $text = ($ocr.Lines | ForEach-Object { $_.Text }) -join "`n"
        $results += [pscustomobject]@{page=[int]($png.BaseName -replace 'page-',''); text=$text}
        $bitmap.Dispose()
        $stream.Dispose()
        Write-Output "$name $($png.BaseName): $($text.Length) characters"
    }
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Directory ($name + '.ocr.json')) -Encoding UTF8
}
