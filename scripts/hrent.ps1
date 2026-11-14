# =============================================================================
# 房屋租赁管理系统 · 一键启动脚本（Windows PowerShell 5.1+）
#
# 一般不直接调用本文件，用项目根目录的 start.cmd：
#   start.cmd                构建（如需要）并后台启动
#   start.cmd run            前台启动（Ctrl+C 停止）
#   start.cmd stop           停止
#   start.cmd restart        重启
#   start.cmd status         查看状态 + 健康检查
#   start.cmd logs -f        跟踪日志
#   start.cmd build          仅构建后端
#   start.cmd build-web      仅构建前端
#
# 参数：--port <n> / --no-build / --rebuild / --with-tests / --env <file>
#
# 设计要点：
#   1. 端口只认 HRENT_PORT，不读 SERVER_PORT（云平台/CI 会注入该变量）。
#   2. 示例配置在 .env.example，真实凭据只写 .env（已被 .gitignore 忽略）。
#   3. JDK 9+ 才加 --add-opens，JDK 8 传入会启动失败。
#   4. 同时兼容 Windows PowerShell 5.1 与 PowerShell 7。
# =============================================================================

$ErrorActionPreference = "Stop"

$RootDir   = Split-Path -Parent $PSScriptRoot
$ApiDir    = Join-Path $RootDir "hrent-api"
$WebDir    = Join-Path $RootDir "hrent-web"
$LogDir    = Join-Path $RootDir "logs"
$JarRel    = "target\hrent-0.0.1-SNAPSHOT.jar"
$JarPath   = Join-Path $ApiDir $JarRel
$PidFile   = Join-Path $LogDir "hrent.pid"
$LogFile   = Join-Path $LogDir "hrent.log"
$ErrLogFile= Join-Path $LogDir "hrent.err.log"
$DefaultPort = 8888
$HealthPath  = "/sysCommon/getBaseInfo"

# 前端（hrent-web）：构建产物目录 + 开发服务器进程
$WebDistDir    = Join-Path $WebDir "dist"
$WebIndex      = Join-Path $WebDistDir "index.html"
$WebPidFile    = Join-Path $LogDir "hrent-web.pid"
# 记下 dev 实际用的端口：否则「HRENT_WEB_PORT=8099 start.cmd dev」之后
# 直接 start.cmd stop，会用默认 8080 找进程，报「未运行」却把 8099 留在那里。
$WebPortFile   = Join-Path $LogDir "hrent-web.port"
$WebLogFile    = Join-Path $LogDir "hrent-web.log"
$WebErrLogFile = Join-Path $LogDir "hrent-web.err.log"
$DefaultWebPort = 8080
$WebPort       = $DefaultWebPort

$Command     = "start"
$CliPort     = 0
$CliWebPort  = 0
$NoBuild     = $false
$ForceBuild  = $false
$WithTests   = $false
$Follow      = $false
$WebLogs     = $false
$EnvFile     = Join-Path $RootDir ".env"

# ---------------------------------------------------------------------------
# 输出
# ---------------------------------------------------------------------------
function Write-Info { param([string]$Message) Write-Host "[hrent] $Message" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Message) Write-Host "[hrent] $Message" -ForegroundColor Green }
function Write-Warn { param([string]$Message) Write-Host "[hrent] $Message" -ForegroundColor Yellow }

# 失败原因落盘：双击 start.cmd 时窗口会立刻关闭，屏幕上的红字往往来不及看，
# 因此把所有失败都写一份到 logs\hrent.error.log，入口脚本结束后会回显它。
function Write-ErrLog {
    param([string]$Message)
    try {
        if (-not (Test-Path -LiteralPath $LogDir)) {
            New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
        }
        $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $text = "[" + $stamp + "] " + $Message
        # 显式 UTF-8 无 BOM：PS 5.1 的 -Encoding UTF8 会写 BOM，
        # 用记事本 / type / 日志采集打开时开头多出不可见字符。
        [System.IO.File]::WriteAllText(
            (Join-Path $LogDir "hrent.error.log"),
            $text,
            (New-Object System.Text.UTF8Encoding($false))
        )
    } catch {
        # 记录失败本身不能再抛错，否则会盖掉原始错误
    }
}

function Die {
    param([string]$Message)
    Write-Host "[hrent] $Message" -ForegroundColor Red
    Write-ErrLog -Message $Message
    exit 1
}

function Show-Usage {
    @"
房屋租赁管理系统 · 一键启动脚本（Windows）

用法：start.cmd <命令> [选项]

命令：
  start        构建（如需要）并后台启动后端；前端已构建时由同一进程提供页面
  dev          后端 + 前端开发服务器（热更新，改前端代码即时生效）
  run          仅前台启动后端，Ctrl+C 停止（排查启动问题用）
  stop         停止后端与前端
  restart      重启后端
  status       查看后端 / 前端状态
  logs [-f] [--web]  查看日志（--web 看前端，-f 持续跟踪）
  build        仅构建后端 jar
  build-web    仅构建前端到 hrent-web\dist
  help         显示本帮助

选项：
  --port <n>      后端端口（默认 8888，也可在 .env 设 HRENT_PORT）
  --web-port <n>  前端开发服务器端口（默认 8080，也可在 .env 设 HRENT_WEB_PORT）
  --no-build      跳过构建，直接用现有 jar
  --rebuild       强制重新构建
  --with-tests    构建时执行单元测试（默认跳过）
  --env <file>    指定环境变量文件（默认项目根 .env）

前端的两种用法：
  1) 生产/演示 —— 单进程、不需要 Node
       start.cmd build-web     构建一次
       start.cmd               只起后端，页面由后端托管
       打开 http://localhost:8888/hrent/web/index.html
  2) 开发 —— 热更新
       start.cmd dev           后端 + 前端开发服务器
       打开 http://localhost:8080/

