param([string]$Container = 'wms-shortage-rr-test-20260914')
$ErrorActionPreference = 'Stop'
$identity = & docker inspect --format '{{.Name}}|{{.HostConfig.NetworkMode}}' $Container
if ($LASTEXITCODE -ne 0 -or $identity -ne '/wms-shortage-rr-test-20260914|none') {
    throw 'Only the dedicated network-disabled shortage test container is allowed.'
}
function Invoke-TestSql([string]$Sql) {
    $result = & docker exec $Container mysql -uroot -N -e $Sql
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL test SQL failed.' }
    return $result
}
Invoke-TestSql 'CREATE DATABASE shortage_test;'
$schema = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../../deploy/mysql/repair/2026-09-14-wms-shortage.sql')
Invoke-TestSql ("USE shortage_test; " + $schema)
Invoke-TestSql @'
USE shortage_test;
CREATE TABLE wms_shipment_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,simulation BIT);
CREATE TABLE wms_inventory(id BIGINT PRIMARY KEY,tenant_id BIGINT,sku_id BIGINT,warehouse_id BIGINT,quantity DECIMAL(20,2),deleted BIT);
INSERT INTO wms_shipment_order VALUES(1,1,0);
INSERT INTO wms_inventory VALUES(1,1,1,1,3,0);
INSERT INTO wms_shortage_event(tenant_id,event_id,event_version,order_id,line_id,target_system,shortage_status,payload,notification_status)
VALUES(1,'isolation-event',1,1,1,'ERP','OPEN','{}','PENDING');
'@
function Test-CurrentRead([string]$Mutation,[string]$Expected,[string]$ExpectedStock) {
    $query = @'
USE shortage_test;
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT order_id FROM wms_shortage_event WHERE tenant_id=1 AND event_id='isolation-event' LIMIT 1;
SELECT GET_LOCK('shortage-snapshot-ready',0);
SELECT SLEEP(4);
SELECT simulation FROM wms_shipment_order WHERE tenant_id=1 AND id=1 FOR UPDATE;
SELECT CONCAT('latest=',event_version,':',notification_status) FROM wms_shortage_event
WHERE tenant_id=1 AND event_id='isolation-event' ORDER BY event_version DESC LIMIT 1 FOR UPDATE;
SELECT CONCAT('stock=',quantity) FROM wms_inventory
WHERE tenant_id=1 AND sku_id=1 AND warehouse_id=1 AND deleted=0 FOR UPDATE;
COMMIT;
SELECT RELEASE_LOCK('shortage-snapshot-ready');
'@
    $start = [System.Diagnostics.ProcessStartInfo]::new('docker')
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @('exec',$Container,'mysql','-uroot','-N','-e',$query)) { $start.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::Start($start)
    $ready = $false
    for ($attempt=0; $attempt -lt 20; $attempt++) {
        $owner = Invoke-TestSql "SELECT IS_USED_LOCK('shortage-snapshot-ready');"
        if ($owner -ne 'NULL') { $ready=$true; break }
        Start-Sleep -Milliseconds 100
    }
    if (!$ready) { throw 'Snapshot session did not become ready.' }
    Invoke-TestSql ("USE shortage_test; " + $Mutation)
    $output = $process.StandardOutput.ReadToEnd()
    $errorOutput = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or !$output.Contains($Expected) -or !$output.Contains($ExpectedStock)) {
        throw ("Current read assertion failed: " + $output + $errorOutput)
    }
    Write-Output ("PASS MySQL REPEATABLE READ after established snapshot: " + $Expected + ', ' + $ExpectedStock)
}
Test-CurrentRead "UPDATE wms_shortage_event SET notification_status='SUCCEEDED' WHERE tenant_id=1 AND event_id='isolation-event'; UPDATE wms_inventory SET quantity=7 WHERE id=1;" 'latest=1:SUCCEEDED' 'stock=7.00'
Test-CurrentRead @'
INSERT INTO wms_shortage_event(tenant_id,event_id,event_version,order_id,line_id,target_system,shortage_status,payload,notification_status)
VALUES(1,'isolation-event',2,1,1,'ERP','RESOLVED','{}','PENDING');
UPDATE wms_inventory SET quantity=10 WHERE id=1;
'@ 'latest=2:PENDING' 'stock=10.00'
Write-Output 'PASS production migration syntax on isolated MySQL 8.4'
