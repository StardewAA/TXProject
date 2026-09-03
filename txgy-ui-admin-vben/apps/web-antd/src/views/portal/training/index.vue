<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { getTenantSimpleList } from '#/api/core/auth';
import { getEmployeeCertificateList } from '#/api/hrm/portal/employee/certificate';
import { getEmployeeTrainingExperienceList } from '#/api/hrm/portal/employee/training-experience';

type ModuleKey = `F${number}`;

const modules: Array<{ key: ModuleKey; label: string; short: string }> = [
  { key: 'F1', label: '学习工作台', short: '工作台' },
  { key: 'F2', label: '课程中心', short: '课程' },
  { key: 'F3', label: '我的学习', short: '学习' },
  { key: 'F4', label: 'VR 实操训练', short: 'VR 实操' },
  { key: 'F5', label: '考试与证书', short: '考试证书' },
  { key: 'F6', label: '直播大厅', short: '直播' },
  { key: 'F7', label: '培训任务与上岗', short: '培训任务' },
  { key: 'F8', label: '个人档案与成长', short: '成长档案' },
  { key: 'F9', label: '校企合作专区', short: '校企合作' },
  { key: 'F10', label: '创新大赛专区', short: '创新大赛' },
];

const activeModule = ref<ModuleKey>('F1');
const accessStore = useAccessStore();
const userStore = useUserStore();
const tenantName = ref('当前企业');
const enterpriseLoading = ref(true);
const enterpriseMessage = ref('正在同步企业培训档案');
const employeeCertificates = ref<
  Array<{
    endTime?: number;
    issuingAuthority?: string;
    name?: string;
    no?: string;
    startTime?: number;
  }>
>([]);
const employeeTrainingExperiences = ref<
  Array<{
    course?: string;
    duration?: string;
    organizationName?: string;
    result?: string;
  }>
>([]);
const keyword = ref('');
const courseCategory = ref('全部课程');
const courseLevel = ref('全部等级');
const courseSort = ref('最新');
const learningTab = ref('进行中');
const examTab = ref('可考试');
const liveTab = ref('直播预告');
const favoriteIds = ref<number[]>([2]);
const enrolledIds = ref<number[]>([1, 3]);
const talentAuthorized = ref(false);
const toast = ref('');
let toastTimer: ReturnType<typeof setTimeout> | undefined;

const categories = [
  '全部课程',
  '行业新规政策',
  '国标规范解读',
  '防腐工艺教程',
  '保温施工工法',
  '安全环保实操',
];

const courses = [
  {
    id: 1,
    title: '防腐涂层施工质量控制',
    category: '防腐工艺教程',
    teacher: '李建国',
    hours: 6,
    level: '初级技工',
    trade: '防腐工',
    progress: 68,
    required: true,
    composite: true,
    learners: 1286,
    color: '#0f766e',
  },
  {
    id: 2,
    title: 'GB 50212 建筑防腐工程规范解读',
    category: '国标规范解读',
    teacher: '周晓峰',
    hours: 4,
    level: '中级技工',
    trade: '项目管理',
    progress: 0,
    required: false,
    composite: false,
    learners: 936,
    color: '#1d4ed8',
  },
  {
    id: 3,
    title: '有限空间与高处作业安全实训',
    category: '安全环保实操',
    teacher: '王海涛',
    hours: 8,
    level: '全部等级',
    trade: '安全员',
    progress: 32,
    required: true,
    composite: true,
    learners: 2104,
    color: '#b45309',
  },
  {
    id: 4,
    title: '管道保温层与保护层标准施工',
    category: '保温施工工法',
    teacher: '陈志强',
    hours: 5,
    level: '学徒',
    trade: '保温工',
    progress: 0,
    required: false,
    composite: false,
    learners: 754,
    color: '#7c3aed',
  },
  {
    id: 5,
    title: '2026 安全生产新规与企业责任',
    category: '行业新规政策',
    teacher: '赵敏',
    hours: 3,
    level: '高级技师',
    trade: '管理人员',
    progress: 0,
    required: false,
    composite: false,
    learners: 1660,
    color: '#be123c',
  },
  {
    id: 6,
    title: '聚氨酯喷涂常见缺陷与整改',
    category: '防腐工艺教程',
    teacher: '刘工',
    hours: 4,
    level: '中级技工',
    trade: '喷涂工',
    progress: 0,
    required: false,
    composite: true,
    learners: 582,
    color: '#0369a1',
  },
];

const filteredCourses = computed(() => {
  const normalized = keyword.value.trim().toLowerCase();
  const result = courses.filter((course) => {
    const matchesKeyword =
      !normalized ||
      [course.title, course.teacher, course.category, course.trade]
        .join(' ')
        .toLowerCase()
        .includes(normalized);
    const matchesCategory =
      courseCategory.value === '全部课程' ||
      course.category === courseCategory.value;
    const matchesLevel =
      courseLevel.value === '全部等级' ||
      course.level === courseLevel.value ||
      course.level === '全部等级';
    return matchesKeyword && matchesCategory && matchesLevel;
  });
  if (courseSort.value === '最热') {
    return [...result].sort((a, b) => b.learners - a.learners);
  }
  if (courseSort.value === '学时最高') {
    return [...result].sort((a, b) => b.hours - a.hours);
  }
  return result;
});

const tasks = [
  { type: '课程', title: '完成《防腐涂层施工质量控制》第 4 章', due: '今天 18:00', urgent: true, target: 'F3' as ModuleKey },
  { type: '考试', title: '岗前安全培训结课考试', due: '剩余 2 天', urgent: true, target: 'F5' as ModuleKey },
  { type: 'VR', title: '有限空间作业应急处置实操', due: '9 月 08 日', urgent: false, target: 'F4' as ModuleKey },
  { type: '直播', title: '新规解读公开课', due: '今天 15:00', urgent: false, target: 'F6' as ModuleKey },
];

const learningCourses = [
  { title: '防腐涂层施工质量控制', kind: '必修 · 复合型', progress: 68, last: '上次学习：表面处理等级判定', status: '进行中' },
  { title: '有限空间与高处作业安全实训', kind: '必修 · 安全员', progress: 32, last: '上次学习：有限空间气体检测', status: '进行中' },
  { title: '项目现场标准化管理', kind: '选修 · 项目管理', progress: 100, last: '已获得 5 学时', status: '已完成' },
  { title: '涂料材料选型基础', kind: '选修 · 学徒', progress: 15, last: '已超过截止时间', status: '逾期' },
];

