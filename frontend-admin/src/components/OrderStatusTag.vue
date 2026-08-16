<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: number | null
}>()

// 订单状态柔和色板：待付款→蓝 / 待发货→橙 / 待收货→紫 / 待评价→灰 / 已完成→绿 / 已取消→红
const statusMap: Record<number, { label: string; bg: string; text: string; dot: string }> = {
  1: { label: '待付款', bg: 'rgba(32, 128, 240, 0.12)', text: '#0C63D4', dot: '#2080F0' },
  2: { label: '待发货', bg: 'rgba(245, 158, 11, 0.14)', text: '#B06F0A', dot: '#F59E0B' },
  3: { label: '待收货', bg: 'rgba(124, 58, 237, 0.12)', text: '#6D28D9', dot: '#7C3AED' },
  4: { label: '待评价', bg: 'rgba(152, 162, 179, 0.16)', text: '#475467', dot: '#98A2B3' },
  5: { label: '已完成', bg: 'rgba(24, 160, 88, 0.12)', text: '#0F8A4C', dot: '#18A058' },
  6: { label: '已取消', bg: 'rgba(208, 48, 80, 0.12)', text: '#B01F3F', dot: '#D03050' },
}

const meta = computed(
  () =>
    statusMap[Number(props.value)] || {
      label: '未知',
      bg: 'rgba(152, 162, 179, 0.16)',
      text: '#475467',
      dot: '#98A2B3',
    },
)
</script>

<template>
  <n-tag
    size="small"
    round
    :bordered="false"
    :color="{ color: meta.bg, textColor: meta.text, borderColor: 'transparent' }"
  >
    <template #icon>
      <span class="status-dot" :style="{ background: meta.dot }" />
    </template>
    {{ meta.label }}
  </n-tag>
</template>

<style scoped>
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  flex-shrink: 0;
}
</style>
