<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';

import { inviteExpert } from '#/api/cms/expert-consult';
import {
  getSimpleUserList,
  type SystemUserApi,
} from '#/api/system/user';

defineOptions({ name: 'CmsExpertInvite' });

const submitting = ref(false);
const usersLoading = ref(false);
const users = ref<SystemUserApi.User[]>([]);
const form = reactive({
  company: '',
  expertName: '',
  expertUserId: undefined as number | undefined,
  fixedPriceYuan: 300,
  introduction: '',
  professionalTitle: '',
  serviceFields: '',
});

const userOptions = computed(() =>
  users.value
    .map((user) => ({
      label: `${user.nickname || user.username}（${user.username}）`,
      value: Number(user.id),
    })),
);

function selectUser(value: unknown) {
  const userId = Number(value);
  const user = users.value.find((item) => Number(item.id) === userId);
  if (user && !form.expertName.trim()) {
    form.expertName = user.nickname || user.username;
  }
}

function resetForm() {
  form.expertUserId = undefined;
  form.expertName = '';
  form.professionalTitle = '';
  form.company = '';
  form.serviceFields = '';
  form.introduction = '';
  form.fixedPriceYuan = 300;
}

async function submit() {
  if (!form.expertUserId) {
    message.warning('请选择要聘请的企业用户账号');
    return;
  }
  if (!form.expertName.trim() || !form.serviceFields.trim()) {
    message.warning('请填写专家姓名和擅长领域');
    return;
  }
  submitting.value = true;
  try {
    await inviteExpert({
      company: form.company.trim(),
      expertName: form.expertName.trim(),
      expertUserId: form.expertUserId,
      fixedPrice: Math.round(Number(form.fixedPriceYuan) * 100),
      introduction: form.introduction.trim(),
      professionalTitle: form.professionalTitle.trim(),
      serviceFields: form.serviceFields.trim(),
    });
    message.success('专家已聘请并直接启用，无需认证审核。');
    resetForm();
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  usersLoading.value = true;
  try {
    users.value = await getSimpleUserList();
  } finally {
    usersLoading.value = false;
  }
});
</script>

<template>
  <Page>
    <div class="invite-page">
      <header class="invite-hero">
        <div>
          <p>CMS 专家管理</p>
          <h1>聘请专家</h1>
          <span>由企业管理员为本企业用户开通专家身份，提交后直接启用。</span>
        </div>
      </header>

      <Alert
        message="只有企业管理员可以聘请专家；个人账户不能自行申请专家认证。"
        show-icon
        type="info"
      />

      <Card class="invite-card" title="专家资料">
        <Form layout="vertical">
          <div class="form-grid">
            <FormItem label="专家账号" required>
              <Select
                v-model:value="form.expertUserId"
                :filter-option="(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())"
                :loading="usersLoading"
                :options="userOptions"
                placeholder="选择本企业已启用的用户账号"
                show-search
                @change="selectUser"
              />
            </FormItem>
            <FormItem label="专家姓名" required>
              <Input v-model:value="form.expertName" :maxlength="64" />
            </FormItem>
            <FormItem label="职称">
              <Input v-model:value="form.professionalTitle" :maxlength="64" />
            </FormItem>
            <FormItem label="所属单位">
              <Input v-model:value="form.company" :maxlength="128" />
            </FormItem>
            <FormItem label="固定咨询价格（元）" required>
              <InputNumber
                v-model:value="form.fixedPriceYuan"
                :max="100000"
                :min="1"
                style="width: 100%"
              />
            </FormItem>
          </div>
          <FormItem label="擅长领域" required>
            <Input.TextArea
              v-model:value="form.serviceFields"
              :maxlength="500"
              :rows="3"
              show-count
            />
          </FormItem>
          <FormItem label="个人简介">
            <Input.TextArea
              v-model:value="form.introduction"
              :maxlength="4000"
              :rows="5"
              show-count
            />
          </FormItem>
          <Button :loading="submitting" type="primary" @click="submit">
            确认聘请并启用
          </Button>
        </Form>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
.invite-page { display: grid; gap: 16px; }
.invite-hero { padding: 25px 28px; border-radius: 22px; background: linear-gradient(130deg, #14243f, #2c4470); color: #fff; }
.invite-hero p { margin: 0 0 5px; color: #dfc48a; }
.invite-hero h1 { margin: 0 0 8px; font-size: 28px; }
.invite-hero span { color: rgb(229 239 252 / 75%); }
.invite-card { max-width: 920px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
@media (max-width: 720px) { .form-grid { grid-template-columns: 1fr; } }
</style>
