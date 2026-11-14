#!/usr/bin/env bash
# =============================================================================
# 房屋租赁管理系统 · 一键启动脚本
# 适用平台：Linux / macOS / WSL / Git Bash（Windows 原生请用 start.cmd）
#
# 设计要点：
#   1. 端口只认 HRENT_PORT，绝不读 SERVER_PORT —— 云平台/CI 常注入该变量，
#      实测曾把 8888 改成 52051 导致启动失败。
#   2. .env 逐行解析而非 source —— 数据库 URL 含 & 与 ?，source 会当成后台符号。
#   3. 脚本内不含任何口令，凭据全部走 .env / 系统环境变量。
#   4. JDK 9+ 才追加 --add-opens；JDK 8 传入会直接启动失败，故按版本分支。
# =============================================================================
set -euo pipefail

APP_NAME="hrent"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$ROOT_DIR/hrent-api"
WEB_DIR="$ROOT_DIR/hrent-web"
LOG_DIR="$ROOT_DIR/logs"
JAR_REL="target/hrent-0.0.1-SNAPSHOT.jar"
JAR_PATH="$API_DIR/$JAR_REL"
PID_FILE="$LOG_DIR/$APP_NAME.pid"
LOG_FILE="$LOG_DIR/$APP_NAME.log"
DEFAULT_PORT=8888
HEALTH_PATH="/sysCommon/getBaseInfo"

# 前端（hrent-web）：构建产物目录 + 开发服务器进程
WEB_DIST_DIR="$WEB_DIR/dist"
WEB_INDEX="$WEB_DIST_DIR/index.html"
WEB_PID_FILE="$LOG_DIR/$APP_NAME-web.pid"
# 记下 dev 实际用的端口：否则「HRENT_WEB_PORT=8099 ./start.sh dev」之后
# 直接执行 ./start.sh stop，会用默认 8080 去找进程，结果报「未运行」却把 8099 留在那里。
WEB_PORT_FILE="$LOG_DIR/$APP_NAME-web.port"
WEB_LOG_FILE="$LOG_DIR/$APP_NAME-web.log"
DEFAULT_WEB_PORT=8080
WEB_PORT="$DEFAULT_WEB_PORT"
WEB_LOGS=0

CMD="start"
CLI_PORT=""
CLI_WEB_PORT=""
NO_BUILD=0
FORCE_BUILD=0
WITH_TESTS=0
FOLLOW=0
ENV_FILE="$ROOT_DIR/.env"

# 业务配置默认值（会被 .env / 系统环境变量覆盖）
HRENT_PORT=""
DB_URL=""
DB_USERNAME=""
DB_PASSWORD=""
TOKEN_SECRET=""
WECHAT_APP_ID=""
WECHAT_APP_SECRET=""
JAVA_OPTS=""

# ---------------------------------------------------------------------------
# 输出
# ---------------------------------------------------------------------------
if [ -t 1 ]; then
    C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_RST=$'\033[0m'
else
    C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''; C_RST=''
fi
info() { printf '%s[hrent]%s %s\n' "$C_INFO" "$C_RST" "$*"; }
ok()   { printf '%s[hrent]%s %s\n' "$C_OK"   "$C_RST" "$*"; }
warn() { printf '%s[hrent]%s %s\n' "$C_WARN" "$C_RST" "$*" >&2; }
die()  { printf '%s[hrent]%s %s\n' "$C_ERR"  "$C_RST" "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
房屋租赁管理系统 · 一键启动脚本（Linux / macOS / WSL / Git Bash）

用法：./start.sh <命令> [选项]

命令：
  start        构建（如需要）并后台启动后端；前端已构建时由同一进程提供页面
  dev          后端 + 前端开发服务器（热更新，改前端代码即时生效）
  run          仅前台启动后端，Ctrl+C 停止（排查启动问题用）
  stop         停止后端与前端
  restart      重启后端
  status       查看后端 / 前端状态
  logs [-f] [--web]  查看日志（--web 看前端，-f 持续跟踪）
  build        仅构建后端 jar
  build-web    仅构建前端到 hrent-web/dist
  help         显示本帮助

选项：
  --port <n>       覆盖后端端口（默认 8888，也可在 .env 设 HRENT_PORT）
  --web-port <n>   前端开发服务器端口（默认 8080，也可在 .env 设 HRENT_WEB_PORT）
  --web            与 logs 搭配，查看前端日志
  --no-build       跳过构建，直接用现有 jar
  --rebuild        强制重新构建
  --with-tests     构建时执行单元测试（默认跳过）
  --env <file>     指定环境变量文件（默认项目根 .env）

前端的两种用法：
  1) 生产/演示 —— 单进程、不需要 Node 运行时
       ./start.sh build-web     构建一次
       ./start.sh               只起后端，页面由后端托管
       打开 http://localhost:8888/hrent/web/index.html
  2) 开发 —— 热更新
       ./start.sh dev           后端 + 前端开发服务器
       打开 http://localhost:8080/

