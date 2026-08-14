"""
数仓 MySQL 仓储

这一层对应文档里的 DW Repository，职责是到真实数仓中补齐配置文件里
没有显式维护的信息，例如字段类型和字段示例值。Service 层只关心
“需要哪些信息”，具体怎样查数仓由仓储层统一封装
SQL 生成闭环中的数据库环境读取 SQL 校验和最终查询执行也集中放在这里
"""

import datetime
from decimal import Decimal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


def _to_json_serializable(value):
    """把数据库返回的非 JSON 原生类型转换为可序列化的 Python 原生类型

    MySQL 的 decimal/datetime 等类型在 Python 中对应 Decimal/datetime 对象，
    而 meta 库的 examples 字段是 JSON 类型，直接写入会报
    "Object of type Decimal is not JSON serializable"。
    """
    if isinstance(value, Decimal):
        # Decimal 转 float 或 int（保留精度优先用 float）
        return float(value)
    if isinstance(value, (datetime.datetime, datetime.date)):
        return value.isoformat()
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="ignore")
    return value


class DWMySQLRepository:
    """负责查询数仓真实表结构和字段样例值"""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_column_types(self, table_name: str) -> dict[str, str]:
        """查询整张表的字段类型，作为 ColumnInfo.type 的真实来源"""
        # 表名加反引号，避免 order 等保留字导致语法错误
        sql = f"show columns from `{table_name}`"
        result = await self.session.execute(text(sql))
        result_dict = result.mappings().fetchall()
        return {row["Field"]: row["Type"] for row in result_dict}

    async def get_column_values(
        self, table_name: str, column_name: str, limit: int = 10
    ) -> list:
        """抽样查询字段示例值，供元数据入库和后续检索链路复用"""
        # 表名和字段名加反引号，避免保留字冲突
        sql = f"select distinct `{column_name}` from `{table_name}` limit {limit}"
        result = await self.session.execute(text(sql))
        # 统一转换为 JSON 可序列化的原生类型，避免 Decimal/datetime 写入 meta 库失败
        return [_to_json_serializable(row[0]) for row in result.fetchall()]

    async def get_db_info(self):
        """读取当前数仓数据库的方言和版本，供 SQL 生成提示词使用"""

        sql = "select version()"
        result = await self.session.execute(text(sql))
        version = result.scalar()

        # dialect 来自 SQLAlchemy 当前绑定的数据库方言，例如 mysql
        dialect = self.session.bind.dialect.name
        return {"dialect": dialect, "version": version}

    async def validate(self, sql: str):
        """用 EXPLAIN 让数据库提前解析 SQL，发现语法 表名 字段名等错误"""
        sql = f"explain {sql}"
        await self.session.execute(text(sql))

    async def run(self, sql: str) -> list[dict]:
        """执行最终 SQL，并把 SQLAlchemy 行对象转换成前端更易消费的字典列表"""
        result = await self.session.execute(text(sql))
        rows = [dict(row) for row in result.mappings().fetchall()]
        # 统一转换非 JSON 原生类型，避免 SSE 响应序列化失败
        return [
            {k: _to_json_serializable(v) for k, v in row.items()} for row in rows
        ]
