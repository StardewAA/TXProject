<template>
  <view class="uni-file-picker__container">
    <view class="file-picker__box" v-for="(url, index) in list" :key="index" :style="boxStyle">
      <view class="file-picker__box-content" :style="borderStyle">
        <image
          class="file-image"
          :src="getImageUrl(url)"
          mode="aspectFill"
          @click.stop="previewImage(url, index)"
        ></image>
        <view v-if="delIcon && !readonly" class="icon-del-box" @click.stop="delFile(index)">
          <view class="icon-del"></view>
          <view class="icon-del rotate"></view>
        </view>
        <!-- <view v-if="item.errMsg" class="file-picker__mask" @click.stop="uploadFiles(item, index)">
          点击重试
        </view> -->
      </view>
    </view>
    <view v-if="list.length < limit && !readonly" class="file-picker__box" :style="boxStyle">
      <view class="file-picker__box-content is-add" :style="borderStyle" @click="choose">
        <slot>
          <view class="icon-add"></view>
          <view class="icon-add rotate"></view>
        </slot>
      </view>
    </view>
  </view>
</template>

<script>
  import sheep from '@/sheep';
  import {
    isFileServiceUrl,
    loadPrivateImage,
    releasePrivateImage,
    subscribePrivateImageSession,
  } from '@/sheep/helper/private-image';

  export default {
    name: 'uploadImage',
    emits: ['uploadFiles', 'choose', 'delFile'],
    props: {
      filesList: {
        type: [Array, String],
        default() {
          return [];
        },
      },
      disabled: {
        type: Boolean,
        default: false,
      },
      disablePreview: {
        type: Boolean,
        default: false,
      },
      limit: {
        type: [Number, String],
        default: 9,
      },
      imageStyles: {
        type: Object,
        default() {
          return {
            width: 'auto',
            height: 'auto',
            border: {},
          };
        },
      },
      delIcon: {
        type: Boolean,
        default: true,
      },
      readonly: {
        type: Boolean,
        default: false,
      },
    },
    data() {
      return {
        // 文件服务地址 -> 已带登录态取回的本地/blob 路径
        resolved: {},
        // 本组件已登记引用（持有中）的文件服务地址；与 loadPrivateImage 的引用计数一一对应，
        // 保证「下载中重复触发」「列表项被删」都不会造成重复计数或泄漏。
        owned: {},
        // 会话失效监听的取消函数
        unsubscribeSession: null,
        // 渲染代数：会话切换即自增，用于丢弃上一轮会话的异步取图结果
        renderRevision: 0,
      };
    },
    watch: {
      list: {
        handler() {
          this.syncFileServiceImages();
        },
        immediate: true,
      },
    },
    mounted() {
      // 切换账号 / 退出登录时：全局缓存已被作废，这里必须**清掉已渲染的旧图并重新取图**，
      // 否则页面上仍挂着上一个账号的图片（即使底层缓存已经清了）。
      this.unsubscribeSession = subscribePrivateImageSession(() => {
        this.renderRevision += 1;
        this.owned = {};
        this.resolved = {};
        this.syncFileServiceImages();
      });
    },
    beforeUnmount() {
      if (typeof this.unsubscribeSession === 'function') {
        this.unsubscribeSession();
        this.unsubscribeSession = null;
      }
      this.releaseOwnedImages();
    },
    computed: {
      list() {
        if (typeof this.filesList === 'string') {
          if (this.filesList) {
            return [this.filesList];
          } else {
            return [];
          }
        }
        return this.filesList;
      },
      styles() {
        let styles = {
          width: 'auto',
          height: 'auto',
          border: {},
        };
        return Object.assign(styles, this.imageStyles);
      },
      boxStyle() {
        const { width = 'auto', height = 'auto' } = this.styles;
        let obj = {};
        if (height === 'auto') {
          if (width !== 'auto') {
            obj.height = this.value2px(width);
            obj['padding-top'] = 0;
          } else {
            obj.height = 0;
          }
        } else {
          obj.height = this.value2px(height);
          obj['padding-top'] = 0;
        }

        if (width === 'auto') {
          if (height !== 'auto') {
            obj.width = this.value2px(height);
          } else {
            obj.width = '33.3%';
          }
        } else {
          obj.width = this.value2px(width);
        }

        let classles = '';
        for (let i in obj) {
          classles += `${i}:${obj[i]};`;
        }
        return classles;
      },
      borderStyle() {
        let { border } = this.styles;
        let obj = {};
        const widthDefaultValue = 1;
        const radiusDefaultValue = 3;
        if (typeof border === 'boolean') {
          obj.border = border ? '1px #eee solid' : 'none';
        } else {
          let width = (border && border.width) || widthDefaultValue;
          width = this.value2px(width);
          let radius = (border && border.radius) || radiusDefaultValue;
          radius = this.value2px(radius);
          obj = {
            'border-width': width,
            'border-style': (border && border.style) || 'solid',
            'border-color': (border && border.color) || '#eee',
            'border-radius': radius,
          };
        }
        let classles = '';
        for (let i in obj) {
          classles += `${i}:${obj[i]};`;
        }
        return classles;
      },
    },
    methods: {
      /**
       * 渲染用的图片地址。
       *
       * 文件服务签发的地址（/infra/file/）不能直接交给 <image>：那是匿名请求，
       * 私有资源必然 401。这里统一改走「带 token 取流 + 本地路径」，
       * 未解析出来之前返回空串（显示占位），避免先闪一次 401 破图。
       */
      getImageUrl(url) {
        if ('blob:http:' === url.substr(0, 10)) {
          return url;
        }
        if (this.resolved[url]) {
          return this.resolved[url];
        }
        if (isFileServiceUrl(url)) {
          return '';
        }
        return sheep.$url.cdn(url);
      },
      /**
       * 同步本组件对文件服务图片的引用：只为「新增且未持有」的地址登记一次引用，
       * 并对已经从列表里移除的地址释放引用（否则 blob 会泄漏到页面卸载）。
       */
      async syncFileServiceImages() {
        const current = this.list.filter((url) => isFileServiceUrl(url));
        const currentSet = {};
        current.forEach((url) => {
          currentSet[url] = true;
        });
        // 1. 释放已不在列表中的
        Object.keys(this.owned).forEach((url) => {
          if (!currentSet[url]) {
            releasePrivateImage(url);
            delete this.owned[url];
            const nextResolved = { ...this.resolved };
            delete nextResolved[url];
            this.resolved = nextResolved;
          }
        });
        // 2. 先登记引用（同步），再异步下载，避免下载期间重复触发导致重复计数
        const pending = [];
        current.forEach((url) => {
          if (!this.owned[url]) {
            this.owned[url] = true;
            pending.push(url);
          }
        });
        // 3. 记录本轮渲染代数：会话切换后旧一轮的下载结果必须被丢弃，
        //    否则会把上一个账号取回的图片写进当前页面。
        const revision = this.renderRevision;
        await Promise.all(
          pending.map(async (url) => {
            try {
              const path = await loadPrivateImage(url);
              if (revision === this.renderRevision && this.owned[url]) {
                this.resolved = { ...this.resolved, [url]: path };
              }
            } catch (error) {
              // 读不到就保持占位：不把原始 URL 暴露给 <image>，避免 401 与地址泄露。
              console.warn('私有图片读取失败', error);
            }
          }),
        );
      },
      releaseOwnedImages() {
        Object.keys(this.owned).forEach((url) => {
          releasePrivateImage(url);
        });
        this.owned = {};
        this.resolved = {};
      },
      uploadFiles(item, index) {
        this.$emit('uploadFiles', item);
      },
      choose() {
        this.$emit('choose');
      },
      delFile(index) {
        this.$emit('delFile', index);
      },
      previewImage(img, index) {
        let urls = [];
        if (Number(this.limit) === 1 && this.disablePreview && !this.disabled) {
          this.$emit('choose');
        }
        if (this.disablePreview) return;
        this.list.forEach((i) => {
          const resolved = this.getImageUrl(i);
          if (resolved) urls.push(resolved);
        });
        if (!urls.length) return;
        uni.previewImage({
          urls: urls,
          current: Math.min(index, urls.length - 1),
        });
      },
      value2px(value) {
        if (typeof value === 'number') {
          value += 'px';
        } else {
          if (value.indexOf('%') === -1) {
            value = value.indexOf('px') !== -1 ? value : value + 'px';
          }
        }
        return value;
      },
    },
  };
