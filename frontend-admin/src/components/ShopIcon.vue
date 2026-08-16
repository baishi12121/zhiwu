<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    :stroke="color || 'currentColor'"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <!-- Shop building -->
    <path d="M4 11h16v9H4z" />
    <!-- Roof (top) -->
    <path d="M6 11l2-5h8l2 5" />
    <!-- Awning with scalloped edge -->
    <path d="M3 11c0 0 1.5-2 3-2s3 2 3 2 1.5-2 3-2 3 2 3 2 1.5-2 3-2 3 2 3 2" />
    <!-- Window (square with inner frame) -->
    <rect x="8" y="14" width="3.5" height="3.5" rx="0.3" />
    <path d="M8 15.75h3.5M9.75 14v3.5" />
    <!-- Door -->
    <path d="M14 20v-4h3.5v4" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  size?: number | string
  color?: string
}>()
</script>
