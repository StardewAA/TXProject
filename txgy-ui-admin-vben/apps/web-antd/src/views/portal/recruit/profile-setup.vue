<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { getPortalRecruitPostTypeList, type HrmPortalRecruitApi } from '#/api/hrm/portal/recruit';
import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import { createPortalResume, RECRUIT_PROFILE_TITLE } from '#/api/hrm/portal/resume';
import { uploadFile } from '#/api/infra/file';

const props = defineProps<{
  embedded?: boolean;
}>();
const emit = defineEmits<{
  completed: [];
}>();

type ProfileForm = {
  avatar?: string;
  birthMonth?: string;
  city?: string;
  company?: string;
  education?: string;
  educationEnd?: string;
  educationStart?: string;
  employmentStatus?: string;
  gender?: number;
  identity?: 'student' | 'worker';
  jobEnd?: string;
  jobStart?: string;
  major?: string;
  name?: string;
  postTypeId?: number;
  postTypeName?: string;
  school?: string;
  schoolType?: string;
  strengths?: string;
  workStart?: string;
};

const router = useRouter();
const userStore = useUserStore();
const step = ref(1);
const submitting = ref(false);
const postModalOpen = ref(false);
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const activePostParent = ref<number>();
const avatarInput = ref<HTMLInputElement>();
const form = reactive<ProfileForm>({
  name: userStore.userInfo?.nickname || '',
  gender: 1,
  identity: 'worker',
  employmentStatus: '在职-月内到岗',
  education: '本科',
  schoolType: '全日制',
});

const steps = [
  { title: '基础信息', headline: '让我们先认识一下你', icon: 'lucide:user-round' },
  { title: '求职期望', headline: '想找什么样的工作？', icon: 'lucide:compass' },
  { title: '教育经历', headline: '简述一下你的学历信息', icon: 'lucide:graduation-cap' },
  { title: '工作经历', headline: '你最近一份工作情况是？', icon: 'lucide:briefcase-business' },
  { title: '个人优势', headline: '分享一下你的个人优势吧', icon: 'lucide:sparkles' },
  { title: '上传头像', headline: '最后一步，添加专属头像', icon: 'lucide:circle-user-round' },
];
const educationOptions = ['初中及以下', '中专/中技', '高中', '大专', '本科', '硕士', '博士'];
const simpleEducation = computed(() => ['初中及以下', '高中'].includes(form.education || ''));
const parentTypes = computed(() => postTypes.value.filter((item) => !item.parentId));
const childTypes = computed(() => postTypes.value.filter((item) => item.parentId === activePostParent.value));
const current = computed(() => steps[step.value - 1]!);
const avatarPreviewUrl = computed(() => getHrmRecruitFilePreviewUrl(form.avatar));

function validateStep() {
  const valid = [
    Boolean(form.name?.trim() && form.birthMonth && form.gender && form.identity && form.employmentStatus),
    Boolean(form.city && form.postTypeId),
    Boolean(form.education && form.educationStart && form.educationEnd && (simpleEducation.value || (form.school && form.major && form.schoolType))),
    true,
    Boolean(form.strengths && form.strengths.trim().length >= 3),
    Boolean(form.avatar),
  ][step.value - 1];
  if (!valid) message.warning('请先完善当前页面的必填信息');
  return valid;
}

function next() {
  if (validateStep() && step.value < 6) step.value += 1;
}

function previous() {
  if (step.value > 1) step.value -= 1;
}

function skipWork() {
  form.company = '';
  form.jobStart = '';
  form.jobEnd = '';
  step.value = 5;
}

async function openPostModal() {
  postModalOpen.value = true;
  if (!postTypes.value.length) {
    postTypes.value = (await getPortalRecruitPostTypeList()) || [];
    activePostParent.value = parentTypes.value[0]?.id;
  }
}

