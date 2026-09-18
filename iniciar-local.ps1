param([string]$Xampp = 'C:\xampp')
$ErrorActionPreference = 'Stop'
$projeto = $PSScriptRoot
$php = Join-Path $Xampp 'php\php.exe'
$mysql = Join-Path $Xampp 'mysql\bin\mysqld.exe'
$api = Join-Path $projeto 'php_appest'
$runtime = Join-Path $projeto '.runtime'
if (!(Test-Path -LiteralPath $php)) { throw "PHP não encontrado em $php. Instale XAMPP ou informe -Xampp." }
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
function PortaAberta([int]$porta) {
    $cliente = New-Object Net.Sockets.TcpClient
    try { $t = $cliente.ConnectAsync('127.0.0.1', $porta); return ($t.Wait(500) -and $cliente.Connected) }
    catch { return $false }
    finally { $cliente.Dispose() }
}
# Reutiliza o MySQL existente; não substitui dados.
if (!(PortaAberta 3306)) {
    if (!(Test-Path -LiteralPath $mysql)) { throw 'MySQL do XAMPP não encontrado.' }
    $ini = Join-Path $Xampp 'mysql\bin\my.ini'
    Start-Process -FilePath $mysql -ArgumentList @("--defaults-file=`"$ini`"", '--standalone') -WindowStyle Hidden | Out-Null
    for ($i=0; $i -lt 40 -and !(PortaAberta 3306); $i++) { Start-Sleep -Milliseconds 250 }
    if (!(PortaAberta 3306)) { throw 'MySQL não iniciou. Confira o painel do XAMPP.' }
}
& $php (Join-Path $api 'tools\instalar.php')
if ($LASTEXITCODE -ne 0) { throw 'A preparação do banco falhou.' }
$url = 'http://127.0.0.1:8088/php_appest/saude.php'
if (PortaAberta 8088) {
    try { $saude = Invoke-RestMethod -Uri $url -TimeoutSec 3 }
    catch { throw 'A porta 8088 está ocupada por outro serviço.' }
    if ($saude.servico -ne 'academia-genios-api' -or $saude.versao -ne 2) { throw 'A porta 8088 está ocupada por outra API.' }
} else {
    $router = Join-Path $api 'tools\router-local.php'
    $processo = Start-Process -FilePath $php -ArgumentList @('-S', '127.0.0.1:8088', '-t', "`"$api`"", "`"$router`"") -WorkingDirectory $api -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'api-out.log') -RedirectStandardError (Join-Path $runtime 'api-error.log')
    $processo.Id | Set-Content -LiteralPath (Join-Path $runtime 'api.pid')
    $pronto = $false
    for ($i=0; $i -lt 20; $i++) {
        try { $saude = Invoke-RestMethod -Uri $url -TimeoutSec 2; if ($saude.servico -eq 'academia-genios-api') { $pronto=$true; break } }
        catch { Start-Sleep -Milliseconds 250 }
    }
    if (!$pronto) { throw 'API não iniciou. Consulte .runtime/api-error.log.' }
}
Write-Output 'API pronta para o emulador: http://10.0.2.2:8088/php_appest/'