示例：
  cp .env.example .env && vi .env     # 首次先配数据库与密钥
  ./start.sh                          # 一键启动
  ./start.sh dev                      # 带前端热更新
  ./start.sh logs -f                  # 看日志
  ./start.sh build-web                # 构建前端
EOF
    exit 0
}

# ---------------------------------------------------------------------------
# .env 解析：逐行 KEY=VALUE，不执行任何 shell 展开
# ---------------------------------------------------------------------------
load_env_file() {
    local file="$1" line key val
    [ -f "$file" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"                                   # 兼容 CRLF
        case "$line" in ''|'#'*) continue ;; esac
        case "$line" in *=*) : ;; *) continue ;; esac
        key="${line%%=*}"; val="${line#*=}"
        key="$(printf '%s' "$key" | tr -d '[:space:]')"
        case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac     # 非法键名跳过
        case "$val" in                                          # 去掉成对引号
            \"*\") val="${val#\"}"; val="${val%\"}" ;;
            \'*\') val="${val#\'}"; val="${val%\'}" ;;
        esac
        export "$key=$val"
    done < "$file"
}

# ---------------------------------------------------------------------------
# Java 探测与版本判断
# ---------------------------------------------------------------------------
JAVA_BIN=""
JAVA_MAJOR=0
JVM_ARGS=()
APP_ARGS=()

detect_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_BIN="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
    else
        die "未找到 java。请安装 JDK 8 及以上（推荐 JDK 17），或设置 JAVA_HOME 后重试。"
    fi

    local ver major second
    ver="$("$JAVA_BIN" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/')"
    major="${ver%% *}"
    second="${ver##* }"
    if [ "$major" = "1" ]; then            # 1.8.0_202 → 8
        major="$second"
    fi
    case "$major" in ''|*[!0-9]*) major=0 ;; esac
    JAVA_MAJOR="$major"

    if [ "$JAVA_MAJOR" -lt 8 ]; then
        die "Java 版本过低（major=$JAVA_MAJOR），最低 JDK 8，推荐 JDK 17。"
    fi
    ok "$("$JAVA_BIN" -version 2>&1 | head -n 1) → $JAVA_BIN"
}

build_jvm_args() {
    JVM_ARGS=()
    local opt
    for opt in ${JAVA_OPTS:-}; do
        JVM_ARGS+=("$opt")
    done
    if [ ${#JVM_ARGS[@]} -eq 0 ]; then
        JVM_ARGS+=("-Dfile.encoding=UTF-8")
    fi
    # 时区固化：Java 侧统一北京时间，不依赖宿主机时区
    case " ${JVM_ARGS[*]} " in
        *" -Duser.timezone="*) : ;;
        *) JVM_ARGS+=("-Duser.timezone=Asia/Shanghai") ;;
    esac
    if [ "$JAVA_MAJOR" -ge 9 ]; then
        JVM_ARGS+=("--add-opens" "java.base/java.lang=ALL-UNNAMED")
        JVM_ARGS+=("--add-opens" "java.base/java.math=ALL-UNNAMED")
    fi
}

# ---------------------------------------------------------------------------
# 平台判定
# Git Bash / MSYS2 / Cygwin 下：bash 的 $! 是 MSYS 包装进程 PID，不是 Windows
# 真实 PID；kill 与 ps -o 也不认 Windows PID。故存活判断走 jps / 端口反查，
# 停止走 taskkill。这是本脚本唯一需要区分平台的地方。
# ---------------------------------------------------------------------------
OS_KIND="unix"
case "$(uname -s 2>/dev/null || echo unknown)" in
    MINGW*|MSYS*|CYGWIN*) OS_KIND="windows" ;;
esac

# 判断某 PID 是否为 Java 进程（端口反查兜底时必须校验，否则会把占用端口的
# 任意进程误认成我们的服务 —— 实测被一个 Python 监听进程骗过）
is_java_process() {
    local pid="$1"
    if [ "$OS_KIND" = "windows" ]; then
        if command -v tasklist >/dev/null 2>&1; then
            tasklist //FI "PID eq $pid" //FO CSV //NH 2>/dev/null | grep -qi "java"
            return $?
        fi
        return 1
    fi
    ps -p "$pid" -o comm= 2>/dev/null | grep -qi "java"
    return $?
}

