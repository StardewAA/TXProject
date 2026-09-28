<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Input, InputNumber, Tag, message } from 'ant-design-vue';

import {
  applyExpert,
  type CmsExpertApplication,
  getMyExpertApplication,
} from '#/api/cms/expert-consult';

defineOptions({ name: 'PortalExpertApply' });

const submitting = ref(false);
const loading = ref(false);
const application = ref<CmsExpertApplication | null>(null);

const form = reactive({
  company: '',
  expertName: '',
  fixedPriceYuan: 300,
  idCard: '',
  introduction: '',
  phone: '',
  professionalTitle: '',
  serviceFields: '',
  yearsOfExperience: 5,
});

const statusTag = computed(() => {
  const s = application.value?.auditStatus;
  if (s === 1) return { color: 'green', text: '已通过（已上架可被预约）' };
  if (s === 2) return { color: 'red', text: '已驳回' };
  return { color: 'orange', text: '待审核' };
});

async function loadMine() {
  loading.value = true;
  try {
    application.value = await getMyExpertApplication();
    if (application.value) {
      form.expertName = application.value.expertName || '';
      form.professionalTitle = application.value.professionalTitle || '';
      form.company = application.value.company || '';
      form.serviceFields = application.value.serviceFields || '';
      form.introduction = application.value.introduction || '';
      form.phone = application.value.phone || '';
      form.fixedPriceYuan = Math.round((application.value.fixedPrice || 30000) / 100);
      form.yearsOfExperience = application.value.yearsOfExperience ?? 5;
    }
  } catch {
    application.value = null;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!form.expertName.trim() || !form.idCard.trim() || !form.phone.trim() || !form.serviceFields.trim()) {
    message.warning('请填写：真实姓名、身份证号、手机号、服务领域');
    return;
  }
  submitting.value = true;
  try {
    application.value = await applyExpert({
      company: form.company,
      expertName: form.expertName.trim(),
      fixedPrice: Math.max(1, Math.round(form.fixedPriceYuan)) * 100,
      idCard: form.idCard.trim(),
      introduction: form.introduction,
      phone: form.phone.trim(),
      professionalTitle: form.professionalTitle,
      serviceFields: form.serviceFields.trim(),
      yearsOfExperience: form.yearsOfExperience,
    });
    message.success('提交成功：实名核验已通过，已自动上架');
  } finally {
    submitting.value = false;
  }
}

onMounted(loadMine);
</script>

<template>
  <div class="portal-expert-apply">
    <div class="apply-card">
      <div class="apply-head">
        <h2>专家入驻申请</h2>
        <p>
          只需填写 4 项必填信息，系统会自动完成实名核验；核验通过后即可出现在「专家咨询」列表，接受企业预约。
        </p>
      </div>

      <div v-if="application" class="apply-status">
        <span>当前状态：</span>
        <Tag :color="statusTag.color">{{ statusTag.text }}</Tag>
        <span v-if="application.auditRemark" class="remark">
          驳回理由：{{ application.auditRemark }}
        </span>
      </div>

      <div class="apply-form">
        <div class="row">
          <label class="required">真实姓名</label>
          <Input v-model:value="form.expertName" placeholder="与身份证一致" />
        </div>
        <div class="row">
          <label class="required">身份证号</label>
          <Input v-model:value="form.idCard" placeholder="18 位身份证号（用于实名核验）" />
        </div>
        <div class="row">
          <label class="required">手机号</label>
          <Input v-model:value="form.phone" placeholder="用于联系与预约通知" />
        </div>
        <div class="row">
          <label class="required">服务领域</label>
          <Input
            v-model:value="form.serviceFields"
            placeholder="如：防腐保温施工、材料选型、标准解读（多个用、分隔）"
          />
        </div>
        <div class="row">
          <label>职称</label>
          <Input v-model:value="form.professionalTitle" placeholder="如：高级工程师" />
        </div>
        <div class="row">
          <label>所属单位</label>
          <Input v-model:value="form.company" placeholder="如：天信管业集团" />
        </div>
        <div class="row">
          <label>从业年限</label>
          <InputNumber v-model:value="form.yearsOfExperience" :min="0" :max="60" style="width: 100%" />
        </div>
        <div class="row">
          <label>固定咨询价（元）</label>
          <InputNumber v-model:value="form.fixedPriceYuan" :min="1" style="width: 100%" />
        </div>
        <div class="row row-full">
          <label>个人简介</label>
          <Input.TextArea
            v-model:value="form.introduction"
            :rows="4"
            placeholder="简要介绍你的从业经历与擅长方向，会展示给咨询者"
          />
        </div>
      </div>

      <div class="apply-foot">
        <Button :loading="submitting" size="large" type="primary" @click="submit">
          {{ application ? '更新申请' : '提交申请' }}
        </Button>
        <span class="hint">提交即代表同意平台对其公开信息进行核验</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.portal-expert-apply {
  display: flex;
  justify-content: center;
  padding: 32px 16px 60px;
}

.apply-card {
  width: 100%;
  max-width: 860px;
  padding: 32px 36px 28px;
  background: #fff;
  border: 1px solid #e3e8ef;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
}

.apply-head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: #0a2540;
}

.apply-head p {
  margin: 0 0 18px;
  font-size: 13.5px;
  line-height: 1.9;
  color: #5b6675;
}

.apply-status {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  margin-bottom: 18px;
  font-size: 13.5px;
  background: #f4f7fb;
  border-radius: 8px;
}

.apply-status .remark {
  color: #b42318;
}

.apply-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 20px;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row-full {
  grid-column: 1 / -1;
}

.row label {
  font-size: 13px;
  color: #344054;
}

.row label.required::before {
  margin-right: 4px;
  color: #d92d20;
  content: '*';
}

.apply-foot {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-top: 26px;
}

.apply-foot .hint {
  font-size: 12.5px;
  color: #8a93a3;
}

@media (max-width: 720px) {
  .apply-form {
    grid-template-columns: 1fr;
  }
}
</style>