</script>

<style lang="scss">
  .uni-file-picker__container {
    /* #ifndef APP-NVUE */
    display: flex;
    box-sizing: border-box;
    /* #endif */
    flex-wrap: wrap;
    margin: -5px;
  }

  .file-picker__box {
    position: relative;
    // flex: 0 0 33.3%;
    width: 33.3%;
    height: 0;
    padding-top: 33.33%;
    /* #ifndef APP-NVUE */
    box-sizing: border-box;
    /* #endif */
  }

  .file-picker__box-content {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    margin: 5px;
    border: 1px #eee solid;
    border-radius: 5px;
    overflow: hidden;
  }

  .file-picker__progress {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    /* border: 1px red solid; */
    z-index: 2;
  }

  .file-picker__progress-item {
    width: 100%;
  }

  .file-picker__mask {
    /* #ifndef APP-NVUE */
    display: flex;
    /* #endif */
    justify-content: center;
    align-items: center;
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    left: 0;
    color: #fff;
    font-size: 12px;
    background-color: rgba(0, 0, 0, 0.4);
  }

  .file-image {
    width: 100%;
    height: 100%;
  }

  .is-add {
    /* #ifndef APP-NVUE */
    display: flex;
    /* #endif */
    align-items: center;
    justify-content: center;
  }

  .icon-add {
    width: 50px;
    height: 5px;
    background-color: #f1f1f1;
    border-radius: 2px;
  }

  .rotate {
    position: absolute;
    transform: rotate(90deg);
  }

  .icon-del-box {
    /* #ifndef APP-NVUE */
    display: flex;
    /* #endif */
    align-items: center;
    justify-content: center;
    position: absolute;
    top: 3px;
    right: 3px;
    height: 26px;
    width: 26px;
    border-radius: 50%;
    background-color: rgba(0, 0, 0, 0.5);
    z-index: 2;
    transform: rotate(-45deg);
  }

  .icon-del {
    width: 15px;
    height: 2px;
    background-color: #fff;
    border-radius: 2px;
  }
</style>
