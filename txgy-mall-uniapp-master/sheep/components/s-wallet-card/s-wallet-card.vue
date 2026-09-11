<!-- 装修用户组件：用户资产 -->
<template>
	<view class="ss-wallet-menu-wrap ss-flex ss-col-center" :style="[bgStyle, { marginLeft: `${data.space}px` }]">
		<view class="menu-item ss-flex-1 ss-flex-col ss-row-center ss-col-center"
			@tap="sheep.$router.go('/pages/user/wallet/money')">
			<view class="value-box ss-flex ss-col-bottom">
				<view class="value-text ss-line-1">{{ fen2yuan(userWallet.balance) || '0.00' }}</view>
				<view class="unit-text ss-m-l-6">元</view>
			</view>
			<view class="menu-title ss-m-t-28">账户余额</view>
		</view>
		<view class="menu-item ss-flex-1 ss-flex-col ss-row-center ss-col-center"
			@tap="sheep.$router.go('/pages/user/wallet/score')">
			<view class="value-box ss-flex ss-col-bottom">
				<view class="value-text">{{ userInfo.point || 0 }}</view>
				<view class="unit-text ss-m-l-6">个</view>
			</view>
			<view class="menu-title ss-m-t-28">积分</view>
		</view>
		<view class="menu-item ss-flex-1 ss-flex-col ss-row-center ss-col-center" @tap="
        sheep.$router.go('/pages/coupon/list', {
          type: 'geted',
        })
      ">
			<view class="value-box ss-flex ss-col-bottom">
				<view class="value-text">{{ numData.unusedCouponCount }}</view>
				<view class="unit-text ss-m-l-6">张</view>
			</view>
			<view class="menu-title ss-m-t-28">优惠券</view>
		</view>
		<view class="menu-item ss-flex-col ss-row-center ss-col-center menu-wallet"
          @tap="sheep.$router.go('/pages/user/wallet/money')">
			<image class="item-icon" :src="sheep.$url.static('/static/img/shop/user/wallet_icon.png')" mode="aspectFit" />
			<view class="menu-title ss-m-t-30">我的钱包</view>
		</view>
	</view>
</template>

<script setup>
	/**
	 * 装修组件 - 订单菜单组
	 */
	import { computed } from 'vue';
	import sheep from '@/sheep';
	import { fen2yuan } from '../../hooks/useGoods';

	// 接收参数
	const props = defineProps({
		// 装修数据
		data: {
		  type: Object,
		  default: () => ({}),
		},
		// 装修样式
		styles: {
		  type: Object,
		  default: () => ({}),
		},
	});
	// 设置背景样式
	const bgStyle = computed(() => {
	  // 直接从 props.styles 解构
	  const { bgType, bgImg, bgColor } = props.styles;
	
	  // 根据 bgType 返回相应的样式
	  if (bgType === 'img') {
		return {
		  background: `url(${bgImg}) no-repeat top center / 100% 100%`,
		};
	  }
	  if (bgColor) {
		return {
		  backgroundColor: bgColor,
		};
	  }
	  return {};
	});
	
	const userWallet = computed(() => sheep.$store('user').userWallet);
	const userInfo = computed(() => sheep.$store('user').userInfo);
	const numData = computed(() => sheep.$store('user').numData);
</script>

<style lang="scss" scoped>
	.ss-wallet-menu-wrap {
		box-sizing: border-box;
		overflow: hidden;
		border-radius: 32rpx;
		padding: 14rpx 10rpx;
		background:
			radial-gradient(circle at top left, rgba(16, 185, 129, 0.18), transparent 28%),
			linear-gradient(135deg, rgba(15, 118, 110, 0.95), rgba(37, 99, 235, 0.92));
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow: var(--ui-Surface-shadow-lg);

		.menu-wallet {
			width: 148rpx;
			margin-left: 4rpx;
			background: rgba(255, 255, 255, 0.08);
			border-radius: 24rpx;
		}

		.menu-item {
			height: 164rpx;
			border-radius: 22rpx;

			.menu-title {
				font-size: 24rpx;
				line-height: 24rpx;
				color: rgba(255, 255, 255, 0.82);
				font-weight: 500;
			}

			.item-icon {
				width: 44rpx;
				height: 44rpx;
				border-radius: 16rpx;
				box-shadow: 0 10rpx 20rpx rgba(15, 23, 42, 0.12);
			}

			.value-box {
				height: 50rpx;
				text-align: center;

				.value-text {
					font-size: 28rpx;
					color: #ffffff;
					line-height: 28rpx;
					vertical-align: text-bottom;
					font-family: OPPOSANS;
				}

				.unit-text {
					font-size: 24rpx;
					color: rgba(255, 255, 255, 0.72);
					line-height: 24rpx;
				}
			}
		}
	}
</style>

