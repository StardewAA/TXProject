<!-- 收货地址的新增/编辑 -->
<template>
  <s-layout :title="state.model.id ? '编辑地址' : '新增地址'">
    <view v-if="state.expired" class="map-card">登录账号已变化，请返回地址列表重新进入。</view>
    <view v-else>
    <view v-if="state.loadError" class="map-card" role="alert">
      {{ state.loadError }}
      <button size="mini" @tap="loadAddress">重试加载地址</button>
    </view>
    <uni-forms
      ref="addressFormRef"
      v-model="state.model"
      :rules="rules"
      validateTrigger="bind"
      labelWidth="160"
      labelAlign="left"
      border
      :labelStyle="{ fontWeight: 'bold' }"
    >
      <view class="bg-white form-box ss-p-x-30">
        <uni-forms-item name="name" label="收货人" class="form-item">
          <uni-easyinput
            v-model="state.model.name"
            placeholder="请填写收货人姓名"
            :inputBorder="false"
            placeholderStyle="color:#BBBBBB;font-size:30rpx;font-weight:400;line-height:normal"
          />
        </uni-forms-item>

        <uni-forms-item name="mobile" label="手机号" class="form-item">
          <uni-easyinput
            v-model="state.model.mobile"
            type="number"
            placeholder="请输入手机号"
            :inputBorder="false"
            placeholderStyle="color:#BBBBBB;font-size:30rpx;font-weight:400;line-height:normal"
          >
          </uni-easyinput>
        </uni-forms-item>
        <uni-forms-item
          name="areaName"
          label="省市区"
          @tap="state.showRegion = true"
          class="form-item"
        >
          <uni-easyinput
            v-model="state.model.areaName"
            disabled
            :inputBorder="false"
            :styles="{ disableColor: '#fff', color: '#333' }"
            placeholderStyle="color:#BBBBBB;font-size:30rpx;font-weight:400;line-height:normal"
            placeholder="请选择省市区"
          >
            <template v-slot:right>
              <uni-icons type="right" />
            </template>
          </uni-easyinput>
        </uni-forms-item>
        <uni-forms-item
          name="detailAddress"
          label="详细地址"
          :formItemStyle="{ alignItems: 'flex-start' }"
          :labelStyle="{ lineHeight: '5em' }"
          class="textarea-item"
        >
          <uni-easyinput
            :inputBorder="false"
            type="textarea"
            v-model="state.model.detailAddress"
            placeholderStyle="color:#BBBBBB;font-size:30rpx;font-weight:400;line-height:normal"
            placeholder="请输入详细地址"
            clearable
          />
        </uni-forms-item>
      </view>
      <view class="map-card">
        <view class="map-title">地址地图（可选）</view>
        <view>{{ state.mapMessage }}</view>
        <view v-if="savedLocation" class="map-detail">{{ savedLocation }}</view>
        <view v-if="state.locationCleared" class="map-detail">地址点位已清除；保存后将使用手工填写的地址。</view>
        <button size="mini" @tap="openMap">查看地图选点</button>
        <button v-if="savedLocation" size="mini" @tap="removeLocation">清除地址点位</button>
      </view>
      <view class="ss-m-y-20 bg-white ss-p-x-30 ss-flex ss-row-between ss-col-center default-box">
        <view class="default-box-title"> 设为默认地址 </view>
        <su-switch style="transform: scale(0.8)" v-model="state.model.defaultStatus" />
      </view>
    </uni-forms>
    <su-fixed bottom :opacity="false" bg="" placeholder :noFixed="false" :index="10">
      <view class="footer-box ss-flex-col ss-row-between ss-p-20">
        <view class="ss-m-b-20">
          <button class="ss-reset-button save-btn ui-Shadow-Main" :disabled="state.saving || (state.addressId && !state.loaded)" @tap="onSave">{{ state.saving ? '正在保存…' : '保存' }}</button>
        </view>
        <button v-if="state.model.id" :disabled="state.saving" class="ss-reset-button cancel-btn" @tap="onDelete">
          删除
        </button>
      </view>
    </su-fixed>

    <!-- 省市区弹窗 -->
    <su-region-picker
      :show="state.showRegion"
      @cancel="state.showRegion = false"
      @confirm="onRegionConfirm"
    />
    <su-popup :show="state.showMap" :showClose="true" round="10" @close="state.showMap = false">
      <view class="map-panel">
        <view class="map-title">地图选点</view>
        <view>{{ state.mapMessage }}</view>
        <view class="map-detail">省市区、街道门牌和工地入口请在地址表单中填写。地图点位不能替代详细地址。</view>
        <button @tap="state.showMap = false">继续手工填写</button>
      </view>
    </su-popup>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref, reactive, unref, computed, watch } from 'vue';
  import sheep from '@/sheep';
  import { onLoad, onHide, onShow } from '@dcloudio/uni-app';
  import { isEmpty } from 'lodash-es';
  import { mobile } from '@/sheep/validate/form';
  import AreaApi from '@/sheep/api/system/area';
  import AddressApi from '@/sheep/api/member/address';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { manualMapMessage, normalizeLocationCapability, locationSummary, clearAddressLocation, createLatestTask } from '@/sheep/helper/fulfillment-view.mjs';

  const addressFormRef = ref(null);
  const state = reactive({
    showRegion: false,
    showMap: false, mapMessage: manualMapMessage, locationCleared: false,
    addressId: '', loaded: false, loadError: '', loading: false, saving: false, expired: false,
    model: {
      name: '',
      mobile: '',
      detailAddress: '',
      defaultStatus: false,
      areaName: '',
    },
    rules: {},
  });
  const task = useTenantTask(() => {
    state.expired = true; state.model = {}; state.showMap = false; state.showRegion = false;
  });
  const addressLoads = createLatestTask(task.current);
  let originalLocationAddress = '';
  const locationAddress = model => `${model.areaId || ''}|${model.detailAddress || ''}`;
  const savedLocation = computed(() => locationSummary(state.model));
  function removeLocation() {
    if (!task.current()) return;
    state.model = clearAddressLocation(state.model); state.locationCleared = true;
  }
  watch(() => locationAddress(state.model), value => {
    if (savedLocation.value && value !== originalLocationAddress) removeLocation();
  });
  async function openMap() {
    if (!task.current()) return;
    state.showMap = true;
    const current = task.capture();
    try {
      const response = await AddressApi.getLocationCapability();
      if (response?.code !== 0) throw new Error('unavailable');
      if (current()) state.mapMessage = normalizeLocationCapability(response.data).message;
    } catch { if (current()) state.mapMessage = '地图服务暂不可用，请继续手工填写地址，不影响保存和下单。'; }
  }
  async function loadAddress() {
    if (!task.current() || !state.addressId || state.loading) return;
    const current = addressLoads.begin(); state.loading = true; state.loadError = '';
    try {
      const response = await AddressApi.getAddress(state.addressId);
      if (!current()) return;
      if (response?.code !== 0 || !response.data || String(response.data.id) !== state.addressId) throw new Error('unavailable');
      originalLocationAddress = locationAddress(response.data);
      state.model = response.data; state.loaded = true; state.locationCleared = false;
    } catch { if (current()) state.loadError = '地址加载失败，请重试；未成功加载前不能保存。'; }
    finally { if (current()) state.loading = false; }
  }
  onHide(() => { task.invalidate(); addressLoads.invalidate(); state.loading = false; state.showMap = false; state.showRegion = false; });
  onShow(() => { if (state.addressId && !state.loaded && !state.loading) loadAddress(); });

  const rules = {
    name: {
      rules: [
        {
          required: true,
          errorMessage: '请输入收货人姓名',
        },
      ],
    },
    mobile,
    detailAddress: {
      rules: [
        {
          required: true,
          errorMessage: '请输入详细地址',
        },
      ],
    },
    areaName: {
      rules: [
        {
          required: true,
          errorMessage: '请选择您的位置',
        },
      ],
    },
  };

  // 确认选择地区
  const onRegionConfirm = (e) => {
    if (!task.current()) return;
    state.model.areaName = `${e.province_name} ${e.city_name} ${e.district_name}`;
    state.model.areaId = e.district_id;
    state.showRegion = false;
  };

  // 获得地区数据
  const getAreaData = () => {
    if (isEmpty(uni.getStorageSync('areaData'))) {
      AreaApi.getAreaTree()
        .then((res) => {
          if (res?.code === 0 && res.data) {
            uni.setStorageSync('areaData', res.data);
          }
        })
        .catch(() => {});
    }
  };

  // 保存收货地址
  const onSave = async () => {
    if (!task.current() || state.saving || (state.addressId && !state.loaded)) return;
    const current = task.capture();
    state.saving = true;
    // 参数校验
    const validate = await unref(addressFormRef)
      .validate()
      .catch(() => null);
    if (!validate || !current()) {
      if (task.current()) state.saving = false;
      return;
    }

    // 提交请求
    const formData = {
      ...state.model,
    };
    delete formData.locationConfirmedAt;
    state.saving = true;
    try {
      const response =
        state.model.id > 0
          ? await AddressApi.updateAddress(formData)
          : await AddressApi.createAddress(formData);
      if (current() && response?.code === 0) {
        sheep.$router.back();
      }
    } catch (error) {
      // request 层负责展示错误提示。
    } finally { if (task.current()) state.saving = false; }
  };

  // 删除收货地址
  const onDelete = () => {
    if (!task.current() || state.saving) return;
    const current = task.capture(), id = state.model.id;
    uni.showModal({
      title: '提示',
      content: '确认删除此收货地址吗？',
      success: async function (res) {
        if (!res.confirm || !current()) {
          return;
        }
        try {
          const response = await AddressApi.deleteAddress(id);
          if (current() && response?.code === 0) {
            sheep.$router.back();
          }
        } catch (error) {
          // request 层负责展示错误提示。
        }
      },
    });
  };

  onLoad(async (options = {}) => {
    const current = task.capture();
    // 获得地区数据
    getAreaData();
    // 情况一：基于 id 获得收件地址
    if (options.id) {
      state.addressId = String(options.id);
      await loadAddress();
      return;
    }
    if (!current()) return;
    // 情况二：微信导入
    if (options.data) {
      let data;
      let dataText = typeof options.data === 'string' ? options.data : '';
      // 路由层会对 query 参数做 encodeURIComponent，H5 某些版本还会再次编码。
      // 在每一轮先尝试 JSON.parse，失败后再解码，避免把地址中的合法百分号误解码。
      for (let index = 0; index < 4 && !data; index++) {
        try {
          data = JSON.parse(dataText);
          break;
        } catch (error) {
          try {
            const decoded = decodeURIComponent(dataText);
            if (decoded === dataText) {
              break;
            }
            dataText = decoded;
          } catch (decodeError) {
            break;
          }
        }
      }
      if (!data || typeof data !== 'object') {
        sheep.$helper.toast('地址参数不正确');
        return;
      }
      const areaData = uni.getStorageSync('areaData');
      if (!Array.isArray(areaData)) {
        sheep.$helper.toast('地区数据加载失败，请稍后重试');
        return;
      }
      // 微信地址导入在 H5 中会经过路由 query 编码，且不同运行时可能会重复编码。
      // 先尝试解析，再逐层解码，兼容 `%7B...%7D` 与 `%257B...%257D` 两种形式。
      const findAreaByName = (areas, name) =>
        Array.isArray(areas) ? areas.find((item) => item?.name === name) : undefined;

      let provinceObj = findAreaByName(areaData, data.province_name);
      const cityObj = provinceObj
        ? findAreaByName(provinceObj.children, data.city_name)
        : undefined;
      const districtObj = cityObj
        ? findAreaByName(cityObj.children, data.district_name)
        : undefined;
      const areaId = (districtObj || cityObj || provinceObj)?.id;
      if (!areaId) {
        sheep.$helper.toast('无法识别收货地区');
        return;
      }

      state.model = {
        ...state.model,
        areaId,
        areaName: [data.province_name, data.city_name, data.district_name]
          .filter(Boolean)
          .join(' '),
        defaultStatus: false,
        detailAddress: data.address || '',
        mobile: data.mobile || '',
        name: data.consignee || '',
      };
    }
  });
