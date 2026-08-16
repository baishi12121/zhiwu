<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: number | null
  activeText?: string
  inactiveText?: string
}>()

// 柔和色彩：浅色背景 + 对应文字色 + 圆点指示
const palette = computed(() => {
  if (props.value === 1) {
    return {
      bg: 'rgba(24, 160, 88, 0.12)',
      text: '#0F8A4C',
      dot: '#18A058',
    }
  }
  return {
    bg: 'rgba(152, 162, 179, 0.16)',
    text: '#667085',
    dot: '#98A2B3',
  }
})

const label = computed(() =>
  props.value === 1 ? props.activeText || '启用' : props.inactiveText || '停用',
)
</script>

<template>
  <n-tag
    size="small"
    round
    :bordered="false"
    :color="{ color: palette.bg, textColor: palette.text, borderColor: 'transparent' }"
  >
    <template #icon>
      <span class="status-dot" :style="{ background: palette.dot }" />
    </template>
    {{ label }}
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
