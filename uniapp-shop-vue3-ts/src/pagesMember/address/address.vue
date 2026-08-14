<script setup lang="ts">
import { deleteMemberAddressByIdAPI, getMemberAddressAPI } from '@/services/address'
import { useAddressStore } from '@/stores/modules/address'
import type { AddressItem } from '@/types/address'
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'

// 获取收货地址列表数据
const addressList = ref<AddressItem[]>([])
const getMemberAddressData = async () => {
  try {
    const res = await getMemberAddressAPI()
    addressList.value = res.data || []
  } catch {
    // 错误已在 http 拦截器中统一 toast 提示
    addressList.value = []
  }
}

// 初始化调用(页面显示)
onShow(() => {
  getMemberAddressData()
})

// 删除收货地址
const onDeleteAddress = (id: number | string) => {
  // 二次确认
  uni.showModal({
    content: '删除地址?',
    confirmColor: '#27BA9B',
    success: async (res) => {
      if (res.confirm) {
        // 根据id删除收货地址
        await deleteMemberAddressByIdAPI(String(id))
        // 重新获取收货地址列表
        getMemberAddressData()
      }
    },
  })
}

// 修改收货地址
const onChangeAddress = (item: AddressItem) => {
  // 修改地址
  const addressStore = useAddressStore()
  addressStore.changeSelectedAddress(item)
  // 返回上一页
  uni.navigateBack()
}
</script>

<template>
  <view class="viewport">
    <!-- 地址列表 -->
    <scroll-view enable-back-to-top class="scroll-view" scroll-y>
      <view v-if="addressList.length" class="address">
        <view class="address-list">
          <!-- 收货地址项 -->
          <view class="item" v-for="item in addressList" :key="item.id">
            <view class="item-content" @tap="onChangeAddress(item)">
              <view class="user">
                {{ item.receiver }}
                <text class="contact">{{ item.contact }}</text>
                <text v-if="item.isDefault" class="badge">默认</text>
              </view>
              <view class="locate">{{ item.fullLocation }} {{ item.address }}</view>
            </view>
            <view class="item-actions">
              <navigator
                class="edit"
                hover-class="none"
                :url="`/pagesMember/address-form/address-form?id=${item.id}`"
              >
                修改
              </navigator>
              <text class="delete" @tap.stop="onDeleteAddress(item.id)">删除</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="blank">暂无收货地址</view>
    </scroll-view>
    <!-- 添加按钮 -->
    <view class="add-btn">
      <navigator hover-class="none" url="/pagesMember/address-form/address-form">
        新建地址
      </navigator>
    </view>
  </view>
</template>

<style lang="scss">
page {
  height: 100%;
  overflow: hidden;
}

.viewport {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #f4f4f4;

  // mp-weixin 的 <scroll-view> 是原生滚动组件，需要显式高度才能渲染内容。
  // flex: 1 让它撑满顶部到 .add-btn 之间的剩余空间；height: 0 是 flex 子项收缩的标准技巧。
  .scroll-view {
    flex: 1;
    height: 0;
    padding-top: 20rpx;
  }
}

.address {
  padding: 0 20rpx;
  margin: 0 20rpx;
  border-radius: 10rpx;
  background-color: #fff;

  .address-list {
    .item {
      display: flex;
      align-items: center;
      border-bottom: 1rpx solid #ddd;

      &:last-child {
        border: none;
      }
    }

    .item-content {
      flex: 1;
      line-height: 1;
      padding: 40rpx 10rpx 38rpx 0;
      overflow: hidden;
    }

    .item-actions {
      display: flex;
      flex-direction: column;
      gap: 12rpx;
      padding: 30rpx 0 30rpx 20rpx;
      border-left: 1rpx solid #eee;
      flex-shrink: 0;

      .edit {
        font-size: 24rpx;
        color: #27ba9b;
        line-height: 1;
      }

      .delete {
        font-size: 24rpx;
        color: #cf4444;
        line-height: 1;
      }
    }
  }
}

.user {
  font-size: 28rpx;
  margin-bottom: 20rpx;
  color: #333;

  .contact {
    color: #666;
  }

  .badge {
    display: inline-block;
    padding: 4rpx 10rpx 2rpx 14rpx;
    margin: 2rpx 0 0 10rpx;
    font-size: 26rpx;
    color: #27ba9b;
    border-radius: 6rpx;
    border: 1rpx solid #27ba9b;
  }
}

.locate {
  line-height: 1.6;
  font-size: 26rpx;
  color: #333;
}

.blank {
  margin-top: 300rpx;
  text-align: center;
  font-size: 32rpx;
  color: #888;
}

.add-btn {
  height: 80rpx;
  text-align: center;
  line-height: 80rpx;
  margin: 30rpx 20rpx;
  color: #fff;
  border-radius: 80rpx;
  font-size: 30rpx;
  background-color: #27ba9b;
}
</style>