示例：
  copy .env.example .env   然后编辑数据库账号密码
  start.cmd
  start.cmd dev
  start.cmd logs -f
"@ | Write-Host
    exit 0
}

# ---------------------------------------------------------------------------
# .env 解析（KEY=VALUE，不执行任何表达式）
# ---------------------------------------------------------------------------
function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($raw in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $line = $raw.Trim()
        if ($line.Length -eq 0) { continue }
        if ($line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($val.Length -ge 2) {
            if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                $val = $val.Substring(1, $val.Length - 2)
            }
        }
        if ($key -match '^[A-Za-z_][A-Za-z0-9_]*$') {
            Set-Item -Path ("Env:" + $key) -Value $val
        }
    }
}

# ---------------------------------------------------------------------------
# 环境变量归一化
# Windows 环境变量本应大小写不敏感，但 Git Bash / WSL 等终端会同时注入
# HTTPS_PROXY 与 https_proxy。PowerShell 5.1 的 Start-Process 内部字典大小写
# 敏感，遇到重复键直接抛「字典中的关键字冲突」而启动失败。这里在启动前清理，
# 优先保留大写形式（Windows 惯例）。
# ---------------------------------------------------------------------------
function Remove-DuplicateEnvKeys {
    $keys = @([System.Environment]::GetEnvironmentVariables('Process').Keys)
    $seen = @{}
    foreach ($k in $keys) {
        $lk = $k.ToLowerInvariant()
        if ($seen.ContainsKey($lk)) {
            $keep = $seen[$lk]
            if ($k -cmatch '^[A-Z0-9_]+$') {
                try { [System.Environment]::SetEnvironmentVariable($keep, $null, 'Process') } catch { }
                $seen[$lk] = $k
            } else {
                try { [System.Environment]::SetEnvironmentVariable($k, $null, 'Process') } catch { }
            }
        } else {
            $seen[$lk] = $k
        }
    }
}

# ---------------------------------------------------------------------------
# Java 探测
# ---------------------------------------------------------------------------
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\java.exe") }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    return $null
}

$script:JavaBin = ""
$script:JavaMajor = 0

function Get-JavaInfo {
    $java = Resolve-Java
    if (-not $java) {
        Die "未找到 java.exe。请安装 JDK 8 及以上（推荐 JDK 17），或设置 JAVA_HOME 后重试。"
    }
    $script:JavaBin = $java

    # java -version 的输出走 stderr；在 $ErrorActionPreference='Stop' 下用 2>&1
    # 会被当成异常抛出，故改用 .NET 进程重定向读取
    $text = ""
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $java
        $psi.Arguments = "-version"
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.CreateNoWindow = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        $text = $stdout + $stderr
    } catch {
        Die "无法执行 java -version，请检查 JAVA_HOME 是否指向有效的 JDK。"
    }

    $major = 0
    if ($text -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }
    }
    if ($major -lt 8) {
        Die ("Java 版本过低（major=" + $major + "），最低 JDK 8，推荐 JDK 17。")
    }
    $script:JavaMajor = $major
    $firstLine = ($text -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 } | Select-Object -First 1)
    Write-Ok ($firstLine.Trim() + "  ->  " + $java)
}

function Get-JvmArgs {
    $list = @()
    if ($env:JAVA_OPTS) {
        foreach ($item in ($env:JAVA_OPTS -split '\s+')) {
            if ($item.Trim().Length -gt 0) { $list += $item.Trim() }
        }
    }
    if ($list.Count -eq 0) { $list += "-Dfile.encoding=UTF-8" }
    # 时区固化：不依赖操作系统时区，Java 侧统一按北京时间解释/格式化 DATETIME
    if (-not ($list -match '-Duser\.timezone')) { $list += "-Duser.timezone=Asia/Shanghai" }

    if ($script:JavaMajor -ge 9) {
        $list += "--add-opens"
        $list += "java.base/java.lang=ALL-UNNAMED"
        $list += "--add-opens"
        $list += "java.base/java.math=ALL-UNNAMED"
    }
    return $list
}

# ---------------------------------------------------------------------------
# 端口 / 健康检查
# ---------------------------------------------------------------------------
function Test-PortInUse {
    param([int]$Port)
    # Get-NetTCPConnection 可用时以它为准（-State Listen 已排除 TIME_WAIT 干扰）；
    # 只有在 cmdlet 不可用时才退回 netstat，且必须过滤 LISTENING，
    # 否则历史连接残留会被误判成端口占用。
    $cmdletOk = $true
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        if ($conn) { return $true }
        return $false
    } catch {
        $cmdletOk = $false
    }
    if (-not $cmdletOk) {
        $hits = & netstat -ano 2>$null | Select-String -Pattern (":" + $Port + "\s") | Select-String -Pattern "LISTENING"
        if ($hits) { return $true }
    }
    return $false
}

function Get-PortOwnerHint {
    param([int]$Port)
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 3
        foreach ($c in $conn) {
            $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
            if ($p) { Write-Host ("  PID " + $c.OwningProcess + "  " + $p.ProcessName) }
        }
    } catch {
        return
    }
}

