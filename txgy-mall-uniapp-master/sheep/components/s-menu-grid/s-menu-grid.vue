<!-- 装修基础组件：宫格导航 -->
<template>
	<view v-if="menuItems.length" class="menu-grid-shell" :style="[bgStyle, { marginLeft: `${data.space}px` }]">
		<uni-grid :showBorder="Boolean(data.border)" :column="data.column">
			<uni-grid-item v-for="(item, index) in menuItems" :key="index" >
				<view class="grid-item-box ss-flex ss-flex-col ss-row-center ss-col-center" @tap="sheep.$router.go(item.url)">
					<view class="img-box">
						<view class="tag-box" v-if="item.badge.show"
							:style="[{ background: item.badge.bgColor, color: item.badge.textColor }]">
							{{ item.badge.text }}
						</view>
						<image class="menu-image" :src="sheep.$url.cdn(item.iconUrl)"></image>
					</view>

					<view class="title-box ss-flex ss-flex-col ss-row-center ss-col-center">
						<view class="grid-text" :style="[{ color: item.titleColor }]">
							{{ item.title }}
						</view>
						<view class="grid-tip" :style="[{ color: item.subtitleColor }]">
							{{ item.subtitle }}
						</view>
					</view>
				</view>
			</uni-grid-item>
		</uni-grid>
	</view>

</template>

<script setup>
	import sheep from '@/sheep';
	import {
		computed
	} from 'vue';
	import { filterMarketingMenuItems } from '@/sheep/helper/simplified-marketing';

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
	const menuItems = computed(() => filterMarketingMenuItems(props.data?.list));
</script>

<style lang="scss" scoped>
	.menu-grid-shell {
		overflow: hidden;
		border-radius: 28rpx;
		background:
			radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 30%),
			linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 252, 0.96));
		border: 1rpx solid rgba(148, 163, 184, 0.14);
		box-shadow: var(--ui-Surface-shadow);
	}

	.menu-image {
		width: 48rpx;
		height: 48rpx;
	}

	.grid-item-box {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		height: 100%;
		padding: 18rpx 0 22rpx;

		.img-box {
			position: relative;
			width: 92rpx;
			height: 92rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			border-radius: 28rpx;
			background: rgba(59, 130, 246, 0.08);
			box-shadow: 0 12rpx 24rpx rgba(15, 23, 42, 0.06);

			.tag-box {
				position: absolute;
				z-index: 2;
				top: 0;
				right: 0;
				font-size: 2em;
				line-height: 1;
				padding: 0.4em 0.6em 0.3em;
				transform: scale(0.4) translateX(0.5em) translatey(-0.6em);
				transform-origin: 100% 0;
				border-radius: 200rpx;
				white-space: nowrap;
			}
		}

		.title-box {
			margin-top: 12rpx;

			.grid-tip {
				font-size: 24rpx;
				color: #94a3b8;
				white-space: nowrap;
				text-align: center;
			}

			.grid-text {
				font-size: 26rpx;
				font-weight: 600;
				color: #1f2937;
				text-align: center;
			}
		}
	}
</style>
