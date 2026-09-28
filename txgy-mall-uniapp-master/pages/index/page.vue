<!-- 自定义页面：支持装修 -->
<template>
  <s-layout
    :title="state.name"
    navbar="custom"
    :bgStyle="state.page"
    :navbarStyle="state.navigationBar"
    onShareAppMessage
    showLeftButton
  >
    <s-block
      v-for="(item, index) in state.components"
      :key="item.id + '_' + index"
      :styles="item.property.style || {}"
    >
      <s-block-item :type="item.id" :data="item.property" :styles="item.property.style || {}" />
    </s-block>
  </s-layout>
</template>

<script setup>
  import { reactive } from 'vue';
  import { onLoad, onPageScroll } from '@dcloudio/uni-app';
  import DiyApi from '@/sheep/api/promotion/diy';

  const state = reactive({
    name: '',
    components: [],
    navigationBar: {},
    page: {},
  });
  onLoad(async (options = {}) => {
    let id = options.id;

    // #ifdef MP
    // 小程序预览自定义页面
    if (options.scene) {
      try {
        const sceneParams = decodeURIComponent(options.scene).split('=');
        id = sceneParams[1];
      } catch (error) {
        id = undefined;
      }
    }
    // #endif

    if (!id) {
      return;
    }
    try {
      const response = await DiyApi.getDiyPage(id);
      const data =
        response?.code === 0 && response.data && typeof response.data === 'object'
          ? response.data
          : null;
      if (!data) {
        return;
      }
      const property = data.property && typeof data.property === 'object' ? data.property : {};
      state.name = typeof data.name === 'string' ? data.name : '';
      state.components = Array.isArray(property.components)
        ? property.components
            .filter(
              (item) =>
                item &&
                typeof item === 'object' &&
                typeof item.id === 'string' &&
                item.id.length > 0,
            )
            .map((item) => {
              const property =
                item.property && typeof item.property === 'object' && !Array.isArray(item.property)
                  ? item.property
                  : {};
              return {
                ...item,
                property: {
                  ...property,
                  style:
                    property.style &&
                    typeof property.style === 'object' &&
                    !Array.isArray(property.style)
                      ? property.style
                      : {},
                },
              };
            })
        : [];
      state.navigationBar =
        property.navigationBar && typeof property.navigationBar === 'object'
          ? property.navigationBar
          : {};
      state.page = property.page && typeof property.page === 'object' ? property.page : {};
    } catch (error) {
      // 请求失败时保留空页面，避免 DIY 接口异常导致白屏。
      state.components = [];
    }
  });

  onPageScroll(() => {});
</script>

<style></style>