# 按监听端口反查 PID：PID 文件丢失或里面记的是包装进程时用它兜底。
# 只返回确实是 node 的 PID，避免把同端口上的其他程序当成前端。
function Get-PortOwnerPid {
    param([int]$Port)
    $candidates = @()
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $conn) { $candidates += [int]$c.OwningProcess }
    } catch {
        # cmdlet 不可用时走 netstat 兜底
    }
    if ($candidates.Count -eq 0) {
        try {
            $lines = netstat -ano 2>$null | Select-String -Pattern (":" + $Port + "\s+.*LISTENING")
            foreach ($l in $lines) {
                $parts = @(([string]$l.Line).Trim() -split '\s+')
                if ($parts.Count -lt 2) { continue }
                $id = 0
                if ([int]::TryParse($parts[$parts.Count - 1], [ref]$id)) { $candidates += $id }
            }
        } catch {
            return 0
        }
    }
    foreach ($id in ($candidates | Select-Object -Unique)) {
        if ($id -le 0) { continue }
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -match '^node') { return $id }
    }
    return 0
}

# 前端真实 PID：先信 PID 文件，其次按端口反查
function Find-WebPid {
    if (Test-Path -LiteralPath $WebPidFile) {
        $raw = (Get-Content -LiteralPath $WebPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        $recorded = 0
        if ($raw) { [void][int]::TryParse(([string]$raw).Trim(), [ref]$recorded) }
        if (Test-WebPidIsOurs -ProcId $recorded) { return $recorded }
    }
    return (Get-PortOwnerPid -Port $WebPort)
}

function Test-Health {
    param([int]$Port)
    $url = "http://127.0.0.1:$Port/hrent$HealthPath"
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        # 两个必须的开关，缺一个都会误报「启动成功」：
        #   --noproxy "*"     本机地址不能走系统代理。实测环境有 http_proxy 时，
        #                     代理直接回 502，服务根本没起来也能拿到「响应」。
        #   -w %{http_code}   curl -s 遇到 4xx/5xx 仍然 exit 0，只看退出码等于
        #                     把「有 HTTP 响应」当成「服务健康」，必须校验 200。
        $code = & $curl.Source -s -m 3 --noproxy "*" -o NUL -X POST `
            -H "Content-Type: application/json" -d "{}" -w "%{http_code}" $url
        if ($LASTEXITCODE -eq 0 -and ("$code".Trim() -eq "200")) { return $true }
        return $false
    }
    try {
        $req = [System.Net.WebRequest]::Create($url)
        $req.Method = "POST"
        $req.ContentType = "application/json"
        $req.Timeout = 3000
        $req.Proxy = $null
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("{}")
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        if ($code -eq 200) { return $true }
        return $false
    } catch {
        return $false
    }
}

# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------
function Test-JarFresh {
    if (-not (Test-Path -LiteralPath $JarPath)) { return $false }
    $jarTime = (Get-Item -LiteralPath $JarPath).LastWriteTime
    $srcNewer = Get-ChildItem -LiteralPath (Join-Path $ApiDir "src") -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $jarTime } | Select-Object -First 1
    if ($srcNewer) { return $false }
    $pom = Get-Item -LiteralPath (Join-Path $ApiDir "pom.xml") -ErrorAction SilentlyContinue
    if ($pom -and $pom.LastWriteTime -gt $jarTime) { return $false }
    return $true
}

function Invoke-Build {
    $mvnw = Join-Path $ApiDir "mvnw.cmd"
    if (-not (Test-Path -LiteralPath $mvnw)) { Die "缺少 hrent-api\mvnw.cmd，Maven Wrapper 未入库。" }
    $mvnArgs = @("-B", "clean", "package")
    if (-not $WithTests) { $mvnArgs += "-DskipTests" }
    Write-Info ("开始构建：mvnw.cmd " + ($mvnArgs -join " "))
    Write-Info "首次构建需联网下载 Maven 与依赖，请耐心等待..."
    Push-Location $ApiDir
    try {
        & $mvnw @mvnArgs
        if ($LASTEXITCODE -ne 0) {
            Die ("构建失败（exit=" + $LASTEXITCODE + "）。离线环境可在有网机器构建后拷贝 target 目录，再用 --no-build 启动。")
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $JarPath)) { Die ("构建结束但未找到产物：" + $JarPath) }
    $sizeMb = [math]::Round((Get-Item -LiteralPath $JarPath).Length / 1MB, 1)
    Write-Ok ("构建完成：" + $JarRel + "（" + $sizeMb + " MB）")
}

function Invoke-BuildWeb {
    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { Die "未找到 npm。请安装 Node.js 16+（推荐 18/20 LTS）。" }
    Push-Location $WebDir
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $WebDir "node_modules"))) {
            Write-Info "安装前端依赖（首次较慢）..."
            & npm install --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { Die "npm install 失败。" }
        }
        $major = 0
        try { $major = [int](& node -p "process.versions.node.split('.')[0]") } catch { $major = 0 }
        Write-Info ("构建前端（Node major=" + $major + "）...")
        if ($major -ge 17) {
            # webpack 3 依赖的 md4 在 Node 17+ 被 OpenSSL 3 移除
            $env:NODE_OPTIONS = "--openssl-legacy-provider"
            & npm run build
            if ($LASTEXITCODE -ne 0) { Die "前端构建失败。" }
            Remove-Item Env:NODE_OPTIONS -ErrorAction SilentlyContinue
        } else {
            & npm run build
            if ($LASTEXITCODE -ne 0) { Die "前端构建失败。" }
        }
        Write-Ok "前端构建完成：hrent-web\dist（已由后端映射到 /hrent/web/，也可交给 IIS/nginx）"
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------------------
# 前端：构建产物 / 开发服务器
# ---------------------------------------------------------------------------
function Test-WebBuilt {
    return (Test-Path -LiteralPath $WebIndex)
}

# 判断某个 PID 是否确实是本项目的 webpack-dev-server（npm.cmd 的承载进程或派生的 node）。
# 只认命令行，避免 PID 复用后误杀别的 node 进程。
function Test-WebPidIsOurs {
    param([int]$ProcId)
    if ($ProcId -le 0) { return $false }
    $proc = Get-Process -Id $ProcId -ErrorAction SilentlyContinue
    if (-not $proc) { return $false }
    try {
        $cim = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $ProcId) -ErrorAction SilentlyContinue
        if ($cim -and $cim.CommandLine -and ($cim.CommandLine -match 'webpack-dev-server|hrent-web')) { return $true }
    } catch {
        # 取不到命令行时退回名称判断（有 PID 文件兜底，不会命中无关进程）
        return ($proc.ProcessName -match '^(node|cmd)$')
    }
    return $false
}

# 前端页面探活：GET 首页返回 200 才算就绪。
# 与后端健康检查同理：必须校验状态码并禁用代理，否则代理返回 502 时 curl 仍以 0 退出，
# 会被误判为「已就绪」。
function Test-WebUp {
    param([int]$Port)
    $url = "http://127.0.0.1:$Port/"
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        $code = (& $curl.Source -s -m 3 --noproxy "*" -o NUL -w "%{http_code}" $url)
        return (([string]$code).Trim() -eq "200")
    }
    try {
        $req = [System.Net.WebRequest]::Create($url)
        $req.Method = "GET"
        $req.Timeout = 3000
        $req.Proxy = $null
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        return ($code -eq 200)
    } catch {
        return $false
    }
}

function Start-WebDev {
    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) {
        Die "未找到 npm。前端开发服务器需要 Node.js 16+（推荐 18/20 LTS）；或用 start.cmd build-web + 后端托管。"
    }

    # 幂等：已在运行就不重复起（重复起会因端口占用直接失败）
    $runningPid = Find-WebPid
    if ($runningPid -gt 0) {
        Write-Ok ("前端开发服务器已在运行（PID " + $runningPid + "）")
        Set-Content -LiteralPath $WebPortFile -Value $WebPort -Encoding ASCII
        Write-Info ("页面地址：http://localhost:" + $WebPort + "/")
        return
    }
    Remove-Item -LiteralPath $WebPidFile -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path -LiteralPath (Join-Path $WebDir "node_modules"))) {
        Write-Info "首次运行，安装前端依赖（较慢，视网络 2-5 分钟）..."
        Push-Location $WebDir
        try {
            & npm install --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { Die "npm install 失败。检查网络或 npm 镜像（npm config get registry）。" }
        } finally {
            Pop-Location
        }
    }

    if (Test-PortInUse -Port $WebPort) {
        Write-Warn ("前端端口 " + $WebPort + " 已被占用，占用者：")
        Get-PortOwnerHint -Port $WebPort
        Die ("请释放该端口，或用 --web-port <其他端口> 指定。")
    }

    # 两个变量都必须显式设置：
    #   NODE_OPTIONS - webpack 3 依赖 md4，Node 17+ 的 OpenSSL 3 已移除，不加会直接抛
    #                  ERR_OSSL_EVP_UNSUPPORTED（与 build-web 是同一个问题）；
    #   PORT         - 固定成刚校验过的端口，否则 portfinder 会静默改用下一个空闲端口，
    #                  脚本报告的地址就和真实地址不一致。
    $env:NODE_OPTIONS = "--openssl-legacy-provider"
    $env:PORT = [string]$WebPort
    $env:HRENT_API_BASE = "http://127.0.0.1:" + $Port

    if (Test-Path -LiteralPath $WebLogFile) {
        Move-Item -LiteralPath $WebLogFile -Destination ($WebLogFile + ".1") -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $WebErrLogFile) {
        Move-Item -LiteralPath $WebErrLogFile -Destination ($WebErrLogFile + ".1") -Force -ErrorAction SilentlyContinue
    }

    Write-Info ("启动前端开发服务器：端口 " + $WebPort + "，/hrent/** 代理到 " + $env:HRENT_API_BASE + "（首次编译约 30-60 秒）")
    $proc = Start-Process -FilePath $npm.Source -ArgumentList @("run", "dev") -WorkingDirectory $WebDir `
        -RedirectStandardOutput $WebLogFile -RedirectStandardError $WebErrLogFile `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $WebPidFile -Value $proc.Id -Encoding ASCII

    $i = 0
    $dotted = $false
    while ($i -lt 180) {
        if ($proc.HasExited) {
            Remove-Item -LiteralPath $WebPidFile -Force -ErrorAction SilentlyContinue
            Write-Warn "前端进程在启动阶段退出，最近 30 行日志："
            if (Test-Path -LiteralPath $WebLogFile) { Get-Content -LiteralPath $WebLogFile -Tail 30 | Write-Host }
            if (Test-Path -LiteralPath $WebErrLogFile) {
                $e = Get-Content -LiteralPath $WebErrLogFile -Tail 30 -ErrorAction SilentlyContinue
                if ($e) { $e | Write-Host }
            }
            Die "前端启动失败。常见原因：依赖未装全（删 node_modules 重装）、端口被占用、Node 版本与 webpack 3 不兼容。"
        }
        if (Test-WebUp -Port $WebPort) {
            if ($dotted) { Write-Host "" }
            # PID 文件回填成真正的监听进程：npm.cmd 的 PID 只是 cmd.exe 外壳，
            # 下次 stop 拿它去按命令行匹配会判「不是前端」，于是找不到进程。
            $realPid = Get-PortOwnerPid -Port $WebPort
            if ($realPid -gt 0) {
                Set-Content -LiteralPath $WebPidFile -Value $realPid -Encoding ASCII
            } else {
                $realPid = $proc.Id
            }
            Set-Content -LiteralPath $WebPortFile -Value $WebPort -Encoding ASCII
            Write-Ok ("前端已就绪（PID " + $realPid + "）")
            Write-Ok ("页面地址：http://localhost:" + $WebPort + "/")
            return
        }
        $i = $i + 1
        Write-Host "." -NoNewline
        $dotted = $true
        Start-Sleep -Seconds 1
    }
    Write-Host ""
    Write-Warn ("等待 180 秒仍未就绪，进程仍在运行（PID " + $proc.Id + "）。")
    Write-Warn "执行 start.cmd logs --web 查看前端日志。"
}

function Stop-WebDev {
    $procId = Find-WebPid
    if ($procId -le 0) {
        # 找不到进程才认为未运行；不去扫全机 node：构建前端（npm run build）也是 node
        Write-Info "前端开发服务器未运行。"
        Remove-Item -LiteralPath $WebPidFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $WebPortFile -Force -ErrorAction SilentlyContinue
        return
    }
    Write-Info ("停止前端开发服务器（PID " + $procId + "）...")
    # npm.cmd -> cmd.exe -> node.exe 三层，必须按进程树结束，否则 node 变孤儿进程继续占端口
    & taskkill /F /T /PID $procId 2>&1 | Out-Null
    $i = 0
    while ($i -lt 10) {
        if (-not (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { break }
        $i = $i + 1
        Start-Sleep -Seconds 1
    }
    # 进程树杀完仍可能有游离 node（npm 先退出时），再按端口确认一次
    $left = Get-PortOwnerPid -Port $WebPort
    if ($left -gt 0) {
        Write-Info ("结束残留前端进程 PID " + $left)
        & taskkill /F /T /PID $left 2>&1 | Out-Null
    }
    Remove-Item -LiteralPath $WebPidFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $WebPortFile -Force -ErrorAction SilentlyContinue
    if (Test-PortInUse -Port $WebPort) {
        Write-Warn ("端口 " + $WebPort + " 仍在监听，可能残留了子进程：")
        Get-PortOwnerHint -Port $WebPort
        return
    }
    Write-Ok "前端已停止。"
}

function Show-WebStatus {
    $procId = Find-WebPid
    if ($procId -gt 0) {
        if (Test-WebUp -Port $WebPort) {
            Write-Ok ("前端开发服务器：运行中（PID " + $procId + "）-> http://localhost:" + $WebPort + "/")
        } else {
            Write-Warn ("前端开发服务器：进程在（PID " + $procId + "），页面暂未就绪 -> http://localhost:" + $WebPort + "/")
        }
        return
    }
    if (Test-WebBuilt) {
        # 只有后端在监听时这个地址才可达；否则只说明「已构建」，不给出会误导人的 URL
        if (Test-PortInUse -Port $Port) {
            Write-Ok ("前端：已构建，由后端托管 -> http://localhost:" + $Port + "/hrent/web/index.html")
        } else {
            Write-Info "前端：已构建（hrent-web\dist 存在），后端启动后由 /hrent/web/ 提供"
        }
        return
    }
    Write-Warn "前端：未构建（执行 start.cmd build-web，或 start.cmd dev 起开发服务器）"
}

# ---------------------------------------------------------------------------
# 启停
# ---------------------------------------------------------------------------
function Set-SpringEnv {
    if ($env:DB_URL)      { $env:SPRING_DATASOURCE_URL = $env:DB_URL }
    if ($env:DB_USERNAME) { $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME }
    if ($env:DB_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD }
    if ($env:TOKEN_SECRET) { $env:TOKEN_SECRET = $env:TOKEN_SECRET }
    if ($env:WECHAT_APP_ID) { $env:WECHAT_APP_ID = $env:WECHAT_APP_ID }
    if ($env:WECHAT_APP_SECRET) { $env:WECHAT_APP_SECRET = $env:WECHAT_APP_SECRET }
    # 把前端产物目录告诉后端，由 WebMvcConfig 映射到 /hrent/web/**，
    # 这样同一个 JVM 既提供接口也提供页面，不必再单独部署静态服务器。
    if (Test-WebBuilt) { $env:HRENT_WEB_DIR = $WebDistDir }
}

function Initialize-Runtime {
    if (-not (Test-Path -LiteralPath $ApiDir)) { Die "缺少 hrent-api 目录，请从项目根目录运行。" }
    if (-not (Test-Path -LiteralPath $LogDir)) {
        New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    }
}

# 启动前拦掉「模板没改」这类必然失败：.env 从 .env.example 复制后，
# DB_PASSWORD 默认是 change-me。不拦的话要等 Spring 起来 → Flyway 连库 →
# 抛一屏 Access denied (1045) 才看得到，双击用户完全不知道要改哪里。
function Assert-EnvReady {
    if ($env:DB_PASSWORD -and ($env:DB_PASSWORD -match '^change[-_]?me')) {
        Die ("`.env` 的 DB_PASSWORD 仍是模板占位符（" + $env:DB_PASSWORD + "），数据库会拒绝登录。请改成真实密码后重试。")
    }
    if ($env:TOKEN_SECRET -and ($env:TOKEN_SECRET -match '^change[-_]?me')) {
        Write-Warn "TOKEN_SECRET 仍是模板占位符，建议换成 32 字节以上随机值（只影响安全性，不阻塞启动）。"
    }
}

function Invoke-Prepare {
    Initialize-Runtime
    Set-SpringEnv
    Assert-EnvReady
    if ($NoBuild) {
        if (-not (Test-Path -LiteralPath $JarPath)) { Die "指定了 --no-build，但 jar 不存在。请先执行 start.cmd build" }
    } elseif ($ForceBuild) {
        Invoke-Build
    } elseif (-not (Test-JarFresh)) {
        Invoke-Build
    } else {
        Write-Ok "jar 已是最新，跳过构建（加 --rebuild 可强制重建）"
    }
}

function Quote-Arg {
    param([string]$Value)
    if ($Value -match '\s') { return ('"' + $Value + '"') }
    return $Value
}

function Start-App {
    Invoke-Prepare
    if (Test-Path -LiteralPath $PidFile) {
        $old = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        # PID 文件可能被写坏（断电/磁盘满）：解析失败就按过期文件处理，
        # 不能让强转异常又变成一次「闪退」。
        $oldId = 0
        if ($old) { [void][int]::TryParse(([string]$old).Trim(), [ref]$oldId) }
        if ($oldId -gt 0) {
            $proc = Get-Process -Id $oldId -ErrorAction SilentlyContinue
            # PID 会被系统复用：只有同名 java 进程才认作本服务，否则当过期 PID 清理
            $isOurs = $false
            if ($proc) {
                try { $isOurs = ($proc.ProcessName -match '^java') } catch { $isOurs = $false }
            }
            if ($isOurs) {
                # 真实端口从进程命令行取（脚本启动时显式传了 --server.port），
                # 否则 `start.cmd --port 9999` 在服务已占用 8888 时会谎报 9999。
                $actualPort = $Port
                try {
                    $cim = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $oldId) -ErrorAction SilentlyContinue
                    if ($cim -and $cim.CommandLine -match '--server\.port=(\d+)') {
                        $actualPort = [int]$Matches[1]
                    }
                } catch {
                    # 取不到就退回命令行端口，不影响主流程
                }
                if ($actualPort -ne $Port) {
                    Die ("服务已在 " + $actualPort + " 端口运行（PID " + $oldId + "）。改用 " + $Port + " 请先执行 start.cmd stop")
                }
                # 重复启动按幂等成功处理（退出码 0）：双击启动时这是最常见场景，
                # 之前按失败退出会让窗口一闪而过，看起来就是「闪退」。
                Write-Ok ("服务已在运行（PID " + $oldId + "），无需重复启动")
                Write-Info ("接口地址：http://localhost:" + $actualPort + "/hrent")
                if (Test-WebBuilt) {
                    Write-Info ("页面地址：http://localhost:" + $actualPort + "/hrent/web/index.html")
                }
                if (Test-Health -Port $actualPort) {
                    Write-Ok "健康检查：通过"
                } else {
                    Write-Warn "健康检查：未通过（执行 start.cmd logs 排查）"
                }
                Write-Info "重启请执行 start.cmd restart，停止请执行 start.cmd stop"
                return
            }
        }
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    }
    if (Test-PortInUse -Port $Port) {
        Write-Warn ("端口 " + $Port + " 已被占用，占用者：")
        Get-PortOwnerHint -Port $Port
        Die "请先释放端口，或用 --port <其他端口> 指定。"
    }

    $jvmArgs = Get-JvmArgs
    $all = @()
    $all += $jvmArgs
    $all += @("-jar", $JarPath)
    $all += @("--server.port=$Port")
    $quoted = @()
    foreach ($a in $all) { $quoted += (Quote-Arg $a) }

    Write-Info ("启动中：端口 " + $Port + "，上下文 /hrent，日志 logs\hrent.log")
    # 日志轮转：Start-Process 的重定向只能覆盖写，先把上一次日志留档为 .1
    if (Test-Path -LiteralPath $LogFile) {
        Move-Item -LiteralPath $LogFile -Destination ($LogFile + ".1") -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $ErrLogFile) {
        Move-Item -LiteralPath $ErrLogFile -Destination ($ErrLogFile + ".1") -Force -ErrorAction SilentlyContinue
    }
    $proc = Start-Process -FilePath $script:JavaBin -ArgumentList ($quoted -join " ") `
        -WorkingDirectory $ApiDir -RedirectStandardOutput $LogFile -RedirectStandardError $ErrLogFile `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $PidFile -Value $proc.Id -Encoding ASCII

    $i = 0
    $dotted = $false
    while ($i -lt 60) {
        if ($proc.HasExited) {
            Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
            Write-Warn "进程在启动阶段退出，最近 30 行日志："
            if (Test-Path -LiteralPath $LogFile) { Get-Content -LiteralPath $LogFile -Tail 30 | Write-Host }
            if (Test-Path -LiteralPath $ErrLogFile) { Get-Content -LiteralPath $ErrLogFile -Tail 30 | Write-Host }
            Die "启动失败。常见原因：数据库未启动、账号密码错误、端口被占用。"
        }
        if (Test-Health -Port $Port) {
            if ($dotted) { Write-Host "" }
            Write-Ok ("启动成功（PID " + $proc.Id + "）")
            Write-Ok ("接口地址：http://localhost:" + $Port + "/hrent")
            if (Test-WebBuilt) {
                Write-Ok ("页面地址：http://localhost:" + $Port + "/hrent/web/index.html")
            } else {
                Write-Warn "前端尚未构建：执行 start.cmd build-web（后端托管），或 start.cmd dev（热更新）"
            }
            Write-Ok ("健康检查：http://localhost:" + $Port + "/hrent" + $HealthPath + "（POST）")
            return
        }
        $i = $i + 1
        Write-Host "." -NoNewline
        $dotted = $true
        Start-Sleep -Seconds 1
    }
    Write-Host ""
    Write-Warn ("等待 60 秒仍未通过健康检查，进程仍在运行（PID " + $proc.Id + "）。")
    Write-Warn "执行 start.cmd logs 查看日志，或 start.cmd stop 停止。"
    exit 1
}

function Start-AppForeground {
    Invoke-Prepare
    $jvmArgs = Get-JvmArgs
    $all = @()
    $all += $jvmArgs
    $all += @("-jar", $JarPath)
    $all += @("--server.port=$Port")
    Write-Info "前台启动（Ctrl+C 停止）"
    Push-Location $ApiDir
    try {
        & $script:JavaBin @all
    } finally {
        Pop-Location
    }
}

function Stop-App {
    if (-not (Test-Path -LiteralPath $PidFile)) {
        Write-Info "未找到 PID 文件（服务可能未运行）。"
        return
    }
    $raw = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $procId = 0
    if ($raw) { [void][int]::TryParse(([string]$raw).Trim(), [ref]$procId) }
    if ($procId -le 0) {
        Write-Warn "PID 文件内容无效，清理。"
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
        return
    }
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-Warn ("PID " + $procId + " 对应进程不存在，清理残留 PID 文件。")
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
        return
    }
    # 防误杀：确认是本项目的 jar
    $isOurs = $false
    try {
        $cim = Get-CimInstance Win32_Process -Filter ("ProcessId = " + $procId) -ErrorAction SilentlyContinue
        if ($cim -and $cim.CommandLine -and $cim.CommandLine.Contains("hrent-0.0.1-SNAPSHOT.jar")) { $isOurs = $true }
    } catch {
        $isOurs = $true
    }
    if (-not $isOurs) {
        Write-Warn ("PID " + $procId + " 不是本项目的 Java 进程，拒绝停止。")
        return
    }
    Write-Info ("停止服务（PID " + $procId + "）...")
    Stop-Process -Id $procId -ErrorAction SilentlyContinue
    $i = 0
    while ($i -lt 20) {
        $still = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $still) { break }
        $i = $i + 1
        Start-Sleep -Seconds 1
    }
    $still = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($still) {
        Write-Warn "优雅停止超时，强制结束。"
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    Write-Ok "已停止。"
}

function Show-Status {
    $procId = 0
    if (Test-Path -LiteralPath $PidFile) {
        $raw = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($raw) { [void][int]::TryParse(([string]$raw).Trim(), [ref]$procId) }
    }
    $proc = $null
    if ($procId -gt 0) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        # PID 会被系统复用：旧进程退出后同号 PID 可能落到任意程序上，
        # 只看「PID 存在」会误报「运行中」，故必须校验是 java 进程。
        if ($proc) {
            $isJava = $false
            try { $isJava = ($proc.ProcessName -match '^java') } catch { $isJava = $false }
            if (-not $isJava) { $proc = $null }
        }
    }
    if ($proc) {
        Write-Ok ("运行中（PID " + $procId + "）")
        # 以健康检查为准：端口监听状态受启动阶段与 TIME_WAIT 干扰，只作辅助信息
        if (Test-Health -Port $Port) {
            Write-Ok ("健康检查：通过 -> http://localhost:" + $Port + "/hrent" + $HealthPath)
            if (Test-PortInUse -Port $Port) { Write-Ok ("端口 " + $Port + " 正在监听") }
        } else {
            Write-Warn "健康检查：未通过（执行 start.cmd logs 排查）"
            if (Test-PortInUse -Port $Port) {
                Write-Warn ("端口 " + $Port + " 正在监听，但接口无响应")
            } else {
                Write-Warn ("端口 " + $Port + " 未监听")
            }
        }
        Show-WebStatus
        return
    }
    Write-Warn "后端：未运行"
    Show-WebStatus
    if (Test-Path -LiteralPath $PidFile) {
        # 只提示不删除：status 保持只读；start/stop 会各自清理
        Write-Info ("存在残留 PID 文件（进程已退出，下次 start/stop 会自动清理）：" + $PidFile)
    }
    exit 1
}

function Show-Logs {
    # --web 看前端日志；默认看后端
    $file = $LogFile
    $errFile = $ErrLogFile
    if ($script:WebLogs) {
        $file = $WebLogFile
        $errFile = $WebErrLogFile
    }
    if (-not (Test-Path -LiteralPath $file)) { Die ("日志文件不存在：" + $file) }
    if ($Follow) {
        Write-Info ("跟踪日志（Ctrl+C 退出）：" + $file)
        Get-Content -LiteralPath $file -Tail 100 -Wait
    } else {
        Get-Content -LiteralPath $file -Tail 200
        if (Test-Path -LiteralPath $errFile) {
            $errTail = Get-Content -LiteralPath $errFile -Tail 40 -ErrorAction SilentlyContinue
            if ($errTail) {
                Write-Warn ("--- 标准错误输出（" + (Split-Path -Leaf $errFile) + "）---")
                $errTail | Write-Host
            }
        }
    }
}

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
# 入口逻辑统一兜底：$ErrorActionPreference = "Stop" 下任何非终止错误都会终止
# 脚本；没有 catch 时 PowerShell 只把红字刷到控制台，双击启动的窗口随即关闭，
# 用户看到的就是「闪退」。这里统一转成可读错误 + 落盘 + 明确退出码。
try {

$rest = @($args)
$idx = 0
while ($idx -lt $rest.Count) {
    $a = [string]$rest[$idx]
    switch ($a) {
        "start"     { $Command = "start"; $idx++ }
        "dev"       { $Command = "dev"; $idx++ }
        "run"       { $Command = "run"; $idx++ }
        "stop"      { $Command = "stop"; $idx++ }
        "restart"   { $Command = "restart"; $idx++ }
        "status"    { $Command = "status"; $idx++ }
        "logs"      { $Command = "logs"; $idx++ }
        "build"     { $Command = "build"; $idx++ }
        "build-web" { $Command = "build-web"; $idx++ }
        "help"      { Show-Usage }
        "-h"        { Show-Usage }
        "--help"    { Show-Usage }
        "--port" {
            if ($idx + 1 -ge $rest.Count) { Die "参数 --port 缺少值" }
            $rawPort = [string]$rest[$idx + 1]
            $parsedPort = 0
            # 显式解析而不是 [int] 强转：强转会抛 .NET 异常，用户看到的是
            # 「无法将值转换为类型 System.Int32」，而不是该改什么。
            if (-not [int]::TryParse($rawPort, [ref]$parsedPort)) {
                Die ("--port 需要 1-65535 的整数，收到：" + $rawPort)
            }
            $CliPort = $parsedPort; $idx = $idx + 2
        }
        "--no-build"  { $NoBuild = $true; $idx++ }
        "--rebuild"   { $ForceBuild = $true; $idx++ }
        "--with-tests" { $WithTests = $true; $idx++ }
        "--web-port" {
            if ($idx + 1 -ge $rest.Count) { Die "参数 --web-port 缺少值" }
            $rawWebPort = [string]$rest[$idx + 1]
            $parsedWebPort = 0
            if (-not [int]::TryParse($rawWebPort, [ref]$parsedWebPort)) {
                Die ("--web-port 需要 1-65535 的整数，收到：" + $rawWebPort)
            }
            if ($parsedWebPort -lt 1 -or $parsedWebPort -gt 65535) {
                Die ("--web-port 超出范围（1-65535）：" + $parsedWebPort)
            }
            $CliWebPort = $parsedWebPort; $idx = $idx + 2
        }
        "--web"       { $WebLogs = $true; $idx++ }
        "--env" {
            if ($idx + 1 -ge $rest.Count) { Die "参数 --env 缺少值" }
            $EnvFile = [string]$rest[$idx + 1]; $idx = $idx + 2
        }
        "-f"        { $Follow = $true; $idx++ }
        "--follow"  { $Follow = $true; $idx++ }
        default     { Die ("未知参数：" + $a + "（执行 start.cmd help 查看用法）") }
    }
}

Import-EnvFile -Path $EnvFile
Remove-DuplicateEnvKeys

$envPort = 0
if ($env:HRENT_PORT) {
    try { $envPort = [int]$env:HRENT_PORT } catch { $envPort = 0 }
}
if ($CliPort -gt 0) {
    $Port = $CliPort
} elseif ($envPort -gt 0) {
    $Port = $envPort
} else {
    $Port = $DefaultPort
}
if ($Port -le 0 -or $Port -gt 65535) { Die ("端口非法：" + $Port) }

# 前端开发服务器端口：命令行 > .env 的 HRENT_WEB_PORT > 8080
$envWebPort = 0
if ($env:HRENT_WEB_PORT) {
    try { $envWebPort = [int]$env:HRENT_WEB_PORT } catch { $envWebPort = 0 }
}
if ($CliWebPort -gt 0) {
    $WebPort = $CliWebPort
} elseif ($envWebPort -gt 0 -and $envWebPort -le 65535) {
    $WebPort = $envWebPort
} else {
    $WebPort = $DefaultWebPort
    # 没有显式指定时，沿用上次 dev 记录的端口（否则 stop/status 会去 8080 找）
    if (Test-Path -LiteralPath $WebPortFile) {
        $saved = (Get-Content -LiteralPath $WebPortFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        $savedPort = 0
        if ($saved -and [int]::TryParse(([string]$saved).Trim(), [ref]$savedPort) -and $savedPort -gt 0 -and $savedPort -le 65535) {
            $WebPort = $savedPort
        }
    }
}

switch ($Command) {
    "start"   { Get-JavaInfo; Start-App; exit 0 }
    # dev = 后端 + 前端开发服务器（热更新）：改前端代码即时生效，接口走 /hrent 代理
    "dev"     { Get-JavaInfo; Start-App; Start-WebDev; exit 0 }
    "run"     { Get-JavaInfo; Start-AppForeground; exit 0 }
    "build"   { Get-JavaInfo; Invoke-Build; exit 0 }
    "restart" { Get-JavaInfo; Stop-App; Start-App; exit 0 }
    # stop 必须连前端一起停，否则 dev server 继续占着端口，下次 dev 会报端口被占用
    "stop"    { Stop-App; Stop-WebDev; exit 0 }
    "status"  { Show-Status; exit 0 }
    "logs"    { Show-Logs; exit 0 }
    "build-web" { Invoke-BuildWeb; exit 0 }
}

} catch {
    $msg = $_.Exception.Message
    Write-Host ""
    Write-Host ("[hrent] 未处理的错误：" + $msg) -ForegroundColor Red
    # 只报行号，不打印整行源码：双击用户看的是「哪一步出问题」，不是脚本内部
    if ($_.InvocationInfo -and $_.InvocationInfo.ScriptLineNumber -gt 0) {
        Write-Host ("[hrent] 位置：scripts\hrent.ps1 第 " + $_.InvocationInfo.ScriptLineNumber + " 行") -ForegroundColor DarkGray
    }
    Write-ErrLog -Message ("未处理的错误：" + $_.Exception.ToString())
    Write-Host "[hrent] 详情已写入 logs\hrent.error.log" -ForegroundColor Yellow
    exit 2
}