# 找到本项目 jar 的进程 PID（jps 精确匹配优先，端口反查仅作兜底且必须校验进程名）
find_app_pid() {
    local jps_bin="" found=""
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jps" ]; then
        jps_bin="$JAVA_HOME/bin/jps"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jps.exe" ]; then
        jps_bin="$JAVA_HOME/bin/jps.exe"
    elif command -v jps >/dev/null 2>&1; then
        jps_bin="$(command -v jps)"
    fi
    if [ -n "$jps_bin" ]; then
        found="$("$jps_bin" -l 2>/dev/null | awk '/hrent-0\.0\.1-SNAPSHOT\.jar/ {print $1; exit}')"
        if [ -n "$found" ]; then
            printf '%s' "$found"
            return 0
        fi
    fi
    # 兜底：按监听端口反查（jps 缺失，或 JVM 由其他 JDK 启动时）
    if command -v lsof >/dev/null 2>&1; then
        found="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1)"
    fi
    if [ -z "$found" ] && command -v netstat >/dev/null 2>&1; then
        found="$(netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]].*LISTEN" | awk '{print $NF}' | head -n 1)"
        case "$found" in ''|*[!0-9]*) found="" ;; esac
    fi
    if [ -n "$found" ] && is_java_process "$found"; then
        printf '%s' "$found"
        return 0
    fi
    printf ''
}

# 发送停止信号：Windows 用 taskkill，其余用 kill
# 注意：Windows 没有 SIGTERM 语义，taskkill 不带 /F 对无窗口的控制台 Java 进程
# 不生效（实测会一直挂到超时），故直接 /F 结束；数据一致性由 MySQL 事务保证。
kill_pid() {
    local pid="$1" force="${2:-0}"
    if [ "$OS_KIND" = "windows" ]; then
        if command -v taskkill >/dev/null 2>&1; then
            taskkill //F //PID "$pid" >/dev/null 2>&1 || true
            return 0
        fi
        kill "$pid" 2>/dev/null || true
        return 0
    fi
    if [ "$force" = "1" ]; then
        kill -9 "$pid" 2>/dev/null || true
    else
        kill -TERM "$pid" 2>/dev/null || true
    fi
    return 0
}

# ---------------------------------------------------------------------------
# 端口占用检测（lsof → ss → netstat 依次降级）
# ---------------------------------------------------------------------------
port_in_use() {
    local port="$1"
    if command -v lsof >/dev/null 2>&1; then
        if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then return 0; else return 1; fi
    fi
    if command -v ss >/dev/null 2>&1; then
        if ss -ltn 2>/dev/null | awk 'NR>1 {print $4}' | grep -qE "[:.]${port}$"; then return 0; else return 1; fi
    fi
    if command -v netstat >/dev/null 2>&1; then
        if netstat -an 2>/dev/null | grep -E "[:.]${port}[[:space:]].*LISTEN" >/dev/null 2>&1; then return 0; else return 1; fi
    fi
    return 1
}

port_owner_hint() {
    local port="$1"
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {print "  PID " $2 "  " $1}' | head -n 3
        return 0
    fi
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp 2>/dev/null | grep -E "[:.]${port}[[:space:]]" | head -n 3
        return 0
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -ano 2>/dev/null | grep -E "[:.]${port}[[:space:]].*LISTEN" \
            | awk '{print "  PID " $NF}' | sort -u | head -n 3
    fi
    return 0
}

health_check() {
    local url="http://127.0.0.1:${PORT}/hrent${HEALTH_PATH}"
    export no_proxy="127.0.0.1,localhost${no_proxy:+,$no_proxy}"
    export NO_PROXY="$no_proxy"
    if command -v curl >/dev/null 2>&1; then
        if curl -fsS -m 3 -X POST -H 'Content-Type: application/json' -d '{}' "$url" >/dev/null 2>&1; then
            return 0
        fi
        return 1
    fi
    if command -v wget >/dev/null 2>&1; then
        if wget -q -T 3 -O /dev/null --header='Content-Type: application/json' --post-data='{}' "$url" >/dev/null 2>&1; then
            return 0
        fi
        return 1
    fi
    return 1
}

# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------
jar_is_fresh() {
    [ -f "$JAR_PATH" ] || return 1
    local newer
    newer="$(find "$API_DIR/src" "$API_DIR/pom.xml" -newer "$JAR_PATH" -type f 2>/dev/null | head -n 1)"
    [ -z "$newer" ]
}

