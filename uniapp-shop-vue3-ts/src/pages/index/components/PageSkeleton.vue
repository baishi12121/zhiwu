<template>
  <view class="skeleton">
    <view class="banner"></view>
    <view class="category">
      <view v-for="item in 10" :key="item" class="category-item">
        <view class="icon"></view>
        <view class="text"></view>
      </view>
    </view>
    <view class="seckill">
      <view class="head">
        <view class="title"></view>
        <view class="tag"></view>
      </view>
      <view class="goods-row">
        <view v-for="item in 3" :key="item" class="goods-card">
          <view class="image"></view>
          <view class="name"></view>
          <view class="price"></view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.skeleton {
  padding-bottom: 20rpx;
}

.banner {
  height: 280rpx;
  background: #eee;
}

.category {
  min-height: 328rpx;
  margin: 20rpx 0 0;
  padding: 10rpx 0;
  display: flex;
  flex-wrap: wrap;

  .category-item {
    width: 150rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .icon {
    width: 100rpx;
    height: 100rpx;
    border-radius: 18rpx;
    background: #eee;
  }

  .text {
    width: 64rpx;
    height: 24rpx;
    margin-top: 14rpx;
    border-radius: 999rpx;
    background: #eee;
  }
}

.seckill {
  margin: 20rpx 20rpx 0;
  padding: 24rpx;
  background: #fff;
  border-radius: 10rpx;

  .head {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20rpx;
  }

  .title {
    width: 150rpx;
    height: 34rpx;
    border-radius: 999rpx;
    background: #eee;
  }

  .tag {
    width: 58rpx;
    height: 34rpx;
    border-radius: 999rpx;
    background: #eee;
  }

  .goods-row {
    display: flex;
    gap: 18rpx;
  }

  .goods-card {
    width: 190rpx;
  }

  .image {
    width: 190rpx;
    height: 130rpx;
    border-radius: 8rpx;
    background: #eee;
  }

  .name,
  .price {
    height: 22rpx;
    margin-top: 12rpx;
    border-radius: 999rpx;
    background: #eee;
  }

  .name {
    width: 160rpx;
  }

  .price {
    width: 90rpx;
  }
}
</style>
