# Restore the original local containers without deleting test records or source files.
$ErrorActionPreference='Stop'
docker stop wms-acceptance-wms wms-acceptance-bpm wms-acceptance-frontend
if($LASTEXITCODE -ne 0){throw 'Acceptance containers could not all be stopped; inspect before restoring'}
docker start deploy-wms-1 deploy-bpm-1 deploy-frontend-1
if($LASTEXITCODE -ne 0){throw 'Original local containers failed to start'}
