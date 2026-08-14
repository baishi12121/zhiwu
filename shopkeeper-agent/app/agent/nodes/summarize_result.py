"""
查询结果 AI 总结节点

在 SQL 执行完成后，调用 LLM 将结构化查询结果总结为自然语言客服回复，
并通过 SSE 以打字机友好的方式推送给前端。

输出顺序：
  1. summary: LLM 生成的客服回复文本（前端打字机逐字渲染）
  2. result:  原始结构化数据（前端渲染商品卡片）
"""

import yaml
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import PromptTemplate
from langgraph.runtime import Runtime

from app.agent.context import DataAgentContext
from app.agent.llm import llm
from app.agent.state import DataAgentState
from app.core.log import logger
from app.prompt.prompt_loader import load_prompt


async def summarize_result(state: DataAgentState, runtime: Runtime[DataAgentContext]):
    """将 SQL 执行结果总结为自然语言客服回复"""

    writer = runtime.stream_writer
    step = "总结回复"
    writer({"type": "progress", "step": step, "status": "running"})

    try:
        query = state["query"]
        query_result = state.get("query_result", [])

        # 空结果：不调 LLM，直接返回兜底文案
        if not query_result:
            empty_reply = (
                f"抱歉，没有找到与「{query}」相关的信息。"
                "您可以换个关键词试试，或者告诉我您想要什么类型的商品，我来帮您推荐～"
            )
            writer({"type": "progress", "step": step, "status": "success"})
            writer({"type": "summary", "message": empty_reply})
            # data 为 None 而非空数组，前端 parseGoods 对空数组返回 null 会回退
            # 到 formatResult，但 formatResult 对 [] 输出空串，用户看不到任何反馈
            writer({"type": "result", "data": None})
            return {"query_result": query_result}

        # 调用 LLM 生成自然语言回复
        prompt = PromptTemplate(
            template=load_prompt("summarize_result"),
            input_variables=["query", "result"],
        )
        output_parser = StrOutputParser()
        chain = prompt | llm | output_parser

        # 将结构化结果转成 YAML，模型更容易理解字段含义
        result_yaml = yaml.dump(query_result, allow_unicode=True, sort_keys=False)

        summary = await chain.ainvoke({"query": query, "result": result_yaml})
        logger.info(f"AI 总结回复：{summary}")

        writer({"type": "progress", "step": step, "status": "success"})

        # 先推送自然语言总结 → 前端打字机逐字渲染
        writer({"type": "summary", "message": summary.strip()})

        # 再推送原始结构化数据 → 前端渲染商品卡片
        writer({"type": "result", "data": query_result})

        return {"query_result": query_result}

    except Exception as e:
        logger.error(f"{step} failed: {e}")
        writer({"type": "progress", "step": step, "status": "error"})
        # 总结失败也要把原始数据推出去，不能让前端白等
        query_result = state.get("query_result", [])
        writer({"type": "result", "data": query_result})
        raise
