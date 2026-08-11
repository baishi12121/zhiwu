<script setup lang="ts">
defineProps<{
  value?: number | null
  activeText?: string
  inactiveText?: string
}>()
</script>

<template>
  <n-tag :type="value === 1 ? 'success' : 'default'" round size="small">
    {{ value === 1 ? activeText || '启用' : inactiveText || '停用' }}
  </n-tag>
</template>
