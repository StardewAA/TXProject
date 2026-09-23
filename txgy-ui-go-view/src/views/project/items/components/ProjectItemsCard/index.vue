<template>
  <div v-if="cardData" class="go-items-list-card">
    <n-card hoverable size="small">
      <div class="list-content">
        <!-- 顶部按钮 -->
        <div class="list-content-top">
          <mac-os-control-btn
            class="top-btn"
            :hidden="['remove']"
            @close="deleteHandle"
            @resize="resizeHandle"
         ></mac-os-control-btn>
        </div>
        <!-- 中间 -->
        <div class="list-content-img" @click="resizeHandle">
          <img
            v-if="previewSrc"
            :src="previewSrc"
            :alt="cardData.title"
            @error="previewError = true"
          />
          <n-text v-else depth="3" class="list-content-empty">
            <n-icon :size="30">
              <component :is="ImageIcon"></component>
            </n-icon>
            <span>{{ $t('project.no_preview') }}</span>
          </n-text>
        </div>
      </div>
      <template #action>
        <div class="go-flex-items-center list-footer" justify="space-between">
          <n-text class="go-ellipsis-1">
            {{ cardData.title || cardData.id || '未命名' }}
          </n-text>
          <!-- 工具 -->
          <div class="go-flex-items-center list-footer-ri">
            <n-space>
              <n-text>
                <n-badge
                  class="go-animation-twinkle"
                  dot
                  :color="cardData.release ? '#34c749' : '#fcbc40'"
              ></n-badge>
                {{
                  cardData.release
                    ? $t('project.release')
                    : $t('project.unreleased')
                }}
              </n-text>

              <template v-for="item in fnBtnList" :key="item.key">
                <template v-if="item.key === 'select'">
                  <n-dropdown
                    trigger="hover"
                    placement="bottom"
                    :options="selectOptions"
                    :show-arrow="true"
                    @select="handleSelect"
                  >
                    <n-button size="small">
                      <template #icon>
                        <component :is="item.icon"></component>
                      </template>
                    </n-button>
                  </n-dropdown>
                </template>

                <n-tooltip v-else placement="bottom" trigger="hover">
                  <template #trigger>
                    <n-button size="small" @click="handleSelect(item.key)">
                      <template #icon>
                        <component :is="item.icon"></component>
                      </template>
                    </n-button>
                  </template>
                  <component :is="item.label"></component>
                </n-tooltip>
              </template>
            </n-space>
          <!-- end -->
          </div>
        </div>
      </template>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, PropType, watch } from 'vue'
import { renderIcon, renderLang } from '@/utils'
import { icon } from '@/plugins'
import { MacOsControlBtn } from '@/components/Tips/MacOsControlBtn'
import { Chartype } from '../../index.d'
const {
  ImageIcon,
  EllipsisHorizontalCircleSharpIcon,
  CopyIcon,
  TrashIcon,
  PencilIcon,
  DownloadIcon,
  BrowsersOutlineIcon,
  HammerIcon,
  SendIcon
} = icon.ionicons5

const emit = defineEmits(['preview', 'delete', 'resize', 'edit', 'release'])

const props = defineProps({
  cardData: Object as PropType<Chartype>
})

// 缩略图地址：为空（项目还没保存过）或图片加载失败时返回空串，由模板展示占位，避免出现破图
// 缩略图文件名固定为 go-view/<id>_index_preview.png，重新保存后地址不变，
// 所以带一个「本次进入页面」的固定时间戳做缓存失效；不能写在模板里，否则每次渲染都会重新请求
const previewTime = Date.now()
const previewError = ref(false)
const previewSrc = computed(() => {
  if (previewError.value) return ''
  const url = String(props.cardData?.image || '').trim()
  return url ? `${url}?time=${previewTime}` : ''
})

watch(
  () => props.cardData?.image,
  () => {
    previewError.value = false
  }
)

const fnBtnList = reactive([
  {
    label: renderLang('global.r_edit'),
    key: 'edit',
    icon: renderIcon(HammerIcon)
  },
  {
    lable: renderLang('global.r_more'),
    key: 'select',
    icon: renderIcon(EllipsisHorizontalCircleSharpIcon)
  }
])

const selectOptions = ref([
  {
    label: renderLang('global.r_preview'),
    key: 'preview',
    icon: renderIcon(BrowsersOutlineIcon)
  },
  {
    label: renderLang('global.r_copy'),
    key: 'copy',
    icon: renderIcon(CopyIcon),
    disabled: true
  },
  {
    label: renderLang('global.r_rename'),
    key: 'rename',
    icon: renderIcon(PencilIcon),
    disabled: true
  },
  {
    type: 'divider',
    key: 'd1'
  },
  {
    label: props.cardData?.release
      ? renderLang('global.r_unpublish')
      : renderLang('global.r_publish'),
    key: 'release',
    icon: renderIcon(SendIcon)
  },
  {
    label: renderLang('global.r_download'),
    key: 'download',
    icon: renderIcon(DownloadIcon),
    disabled: true
  },
  {
    type: 'divider',
    key: 'd2'
  },
  {
    label: renderLang('global.r_delete'),
    key: 'delete',
    icon: renderIcon(TrashIcon)
  }
])

const handleSelect = (key: string) => {
  switch (key) {
    case 'preview':
      previewHandle()
      break
    case 'delete':
      deleteHandle()
      break
    case 'release':
      releaseHandle()
      break
    case 'edit':
      editHandle()
      break
  }
}

// 预览处理
const previewHandle = () => {
  emit('preview', props.cardData)
}

// 删除处理
const deleteHandle = () => {
  emit('delete', props.cardData)
}

// 编辑处理
const editHandle = () => {
  emit('edit', props.cardData)
}

// 编辑处理
const releaseHandle = () => {
  emit('release', props.cardData)
}

// 放大处理
const resizeHandle = () => {
  emit('resize', props.cardData)
}
</script>

<style lang="scss" scoped>
$contentHeight: 180px;
@include go('items-list-card') {
  position: relative;
  border-radius: $--border-radius-base;
  border: 1px solid rgba(0, 0, 0, 0);
  @extend .go-transition;
  &:hover {
    @include hover-border-color('hover-border-color');
  }
  .list-content {
    margin-top: 20px;
    margin-bottom: 5px;
    cursor: pointer;
    border-radius: $--border-radius-base;
    @include background-image('background-point');
    @extend .go-point-bg;
    &-top {
      position: absolute;
      top: 10px;
      left: 10px;
      height: 22px;
    }
    &-img {
      height: $contentHeight;
      @extend .go-flex-center;
      @extend .go-border-radius;
      img {
        max-width: 100%;
        max-height: $contentHeight;
        object-fit: contain;
        @extend .go-border-radius;
      }
      .list-content-empty {
        @extend .go-flex-center;
        height: 100%;
        flex-direction: column;
        gap: 8px;
        span {
          font-size: 13px;
        }
      }
    }
  }
  .list-footer {
    flex-wrap: nowrap;
    justify-content: space-between;
    line-height: 30px;
    &-ri {
      justify-content: flex-end;
      min-width: 180px;
    }
  }
}
</style>
