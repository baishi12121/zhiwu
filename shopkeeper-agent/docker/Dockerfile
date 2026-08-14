# =====================================================================
# shopkeeper-agent Dockerfile
# 基于 Python 3.14 + uv，构建 FastAPI 智能客服应用镜像
# =====================================================================
FROM python:3.14-slim

# 设置时区和基础环境变量
ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv

# 安装系统依赖
# - curl: 健康检查
# - default-libmysqlclient-dev/pkg-config: asyncmy 编译需要
# - gcc: 编译 Python 扩展
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    gcc \
    pkg-config \
    default-libmysqlclient-dev \
    && rm -rf /var/lib/apt/lists/*

# 安装 uv（Python 包管理器）
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# 设置工作目录
WORKDIR /app

# 先拷贝依赖清单，利用 Docker 缓存层加速构建
COPY pyproject.toml uv.lock ./

# 使用 uv 同步依赖到 .venv（不安装项目本身，--no-install-project）
RUN uv sync --frozen --no-install-project --no-dev

# 拷贝项目源码
COPY . .

# 安装项目本身（不包含 dev 依赖）
RUN uv sync --frozen --no-dev

# 创建日志目录
RUN mkdir -p /app/logs

# 把 .venv 的 bin 加入 PATH，让 fastapi/uvicorn 命令可直接调用
ENV PATH="/app/.venv/bin:$PATH"

# 暴露 FastAPI 服务端口
EXPOSE 8090

# 健康检查：每 30 秒检查一次 /docs 是否可访问
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8090/docs || exit 1

# 入口脚本支持两种模式：
# - serve（默认）：启动 FastAPI 服务
# - build：构建元数据知识库后退出
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["serve"]
