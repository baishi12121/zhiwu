<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { useMessage } from 'naive-ui'
import { RefreshOutline, TrendingUpOutline, PeopleOutline, CartOutline, CashOutline } from '@vicons/ionicons5'
import {
  getCategoryDistribution,
  getDailyTrend,
  getProductRanking,
  getSalesOverview,
} from '@/api/sales'
import type { SalesOverview } from '@/types/admin'

use([CanvasRenderer, LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const message = useMessage()
const loading = ref(false)
const overview = ref<SalesOverview>({})
const trend = ref<Array<Record<string, unknown>>>([])
const ranking = ref<Array<Record<string, unknown>>>([])
const categories = ref<Array<Record<string, unknown>>>([])

const numberValue = (value: unknown) => Number(value ?? 0)
const money = (value: unknown) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(
    numberValue(value),
  )

const pickValue = (row: Record<string, unknown>, keys: string[]) => keys.map((key) => row[key]).find(Boolean) ?? 0
const pickLabel = (row: Record<string, unknown>, keys: string[]) => String(pickValue(row, keys) || '未命名')

const metrics = computed(() => [
  {
    label: '总订单数',
    value: numberValue(overview.value.totalOrders ?? overview.value.orderCount).toLocaleString(),
    foot: '已支付订单口径',
    icon: CartOutline,
    accent: 'primary',
  },
  {
    label: '总销量',
    value: numberValue(overview.value.totalSales ?? overview.value.salesCount).toLocaleString(),
    foot: '商品销售件数',
    icon: TrendingUpOutline,
    accent: 'info',
  },
  {
    label: '销售额',
    value: money(overview.value.totalAmount ?? overview.value.salesAmount ?? overview.value.amount),
    foot: '支付金额汇总',
    icon: CashOutline,
    accent: 'success',
  },
  {
    label: '用户数',
    value: numberValue(overview.value.totalUsers ?? overview.value.userCount).toLocaleString(),
    foot: '注册用户总量',
    icon: PeopleOutline,
    accent: 'warning',
  },
])

const trendOption = computed(() => ({
  color: ['#18A058'],
  tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 18, top: 28, bottom: 32 },
  xAxis: {
    type: 'category',
    data: trend.value.map((row) => pickLabel(row, ['date', 'day', 'statDate'])),
    axisLine: { lineStyle: { color: '#E8EAED' } },
    axisLabel: { color: '#667085', fontSize: 12 },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F2F4F6' } },
    axisLabel: { color: '#667085', fontSize: 12 },
    axisLine: { show: false },
    axisTick: { show: false },
  },
  series: [
    {
      name: '销售额',
      type: 'line',
      smooth: true,
      areaStyle: {
        opacity: 0.12,
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: '#18A058' },
          { offset: 1, color: 'rgba(24,160,88,0.05)' },
        ] },
      },
      lineStyle: { width: 2 },
      symbolSize: 6,
      data: trend.value.map((row) => pickValue(row, ['amount', 'salesAmount', 'totalAmount'])),
    },
  ],
}))

const categoryOption = computed(() => ({
  color: ['#18A058', '#2080F0', '#F59E0B', '#D03050', '#7C3AED', '#10B981'],
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, type: 'scroll', textStyle: { color: '#667085', fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '46%'],
      label: { color: '#1F2937', fontSize: 12 },
      labelLine: { lineStyle: { color: '#C5C9D1' } },
      data: categories.value.map((row) => ({
        name: pickLabel(row, ['categoryName', 'name']),
        value: pickValue(row, ['amount', 'salesAmount', 'totalAmount']),
      })),
    },
  ],
}))

type RankingRow = Record<string, unknown>

const rankingColumns: DataTableColumns<RankingRow> = [
  {
    title: '排名',
    key: 'rank',
    width: 80,
    render: (_row, index) =>
      h('span', { class: ['rank-cell', index < 3 ? `rank-cell--top-${index + 1}` : ''] }, String(index + 1)),
  },
  {
    title: '商品',
    key: 'productName',
    render: (row) => h('span', { class: 'cell-text' }, pickLabel(row, ['productName', 'name'])),
  },
  {
    title: '销量',
    key: 'salesCount',
    render: (row) =>
      h('span', { class: 'count-cell' }, String(pickValue(row, ['salesCount', 'count']))),
  },
  {
    title: '销售额',
    key: 'salesAmount',
    render: (row) =>
      h('span', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, '¥'),
        h('span', { class: 'price-amount' }, Number(pickValue(row, ['salesAmount', 'amount']) || 0).toFixed(2)),
      ]),
  },
]