do_build() {
    cd "$API_DIR"
    [ -f ./mvnw ] || die "缺少 $API_DIR/mvnw，Maven Wrapper 未入库。"
    chmod +x ./mvnw 2>/dev/null || true

    BUILD_ARGS=("-B" "clean" "package")
    if [ "$WITH_TESTS" -eq 0 ]; then
        BUILD_ARGS+=("-DskipTests")
    fi

    info "开始构建：./mvnw ${BUILD_ARGS[*]}"
    info "首次构建需联网下载 Maven 与依赖，请耐心等待..."
    if ! ./mvnw "${BUILD_ARGS[@]}"; then
        die "构建失败。离线环境可在有网机器构建后拷贝 target/*.jar 过来，再用 --no-build 启动。"
    fi
    [ -f "$JAR_PATH" ] || die "构建结束但未找到产物：$JAR_PATH"
    ok "构建完成：$JAR_REL（$(du -h "$JAR_PATH" | awk '{print $1}')）"
}

do_build_web() {
    command -v npm >/dev/null 2>&1 || die "未找到 npm，无法构建前端。请安装 Node.js 16+（推荐 18/20 LTS）。"
    cd "$WEB_DIR"
    if [ ! -d node_modules ]; then
        info "安装前端依赖（首次较慢）..."
        npm install --no-audit --no-fund
    fi
    local major
    major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
    case "$major" in ''|*[!0-9]*) major=0 ;; esac
    info "构建前端（Node major=$major）..."
    if [ "$major" -ge 17 ]; then
        # webpack 3 依赖的 md4 在 Node 17+ 被 OpenSSL 3 移除，必须加该开关
        NODE_OPTIONS=--openssl-legacy-provider npm run build
    else
        npm run build
    fi
    ok "前端构建完成：hrent-web/dist（整目录交给 nginx 或任意静态服务器）"
}

# ---------------------------------------------------------------------------
# 启停
# ---------------------------------------------------------------------------
export_spring_env() {
    if [ -n "$DB_URL" ]; then export SPRING_DATASOURCE_URL="$DB_URL"; fi
    if [ -n "$DB_USERNAME" ]; then export SPRING_DATASOURCE_USERNAME="$DB_USERNAME"; fi
    if [ -n "$DB_PASSWORD" ]; then export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"; fi
    if [ -n "$TOKEN_SECRET" ]; then export TOKEN_SECRET="$TOKEN_SECRET"; fi
    if [ -n "$WECHAT_APP_ID" ]; then export WECHAT_APP_ID="$WECHAT_APP_ID"; fi
    if [ -n "$WECHAT_APP_SECRET" ]; then export WECHAT_APP_SECRET="$WECHAT_APP_SECRET"; fi
    return 0
}

prepare_runtime() {
    [ -d "$API_DIR" ] || die "缺少 $API_DIR，请在项目根目录运行本脚本。"
    mkdir -p "$LOG_DIR"
}

ensure_ready() {
    prepare_runtime
    export_spring_env
    if [ "$NO_BUILD" -eq 1 ]; then
        [ -f "$JAR_PATH" ] || die "指定了 --no-build，但 $JAR_REL 不存在。请先执行 ./start.sh build"
    elif [ "$FORCE_BUILD" -eq 1 ]; then
        do_build
    elif ! jar_is_fresh; then
        do_build
    else
        ok "jar 已是最新，跳过构建（加 --rebuild 可强制重建）"
    fi
}

