<template>
  <div :class="`go-preview ${localStorageInfo.editCanvasConfig.previewScaleType}`" @mousedown="dragCanvas">
    <template v-if="showEntity">
      <!-- 实体区域 -->
      <div ref="entityRef" class="go-preview-entity">
        <!-- 缩放层 -->
        <div ref="previewRef" class="go-preview-scale">
          <!-- 展示层 -->
          <div :style="previewRefStyle" v-if="show">
            <!-- 渲染层 -->
            <preview-render-list></preview-render-list>
          </div>
        </div>
      </div>
    </template>
    <template v-else>
      <!-- 缩放层 -->
      <div ref="previewRef" class="go-preview-scale">
        <!-- 展示层 -->
        <div :style="previewRefStyle" v-if="show">
          <!-- 渲染层 -->
          <preview-render-list></preview-render-list>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PreviewRenderList } from './components/PreviewRenderList'
import { getFilterStyle, routerTurnByName, getSessionStorage, setTitle } from '@/utils'
import { getEditCanvasConfigStyle, getSessionStorageInfo, keyRecordHandle, dragCanvas } from './utils'
import { PageEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { useScale } from './hooks/useScale.hook'
import { useStore } from './hooks/useStore.hook'
import { PreviewScaleEnum } from '@/enums/styleEnum'
import { useComInstall } from './hooks/useComInstall.hook'
import type { ChartEditStorageType } from './index.d'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'

const storageList: ChartEditStorageType[] = getSessionStorage(
  StorageEnum.GO_CHART_STORAGE_LIST
)

const rawPreviewInfo = await getSessionStorageInfo() as unknown as ChartEditStorageType
console.log(rawPreviewInfo)
// @ts-ignore
if (rawPreviewInfo && rawPreviewInfo.isRelease === false) {
  routerTurnByName(PageEnum.REDIRECT_UN_PUBLISH_NAME, true, false)
}
// 大屏不存在 / 已被删除 / 接口异常时 getSessionStorageInfo 会返回空值。
// 以前这里会继续往下执行，useStore(undefined) 抛错后整页就是一块空白画布，
// 用户完全看不出发生了什么。现在退回一份空骨架并跳回「我的项目」，给出明确提示。
const previewInfoMissing = !rawPreviewInfo || !rawPreviewInfo.editCanvasConfig
if (previewInfoMissing) {
  window['$message']?.warning?.('大屏不存在或已被删除，已返回「我的项目」')
  routerTurnByName(PageEnum.BASE_HOME_ITEMS_NAME, true, false)
}
const localStorageInfo: ChartEditStorageType = previewInfoMissing
  ? ({
      editCanvasConfig: {
        width: 1920,
        height: 1080,
        previewScaleType: PreviewScaleEnum.FIT,
        projectName: ''
      },
      componentList: [],
      requestGlobalConfig: {}
    } as unknown as ChartEditStorageType)
  : rawPreviewInfo
setTitle(`预览-${localStorageInfo.editCanvasConfig.projectName}`)

const previewRefStyle = computed(() => {
  return {
    ...getEditCanvasConfigStyle(localStorageInfo.editCanvasConfig),
    ...getFilterStyle(localStorageInfo.editCanvasConfig.filterShow ? localStorageInfo.editCanvasConfig : undefined),
  }
})

const showEntity = computed(() => {
  const type = localStorageInfo .editCanvasConfig.previewScaleType
  return type === PreviewScaleEnum.SCROLL_Y || type === PreviewScaleEnum.SCROLL_X
})

useStore(localStorageInfo)
const { entityRef, previewRef } = useScale(localStorageInfo)
const { show } = useComInstall(localStorageInfo)

// 开启键盘监听
keyRecordHandle()
</script>

<style lang="scss" scoped>
@include go('preview') {
  position: relative;
  height: 100vh;
  width: 100vw;
  @include background-image('background-image');
  &.fit,
  &.full {
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    .go-preview-scale {
      transform-origin: center center;
    }
  }
  &.scrollY {
    overflow-x: hidden;
    .go-preview-scale {
      transform-origin: left top;
    }
  }
  &.scrollX {
    overflow-y: hidden;
    .go-preview-scale {
      transform-origin: left top;
    }
  }
  .go-preview-entity {
    overflow: hidden;
  }
}
</style>
