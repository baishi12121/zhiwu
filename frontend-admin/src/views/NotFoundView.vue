<template>
  <main class="not-found">
    <n-result status="404" title="页面不存在" description="当前地址没有对应的后台页面">
      <template #footer>
        <n-button type="primary" @click="$router.replace('/dashboard')">返回运营总览</n-button>
      </template>
    </n-result>
  </main>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: #eef3f2;
}
</style>