function choosePost(type: HrmPortalRecruitApi.PostType) {
  form.postTypeId = type.id;
  form.postTypeName = type.name;
  postModalOpen.value = false;
}

async function handleAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) {
    message.warning('请选择 5MB 以内的图片');
    return;
  }
  form.avatar = (await uploadFile({ file, directory: 'hrm/personal/avatar' })) as string;
}

async function finish() {
  if (!validateStep()) return;
  submitting.value = true;
  try {
    await createPortalResume({
      title: RECRUIT_PROFILE_TITLE,
      content: JSON.stringify({ ...form, completedAt: new Date().toISOString() }),
      remark: '个人求职资料',
    });
    message.success('求职资料已完善');
    if (props.embedded) {
      emit('completed');
    } else {
      await router.replace('/portal/recruit');
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div :class="{ 'setup-page--modal': embedded }" class="setup-page">
    <div class="setup-shell">
      <aside class="setup-aside">
        <h1>{{ current.headline }}</h1>
        <div class="industry-visual" aria-hidden="true">
          <span class="visual-ring ring-one"></span><span class="visual-ring ring-two"></span>
          <span class="visual-icon"><IconifyIcon :icon="current.icon" /></span>
          <span class="visual-label">职业档案 · {{ String(step).padStart(2, '0') }}</span>
        </div>
        <div class="resume-tip"><IconifyIcon icon="lucide:file-up" /><div><strong>已有电子简历？</strong><small>完成资料后，可在个人主页上传附件简历</small></div></div>
      </aside>

      <main class="setup-main">
        <header class="step-header"><strong>{{ current.title }} {{ step }}/6</strong><div class="progress"><i :style="{ width: `${step / 6 * 100}%` }"></i></div></header>

        <section v-if="step === 1" class="form-stack">
          <label>姓名<a-input v-model:value="form.name" maxlength="25" placeholder="请输入真实姓名" /></label>
          <label>性别<div class="choice-row"><button v-for="item in [{label:'男',value:1},{label:'女',value:2}]" :key="item.value" :class="{ selected: form.gender === item.value }" @click="form.gender=item.value">{{ item.label }}</button></div></label>
          <label>出生年月<a-input v-model:value="form.birthMonth" type="month" /></label>
          <label>身份<div class="identity-row"><button :class="{ selected: form.identity === 'worker' }" @click="form.identity='worker'"><IconifyIcon icon="lucide:hard-hat" /><b>职场人</b></button><button :class="{ selected: form.identity === 'student' }" @click="form.identity='student'"><IconifyIcon icon="lucide:book-open" /><b>学生</b></button></div></label>
          <label>求职状态<div class="choice-grid"><button v-for="status in form.identity === 'student' ? ['离校-随时到岗','在校-月内到岗','在校-考虑机会','在校-暂不考虑'] : ['离职-随时到岗','在职-月内到岗','在职-考虑机会','在职-暂不考虑']" :key="status" :class="{ selected: form.employmentStatus === status }" @click="form.employmentStatus=status">{{ status }}</button></div></label>
        </section>

        <section v-else-if="step === 2" class="form-stack compact">
          <label>你理想的工作城市<a-select v-model:value="form.city" placeholder="选择理想工作城市" show-search><a-select-option v-for="city in ['延安','西安','榆林','太原','郑州','北京','上海','广州','深圳']" :key="city" :value="city">{{ city }}</a-select-option></a-select></label>
          <label>你的期望职位<button class="field-button" type="button" @click="openPostModal">{{ form.postTypeName || '选择感兴趣的方向' }}<IconifyIcon icon="lucide:chevron-right" /></button></label>
        </section>

        <section v-else-if="step === 3" class="form-stack">
          <label>你的学历<a-select v-model:value="form.education"><a-select-option v-for="item in educationOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
          <template v-if="!simpleEducation"><label>类型<div class="choice-row"><button v-for="item in ['全日制','非全日制']" :key="item" :class="{ selected: form.schoolType === item }" @click="form.schoolType=item">{{ item }}</button></div></label><label>你毕业于<a-input v-model:value="form.school" placeholder="例如：延安大学" /></label><label>你的专业<a-input v-model:value="form.major" placeholder="例如：材料科学与工程" /></label></template>
          <label>时间<div class="date-range"><a-input v-model:value="form.educationStart" :type="simpleEducation ? 'number' : 'month'" :placeholder="simpleEducation ? '入学年份' : '入学年月'" /><span>至</span><a-input v-model:value="form.educationEnd" :type="simpleEducation ? 'number' : 'month'" :placeholder="simpleEducation ? '毕业年份' : '毕业年月'" /></div></label>
        </section>

        <section v-else-if="step === 4" class="form-stack compact">
          <label>你最近工作的职位<button class="field-button" type="button" @click="openPostModal">{{ form.postTypeName || '选择职位类型' }}<IconifyIcon icon="lucide:chevron-right" /></button></label>
          <label>你最近就职的公司<a-input v-model:value="form.company" placeholder="例如：天信管业有限公司" /></label>
          <label>在职时间<div class="date-range"><a-input v-model:value="form.jobStart" type="month" /><span>至</span><a-input v-model:value="form.jobEnd" type="month" /></div></label>
        </section>

        <section v-else-if="step === 5" class="form-stack compact"><label>用 1～2 句话介绍自己<a-textarea v-model:value="form.strengths" :maxlength="200" :rows="8" show-count placeholder="例如：5 年防腐保温工程经验，熟悉施工组织、质量验收与现场协调。" /></label></section>

        <section v-else class="avatar-step"><div class="avatar-preview"><img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="头像" /><IconifyIcon v-else icon="lucide:user-round" /></div><input ref="avatarInput" hidden type="file" accept="image/*" @change="handleAvatar" /><a-button type="primary" @click="avatarInput?.click()">上传头像</a-button><p>选择一张清晰的正面职业照，支持 JPG、PNG，大小不超过 5MB</p></section>

        <footer class="setup-actions"><button v-if="step === 4" class="skip" type="button" @click="skipWork">跳过此步骤 <IconifyIcon icon="lucide:chevron-right" /></button><span v-else></span><div><a-button v-if="step > 1" @click="previous">上一步</a-button><a-button v-if="step < 6" type="primary" @click="next">下一步</a-button><a-button v-else type="primary" :loading="submitting" @click="finish">完成</a-button></div></footer>
      </main>
    </div>

    <a-modal v-model:open="postModalOpen" title="请选择职位类型" :footer="null" width="820px" wrap-class-name="post-type-modal">
      <div class="post-picker"><nav><button v-for="parent in parentTypes" :key="parent.id" :class="{ active: activePostParent === parent.id }" @click="activePostParent=parent.id">{{ parent.name }}</button></nav><div class="post-grid"><button v-for="type in childTypes" :key="type.id" @click="choosePost(type)"><strong>{{ type.name }}</strong><span>查看并选择该职位方向</span></button><button v-if="!childTypes.length" v-for="type in postTypes" :key="type.id" @click="choosePost(type)"><strong>{{ type.name }}</strong><span>查看并选择该职位方向</span></button></div></div>
    </a-modal>
  </div>
</template>

<style scoped>
.setup-page{min-height:100vh;padding:26px;background:#08b7b4}.setup-shell{display:grid;grid-template-columns:42% 58%;width:min(1180px,100%);min-height:calc(100vh - 52px);margin:auto;overflow:hidden;border-radius:18px;background:#fff;box-shadow:0 24px 80px rgb(4 91 95 / 22%)}.setup-aside{position:relative;display:flex;padding:64px 70px 38px;background:linear-gradient(160deg,#fffaf3 0%,#ebfaf8 100%);flex-direction:column}.setup-aside h1{max-width:340px;margin:0;color:#142338;font-size:30px;line-height:1.35}.industry-visual{position:relative;display:grid;width:280px;height:280px;margin:82px auto 0;place-items:center}.visual-ring{position:absolute;border:1px solid rgb(4 174 169 / 22%);border-radius:50%}.ring-one{width:250px;height:250px}.ring-two{width:185px;height:185px;border-style:dashed}.visual-icon{display:grid;width:114px;height:114px;place-items:center;border-radius:34px;background:#fff;color:#08aaa6;box-shadow:0 18px 44px rgb(2 132 132 / 18%);font-size:56px;transform:rotate(-3deg)}.visual-label{position:absolute;right:8px;bottom:46px;padding:9px 14px;border-radius:20px;background:#123c50;color:#fff;font-size:15px}.resume-tip{display:flex;align-items:center;gap:12px;margin-top:auto;padding:16px;border:1px dashed #a7d8d5;border-radius:10px;background:rgb(255 255 255 / 72%);color:#0ca7a4}.resume-tip div{display:grid;gap:3px}.resume-tip strong{color:#24374a;font-size:15px}.resume-tip small{color:#81909f;font-size:15px}.setup-main{display:flex;min-width:0;padding:42px 76px 34px;flex-direction:column}.step-header{display:grid;gap:14px;color:#172437;font-size:16px}.progress{height:5px;overflow:hidden;border-radius:3px;background:#edf0f2}.progress i{display:block;height:100%;border-radius:3px;background:#06b5b1;transition:width .25s ease}.form-stack{display:grid;max-width:560px;gap:20px;margin-top:34px}.form-stack.compact{gap:28px}.form-stack label{display:grid;gap:9px;color:#748393;font-size:15px}.form-stack :deep(.ant-input),.form-stack :deep(.ant-select-selector),.field-button{min-height:52px!important;border:1px solid transparent!important;border-radius:8px!important;background:#f6f7f8!important;color:#172437!important}.form-stack :deep(.ant-select){width:100%}.choice-row,.choice-grid,.identity-row{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.choice-grid{gap:12px}.choice-row button,.choice-grid button,.identity-row button{min-height:54px;border:1px solid transparent;border-radius:8px;background:#f6f7f8;color:#172437;cursor:pointer}.choice-row button.selected,.choice-grid button.selected,.identity-row button.selected{border-color:#06b5b1;background:#eafafa;color:#049b99}.identity-row button{display:grid;min-height:112px;place-items:center;align-content:center;gap:8px;font-size:28px}.identity-row b{font-size:17px}.field-button{display:flex;align-items:center;justify-content:space-between;padding:0 16px;cursor:pointer;text-align:left}.date-range{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:12px}.avatar-step{display:grid;margin-top:48px;place-items:center;gap:18px;text-align:center}.avatar-preview{display:grid;width:160px;height:160px;overflow:hidden;place-items:center;border:8px solid #f1f6f6;border-radius:50%;background:#e9eeef;color:#a7b0ba;font-size:82px}.avatar-preview img{width:100%;height:100%;object-fit:cover}.avatar-step p{max-width:430px;color:#758392;font-size:15px}.setup-actions{display:flex;align-items:center;justify-content:space-between;margin-top:auto;padding-top:26px;border-top:1px solid #eef0f1}.setup-actions>div{display:flex;gap:14px}.setup-actions :deep(.ant-btn){min-width:96px}.skip{display:flex;align-items:center;gap:3px;border:0;background:transparent;color:#08aaa6;cursor:pointer}.post-picker{display:grid;grid-template-columns:150px 1fr;min-height:440px}.post-picker nav{display:flex;padding-right:14px;border-right:1px solid #e9edef;flex-direction:column;gap:4px}.post-picker nav button{padding:11px 14px;border:0;border-radius:6px;background:transparent;color:#4d5f6c;cursor:pointer;text-align:left;font-size:15px}.post-picker nav button.active{background:#e5f8f7;color:#049b99;font-weight:700}.post-grid{display:grid;grid-template-columns:1fr 1fr;align-content:start;gap:12px;padding-left:20px}.post-grid button{display:grid;gap:7px;padding:16px;border:1px solid #e4e8eb;border-radius:8px;background:#fff;cursor:pointer;text-align:left;font-size:15px}.post-grid button:hover{border-color:#08aaa6}.post-grid strong{color:#069f9c;font-size:17px}.post-grid span{color:#8a969f;font-size:15px}@media(max-width:800px){.setup-page{padding:0}.setup-shell{display:block;min-height:100vh;border-radius:0}.setup-aside{display:none}.setup-main{min-height:100vh;padding:28px 22px}.form-stack{margin-top:28px}.post-picker{grid-template-columns:110px 1fr}.post-grid{grid-template-columns:1fr}}@media(prefers-reduced-motion:reduce){.progress i{transition:none}}
.setup-page{background:#e87522}.setup-shell{box-shadow:0 24px 80px rgb(151 70 20 / 24%)}.setup-aside{background:linear-gradient(160deg,#fffaf3 0%,#fff0dd 100%)}.visual-icon,.resume-tip,.skip{color:#d86616}.visual-ring{border-color:rgb(232 117 34 / 28%)}.visual-icon{box-shadow:0 18px 44px rgb(216 102 22 / 18%)}.visual-label{background:#8a421d}.resume-tip{border-color:#f1bd83}.progress i{background:#e87522}.choice-row button.selected,.choice-grid button.selected,.identity-row button.selected{border-color:#e87522;background:#fff0df;color:#c85e18}.post-picker nav button.active{background:#fff0df;color:#c85e18}.post-grid button:hover{border-color:#e87522}.post-grid strong{color:#c85e18}
.setup-page { background: #fff8ef; }
.setup-shell { box-shadow: 0 24px 80px rgb(183 92 24 / 12%); }
.setup-aside { background: linear-gradient(160deg, #fff 0%, #fff7ed 100%); }
.setup-main { background: #fff; }
.form-stack :deep(.ant-input), .form-stack :deep(.ant-select-selector), .field-button { border-color: #f4e5d7 !important; background: #fffdfa !important; }
.choice-row button, .choice-grid button, .identity-row button { border-color: #f4e5d7; background: #fffdfa; }
.setup-actions :deep(.ant-btn-primary) { border-color: #e87522; background: linear-gradient(135deg, #ef9a4a, #d86616); }
.setup-page--modal { min-height: auto; padding: 0; background: transparent; font-size: 15px; }
.setup-page--modal button { font-size: 15px; }
.setup-page--modal :deep(.ant-btn) { font-size: 15px; }
.setup-page--modal :deep(.ant-input), .setup-page--modal :deep(.ant-select-selector), .setup-page--modal :deep(.ant-select-selection-item), .setup-page--modal :deep(.ant-select-selection-placeholder) { font-size: 15px; }
.setup-page :deep(.iconify), .setup-page :deep(.anticon) { display: inline-flex; align-items: center; color: inherit; font-style: normal; line-height: 0; text-align: center; text-transform: none; vertical-align: -0.125em; text-rendering: optimizeLegibility; -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale; }
.setup-page--modal .setup-shell { width: 100%; min-height: min(700px, calc(100vh - 150px)); border-radius: 14px; box-shadow: 0 18px 46px rgb(183 92 24 / 10%); }
.setup-page--modal .setup-aside { padding: 38px 46px 28px; }
.setup-page--modal .industry-visual { margin-top: 50px; }
.setup-page--modal .setup-main { overflow-y: auto; padding: 34px 52px 28px; }
@media (max-width: 800px) {
  .setup-page--modal .setup-shell { min-height: calc(100vh - 120px); }
  .setup-page--modal .setup-main { padding: 28px 22px; }
}
</style>