start_bg() {
    ensure_ready
    local running_pid
    running_pid="$(find_app_pid)"
    if [ -n "$running_pid" ]; then
        die "服务已在运行（PID $running_pid）。如需重启请执行 ./start.sh restart"
    fi
    rm -f "$PID_FILE"
    if port_in_use "$PORT"; then
        warn "端口 $PORT 已被占用，占用者："
        port_owner_hint "$PORT" >&2 || true
        die "请先释放端口，或用 --port <其他端口> 指定。"
    fi

    build_jvm_args
    APP_ARGS=("--server.port=${PORT}")
    # 前端产物存在时告知后端，由 WebMvcConfig 映射到 /hrent/web/**：
    # 单进程同时提供页面与接口，服务器上不必再单独装 nginx。
    if web_built; then export HRENT_WEB_DIR="$WEB_DIST_DIR"; fi
    cd "$API_DIR"

    # 日志轮转：上一次日志留档为 .log.1，本次从空文件开始，避免无限增长
    if [ -f "$LOG_FILE" ]; then
        mv -f "$LOG_FILE" "$LOG_FILE.1" 2>/dev/null || true
    fi
    : > "$LOG_FILE"
    local start_line=0

    info "启动中：端口 $PORT，上下文 /hrent，日志 logs/${APP_NAME}.log"
    if command -v nohup >/dev/null 2>&1; then
        nohup "$JAVA_BIN" "${JVM_ARGS[@]}" -jar "$JAR_PATH" "${APP_ARGS[@]}" >>"$LOG_FILE" 2>&1 &
    else
        "$JAVA_BIN" "${JVM_ARGS[@]}" -jar "$JAR_PATH" "${APP_ARGS[@]}" >>"$LOG_FILE" 2>&1 &
    fi
    # 先记录启动时的 shell PID 作为占位，拿到真实 PID 后立刻覆盖
    echo "$!" > "$PID_FILE"

    local i=0 dotted=0 pid=""
    while [ "$i" -lt 60 ]; do
        pid="$(find_app_pid)"
        if [ -n "$pid" ]; then
            echo "$pid" > "$PID_FILE"
        fi
        if health_check; then
            if [ "$dotted" -eq 1 ]; then printf '\n'; fi
            ok "启动成功（PID ${pid:-未知}）"
            ok "接口地址：http://localhost:${PORT}/hrent"
            if web_built; then
                ok "页面地址：http://localhost:${PORT}/hrent/web/index.html"
            else
                warn "前端尚未构建：执行 ./start.sh build-web（后端托管），或 ./start.sh dev（热更新）"
            fi
            ok "健康检查：http://localhost:${PORT}/hrent${HEALTH_PATH}（POST）"
            return 0
        fi
        # 快速失败：本次启动日志里出现致命错误就不必等满 60 秒
        if tail -n "+$((start_line + 1))" "$LOG_FILE" 2>/dev/null \
            | grep -qE "APPLICATION FAILED TO START|Web server failed to start|was already in use|Access denied for user|Communications link failure|Unknown database"; then
            if [ "$dotted" -eq 1 ]; then printf '\n'; fi
            warn "启动失败，最近 40 行日志："
            tail -n 40 "$LOG_FILE" >&2 || true
            rm -f "$PID_FILE"
            die "请按日志提示修正后重试（常见原因：数据库未启动、账号密码错误、端口被占用）。"
        fi
        i=$((i + 1))
        printf '.'
        dotted=1
        sleep 1
    done
    printf '\n'
    warn "等待 60 秒仍未通过健康检查（PID ${pid:-未知}）。"
    warn "执行 ./start.sh logs 查看日志，或 ./start.sh stop 停止。"
    exit 1
}

run_fg() {
    ensure_ready
    build_jvm_args
    APP_ARGS=("--server.port=${PORT}")
    cd "$API_DIR"
    info "前台启动（Ctrl+C 停止）"
    exec "$JAVA_BIN" "${JVM_ARGS[@]}" -jar "$JAR_PATH" "${APP_ARGS[@]}"
}

# ---------------------------------------------------------------------------
# 前端：构建产物 / 开发服务器
# ---------------------------------------------------------------------------
web_built() { [ -f "$WEB_INDEX" ]; }

is_node_process() {
    local pid="$1"
    [ -n "$pid" ] || return 1
    if [ "$OS_KIND" = "windows" ]; then
        if command -v tasklist >/dev/null 2>&1; then
            tasklist //FI "PID eq $pid" //FO CSV //NH 2>/dev/null | grep -qi "node"
            return $?
        fi
        return 1
    fi
    if [ -r "/proc/$pid/comm" ]; then
        grep -qi '^node' "/proc/$pid/comm" 2>/dev/null
        return $?
    fi
    ps -p "$pid" -o comm= 2>/dev/null | grep -qi "node"
    return $?
}

# 前端真实 PID：先信 PID 文件，其次按监听端口反查。
# Git Bash 下 `$!` 给出的是 MSYS 中间进程号，与真实 node PID 不同，
# 端口反查是必需的兜底——否则 stop 之后 8080 仍被占用。
# 注意：所有命令替换都要带 `|| true`。脚本是 `set -euo pipefail`，
# 管道末尾的 head 提前关闭会让 sort/netstat 收到 SIGPIPE（141），
# 赋值语句因此返回非 0，函数会在赋值处直接退出、返回空 PID。
find_web_pid() {
    local pid=""
    if [ -f "$WEB_PID_FILE" ]; then
        pid="$(cat "$WEB_PID_FILE" 2>/dev/null || true)"
        if web_pid_is_ours "$pid" || is_node_process "$pid"; then
            printf '%s' "$pid"
            return 0
        fi
    fi
    pid="$(port_owner_pid)"
    if [ -n "$pid" ]; then
        printf '%s' "$pid"
        return 0
    fi
    printf ''
}

# 按监听端口反查 node PID（不做 PID 文件判断，供 find_web_pid 与启动后就绪回填共用）
port_owner_pid() {
    local pid=""
    if command -v lsof >/dev/null 2>&1; then
        pid="$(lsof -nP -iTCP:"$WEB_PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
    fi
    if [ -z "$pid" ] && command -v netstat >/dev/null 2>&1; then
        pid="$(netstat -ano 2>/dev/null | grep -E "[:.]${WEB_PORT}[[:space:]].*LISTEN" | awk '{print $NF}' | head -n 1 || true)"
        case "$pid" in ''|*[!0-9]*) pid="" ;; esac
    fi
    if [ -n "$pid" ] && { is_node_process "$pid" || web_pid_is_ours "$pid"; }; then
        printf '%s' "$pid"
        return 0
    fi
    printf ''
}