const visibleLearningCourses = computed(() =>
  learningCourses.filter((item) => item.status === learningTab.value),
);

const exams = [
  { title: '岗前安全培训结课考试', type: '结课考试', start: '今天 14:00—18:00', duration: '60 分钟', attempts: 2, status: '可考试', score: null },
  { title: '防腐工中级技能模拟考试', type: '模拟考试', start: '9 月 06 日 09:00', duration: '90 分钟', attempts: 3, status: '待考试', score: null },
  { title: '高处作业安全认证考试', type: '取证考试', start: '8 月 20 日', duration: '60 分钟', attempts: 0, status: '已结束', score: 86 },
];

const visibleExams = computed(() =>
  exams.filter((item) => item.status === examTab.value),
);

const liveRooms = [
  { title: '2026 安全生产新规重点解读', teacher: '赵敏', time: '今天 15:00', status: '直播预告', access: '平台公开', booked: false },
  { title: '聚氨酯喷涂缺陷在线答疑', teacher: '刘工', time: '正在直播 · 已进行 38 分钟', status: '正在直播', access: '企业内部', booked: true },
  { title: '防腐工程质量验收案例复盘', teacher: '周晓峰', time: '8 月 28 日', status: '历史回放', access: '平台公开', booked: true },
];

const visibleLiveRooms = computed(() =>
  liveRooms.filter((item) => item.status === liveTab.value),
);

function notify(message: string) {
  toast.value = message;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ''), 2400);
}

