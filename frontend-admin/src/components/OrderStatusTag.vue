<script setup lang="ts">
import { computed } from 'vue'
import type { TagProps } from 'naive-ui'

const props = defineProps<{
  value?: number | null
}>()

const statusMap: Record<number, { label: string; type: TagProps['type'] }> = {
  1: { label: '待付款', type: 'default' },
  2: { label: '待发货', type: 'warning' },
  3: { label: '待收货', type: 'info' },
  4: { label: '待评价', type: 'success' },
  5: { label: '已完成', type: 'success' },
  6: { label: '已取消', type: 'error' },
}

const meta = computed(() => statusMap[Number(props.value)] || { label: '未知', type: 'default' as TagProps['type'] })
</script>

<template>
  <n-tag size="small" :type="meta.type" round>{{ meta.label }}</n-tag>
</template>
