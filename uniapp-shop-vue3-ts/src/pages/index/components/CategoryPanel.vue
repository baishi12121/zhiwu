<script setup lang="ts">
import type { CategoryItem } from '@/types/home'

// 定义 props 接收数据
defineProps<{
  list: CategoryItem[]
}>()

const iconMap: Record<string, string> = {
  全部: 'bars',
  服饰: 'shop',
  美妆: 'color',
  家居: 'home',
  数码: 'phone',
  运动: 'medal',
  母婴: 'personadd',
  生鲜: 'gift',
  图书: 'list',
  配饰: 'tune',
}

const categoryIcon = (item: CategoryItem) => {
  return iconMap[item.name] || 'bars'
}

const SELECTED_CATEGORY_KEY = 'home:selectedCategoryId'

const navigateCategory = (item: CategoryItem) => {
  uni.setStorageSync(SELECTED_CATEGORY_KEY, item.id)
  uni.switchTab({
    url: '/pages/category/category',
  })
}
</script>

<template>
  <view class="category">
    <view
      class="category-item"
      hover-class="category-item-hover"
      v-for="item in list"
      :key="item.id"
      @tap="navigateCategory(item)"
    >
      <view class="icon-box">
        <uni-icons class="icon" :type="categoryIcon(item)" size="24" color="#686c72" />
      </view>
      <text class="text">{{ item.name }}</text>
    </view>
  </view>
</template>

<style lang="scss">
@import '../styles/category.scss';
</style>
