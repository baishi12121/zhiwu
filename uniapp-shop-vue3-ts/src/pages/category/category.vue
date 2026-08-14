<script setup lang="ts">
import { getCategoryTopAPI } from '@/services/category'
import { getHomeBannerAPI } from '@/services/home'
import type { CategoryTopItem } from '@/types/category'
import type { BannerItem } from '@/types/home'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import PageSkeleton from './components/PageSkeleton.vue'

const SELECTED_CATEGORY_KEY = 'home:selectedCategoryId'

// 获取轮播图数据
const bannerList = ref<BannerItem[]>([])
const getBannerData = async () => {
  const res = await getHomeBannerAPI(2)
  bannerList.value = res.data
}

// 获取分类列表数据
const categoryList = ref<CategoryTopItem[]>([])
const activeIndex = ref(0)
const applySelectedCategory = (selectedId?: string) => {
  if (!selectedId || !categoryList.value.length) return
  if (selectedId === 'all') {
    activeIndex.value = 0
    return
  }
  const index = categoryList.value.findIndex((item) => item.id === selectedId)
  if (index >= 0) activeIndex.value = index
}

const consumeSelectedCategory = () => {
  const selectedId = uni.getStorageSync(SELECTED_CATEGORY_KEY)
  if (typeof selectedId !== 'string' || !selectedId) return
  applySelectedCategory(selectedId)
  uni.removeStorageSync(SELECTED_CATEGORY_KEY)
}

const getCategoryTopData = async (selectedId?: string) => {
  const res = await getCategoryTopAPI()
  categoryList.value = res.data
  applySelectedCategory(selectedId)
}

// 是否数据加载完毕
const isFinish = ref(false)
// 页面加载
onLoad(async (query) => {
  const selectedId = typeof query?.id === 'string' ? query.id : undefined
  await Promise.all([getBannerData(), getCategoryTopData(selectedId)])
  consumeSelectedCategory()
  isFinish.value = true
})

onShow(() => {
  if (isFinish.value) consumeSelectedCategory()
})

// 提取当前二级分类数据
const subCategoryList = computed(() => {
  return categoryList.value[activeIndex.value]?.children || []
})
</script>

<template>
  <view class="viewport" v-if="isFinish">
    <!-- 搜索框 -->
    <view class="search">
      <view class="input">
        <text class="icon-search">女靴</text>
      </view>
    </view>
    <!-- 分类 -->
    <view class="categories">
      <!-- 左侧：一级分类 -->
      <scroll-view class="primary" scroll-y>
        <view
          v-for="(item, index) in categoryList"
          :key="item.id"
          class="item"
          :class="{ active: index === activeIndex }"
          @tap="activeIndex = index"
        >
          <text class="name">
            {{ item.name }}
          </text>
        </view>
      </scroll-view>
      <!-- 右侧：二级分类 -->
      <scroll-view enable-back-to-top class="secondary" scroll-y>
        <!-- 焦点图 -->
        <XtxSwiper class="banner" :list="bannerList" />
        <!-- 内容区域 -->
        <view class="panel" v-for="item in subCategoryList" :key="item.id">
          <view class="title">
            <text class="name">{{ item.name }}</text>
            <navigator class="more" hover-class="none">全部</navigator>
          </view>
          <view class="section">
            <navigator
              v-for="goods in item.goods"
              :key="goods.id"
              class="goods"
              hover-class="none"
              :url="`/pages/goods/goods?id=${goods.id}`"
            >
              <image class="image" :src="goods.picture"></image>
              <view class="name ellipsis">{{ goods.name }}</view>
              <view class="price">
                <text class="symbol">¥</text>
                <text class="number">{{ goods.price }}</text>
              </view>
            </navigator>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
  <PageSkeleton v-else />
</template>

<style lang="scss">
@import './styles/category.scss';
</style>
