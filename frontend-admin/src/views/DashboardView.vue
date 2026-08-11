<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { useMessage } from 'naive-ui'
import { RefreshOutline } from '@vicons/ionicons5'
import { ChartLine } from '@vicons/fa'
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
  { label: '总订单数', value: numberValue(overview.value.totalOrders ?? overview.value.orderCount), foot: '已支付订单口径' },
  { label: '总销量', value: numberValue(overview.value.totalSales ?? overview.value.salesCount), foot: '商品销售件数' },
  {
    label: '销售额',
    value: money(overview.value.totalAmount ?? overview.value.salesAmount ?? overview.value.amount),
    foot: '支付金额汇总',
  },
  { label: '用户数', value: numberValue(overview.value.totalUsers ?? overview.value.userCount), foot: '注册用户总量' },
])

const trendOption = computed(() => ({
  color: ['#0f766e', '#2563eb'],
  tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 18, top: 28, bottom: 32 },
  xAxis: {
    type: 'category',
    data: trend.value.map((row) => pickLabel(row, ['date', 'day', 'statDate'])),
    axisLine: { lineStyle: { color: '#d8e1e0' } },
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf2f2' } } },
  series: [
    {
      name: '销售额',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.12 },
      data: trend.value.map((row) => pickValue(row, ['amount', 'salesAmount', 'totalAmount'])),
    },
  ],
}))

const categoryOption = computed(() => ({
  color: ['#0f766e', '#2563eb', '#eab308', '#dc2626', '#7c3aed'],
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, type: 'scroll' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: categories.value.map((row) => ({
        name: pickLabel(row, ['categoryName', 'name']),
        value: pickValue(row, ['amount', 'salesAmount', 'totalAmount']),
      })),
    },
  ],
}))

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
    <div class="page-head">
      <div>
        <h1 class="page-title">运营总览</h1>
        <p class="page-subtitle">来自 /admin/sales 的订单、销售额、分类分布与商品排行。</p>
      </div>
      <n-button :loading="loading" @click="load">
        <template #icon>
          <n-icon><RefreshOutline /></n-icon>
        </template>
        刷新
      </n-button>
    </div>

    <div class="metrics-grid">
      <div v-for="item in metrics" :key="item.label" class="metric">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-foot">{{ item.foot }}</div>
      </div>
    </div>

    <div class="two-col">
      <section class="panel panel-pad">
        <div class="chart-head">
          <h2 class="section-title">近 7 日销售趋势</h2>
          <n-icon color="#0f766e"><ChartLine /></n-icon>
        </div>
        <v-chart v-if="trend.length" class="chart" :option="trendOption" autoresize />
        <n-empty v-else description="暂无趋势数据" class="empty" />
      </section>
      <section class="panel panel-pad">
        <h2 class="section-title">分类销售额分布</h2>
        <v-chart v-if="categories.length" class="chart small" :option="categoryOption" autoresize />
        <n-empty v-else description="暂无分类数据" class="empty" />
      </section>
    </div>

    <section class="panel panel-pad">
      <h2 class="section-title">商品销售排行</h2>
      <n-data-table
        :loading="loading"
        :bordered="false"
        :columns="[
          { title: '商品', key: 'productName' },
          { title: '销量', key: 'salesCount' },
          { title: '销售额', key: 'salesAmount' },
        ]"
        :data="ranking"
      />
    </section>
  </div>
</template>

<style scoped>
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart {
  width: 100%;
  height: 320px;
}

.chart.small {
  height: 320px;
}

.empty {
  min-height: 260px;
  justify-content: center;
}
</style>
