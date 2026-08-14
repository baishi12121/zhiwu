<script setup lang="ts">
import { getHomeBannerAPI, getHomeCategoryAPI, getHomeSeckillAPI } from '@/services/home'
import type { BannerItem, CategoryItem, SeckillItem } from '@/types/home'
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import CustomNavbar from './components/CustomNavbar.vue'
import CategoryPanel from './components/CategoryPanel.vue'
import SeckillPanel from './components/SeckillPanel.vue'
import { useGuessList } from '@/composables'

// 获取轮播图数据
const bannerList = ref<BannerItem[]>([])
const getHomeBannerData = async () => {
  const res = await getHomeBannerAPI()
  bannerList.value = res.data
}

// 获取前台分类数据
const categoryList = ref<CategoryItem[]>([])
const getHomeCategoryData = async () => {
  const res = await getHomeCategoryAPI()
  categoryList.value = res.data
}

// 获取热门推荐数据
const seckillList = ref<SeckillItem[]>([])
const getHomeSeckillData = async () => {
  const res = await getHomeSeckillAPI()
  seckillList.value = res.data
}

const ignoreRequestError = (task: Promise<unknown> | undefined) => {
  return task?.catch(() => undefined) || Promise.resolve()
}

// 是否加载中标记
const isLoading = ref(false)

// 页面加载
onLoad(async () => {
  isLoading.value = true
  try {
    await Promise.all([
      ignoreRequestError(getHomeBannerData()),
      ignoreRequestError(getHomeCategoryData()),
      ignoreRequestError(getHomeSeckillData()),
    ])
  } finally {
    isLoading.value = false
  }
})

// 猜你喜欢组合式函数调用
const { guessRef, onScrolltolower } = useGuessList()
// 当前下拉刷新状态
const isTriggered = ref(false)
// 自定义下拉刷新被触发
const onRefresherrefresh = async () => {
  // 开始动画
  isTriggered.value = true
  // 加载数据
  // await getHomeBannerData()
  // await getHomeCategoryData()
  // await getHomeHotData()
  // 重置猜你喜欢组件数据
  guessRef.value?.resetData()
  try {
    await Promise.all([
      ignoreRequestError(getHomeBannerData()),
      ignoreRequestError(getHomeCategoryData()),
      ignoreRequestError(getHomeSeckillData()),
      ignoreRequestError(guessRef.value?.getMore()),
    ])
  } finally {
    // 关闭动画
    isTriggered.value = false
  }
}
</script>

<template>
  <view class="viewport">
    <!-- 自定义导航栏 -->
    <CustomNavbar />
    <!-- 滚动容器 -->
    <scroll-view
      enable-back-to-top
      refresher-enabled
      @refresherrefresh="onRefresherrefresh"
      :refresher-triggered="isTriggered"
      @scrolltolower="onScrolltolower"
      class="scroll-view"
      scroll-y
    >
      <view v-if="isLoading" class="home-loading">
        <view class="loading-banner"></view>
        <view class="loading-category">
          <view v-for="item in 10" :key="item" class="loading-category-item">
            <view class="loading-icon"></view>
            <view class="loading-text"></view>
          </view>
        </view>
        <view class="loading-seckill"></view>
      </view>
      <template v-else>
        <!-- 自定义轮播图 -->
        <XtxSwiper :list="bannerList" />
        <!-- 分类面板 -->
        <CategoryPanel :list="categoryList" />
        <!-- 热门推荐 -->
        <SeckillPanel :list="seckillList" />
        <!-- 猜你喜欢 -->
        <XtxGuess ref="guessRef" />
      </template>
    </scroll-view>
  </view>
</template>

<style lang="scss">
page {
  background-color: #f7f7f7;
  height: 100%;
  overflow: hidden;
}

.viewport {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.scroll-view {
  flex: 1;
  overflow: hidden;
}

.home-loading {
  padding-bottom: 20rpx;
}

.loading-banner {
  height: 280rpx;
  background: #eee;
}

.loading-category {
  min-height: 328rpx;
  margin: 20rpx 0 0;
  padding: 10rpx 0;
  display: flex;
  flex-wrap: wrap;
}

.loading-category-item {
  width: 150rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.loading-icon {
  width: 100rpx;
  height: 100rpx;
  border-radius: 18rpx;
  background: #eee;
}

.loading-text {
  width: 64rpx;
  height: 24rpx;
  margin-top: 14rpx;
  border-radius: 999rpx;
  background: #eee;
}

.loading-seckill {
  height: 250rpx;
  margin: 20rpx;
  border-radius: 10rpx;
  background: #eee;
}
</style>