const load = async () => {
  loading.value = true
  try {
    const [overviewData, trendData, rankingData, categoryData] = await Promise.all([
      getSalesOverview(),
      getDailyTrend(),
      getProductRanking(8),
      getCategoryDistribution(),
    ])
    overview.value = overviewData || {}
    trend.value = trendData || []
    ranking.value = rankingData || []
    categories.value = categoryData || []
  } catch (error) {
    message.warning(error instanceof Error ? error.message : '销售数据暂不可用')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">运营总览</h1>
        <p class="page-subtitle">订单、销售额、分类分布与商品排行一目了然。</p>
      </div>
      <div class="page-head-actions">
        <n-button :loading="loading" @click="load">
          <template #icon>
            <n-icon><RefreshOutline /></n-icon>
          </template>
          刷新
        </n-button>
      </div>
    </header>

    <!-- 关键指标 -->
    <div class="metrics-grid">
      <div
        v-for="item in metrics"
        :key="item.label"
        :class="['metric-card', `metric-card--${item.accent}`]"
      >
        <div class="metric-icon">
          <n-icon :size="20"><component :is="item.icon" /></n-icon>
        </div>
        <div class="metric-body">
          <span class="metric-label">{{ item.label }}</span>
          <strong class="metric-value">{{ item.value }}</strong>
          <span class="metric-foot">{{ item.foot }}</span>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="two-col">
      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h2 class="section-title">近 7 日销售趋势</h2>
            <p class="section-subtitle">按日汇总的销售额曲线。</p>
          </div>
        </div>
        <v-chart v-if="trend.length" class="chart" :option="trendOption" autoresize />
        <n-empty v-else description="暂无趋势数据" class="chart-empty" />
      </section>

      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h2 class="section-title">分类销售额分布</h2>
            <p class="section-subtitle">各一级分类销售额占比。</p>
          </div>
        </div>
        <v-chart v-if="categories.length" class="chart" :option="categoryOption" autoresize />
        <n-empty v-else description="暂无分类数据" class="chart-empty" />
      </section>
    </div>

    <!-- 商品排行 -->
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2 class="section-title">商品销售排行</h2>
          <p class="section-subtitle">销量与销售额 Top 8。</p>
        </div>
      </div>
      <n-data-table
        :loading="loading"
        :bordered="false"
        :single-line="false"
        :columns="rankingColumns"
        :data="ranking"
        :row-key="(row: RankingRow) => pickLabel(row, ['productId', 'id'])"
      />
    </section>
  </div>
</template>

<style scoped>
/* —— 关键指标 —— */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  align-items: stretch;
  gap: 14px;
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  transition: border-color 0.2s;
}

.metric-card:hover {
  border-color: var(--color-border-strong);
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-tag);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.metric-card--primary .metric-icon {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.metric-card--info .metric-icon {
  background: rgba(32, 128, 240, 0.1);
  color: #2080F0;
}

.metric-card--success .metric-icon {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.metric-card--warning .metric-icon {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.metric-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.metric-label {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.metric-value {
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: var(--font-weight-semibold);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-foot {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

/* —— 双栏图表 —— */
.two-col {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.chart-panel {
  display: flex;
  flex-direction: column;
}

.panel-head {
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--color-border);
}

.section-title {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
  line-height: 1.4;
  margin: 0;
}

.section-subtitle {
  color: var(--color-text-tertiary);
  font-size: 12px;
  margin: 4px 0 0;
}

.chart {
  width: 100%;
  height: 320px;
  padding: 8px 12px 12px;
}

.chart-empty {
  min-height: 260px;
  justify-content: center;
  padding: 40px 0;
}

/* —— 排行表格 —— */
.rank-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: var(--font-weight-semibold);
}

.rank-cell--top-1 {
  background: linear-gradient(135deg, #FFD700, #FFA500);
  color: #fff;
}

.rank-cell--top-2 {
  background: linear-gradient(135deg, #C0C0C0, #A0A0A0);
  color: #fff;
}

.rank-cell--top-3 {
  background: linear-gradient(135deg, #CD7F32, #A05A2C);
  color: #fff;
}

.cell-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.count-cell {
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.price-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--color-text-primary);
}

.price-symbol {
  font-size: 12px;
}

.price-amount {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

/* —— Responsive —— */
@media (max-width: 1200px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
