param(
    [string]$Database = "house_rent_w5",
    [string]$DbUser = "root",
    [string]$DbPassword = "root",
    [string]$MysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    [switch]$ResetOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$seedDir = Join-Path $repoRoot "hrent-api\src\main\resources\db\seed"
$seedSql = Join-Path $seedDir "seed_w9_200.sql"
$resetSql = Join-Path $seedDir "reset_w9_200.sql"

if (-not (Test-Path -LiteralPath $MysqlBin)) {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        throw "找不到 mysql 客户端，请用 -MysqlBin 指定 mysql.exe 路径"
    }
    $MysqlBin = $cmd.Source
}
if (-not (Test-Path -LiteralPath $seedSql)) { throw "找不到种子脚本：$seedSql" }

$run = {
    param([string]$file)
    $fwd = $file.Replace("\", "/")
    $env:MYSQL_PWD = $DbPassword
    & $MysqlBin --default-character-set=utf8mb4 "-u$DbUser" "-D$Database" -e "source $fwd"
    if ($LASTEXITCODE -ne 0) { throw "mysql 执行失败：$file" }
}

if ($ResetOnly) {
    & $run $resetSql
    Write-Output "W9 种子已清理"
    exit 0
}

& $run $seedSql
Write-Output "W9 种子已导入：200 房 / 53 租约 / 50 评价 / 90 账单（30 租约 × 3 月）"
Write-Output "演示账号：seed_l1..3 / seed_t1..6，口令均为 123456"
