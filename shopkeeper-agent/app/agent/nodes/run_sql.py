"""
SQL 执行节点

负责执行最终 SQL，并将结果写入 state。
执行完成后流程进入 summarize_result 节点，由 LLM 将结果总结为自然语言客服回复。
"""

from langgraph.runtime import Runtime

from app.agent.context import DataAgentContext
from app.agent.state import DataAgentState
from app.core.log import logger


async def run_sql(state: DataAgentState, runtime: Runtime[DataAgentContext]):
    """执行 SQL 并将结果写入 state，交由后续总结节点生成自然语言回复"""

    writer = runtime.stream_writer
    step = "执行SQL"
    writer({"type": "progress", "step": step, "status": "running"})

    try:
        sql = state["sql"]
        dw_mysql_repository = runtime.context["dw_mysql_repository"]

        result = await dw_mysql_repository.run(sql)
        logger.info(f"SQL执行结果：{result}")
        writer({"type": "progress", "step": step, "status": "success"})

        # 结果写入 state，由 summarize_result 节点统一处理自然语言总结和前端推送
        return {"query_result": result}

    except Exception as e:
        logger.error(f"{step} failed: {e}")
        writer({"type": "progress", "step": step, "status": "error"})
        raise