# 判断 PID 是否为本项目的前端进程。Linux 有 /proc 时校验命令行，避免 PID 复用误判；
# 无 /proc 的平台（macOS / Git Bash）只能以 PID 文件为凭据。
web_pid_is_ours() {
    local pid="$1"
    [ -n "$pid" ] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    if [ -r "/proc/$pid/cmdline" ]; then
        if tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -qE 'webpack-dev-server|hrent-web'; then
            return 0
        fi
        return 1
    fi
    return 0
}

# 前端页面探活：必须校验 HTTP 200 并禁用代理。
# 只 curl -f 会被代理返回的 502 骗成「就绪」（health_check 早期版本踩过同类坑）。
web_up() {
    local port="$1"
    export no_proxy="127.0.0.1,localhost${no_proxy:+,$no_proxy}"
    export NO_PROXY="$no_proxy"
    command -v curl >/dev/null 2>&1 || return 1
    [ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 --noproxy '*' "http://127.0.0.1:${port}/" 2>/dev/null)" = "200" ]
}

start_web_dev() {
    command -v npm >/dev/null 2>&1 \
        || die "未找到 npm。前端开发服务器需要 Node.js 16+（推荐 18/20 LTS）；或用 ./start.sh build-web + 后端托管。"

    # 幂等：已在运行就不重复起（重复起会因端口占用直接失败）
    local running=""
    running="$(find_web_pid)"
    if [ -n "$running" ]; then
        ok "前端开发服务器已在运行（PID $running）"
        echo "$WEB_PORT" > "$WEB_PORT_FILE"
        info "页面地址：http://localhost:${WEB_PORT}/"
        return 0
    fi
    rm -f "$WEB_PID_FILE"

    if [ ! -d "$WEB_DIR/node_modules" ]; then
        info "首次运行，安装前端依赖（较慢，视网络 2-5 分钟）..."
        ( cd "$WEB_DIR" && npm install --no-audit --no-fund ) \
            || die "npm install 失败。检查网络或 npm 镜像（npm config get registry）。"
    fi

    if port_in_use "$WEB_PORT"; then
        warn "前端端口 $WEB_PORT 已被占用，占用者："
        port_owner_hint "$WEB_PORT" >&2 || true
        die "请释放该端口，或用 --web-port <其他端口> 指定。"
    fi

    if [ -f "$WEB_LOG_FILE" ]; then mv -f "$WEB_LOG_FILE" "$WEB_LOG_FILE.1" 2>/dev/null || true; fi

    info "启动前端开发服务器：端口 $WEB_PORT，/hrent/** 代理到 http://127.0.0.1:${PORT}（首次编译约 30-60 秒）"
    # 环境变量只作用于这条子 shell，不污染主脚本的 PORT：
    #   NODE_OPTIONS - webpack 3 依赖的 md4 在 Node 17+ 的 OpenSSL 3 中被移除；
    #   PORT         - 固定成刚校验过的端口，否则 portfinder 会静默改用下一个端口。
    # 必须 nohup：脚本退出时非交互 shell 会给后台 job 发 SIGHUP，
    # 少了它 dev server 会随 start.sh 一起消失（表现为「已就绪」之后立刻连不上）。
    if command -v nohup >/dev/null 2>&1; then
        ( cd "$WEB_DIR" && exec nohup env \
            NODE_OPTIONS="--openssl-legacy-provider" \
            PORT="$WEB_PORT" \
            HRENT_API_BASE="http://127.0.0.1:${PORT}" \
            npm run dev ) >>"$WEB_LOG_FILE" 2>&1 &
    else
        ( cd "$WEB_DIR" && exec env \
            NODE_OPTIONS="--openssl-legacy-provider" \
            PORT="$WEB_PORT" \
            HRENT_API_BASE="http://127.0.0.1:${PORT}" \
            npm run dev ) >>"$WEB_LOG_FILE" 2>&1 &
    fi
    echo "$!" > "$WEB_PID_FILE"

    local i=0 dotted=0 cur=""
    while [ "$i" -lt 180 ]; do
        cur="$(cat "$WEB_PID_FILE" 2>/dev/null || true)"
        if [ -z "$cur" ] || ! kill -0 "$cur" 2>/dev/null; then
            warn "前端进程在启动阶段退出，最近 30 行日志："
            tail -n 30 "$WEB_LOG_FILE" >&2 || true
            rm -f "$WEB_PID_FILE"
            die "前端启动失败。常见原因：依赖未装全、端口被占用、Node 版本与 webpack 3 不兼容。"
        fi
        if web_up "$WEB_PORT"; then
            if [ "$dotted" -eq 1 ]; then printf '\n'; fi
            # 把 PID 文件回填成真正的监听进程：`$!` 是 npm/MSYS 的包装进程，
            # 下次 stop 只拿它去 kill -0 会判「不是 node」，从而找不到进程。
            local real=""
            real="$(port_owner_pid)"
            if [ -n "$real" ]; then
                echo "$real" > "$WEB_PID_FILE"
                cur="$real"
            fi
            echo "$WEB_PORT" > "$WEB_PORT_FILE"
            ok "前端已就绪（PID $cur）"
            ok "页面地址：http://localhost:${WEB_PORT}/"
            return 0
        fi
        i=$((i + 1)); printf '.'; dotted=1
        sleep 1
    done
    printf '\n'
    warn "等待 180 秒仍未就绪，执行 ./start.sh logs --web 查看前端日志。"
    return 1
}

