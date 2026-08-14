#!/bin/sh
# =====================================================================
# shopkeeper-agent 容器入口脚本
# 支持两种运行模式：
#   serve  - 启动 FastAPI 服务（默认）
#   build  - 构建元数据知识库后退出
# 用法：
#   docker run shopkeeper-agent              # 默认启动服务
#   docker run shopkeeper-agent build        # 构建知识库
# =====================================================================
set -e

MODE="${1:-serve}"
WAIT_FOR_EXTERNAL_SERVICES="${WAIT_FOR_EXTERNAL_SERVICES:-false}"

# 等待服务就绪的辅助函数
# 参数: 服务名、主机、端口
wait_for() {
    name="$1"
    host="$2"
    port="$3"
    max_wait=120
    waited=0
    echo "  等待 $name ($host:$port)..."
    # 用 Python 检测端口（容器内有 Python，避免依赖 nc）
    while ! python -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('$host', $port)); s.close()" 2>/dev/null; do
        sleep 2
        waited=$((waited + 2))
        if [ $waited -ge $max_wait ]; then
            echo "  [ERROR] $name 在 ${max_wait}s 内未就绪"
            exit 1
        fi
    done
    echo "  [OK] $name 已就绪（等待 ${waited}s）"
}

echo "========== shopkeeper-agent 启动 =========="
echo "运行模式: $MODE"
echo "Python 版本: $(python --version)"
echo "工作目录: $(pwd)"

# 打印关键配置（隐藏敏感信息）
echo "数据库配置:"
echo "  meta: ${DB_META_HOST:-host.docker.internal}:${DB_META_PORT:-3306}/${DB_META_DATABASE:-meta}"
echo "  dw:   ${DB_DW_HOST:-host.docker.internal}:${DB_DW_PORT:-3306}/${DB_DW_DATABASE:-mall}"
echo "Qdrant:    ${QDRANT_HOST:-qdrant}:${QDRANT_PORT:-6333}"
echo "Embedding: ${EMBEDDING_HOST:-embedding}:${EMBEDDING_PORT:-80}"
echo "ES:        ${ES_HOST:-elasticsearch}:${ES_PORT:-9200}"
echo "LLM:       ${LLM_MODEL_NAME:-qwen-max} @ ${LLM_BASE_URL:-https://dashscope.aliyuncs.com/compatible-mode/v1}"
echo "等待外部依赖: $WAIT_FOR_EXTERNAL_SERVICES"
echo "=========================================="

wait_for_external_services() {
    if [ "$WAIT_FOR_EXTERNAL_SERVICES" != "true" ]; then
        echo "跳过外部依赖等待（WAIT_FOR_EXTERNAL_SERVICES=false）"
        return
    fi

    echo "等待依赖服务就绪..."
    wait_for "Qdrant" "${QDRANT_HOST:-qdrant}" "${QDRANT_PORT:-6333}"
    wait_for "Embedding" "${EMBEDDING_HOST:-embedding}" "${EMBEDDING_PORT:-80}"
    wait_for "Elasticsearch" "${ES_HOST:-elasticsearch}" "${ES_PORT:-9200}"
}

case "$MODE" in
    serve)
        echo "[1/2] 检查启动前依赖..."
        wait_for_external_services

        echo "[2/2] 启动 FastAPI 服务（端口 8090）..."
        exec uv run fastapi dev main.py --host 0.0.0.0 --port 8090
        ;;
    build)
        echo "[1/2] 检查构建前依赖..."
        wait_for_external_services

        echo "[2/2] 构建元数据知识库..."
        exec uv run python -m app.scripts.build_meta_knowledge -c conf/meta_config.yaml
        ;;
    *)
        echo "未知模式: $MODE"
        echo "支持的模式: serve（默认）, build"
        exit 1
        ;;
esac
