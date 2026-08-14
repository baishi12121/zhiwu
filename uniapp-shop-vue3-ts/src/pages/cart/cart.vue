<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import CartMain from './components/CartMain.vue'

// 页面级 onShow：子组件 onShow 在微信小程序等平台不可靠，由页面统一触发刷新
const cartMainRef = ref<InstanceType<typeof CartMain>>()
onShow(() => {
  console.log('[cart.vue] 页面 onShow 触发, cartMainRef =', !!cartMainRef.value)
  cartMainRef.value?.getMemberCartData()
})
</script>

<template>
  <CartMain ref="cartMainRef" />
</template>

<style lang="scss">
page {
  height: 100%;
}
</style>