stop_web_dev() {
    local pid=""
    pid="$(find_web_pid)"
    if [ -z "$pid" ]; then
        # 没有 PID 也不按命令行 pkill 匹配 node：会误杀正在 `npm run build` 的进程
        info "前端开发服务器未运行。"
        rm -f "$WEB_PID_FILE" "$WEB_PORT_FILE"
        return 0
    fi
    info "停止前端开发服务器（PID $pid）..."
    if [ "$OS_KIND" = "windows" ]; then
        # npm.cmd → cmd.exe → node.exe 三层，必须按进程树结束
        taskkill //F //T //PID "$pid" >/dev/null 2>&1 || true
    else
        # npm 会派生 node，只杀父进程会留下占端口的孤儿：先杀进程组，再补一刀
        kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
        local i=0
        while [ "$i" -lt 10 ]; do
            if ! kill -0 "$pid" 2>/dev/null; then break; fi
            i=$((i + 1)); sleep 1
        done
        if kill -0 "$pid" 2>/dev/null; then
            kill -KILL "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
            sleep 1
        fi
    fi
    rm -f "$WEB_PID_FILE" "$WEB_PORT_FILE"
    if port_in_use "$WEB_PORT"; then
        warn "端口 $WEB_PORT 仍在监听，可能残留了子进程："
        port_owner_hint "$WEB_PORT" >&2 || true
        return 1
    fi
    ok "前端已停止。"
}

status_web() {
    local pid=""
    pid="$(find_web_pid)"
    if [ -n "$pid" ]; then
        if web_up "$WEB_PORT"; then
            ok "前端开发服务器：运行中（PID $pid）→ http://localhost:${WEB_PORT}/"
        else
            warn "前端开发服务器：进程在（PID $pid），页面暂未就绪 → http://localhost:${WEB_PORT}/"
        fi
        return 0
    fi
    if web_built; then
        # 只有后端在监听时这个地址才可达，否则只说明「已构建」
        if port_in_use "$PORT"; then
            ok "前端：已构建，由后端托管 → http://localhost:${PORT}/hrent/web/index.html"
        else
            info "前端：已构建（hrent-web/dist 存在），后端启动后由 /hrent/web/ 提供"
        fi
        return 0
    fi
    warn "前端：未构建（执行 ./start.sh build-web，或 ./start.sh dev 起开发服务器）"
}

stop_app() {
    local pid
    pid="$(find_app_pid)"
    if [ -z "$pid" ]; then
        info "服务未在运行。"
        if [ -f "$PID_FILE" ]; then
            rm -f "$PID_FILE"
            info "已清理残留 PID 文件。"
        fi
        return 0
    fi
    info "停止服务（PID $pid）..."
    kill_pid "$pid" 0

    # Windows 已直接强杀，只需短等待；Unix 给 SIGTERM 留 15 秒优雅退出
    local max_wait=15
    if [ "$OS_KIND" = "windows" ]; then max_wait=6; fi
    local i=0
    while [ "$i" -lt "$max_wait" ]; do
        if [ -z "$(find_app_pid)" ]; then break; fi
        i=$((i + 1))
        sleep 1
    done
    if [ -n "$(find_app_pid)" ]; then
        warn "优雅停止超时，强制结束。"
        kill_pid "$pid" 1
        sleep 2
    fi
    if [ -n "$(find_app_pid)" ]; then
        warn "进程仍在运行（PID $pid）。手工处理："
        if [ "$OS_KIND" = "windows" ]; then
            warn "  taskkill //F //PID $pid"
        else
            warn "  kill -9 $pid"
        fi
        return 1
    fi
    rm -f "$PID_FILE"
    ok "已停止。"
}