</script>

<style lang="scss" scoped>
  .map-card { padding: 24rpx 30rpx; margin: 20rpx 0; background: white; color: #45625c; line-height: 1.7; font-size: 26rpx; }
  .map-title { font-size: 30rpx; color: #263c37; font-weight: bold; margin-bottom: 12rpx; }
  .map-detail { color: #666; margin: 14rpx 0; overflow-wrap: anywhere; }
  .map-panel { padding: 60rpx 30rpx 40rpx; line-height: 1.8; min-height: 280rpx; }
  .map-card button { margin: 14rpx 20rpx 0 0; }
  :deep() {
    .uni-forms-item__label .label-text {
      font-size: 28rpx !important;
      color: #333333 !important;
      line-height: normal !important;
    }

    .uni-easyinput__content-input {
      font-size: 28rpx !important;
      color: #333333 !important;
      line-height: normal !important;
      padding-left: 0 !important;
    }

    .uni-easyinput__content-textarea {
      font-size: 28rpx !important;
      color: #333333 !important;
      line-height: normal !important;
      margin-top: 8rpx !important;
    }

    .uni-icons {
      font-size: 40rpx !important;
    }

    .is-textarea-icon {
      margin-top: 22rpx;
    }

    .is-disabled {
      color: #333333;
    }
  }

  .default-box {
    width: 100%;
    box-sizing: border-box;
    height: 100rpx;

    .default-box-title {
      font-size: 28rpx;
      color: #333333;
      line-height: normal;
    }
  }

  .footer-box {
    .save-btn {
      width: 710rpx;
      height: 80rpx;
      border-radius: 40rpx;
      background: linear-gradient(90deg, var(--ui-BG-Main), var(--ui-BG-Main-gradient));
      color: $white;
    }

    .cancel-btn {
      width: 710rpx;
      height: 80rpx;
      border-radius: 40rpx;
      background: var(--ui-BG);
    }
  }
</style>
