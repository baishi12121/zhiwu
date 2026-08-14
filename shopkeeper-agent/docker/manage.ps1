# =====================================================================
# shopkeeper-agent 容器化运维脚本
# 用法：
#   .\docker\manage.ps1 build      # 构建镜像
#   .\docker\manage.ps1 up         # 启动所有服务（ES + Qdrant + Embedding + Agent）
#   .\docker\manage.ps1 down       # 停止所有服务
#   .\docker\manage.ps1 logs       # 查看 Agent 日志
#   .\docker\manage.ps1 build-kb   # 构建元数据知识库（一次性）
#   .\docker\manage.ps1 restart    # 重启 Agent 服务
# =====================================================================
param(
    [Parameter(Position=0)]
    [ValidateSet("build", "up", "down", "logs", "build-kb", "restart", "status")]
    [string]$Action = "up"
)

$ScriptDir = $PSScriptRoot
$ComposeFile = Join-Path $ScriptDir "docker-compose.yaml"

switch ($Action) {
    "build" {
        Write-Host "构建 shopkeeper-agent 镜像..." -ForegroundColor Cyan
        docker compose -f $ComposeFile build shopkeeper-agent
    }
    "up" {
        Write-Host "启动所有服务（ES + Qdrant + Embedding + shopkeeper-agent）..." -ForegroundColor Cyan
        docker compose -f $ComposeFile up -d
        Write-Host "`n服务端口：" -ForegroundColor Green
        Write-Host "  Elasticsearch: http://localhost:9200"
        Write-Host "  Qdrant:        http://localhost:6333"
        Write-Host "  Embedding:     http://localhost:8089/health"
        Write-Host "  shopkeeper-agent: http://localhost:8090/docs"
        Write-Host "`n查看日志: .\docker\manage.ps1 logs"
    }
    "down" {
        Write-Host "停止所有服务..." -ForegroundColor Cyan
        docker compose -f $ComposeFile down
    }
    "logs" {
        Write-Host "查看 shopkeeper-agent 日志（Ctrl+C 退出）..." -ForegroundColor Cyan
        docker compose -f $ComposeFile logs -f shopkeeper-agent
    }
    "build-kb" {
        Write-Host "构建元数据知识库（一次性任务）..." -ForegroundColor Cyan
        # 先确保依赖服务已启动
        docker compose -f $ComposeFile up -d elasticsearch qdrant embedding
        # 运行 build 模式的容器（执行完自动退出）
        docker compose -f $ComposeFile run --rm shopkeeper-agent build
    }
    "restart" {
        Write-Host "重启 shopkeeper-agent 服务..." -ForegroundColor Cyan
        docker compose -f $ComposeFile restart shopkeeper-agent
    }
    "status" {
        Write-Host "服务状态：" -ForegroundColor Cyan
        docker compose -f $ComposeFile ps
    }
}