function selectModule(key: ModuleKey) {
  activeModule.value = key;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function toggleFavorite(id: number) {
  favoriteIds.value = favoriteIds.value.includes(id)
    ? favoriteIds.value.filter((item) => item !== id)
    : [...favoriteIds.value, id];
  notify(favoriteIds.value.includes(id) ? '已收藏课程' : '已取消收藏');
}

function enroll(id: number) {
  if (!enrolledIds.value.includes(id)) enrolledIds.value.push(id);
  notify('选课成功，已加入“我的学习”');
}

function toggleTalentAuthorization() {
  talentAuthorized.value = !talentAuthorized.value;
  notify(talentAuthorized.value ? '已授权档案进入院校人才库' : '已关闭人才库授权');
}

function formatDate(value?: number) {
  if (!value) return '长期有效';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}

async function loadEnterpriseLearningData() {
  enterpriseLoading.value = true;
  const results = await Promise.allSettled([
    getTenantSimpleList(),
    getEmployeeCertificateList(),
    getEmployeeTrainingExperienceList(),
  ]);

  const [tenantResult, certificateResult, trainingResult] = results;
  if (tenantResult.status === 'fulfilled') {
    tenantName.value =
      tenantResult.value.find((item) => item.id === accessStore.tenantId)?.name ||
      `企业租户 ${accessStore.tenantId ?? '-'}`;
  }
  if (certificateResult.status === 'fulfilled') {
    employeeCertificates.value = certificateResult.value;
  }
  if (trainingResult.status === 'fulfilled') {
    employeeTrainingExperiences.value = trainingResult.value;
  }

  const loadedCount = results.filter((item) => item.status === 'fulfilled').length;
  enterpriseMessage.value =
    loadedCount === results.length
      ? '企业培训档案已同步'
      : loadedCount > 0
        ? '当前账号未绑定员工档案'
        : '企业数据暂时无法连接';
  enterpriseLoading.value = false;
}

onMounted(loadEnterpriseLearningData);
</script>

<template>
  <div class="academy-page">
    <section class="academy-hero">
      <div>
        <p class="eyebrow">{{ tenantName }} · 云培训学院 · 学员中心</p>
        <h1>让每一次学习，都成为成长的刻度</h1>
        <p class="hero-copy">课程、实操、考试与证书一站贯通，清晰掌握今天要做什么、距离下一等级还有多远。</p>
      </div>
      <div class="hero-progress">
        <div class="level-row"><span>当前等级</span><strong>初级技工</strong></div>
        <div class="level-track"><span style="width: 72%"></span></div>
        <small>{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '当前学员' }} · {{ enterpriseLoading ? '正在同步…' : enterpriseMessage }}</small>
      </div>
    </section>

    <nav class="module-nav" aria-label="云培训学院功能导航">
      <button
        v-for="item in modules"
        :key="item.key"
        :class="{ active: activeModule === item.key }"
        type="button"
        @click="selectModule(item.key)"
      >
        <span>{{ item.key }}</span>{{ item.short }}
      </button>
    </nav>

    <template v-if="activeModule === 'F1'">
      <section class="section-title-row">
        <div><p class="section-kicker">LEARNING OVERVIEW</p><h2>学习工作台</h2></div>
        <button class="text-button" type="button" @click="notify('消息中心已全部标记为已读')">3 条未读消息</button>
      </section>
      <section class="metric-grid">
        <article><span>累计有效学时</span><strong>126.5</strong><small>小时</small></article>
        <article><span>本周学时</span><strong>8.5</strong><small>较上周 +18%</small></article>
        <article><span>培训记录</span><strong>{{ employeeTrainingExperiences.length }}</strong><small>来自当前企业档案</small></article>
        <article><span>通过考试</span><strong>12</strong><small>通过率 92%</small></article>
        <article><span>企业证书</span><strong>{{ employeeCertificates.length }}</strong><small>来自当前员工档案</small></article>
        <article><span>VR 实操</span><strong>18</strong><small>平均得分 86</small></article>
      </section>
      <div class="two-column">
        <section class="panel">
          <div class="panel-heading"><div><span>今日重点</span><h3>待办任务</h3></div><b>{{ tasks.length }} 项</b></div>
          <button v-for="task in tasks" :key="task.title" class="task-row" type="button" @click="selectModule(task.target)">
            <span class="task-type">{{ task.type }}</span>
            <span class="task-main"><strong>{{ task.title }}</strong><small>{{ task.due }}</small></span>
            <span :class="['task-dot', { urgent: task.urgent }]">→</span>
          </button>
        </section>
        <section class="panel">
          <div class="panel-heading"><div><span>最近 14 天</span><h3>学习趋势</h3></div><b>32.5h</b></div>
          <div class="chart" aria-label="近14天学习时长柱状图">
            <span v-for="(height, index) in [28, 54, 38, 76, 42, 88, 60, 35, 68, 92, 48, 74, 57, 82]" :key="index" :style="{ height: `${height}%` }"></span>
          </div>
          <div class="chart-labels"><span>8月21日</span><span>今天</span></div>
        </section>
      </div>
      <section class="panel">
        <div class="panel-heading"><div><span>常用功能</span><h3>快捷入口</h3></div></div>
        <div class="quick-grid">
          <button v-for="item in [{t:'继续学习',d:'上次学习至 68%',k:'F3'},{t:'进入考试',d:'1 场考试可参加',k:'F5'},{t:'直播大厅',d:'今天 15:00 开播',k:'F6'},{t:'VR 实操',d:'2 项任务待完成',k:'F4'},{t:'我的证书',d:'5 本有效证书',k:'F5'},{t:'成长档案',d:'查看晋升条件',k:'F8'}]" :key="item.t" type="button" @click="selectModule(item.k as ModuleKey)">
            <strong>{{ item.t }}</strong><span>{{ item.d }}</span><i>→</i>
          </button>
        </div>
      </section>
    </template>

    <template v-else-if="activeModule === 'F2'">
      <section class="section-title-row"><div><p class="section-kicker">COURSE DISCOVERY</p><h2>课程中心</h2><p>发现适合岗位与成长阶段的专业课程</p></div><button class="outline-button" type="button" @click="notify('已展示收藏课程')">我的收藏 {{ favoriteIds.length }}</button></section>
      <section class="panel filter-panel">
        <label class="search-box"><span>⌕</span><input v-model="keyword" placeholder="搜索课程、讲师或标签" /></label>
        <div class="filter-row"><span>课程分类</span><button v-for="item in categories" :key="item" :class="{ active: courseCategory === item }" type="button" @click="courseCategory = item">{{ item }}</button></div>
        <div class="filter-row"><span>成长等级</span><button v-for="item in ['全部等级','学徒','初级技工','中级技工','高级技师']" :key="item" :class="{ active: courseLevel === item }" type="button" @click="courseLevel = item">{{ item }}</button></div>
      </section>
      <div class="list-toolbar"><span>找到 <strong>{{ filteredCourses.length }}</strong> 门课程</span><div><button v-for="item in ['最新','最热','学时最高']" :key="item" :class="{ active: courseSort === item }" type="button" @click="courseSort = item">{{ item }}</button></div></div>
      <section class="course-grid">
        <article v-for="course in filteredCourses" :key="course.id" class="course-card">
          <div class="course-cover" :style="{ background: `linear-gradient(135deg, ${course.color}, #111827)` }"><span>{{ course.category }}</span><strong>{{ course.trade }}</strong><button type="button" @click="toggleFavorite(course.id)">{{ favoriteIds.includes(course.id) ? '★' : '☆' }}</button></div>
          <div class="course-body"><div class="tag-row"><span v-if="course.required">必修</span><span>{{ course.level }}</span><span v-if="course.composite">复合型</span></div><h3>{{ course.title }}</h3><p>讲师 {{ course.teacher }} · {{ course.learners }} 人学习</p><div v-if="course.progress" class="progress-line"><span :style="{ width: `${course.progress}%` }"></span></div><footer><span>{{ course.hours }} 学时</span><button v-if="enrolledIds.includes(course.id)" type="button" @click="selectModule('F3')">继续学习</button><button v-else type="button" @click="enroll(course.id)">加入课程</button></footer></div>
        </article>
      </section>
    </template>

    <template v-else-if="activeModule === 'F3'">
      <section class="section-title-row"><div><p class="section-kicker">MY LEARNING</p><h2>我的学习</h2><p>继续上次进度，有效学习自动计入档案</p></div><button class="outline-button" type="button" @click="notify('学习轨迹已展开')">查看学习轨迹</button></section>
      <div class="tab-bar"><button v-for="item in ['待学习','进行中','已完成','逾期']" :key="item" :class="{ active: learningTab === item }" type="button" @click="learningTab = item">{{ item }}</button></div>
      <section class="learning-list">
        <article v-for="item in visibleLearningCourses" :key="item.title"><div class="learning-thumb">课程</div><div class="learning-content"><div><span>{{ item.kind }}</span><h3>{{ item.title }}</h3><p>{{ item.last }}</p></div><div class="learning-action"><strong>{{ item.progress }}%</strong><div class="progress-line"><span :style="{ width: `${item.progress}%` }"></span></div><button type="button" @click="notify(item.progress === 100 ? '课程学习明细已打开' : '正在恢复上次学习位置')">{{ item.progress === 100 ? '查看明细' : '继续学习' }}</button></div></div></article>
        <div v-if="!visibleLearningCourses.length" class="empty-state">当前分组暂无课程</div>
      </section>
      <section class="panel compact-panel"><div class="panel-heading"><div><span>学习行为</span><h3>最近轨迹</h3></div><button class="text-button" type="button" @click="notify('申诉入口已打开')">学时异常申诉</button></div><div class="timeline"><p><b>今天 09:42</b><span>视频学习 · 表面处理等级判定</span><em>+0.5 学时</em></p><p><b>昨天 16:18</b><span>文档阅读 · 高处作业安全规范</span><em>+0.8 学时</em></p><p><b>9 月 01 日</b><span>培训打卡 · 新员工岗前培训</span><em>已打卡</em></p></div></section>
    </template>

    <template v-else-if="activeModule === 'F4'">
      <section class="section-title-row"><div><p class="section-kicker">VR PRACTICE</p><h2>VR 实操训练</h2><p>以标准化场景强化关键工序和安全处置能力</p></div><span class="status-pill">设备状态：可连接</span></section>
      <section class="feature-banner vr-banner"><div><span>本周必做</span><h2>有限空间作业应急处置</h2><p>完成 2 次 · 合格线 80 分 · 截止 9 月 08 日</p><button type="button" @click="notify('正在检测 VR 设备与运行环境')">启动实操</button></div><div class="score-ring"><strong>1/2</strong><span>已完成</span></div></section>
      <section class="three-grid">
        <article class="info-card"><span>待完成</span><h3>脚手架搭设安全检查</h3><p>高级技师 · 安全员</p><footer><small>截止 9 月 12 日</small><button type="button" @click="notify('实操详情已打开')">查看任务</button></footer></article>
        <article class="info-card"><span>待复核</span><h3>储罐内壁防腐施工</h3><p>中级技工 · 防腐工</p><footer><small>本次得分 83</small><button type="button" @click="notify('正在等待管理员复核')">查看过程</button></footer></article>
        <article class="info-card"><span class="success">已通过</span><h3>聚氨酯喷涂设备操作</h3><p>初级技工 · 喷涂工</p><footer><small>92 分 · 2 学时</small><button type="button" @click="notify('实操成绩明细已打开')">成绩明细</button></footer></article>
      </section>
      <section class="panel"><div class="panel-heading"><div><span>过程可追溯</span><h3>个人实操历史</h3></div></div><div class="data-table"><div class="table-head"><span>实操项目</span><span>时间</span><span>得分</span><span>结果</span><span>获得学时</span></div><div><span>聚氨酯喷涂设备操作</span><span>08-30 14:20</span><span>92</span><span class="green">合格</span><span>2.0</span></div><div><span>有限空间作业应急处置</span><span>08-26 10:15</span><span>76</span><span class="orange">未通过</span><span>0</span></div></div></section>
    </template>

    <template v-else-if="activeModule === 'F5'">
      <section class="section-title-row"><div><p class="section-kicker">EXAM & CERTIFICATE</p><h2>考试与证书</h2><p>考试任务、成绩复盘与电子证书集中管理</p></div><button class="outline-button" type="button" @click="notify('证书验真页面已打开')">证书验真</button></section>
      <div class="tab-bar"><button v-for="item in ['待考试','可考试','已结束']" :key="item" :class="{ active: examTab === item }" type="button" @click="examTab = item">{{ item }}</button><button type="button" @click="notify('错题本已打开')">错题本 <b>36</b></button><button type="button" @click="notify('我的证书已打开')">我的证书 <b>5</b></button></div>
      <section class="learning-list exam-list"><article v-for="item in visibleExams" :key="item.title"><div class="exam-date"><strong>{{ item.status === '可考试' ? '今日' : item.status === '待考试' ? '09/06' : '86' }}</strong><span>{{ item.score ? '分' : item.type }}</span></div><div class="learning-content"><div><span>{{ item.type }}</span><h3>{{ item.title }}</h3><p>{{ item.start }} · {{ item.duration }} · 剩余 {{ item.attempts }} 次</p></div><div class="learning-action"><button type="button" @click="notify(item.status === '可考试' ? '考试环境检测已启动' : '考试详情已打开')">{{ item.status === '可考试' ? '进入考试' : '查看详情' }}</button></div></div></article></section>
      <section class="panel"><div class="panel-heading"><div><span>当前企业员工档案</span><h3>我的证书</h3></div><button class="text-button" type="button" @click="loadEnterpriseLearningData">重新同步</button></div><div v-if="employeeCertificates.length" class="certificate-grid"><article v-for="(certificate, index) in employeeCertificates" :key="certificate.no || index"><div :class="['certificate-mark', { gold: index % 2 }]">证</div><div><strong>{{ certificate.name || '未命名证书' }}</strong><p>{{ certificate.no || '暂无证书编号' }} · 有效至 {{ formatDate(certificate.endTime) }}</p><small>{{ certificate.issuingAuthority || tenantName }}</small></div><span class="success">有效</span></article></div><div v-else class="enterprise-empty"><strong>{{ enterpriseLoading ? '正在读取企业证书…' : '当前企业员工档案中暂无证书' }}</strong><p>证书由企业后台维护，保存后会自动同步到此处。</p></div></section>
    </template>

    <template v-else-if="activeModule === 'F6'">
      <section class="section-title-row"><div><p class="section-kicker">LIVE LEARNING</p><h2>直播大厅</h2><p>预约行业公开课，参与直播互动并完成课后任务</p></div><span class="status-pill live">● 1 场正在直播</span></section>
      <div class="tab-bar"><button v-for="item in ['直播预告','正在直播','历史回放']" :key="item" :class="{ active: liveTab === item }" type="button" @click="liveTab = item">{{ item }}</button></div>
      <section class="live-grid"><article v-for="item in visibleLiveRooms" :key="item.title"><div class="live-cover"><span>{{ item.status }}</span><b>LIVE</b></div><div><span>{{ item.access }}</span><h3>{{ item.title }}</h3><p>讲师 {{ item.teacher }}</p><strong>{{ item.time }}</strong><button type="button" @click="notify(item.status === '正在直播' ? '正在进入直播间' : item.status === '历史回放' ? '回放已打开，将记录有效学时' : '预约成功，开播前将提醒你')">{{ item.status === '正在直播' ? '进入直播' : item.status === '历史回放' ? '观看回放' : item.booked ? '已预约' : '立即预约' }}</button></div></article></section>
      <section class="panel compact-panel"><div class="panel-heading"><div><span>课后巩固</span><h3>待提交作业</h3></div></div><div class="assignment"><div><strong>《安全生产新规》案例分析</strong><p>支持文本、图片和附件 · 截止 9 月 06 日 18:00</p></div><button type="button" @click="notify('作业提交面板已打开')">提交作业</button></div></section>
    </template>

    <template v-else-if="activeModule === 'F7'">
      <section class="section-title-row"><div><p class="section-kicker">TRAINING QUALIFICATION</p><h2>培训任务与上岗</h2><p>清楚掌握被要求完成的内容和上岗资格缺口</p></div><span class="status-pill warning-bg">当前项目：兰州新区管网工程</span></section>
      <section class="feature-banner qualification-banner"><div><span>上岗资格自查</span><h2>暂未满足上岗条件</h2><p>4 项要求已完成 3 项，请在截止时间前完成剩余考试。</p><button type="button" @click="selectModule('F5')">去完成考试</button></div><div class="score-ring orange-ring"><strong>75%</strong><span>总进度</span></div></section>
      <section class="panel"><div class="panel-heading"><div><span>岗前培训班</span><h3>兰州项目防腐作业专项班</h3></div><b>截止 9 月 10 日</b></div><div class="requirement-grid"><article class="done"><span>✓</span><div><strong>规定课程</strong><p>3/3 已完成</p></div></article><article class="done"><span>✓</span><div><strong>有效学时</strong><p>12/12 小时</p></div></article><article class="todo"><span>!</span><div><strong>结课考试</strong><p>待完成 · 9 月 05 日截止</p></div></article><article class="done"><span>✓</span><div><strong>VR 实操</strong><p>1/1 已通过</p></div></article></div></section>
      <section class="panel compact-panel"><div class="panel-heading"><div><span>企业管理员</span><h3>督促提醒</h3></div></div><div class="notice-row"><span>未读</span><div><strong>请尽快完成岗前安全培训结课考试</strong><p>兰州项目防腐作业专项班 · 今天 09:30</p></div><button type="button" @click="selectModule('F5')">立即处理</button></div></section>
    </template>

    <template v-else-if="activeModule === 'F8'">
      <section class="section-title-row"><div><p class="section-kicker">GROWTH PROFILE</p><h2>个人档案与成长</h2><p>沉淀可信的学习、考试、证书与实操成果</p></div><button class="outline-button" type="button" @click="notify('个人培训档案 PDF 正在生成')">导出培训档案</button></section>
      <section class="feature-banner growth-banner"><div><span>人才成长路径</span><h2>初级技工 → 中级技工</h2><p>综合完成度 72%，继续积累实操与专业学时。</p></div><div class="score-ring"><strong>72%</strong><span>成长进度</span></div></section>
      <section class="metric-grid four"><article><span>有效学时</span><strong>126.5</strong><small>/ 150 小时</small></article><article><span>技能证书</span><strong>5</strong><small>/ 6 本</small></article><article><span>VR 实操</span><strong>18</strong><small>/ 20 次</small></article><article><span>考试通过率</span><strong>92%</strong><small>12 场通过</small></article></section>
      <div class="two-column"><section class="panel"><div class="panel-heading"><div><span>晋升要求</span><h3>条件缺口</h3></div></div><div class="gap-list"><button type="button" @click="selectModule('F2')"><span>专业课程学时</span><strong>还差 12 学时 →</strong></button><button type="button" @click="selectModule('F4')"><span>VR 实操项目</span><strong>还差 2 项 →</strong></button><button type="button" @click="selectModule('F5')"><span>中级技能认证</span><strong>待参加考试 →</strong></button></div></section><section class="panel"><div class="panel-heading"><div><span>证书管理</span><h3>到期与复审</h3></div></div><div class="renew-card"><span>28</span><div><strong>防腐工初级岗位能力证书</strong><p>距离到期还有 28 天，需完成复审课程与考试。</p><button type="button" @click="selectModule('F2')">查看复审要求</button></div></div></section></div>
    </template>

    <template v-else-if="activeModule === 'F9'">
      <section class="section-title-row"><div><p class="section-kicker">SCHOOL × ENTERPRISE</p><h2>校企合作专区</h2><p>连接定向培养、实习实践与就业机会</p></div><button :class="['switch-button', { active: talentAuthorized }]" type="button" @click="toggleTalentAuthorization"><span></span>{{ talentAuthorized ? '人才库已授权' : '授权进入人才库' }}</button></section>
      <section class="feature-banner school-banner"><div><span>我的定向培训班</span><h2>延安大学 · 防腐保温技术人才班</h2><p>课程 8 门 · 实习任务 3 项 · 截止 2026 年 12 月 20 日</p><button type="button" @click="selectModule('F3')">进入班级学习</button></div><div class="score-ring"><strong>64%</strong><span>培养进度</span></div></section>
      <div class="two-column"><section class="panel"><div class="panel-heading"><div><span>实习安排</span><h3>本周任务</h3></div></div><div class="internship"><time>09/07</time><div><strong>防腐车间生产见习</strong><p>天信管业生产基地 · 08:30 集合</p></div><span>待确认</span></div><div class="internship"><time>09/10</time><div><strong>质量检验跟岗实训</strong><p>检测中心 · 指导老师：刘工</p></div><span>已确认</span></div></section><section class="panel"><div class="panel-heading"><div><span>就业与实习</span><h3>为你推荐的机会</h3></div></div><div class="job-card"><span>实习</span><strong>防腐工程技术助理</strong><p>某大型管道工程企业 · 陕西</p><div><button type="button" @click="notify('已接受企业意向，院校将继续跟进')">接受意向</button><button type="button" @click="notify('已婉拒该意向')">暂不考虑</button></div></div></section></div>
      <section class="panel compact-panel"><div class="panel-heading"><div><span>隐私与授权</span><h3>人才档案展示范围</h3></div></div><p class="privacy-copy">证书与技能等级、课程学时、实习评价已选择对外展示；身份证号、手机号等敏感信息始终脱敏。</p></section>
    </template>

    <template v-else-if="activeModule === 'F10'">
      <section class="section-title-row"><div><p class="section-kicker">INNOVATION COMPETITION</p><h2>创新大赛专区</h2><p>展示创新能力，让优秀成果被行业看见</p></div><button class="outline-button" type="button" @click="notify('我的报名记录已打开')">我的报名</button></section>
      <section class="feature-banner competition-banner"><div><span>正在报名 · 还有 12 天</span><h2>2026 防腐保温技术创新大赛</h2><p>绿色材料 · 智能施工 · 安全创新三大赛道</p><div class="button-row"><button type="button" @click="notify('大赛报名信息填写页已打开')">立即报名</button><button class="ghost" type="button" @click="notify('大赛章程已打开')">查看章程</button></div></div><div class="competition-date"><strong>09.15</strong><span>报名截止</span></div></section>
      <div class="two-column"><section class="panel"><div class="panel-heading"><div><span>我的作品</span><h3>智能喷涂厚度实时检测装置</h3></div><b>评审中</b></div><div class="work-progress"><div><span class="done">✓ 报名成功</span><span class="done">✓ 作品提交</span><span class="current">3 专家评审</span><span>4 结果公示</span></div><p>参赛编号：CX2026-0318 · 当前版本 V2.1</p><button type="button" @click="notify('作品版本与评审进度已打开')">查看作品与进度</button></div></section><section class="panel"><div class="panel-heading"><div><span>获奖公示</span><h3>上届优秀成果</h3></div><button class="text-button" type="button" @click="notify('成果库已打开')">进入成果库</button></div><div class="award"><i>一等奖</i><div><strong>复杂管廊智能巡检机器人</strong><p>智能施工赛道 · 第三届</p></div></div><div class="award"><i>二等奖</i><div><strong>低 VOC 长效防腐涂层体系</strong><p>绿色材料赛道 · 第三届</p></div></div></section></div>
    </template>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </div>
