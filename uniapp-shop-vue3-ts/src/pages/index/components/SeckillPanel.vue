<script setup lang="ts">
import type { SeckillItem } from '@/types/home'

defineProps<{
  list: SeckillItem[]
}>()

const stockPercent = (stock?: number) => {
  const value = Number(stock || 0)
  return `${Math.max(8, Math.min(100, value))}%`
}
</script>

<template>
  <view class="seckill">
    <view class="seckill-head">
      <view class="title-row">
        <view class="title-icon">
          <uni-icons type="fire" size="18" color="#e54835" />
        </view>
        <text class="title">秒杀专区</text>
        <text class="subtitle">{{ list[0]?.activityName || '限时抢购' }}</text>
      </view>
      <text class="tag">{{ list.length ? '热抢中' : '限时' }}</text>
    </view>
    <scroll-view v-if="list.length" class="goods-scroll" scroll-x enable-flex>
      <navigator
        v-for="item in list"
        :key="item.id"
        class="goods-card"
        hover-class="none"
        :url="`/pages/goods/goods?id=${item.spuId}`"
      >
        <view class="image-wrap">
          <image
            class="image"
            mode="aspectFill"
            :src="item.picture || '/static/images/logo_icon.png'"
          ></image>
          <text class="rush-tag">秒杀</text>
        </view>
        <text class="name ellipsis">{{ item.name }}</text>
        <view class="price-row">
          <text class="price">¥{{ item.seckillPrice }}</text>
          <text class="old-price">¥{{ item.originalPrice }}</text>
        </view>
        <view class="stock-row">
          <text class="stock">剩余 {{ item.seckillStock }}</text>
          <view class="stock-bar">
            <view
              class="stock-bar-inner"
              :style="{ width: stockPercent(item.seckillStock) }"
            ></view>
          </view>
        </view>
      </navigator>
    </scroll-view>
    <view v-else class="empty">
      <view class="empty-icon">
        <uni-icons type="calendar" size="20" color="#a4a8ad" />
      </view>
      <view class="empty-copy">
        <text class="empty-title">暂无可抢商品</text>
        <text class="empty-desc">秒杀商品正在排期中</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.seckill {
  margin: 20rpx 20rpx 0;
  padding: 24rpx 0 26rpx;
  background: #fff;
  border-radius: 10rpx;
  overflow: hidden;
}

.seckill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx 20rpx;

  .title-row {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .title-icon {
    width: 40rpx;
    height: 40rpx;
    margin-right: 8rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 999rpx;
    background: #fff0ed;
  }

  .title {
    color: #222;
    font-size: 34rpx;
    font-weight: 700;
    white-space: nowrap;
  }

  .subtitle {
    margin-left: 16rpx;
    color: #888;
    font-size: 24rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tag {
    padding: 6rpx 14rpx;
    color: #fff;
    font-size: 22rpx;
    border-radius: 999rpx;
    background: linear-gradient(135deg, #f15a42, #e54835);
    white-space: nowrap;
  }
}

.goods-scroll {
  white-space: nowrap;
}

.empty {
  margin: 4rpx 24rpx 0;
  padding: 28rpx 0 8rpx;
  display: flex;
  align-items: center;
  color: #999;
}

.empty-icon {
  width: 54rpx;
  height: 54rpx;
  margin-right: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  background: #f5f6f7;
}

.empty-copy {
  display: flex;
  flex-direction: column;
}

.empty-title {
  color: #777;
  font-size: 26rpx;
}

.empty-desc {
  margin-top: 4rpx;
  color: #aaa;
  font-size: 22rpx;
}

.goods-card {
  display: inline-flex;
  flex-direction: column;
  width: 210rpx;
  margin-left: 20rpx;
  padding: 14rpx;
  vertical-align: top;
  background: #fff7f5;
  border-radius: 10rpx;

  &:last-child {
    margin-right: 20rpx;
  }

  .image-wrap {
    position: relative;
    width: 182rpx;
    height: 150rpx;
  }

  .image {
    width: 182rpx;
    height: 150rpx;
    border-radius: 8rpx;
    background: #f3f4f4;
  }

  .rush-tag {
    position: absolute;
    left: 8rpx;
    top: 8rpx;
    padding: 4rpx 8rpx;
    color: #fff;
    font-size: 18rpx;
    line-height: 1;
    border-radius: 999rpx;
    background: rgba(229, 72, 53, 0.92);
  }

  .name {
    margin-top: 12rpx;
    color: #333;
    font-size: 24rpx;
  }

  .price-row {
    display: flex;
    align-items: baseline;
    gap: 8rpx;
    margin-top: 8rpx;
  }

  .price {
    color: #e54835;
    font-size: 28rpx;
    font-weight: 700;
  }

  .old-price {
    color: #aaa;
    font-size: 20rpx;
    text-decoration: line-through;
  }

  .stock-row {
    margin-top: 8rpx;
  }

  .stock {
    color: #888;
    font-size: 22rpx;
  }

  .stock-bar {
    width: 100%;
    height: 6rpx;
    margin-top: 8rpx;
    overflow: hidden;
    border-radius: 999rpx;
    background: #ffe0da;
  }

  .stock-bar-inner {
    height: 100%;
    border-radius: 999rpx;
    background: #e54835;
  }
}
</style>