status_app() {
    local pid
    pid="$(find_app_pid)"
    if [ -n "$pid" ]; then
        ok "运行中（PID $pid）"
        # 以健康检查为准：端口是否监听受启动阶段与 TIME_WAIT 干扰，只作辅助信息
        if health_check; then
            ok "健康检查：通过 → http://localhost:${PORT}/hrent${HEALTH_PATH}"
            if port_in_use "$PORT"; then
                ok "端口 $PORT 正在监听"
            fi
        else
            warn "健康检查：未通过（执行 ./start.sh logs 排查）"
            if port_in_use "$PORT"; then
                warn "端口 $PORT 正在监听，但接口无响应"
            else
                warn "端口 $PORT 未监听"
            fi
        fi
        return 0
    fi
    warn "后端：未运行"
    if [ -f "$PID_FILE" ]; then
        warn "存在残留 PID 文件（上次可能有异常退出）：$PID_FILE"
    fi
    return 1
}

show_logs() {
    local file="$LOG_FILE"
    if [ "$WEB_LOGS" -eq 1 ]; then file="$WEB_LOG_FILE"; fi
    [ -f "$file" ] || die "日志文件不存在：$file（服务可能从未启动）"
    if [ "$FOLLOW" -eq 1 ]; then
        info "跟踪日志（Ctrl+C 退出）：$file"
        tail -n 100 -f "$file"
    else
        tail -n 200 "$file"
    fi
}

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
while [ $# -gt 0 ]; do
    case "$1" in
        start|run|stop|restart|status|logs|build|build-web|dev)
            CMD="$1"; shift ;;
        help|-h|--help)
            usage ;;
        --port)
            [ $# -ge 2 ] || die "参数 --port 缺少值"
            CLI_PORT="$2"; shift 2 ;;
        --port=*)
            CLI_PORT="${1#*=}"; shift ;;
        --no-build)
            NO_BUILD=1; shift ;;
        --web-port)
            [ $# -ge 2 ] || die "参数 --web-port 缺少值"
            CLI_WEB_PORT="$2"; shift 2 ;;
        --web-port=*)
            CLI_WEB_PORT="${1#*=}"; shift ;;
        --web)
            WEB_LOGS=1; shift ;;
        --rebuild)
            FORCE_BUILD=1; shift ;;
        --with-tests)
            WITH_TESTS=1; shift ;;
        --env)
            [ $# -ge 2 ] || die "参数 --env 缺少值"
            ENV_FILE="$2"; shift 2 ;;
        --env=*)
            ENV_FILE="${1#*=}"; shift ;;
        -f|--follow)
            FOLLOW=1; shift ;;
        *)
            die "未知参数：$1（执行 ./start.sh help 查看用法）" ;;
    esac
done

load_env_file "$ENV_FILE"
PORT="${CLI_PORT:-${HRENT_PORT:-$DEFAULT_PORT}}"
# 前端端口：命令行 > .env 的 HRENT_WEB_PORT > 上次 dev 记录的端口 > 8080
WEB_PORT="${CLI_WEB_PORT:-${HRENT_WEB_PORT:-$DEFAULT_WEB_PORT}}"
if [ -z "$CLI_WEB_PORT" ] && [ -z "${HRENT_WEB_PORT:-}" ] && [ -f "$WEB_PORT_FILE" ]; then
    _p="$(cat "$WEB_PORT_FILE" 2>/dev/null || true)"
    case "$_p" in ''|*[!0-9]*) : ;; *) WEB_PORT="$_p" ;; esac
fi
case "$PORT" in ''|*[!0-9]*) die "端口非法：$PORT（必须是数字）" ;; esac
case "$WEB_PORT" in ''|*[!0-9]*) die "前端端口非法：$WEB_PORT（必须是数字）" ;; esac

case "$CMD" in
    start|run|build|restart|dev)
        detect_java ;;
esac

case "$CMD" in
    start)     start_bg ;;
    # dev = 后端 + 前端开发服务器（热更新）
    dev)       start_bg; start_web_dev ;;
    run)       run_fg ;;
    # stop 必须连前端一起停，否则 dev server 继续占着端口，下次 dev 会报端口被占用
    stop)
        rc=0
        stop_app || rc=$?
        stop_web_dev || rc=$?
        exit "$rc"
        ;;
    restart)   stop_app; start_bg ;;
    status)
        rc=0
        status_app || rc=$?
        status_web
        exit "$rc"
        ;;
    logs)      show_logs ;;
    build)     cd "$API_DIR" && do_build ;;
    build-web) do_build_web ;;
esac
