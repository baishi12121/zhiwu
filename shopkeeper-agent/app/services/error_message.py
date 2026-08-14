"""
对外错误文案转换。
"""


def to_public_error_message(error: Exception | str) -> str:
    """把底层供应商或框架异常转换成可展示给用户的文案。"""

    raw_message = str(error)
    if raw_message.startswith("AI "):
        return raw_message

    lowered = raw_message.lower()

    if (
        "allocationquota.freetieronly" in lowered
        or "free quota exhausted" in lowered
        or "free tier" in lowered
        or "免费额度已用完" in raw_message
    ):
        return "AI 模型免费额度已用完，请在模型服务控制台充值、关闭仅使用免费额度，或更换可用的 LLM_API_KEY。"

    if "model_not_found" in lowered or "does not exist" in lowered:
        return "AI 模型配置不可用，请检查 LLM_MODEL_NAME 是否正确或当前账号是否有访问权限。"

    if "401" in lowered or "unauthorized" in lowered or "api key" in lowered:
        return "AI 模型密钥无效，请检查 LLM_API_KEY 配置。"

    if "timeout" in lowered or "timed out" in lowered:
        return "AI 模型响应超时，请稍后重试。"

    return "AI 服务暂时不可用，请稍后重试。"