</template>

<style scoped>
.academy-page { --blue: #2563eb; --ink: #172033; --muted: #667085; display: grid; gap: 22px; color: var(--ink); }
button, input { font: inherit; }
button { cursor: pointer; }
.academy-hero { display: flex; align-items: center; justify-content: space-between; gap: 28px; min-height: 174px; padding: 34px 40px; overflow: hidden; border: 1px solid #dbe7ff; background: linear-gradient(115deg, #eef5ff 0%, #f7fbff 54%, #eefcf6 100%); }
.eyebrow, .section-kicker { margin: 0 0 8px; color: var(--blue); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.academy-hero h1 { margin: 0 0 12px; font-size: 30px; letter-spacing: -.02em; }
.hero-copy { max-width: 650px; margin: 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.hero-progress { width: 300px; padding: 20px; border: 1px solid rgb(255 255 255 / 85%); background: rgb(255 255 255 / 72%); box-shadow: 0 12px 30px rgb(37 99 235 / 8%); }
.level-row { display: flex; justify-content: space-between; font-size: 13px; }.level-row strong { color: var(--blue); }.level-track, .progress-line { height: 6px; overflow: hidden; border-radius: 999px; background: #e6eaf0; }.level-track { margin: 14px 0 10px; }.level-track span, .progress-line span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #2563eb, #22c55e); }.hero-progress small { color: var(--muted); font-size: 11px; }
.module-nav { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); border: 1px solid #e4e7ec; background: #fff; }
.module-nav button { min-height: 70px; padding: 11px 6px; border: 0; border-right: 1px solid #eef0f3; background: #fff; color: #667085; font-size: 12px; }.module-nav button:last-child { border-right: 0; }.module-nav button span { display: block; margin-bottom: 6px; color: #98a2b3; font-size: 10px; font-weight: 700; }.module-nav button:hover { background: #f8fafc; color: var(--blue); }.module-nav button.active { box-shadow: inset 0 -3px var(--blue); background: #eff6ff; color: var(--blue); font-weight: 700; }.module-nav button.active span { color: var(--blue); }
.section-title-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding-top: 8px; }.section-title-row h2 { margin: 0; font-size: 25px; }.section-title-row p:last-child { margin: 7px 0 0; color: var(--muted); font-size: 13px; }
.text-button { padding: 0; border: 0; background: none; color: var(--blue); font-size: 13px; }.outline-button { padding: 10px 16px; border: 1px solid #d0d5dd; background: #fff; color: #344054; font-size: 13px; }.outline-button:hover { border-color: var(--blue); color: var(--blue); }
.metric-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }.metric-grid article { padding: 19px; border: 1px solid #e4e7ec; background: #fff; }.metric-grid article > span { display: block; color: var(--muted); font-size: 12px; }.metric-grid strong { display: inline-block; margin: 12px 5px 5px 0; font-size: 26px; }.metric-grid small { color: #98a2b3; font-size: 11px; }.metric-grid.four { grid-template-columns: repeat(4, 1fr); }
.warning { color: #d97706 !important; }.green, .success { color: #16a34a !important; }.orange { color: #ea580c !important; }
.two-column { display: grid; grid-template-columns: 1.25fr 1fr; gap: 18px; }.panel { padding: 22px; border: 1px solid #e4e7ec; background: #fff; }.compact-panel { padding-bottom: 18px; }.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 16px; }.panel-heading span { color: #98a2b3; font-size: 11px; }.panel-heading h3 { margin: 3px 0 0; font-size: 17px; }.panel-heading b { padding: 5px 9px; border-radius: 4px; background: #eff6ff; color: var(--blue); font-size: 11px; }
.task-row { display: flex; align-items: center; width: 100%; padding: 13px 0; border: 0; border-top: 1px solid #f0f1f3; background: #fff; text-align: left; }.task-type { width: 46px; color: var(--blue); font-size: 11px; font-weight: 700; }.task-main { display: grid; flex: 1; gap: 4px; }.task-main strong { font-size: 13px; }.task-main small { color: #98a2b3; font-size: 11px; }.task-dot { color: #98a2b3; }.task-dot.urgent { color: #ef4444; }
.chart { display: flex; align-items: flex-end; gap: 8px; height: 160px; padding: 15px 8px 0; border-bottom: 1px solid #dfe3e8; background: repeating-linear-gradient(to top, transparent 0, transparent 39px, #f2f4f7 40px); }.chart span { flex: 1; min-width: 5px; border-radius: 3px 3px 0 0; background: linear-gradient(#60a5fa, #2563eb); }.chart-labels { display: flex; justify-content: space-between; padding-top: 8px; color: #98a2b3; font-size: 10px; }
.quick-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; }.quick-grid button { position: relative; display: grid; gap: 6px; min-height: 86px; padding: 16px; border: 1px solid #eaecf0; background: #fbfcfe; text-align: left; }.quick-grid button:hover { border-color: #93b4f7; background: #f5f9ff; }.quick-grid strong { font-size: 13px; }.quick-grid span { color: #98a2b3; font-size: 10px; }.quick-grid i { position: absolute; right: 13px; bottom: 12px; color: var(--blue); font-style: normal; }
.filter-panel { display: grid; gap: 16px; }.search-box { display: flex; align-items: center; max-width: 480px; border: 1px solid #d0d5dd; background: #fff; }.search-box span { padding-left: 13px; color: #98a2b3; }.search-box input { width: 100%; padding: 11px 13px; border: 0; outline: none; }.filter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }.filter-row > span { width: 66px; color: #667085; font-size: 12px; }.filter-row button, .list-toolbar button, .tab-bar button { padding: 7px 11px; border: 0; background: transparent; color: #667085; font-size: 12px; }.filter-row button.active, .list-toolbar button.active { border-radius: 4px; background: #eff6ff; color: var(--blue); font-weight: 600; }
.list-toolbar { display: flex; align-items: center; justify-content: space-between; color: #667085; font-size: 12px; }.list-toolbar strong { color: var(--blue); }
.course-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }.course-card { overflow: hidden; border: 1px solid #e4e7ec; background: #fff; }.course-cover { position: relative; display: flex; min-height: 122px; padding: 18px; color: #fff; flex-direction: column; justify-content: space-between; }.course-cover span { font-size: 11px; opacity: .8; }.course-cover strong { max-width: 70%; font-size: 18px; }.course-cover button { position: absolute; top: 12px; right: 12px; width: 32px; height: 32px; border: 1px solid rgb(255 255 255 / 35%); border-radius: 50%; background: rgb(0 0 0 / 18%); color: #fff; font-size: 19px; }.course-body { padding: 17px; }.tag-row { display: flex; gap: 6px; }.tag-row span { padding: 3px 7px; border-radius: 3px; background: #f2f4f7; color: #667085; font-size: 9px; }.course-body h3 { min-height: 44px; margin: 10px 0 6px; font-size: 15px; line-height: 1.45; }.course-body p { margin: 0 0 13px; color: #98a2b3; font-size: 11px; }.course-body footer { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }.course-body footer span { color: #d97706; font-size: 12px; font-weight: 700; }.course-body footer button, .learning-action button, .assignment button, .job-card button { padding: 8px 13px; border: 0; border-radius: 3px; background: var(--blue); color: #fff; font-size: 11px; }
.tab-bar { display: flex; border-bottom: 1px solid #e4e7ec; gap: 6px; }.tab-bar button { padding: 11px 16px; }.tab-bar button.active { box-shadow: inset 0 -2px var(--blue); color: var(--blue); font-weight: 700; }.tab-bar b { padding: 1px 5px; border-radius: 8px; background: #eef2f6; font-size: 9px; }
.learning-list { display: grid; gap: 12px; }.learning-list > article { display: flex; min-height: 116px; padding: 16px; border: 1px solid #e4e7ec; background: #fff; }.learning-thumb { display: grid; width: 130px; background: linear-gradient(135deg, #dbeafe, #bfdbfe); color: var(--blue); font-size: 14px; font-weight: 700; place-items: center; }.learning-content { display: flex; align-items: center; justify-content: space-between; flex: 1; gap: 25px; padding-left: 20px; }.learning-content span { color: var(--blue); font-size: 10px; }.learning-content h3 { margin: 6px 0; font-size: 16px; }.learning-content p { margin: 0; color: #98a2b3; font-size: 11px; }.learning-action { width: 180px; text-align: right; }.learning-action strong { display: block; margin-bottom: 8px; color: var(--blue); }.learning-action button { margin-top: 12px; }.empty-state { padding: 50px; border: 1px dashed #d0d5dd; color: #98a2b3; text-align: center; }
.timeline { display: grid; }.timeline p { display: grid; grid-template-columns: 100px 1fr auto; margin: 0; padding: 11px 0; border-top: 1px solid #f0f1f3; font-size: 11px; }.timeline b { color: #475467; }.timeline span { color: #667085; }.timeline em { color: #16a34a; font-style: normal; }
.status-pill { padding: 8px 12px; border-radius: 20px; background: #ecfdf3; color: #15803d; font-size: 11px; }.status-pill.live { background: #fff1f2; color: #e11d48; }.warning-bg { background: #fff7ed; color: #c2410c; }
.feature-banner { display: flex; align-items: center; justify-content: space-between; min-height: 210px; padding: 34px 46px; color: #fff; }.feature-banner > div > span { font-size: 11px; font-weight: 700; letter-spacing: .08em; opacity: .8; }.feature-banner h2 { margin: 11px 0 8px; font-size: 26px; }.feature-banner p { margin: 0 0 20px; font-size: 12px; opacity: .8; }.feature-banner button { padding: 10px 18px; border: 0; background: #fff; color: #1d4ed8; font-size: 12px; font-weight: 700; }.vr-banner { background: linear-gradient(120deg, #0f172a, #1d4ed8); }.qualification-banner { background: linear-gradient(120deg, #7c2d12, #ea580c); }.growth-banner { background: linear-gradient(120deg, #064e3b, #059669); }.school-banner { background: linear-gradient(120deg, #312e81, #6366f1); }.competition-banner { background: linear-gradient(120deg, #701a75, #be185d); }
.score-ring { display: grid; width: 116px; height: 116px; border: 10px solid rgb(255 255 255 / 25%); border-top-color: #fff; border-radius: 50%; place-content: center; text-align: center; }.score-ring strong { font-size: 25px; }.score-ring span { font-size: 10px; opacity: .75; }.orange-ring { border-right-color: #fff; }
.three-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }.info-card { padding: 20px; border: 1px solid #e4e7ec; background: #fff; }.info-card > span { color: #d97706; font-size: 10px; font-weight: 700; }.info-card h3 { margin: 10px 0 7px; font-size: 15px; }.info-card p { color: #98a2b3; font-size: 11px; }.info-card footer { display: flex; align-items: center; justify-content: space-between; margin-top: 24px; }.info-card footer small { color: #667085; }.info-card footer button { border: 0; background: none; color: var(--blue); font-size: 11px; }
.data-table { display: grid; }.data-table > div { display: grid; grid-template-columns: 2fr 1fr .6fr .7fr .7fr; gap: 12px; padding: 12px 8px; border-top: 1px solid #f0f1f3; font-size: 11px; }.data-table .table-head { background: #f8fafc; color: #667085; font-weight: 700; }
.exam-list .exam-date { display: grid; width: 100px; background: #f1f5f9; place-content: center; text-align: center; }.exam-date strong { font-size: 22px; }.exam-date span { margin-top: 4px; color: #667085; font-size: 9px; }.certificate-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.certificate-grid article { display: flex; align-items: center; gap: 13px; padding: 16px; border: 1px solid #e4e7ec; }.certificate-mark { display: grid; width: 42px; height: 52px; background: #dbeafe; color: #1d4ed8; font-size: 18px; font-weight: 700; place-content: center; }.certificate-mark.gold { background: #fef3c7; color: #b45309; }.certificate-grid article > div:nth-child(2) { flex: 1; }.certificate-grid strong { font-size: 12px; }.certificate-grid p { margin: 5px 0 0; color: #98a2b3; font-size: 9px; }.certificate-grid article > span { font-size: 10px; }
.certificate-grid small { color: #98a2b3; font-size: 9px; }.enterprise-empty { padding: 36px 20px; border: 1px dashed #d0d5dd; background: #f8fafc; text-align: center; }.enterprise-empty strong { font-size: 13px; }.enterprise-empty p { margin: 7px 0 0; color: #98a2b3; font-size: 10px; }
.live-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }.live-grid article { overflow: hidden; border: 1px solid #e4e7ec; background: #fff; }.live-cover { display: flex; align-items: flex-start; justify-content: space-between; min-height: 125px; padding: 16px; background: linear-gradient(135deg, #111827, #334155); color: #fff; }.live-cover span { padding: 4px 8px; border-radius: 3px; background: #e11d48; font-size: 9px; }.live-cover b { margin: auto; font-size: 24px; opacity: .18; }.live-grid article > div:last-child { padding: 17px; }.live-grid article > div:last-child > span { color: var(--blue); font-size: 9px; }.live-grid h3 { min-height: 42px; margin: 8px 0 5px; font-size: 15px; }.live-grid p { margin: 0 0 15px; color: #98a2b3; font-size: 10px; }.live-grid strong { font-size: 11px; }.live-grid button { float: right; padding: 7px 11px; border: 1px solid #bfdbfe; background: #eff6ff; color: var(--blue); font-size: 10px; }
.assignment, .notice-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding-top: 12px; border-top: 1px solid #f0f1f3; }.assignment strong, .notice-row strong { font-size: 13px; }.assignment p, .notice-row p { margin: 5px 0 0; color: #98a2b3; font-size: 10px; }
.requirement-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }.requirement-grid article { display: flex; align-items: center; gap: 12px; padding: 17px; border: 1px solid #e4e7ec; }.requirement-grid article > span { display: grid; width: 30px; height: 30px; border-radius: 50%; place-content: center; }.requirement-grid .done > span { background: #dcfce7; color: #16a34a; }.requirement-grid .todo > span { background: #ffedd5; color: #ea580c; }.requirement-grid strong { font-size: 12px; }.requirement-grid p { margin: 4px 0 0; color: #98a2b3; font-size: 9px; }.notice-row > span { padding: 4px 7px; border-radius: 3px; background: #fee2e2; color: #dc2626; font-size: 9px; }.notice-row > div { flex: 1; }.notice-row button { border: 0; background: none; color: var(--blue); font-size: 11px; }
.gap-list { display: grid; }.gap-list button { display: flex; justify-content: space-between; padding: 14px 0; border: 0; border-top: 1px solid #f0f1f3; background: #fff; color: #475467; text-align: left; }.gap-list strong { color: #ea580c; font-size: 11px; }.renew-card { display: flex; align-items: center; gap: 16px; padding-top: 12px; border-top: 1px solid #f0f1f3; }.renew-card > span { display: grid; width: 58px; height: 58px; border-radius: 50%; background: #fff7ed; color: #ea580c; font-size: 20px; font-weight: 700; place-content: center; }.renew-card strong { font-size: 12px; }.renew-card p { color: #98a2b3; font-size: 10px; }.renew-card button { padding: 0; border: 0; background: none; color: var(--blue); font-size: 10px; }
.switch-button { display: flex; align-items: center; gap: 8px; padding: 9px 13px; border: 1px solid #d0d5dd; background: #fff; color: #667085; font-size: 11px; }.switch-button span { position: relative; width: 28px; height: 16px; border-radius: 10px; background: #d0d5dd; }.switch-button span::after { position: absolute; top: 3px; left: 3px; width: 10px; height: 10px; border-radius: 50%; background: #fff; content: ''; transition: .2s; }.switch-button.active { color: #15803d; }.switch-button.active span { background: #22c55e; }.switch-button.active span::after { left: 15px; }
.internship { display: grid; grid-template-columns: 50px 1fr auto; align-items: center; gap: 12px; padding: 13px 0; border-top: 1px solid #f0f1f3; }.internship time { color: var(--blue); font-weight: 700; }.internship strong { font-size: 12px; }.internship p { margin: 4px 0 0; color: #98a2b3; font-size: 9px; }.internship > span { color: #d97706; font-size: 9px; }.job-card > span { color: var(--blue); font-size: 9px; }.job-card > strong { display: block; margin-top: 8px; }.job-card p { color: #98a2b3; font-size: 10px; }.job-card div { display: flex; gap: 8px; margin-top: 18px; }.job-card button:last-child { border: 1px solid #d0d5dd; background: #fff; color: #667085; }.privacy-copy { margin: 0; color: #667085; font-size: 11px; line-height: 1.7; }
.competition-date { display: grid; width: 120px; height: 120px; border: 1px solid rgb(255 255 255 / 30%); background: rgb(255 255 255 / 10%); place-content: center; text-align: center; }.competition-date strong { font-size: 29px; }.competition-date span { font-size: 10px; opacity: .7; }.button-row { display: flex; gap: 9px; }.feature-banner button.ghost { border: 1px solid rgb(255 255 255 / 50%); background: transparent; color: #fff; }.work-progress > div { display: grid; grid-template-columns: repeat(4, 1fr); gap: 5px; }.work-progress span { padding: 10px 5px; border-top: 3px solid #d0d5dd; color: #98a2b3; font-size: 9px; }.work-progress span.done { border-color: #22c55e; color: #15803d; }.work-progress span.current { border-color: var(--blue); color: var(--blue); }.work-progress p { color: #98a2b3; font-size: 10px; }.work-progress button { padding: 8px 12px; border: 0; background: var(--blue); color: #fff; font-size: 10px; }.award { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid #f0f1f3; }.award i { width: 45px; color: #b45309; font-size: 10px; font-style: normal; }.award strong { font-size: 12px; }.award p { margin: 4px 0 0; color: #98a2b3; font-size: 9px; }
.toast { position: fixed; z-index: 30; right: 28px; bottom: 28px; padding: 12px 18px; border-radius: 6px; background: #172033; color: #fff; box-shadow: 0 12px 30px rgb(15 23 42 / 25%); font-size: 12px; }
@media (max-width: 1100px) { .module-nav { grid-template-columns: repeat(5, 1fr); }.metric-grid { grid-template-columns: repeat(3, 1fr); }.quick-grid { grid-template-columns: repeat(3, 1fr); }.course-grid, .live-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 760px) { .academy-hero { align-items: stretch; padding: 24px; flex-direction: column; }.academy-hero h1 { font-size: 24px; }.hero-progress { width: auto; }.module-nav { grid-template-columns: repeat(2, 1fr); }.metric-grid, .metric-grid.four, .course-grid, .live-grid, .three-grid, .two-column, .requirement-grid, .certificate-grid { grid-template-columns: 1fr; }.section-title-row { align-items: flex-start; flex-direction: column; }.quick-grid { grid-template-columns: repeat(2, 1fr); }.feature-banner { align-items: flex-start; padding: 28px; flex-direction: column; }.score-ring, .competition-date { margin-top: 20px; }.learning-list > article { flex-direction: column; }.learning-thumb { width: 100%; min-height: 85px; }.learning-content { align-items: flex-start; padding: 15px 0 0; flex-direction: column; }.learning-action { width: 100%; text-align: left; }.filter-row > span { width: 100%; }.data-table { overflow-x: auto; }.data-table > div { min-width: 620px; }.timeline p { grid-template-columns: 90px 1fr; }.timeline em { grid-column: 2; }.academy-page { gap: 17px; } }
</style>
