# AI 匿名查询和 SQL 执行修复文档

## 问题是什么

`/ai/chat` 在网关白名单中匿名放行，Java 层会把用户问题转发给 `shopkeeper-agent`，Python Agent 最终执行 LLM 生成的 SQL。

关键风险点：

- 网关允许匿名访问 `/ai/chat`。
- `shopkeeper-agent` 的 `/api/query` 无鉴权。
- SQL 只依赖提示词约束为 SELECT，代码没有强制校验。
- Python 端使用 `text(sql)` 直接执行最终 SQL。
- AI 查询使用的业务库账号默认仍可能是高权限账号。

## 影响

- 外部用户可绕过登录消耗 LLM 调用额度。
- Prompt injection 可能诱导模型生成非预期 SQL。
- 如果数据库账号权限过高，可能造成数据泄露、写入、删除或结构破坏。
- 长查询或笛卡尔积查询可能拖垮 MySQL。

## 怎么修复

### 1. `/ai/chat` 默认要求登录

从网关白名单中移除：

```java
"/ai/chat",
"/ai/chat/**",
```

保留 `/ai/health` 是否公开要按部署场景决定。生产建议健康检查只对内网开放。

### 2. Java 转发用户身份

`mall-ai-service` 接收网关注入的 `X-User-Id`，并转发给 Python Agent：

```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(@RequestHeader("X-User-Id") Long userId,
                         @Valid @RequestBody ChatRequest request) {
    return aiAgentService.chat(userId, request.getQuery());
}
```

`AiAgentService` 转发请求头：

```java
return aiAgentWebClient.post()
        .uri(properties.getQueryPath())
        .header("X-User-Id", String.valueOf(userId))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .retrieve()
        .bodyToFlux(String.class);
```

### 3. Python Agent 增加内部鉴权

Java 与 Python 之间增加内部服务 token：

```python
from fastapi import Header, HTTPException

async def verify_internal_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")
```

在 `/api/query` 上挂载依赖，避免绕过 Java 网关直连 Python。

### 4. SQL 强制白名单校验

在 `DWMySQLRepository.validate()` 前增加 SQL parser 校验，原则：

- 只允许单条语句。
- 只允许 `SELECT`。
- 禁止 `INSERT`、`UPDATE`、`DELETE`、`DROP`、`ALTER`、`CREATE`、`TRUNCATE`。
- 禁止 `INTO OUTFILE`、`LOAD_FILE`、存储过程、变量赋值。
- 必须有 `LIMIT`，且上限不超过 100。

推荐使用 `sqlglot`：

```python
import sqlglot
from sqlglot import exp

def validate_readonly_sql(sql: str) -> str:
    statements = sqlglot.parse(sql, read="mysql")
    if len(statements) != 1:
        raise ValueError("只允许单条 SQL")

    root = statements[0]
    if not isinstance(root, exp.Select):
        raise ValueError("只允许 SELECT 查询")

    forbidden = (exp.Insert, exp.Update, exp.Delete, exp.Drop, exp.Create, exp.Alter)
    if any(root.find(node_type) for node_type in forbidden):
        raise ValueError("禁止写操作或 DDL")

    if root.args.get("limit") is None:
        root.set("limit", exp.Limit(expression=exp.Literal.number(20)))

    return root.sql(dialect="mysql")
```

### 5. AI 使用只读数据库账号

`shopkeeper-agent` 查询 `mall` 库必须使用只读账号：

```sql
CREATE USER 'mall_readonly'@'%' IDENTIFIED BY 'strong-password';
GRANT SELECT ON mall.* TO 'mall_readonly'@'%';
```

### 6. 增加限流和审计

按用户 ID 限制调用频率：

- 每用户每分钟最多 5 次。
- 每用户每天最多 100 次。
- 记录 userId、query、最终 SQL、耗时、行数、错误信息。

## 验证方式

1. 未登录调用 `/ai/chat` 应返回 401。
2. 直连 `shopkeeper-agent /api/query` 不带内部 token 应返回 401。
3. 输入“删除所有订单表数据”，最终 SQL 校验应拒绝。
4. 输入普通统计问题，最终 SQL 应被规范为单条 SELECT，并带 LIMIT。
5. 用 `mall_readonly` 执行写 SQL，应被 MySQL 拒绝。
