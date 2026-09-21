<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { TRAINING_CATALOG } from '../course-catalog';
import { TRAINING_MODULES } from '../training-modules';
import industrialBanner from '#/assets/imgs/academy/industrial-banner.png';
import safetyCover from '#/assets/imgs/academy/safety-course.png';
import coatingCover from '#/assets/imgs/academy/coating-course.png';
import insulationCover from '#/assets/imgs/academy/insulation-course.png';
import inspectionCover from '#/assets/imgs/academy/inspection-course.png';
import ModuleContent from './components/training-tabs/ModuleContent.vue';

const router = useRouter();
const activeTab = ref('近期上新');
const activeModulePath = ref<string>(TRAINING_MODULES[0].path);
const activeSubModule = ref('');
const contentSectionRef = ref<HTMLElement | null>(null);
const activeBanner = ref(0);
const bannerPaused = ref(false);
let bannerTimer: ReturnType<typeof setInterval> | undefined;
const banners = [
  { title: '防腐保温专业技能\n成长训练营', subtitle: '课程 · 实操 · 考试 · 认证，一站式学习成长', button: '立即学习', module: '/portal/training/courses', category: '' },
  { title: '岗位技能提升专题', subtitle: '围绕实际岗位能力，完成分级学习与实操训练', button: '查看岗位课程', module: '/portal/training/courses', category: 'G' },
  { title: '安全生产与质量提升专题', subtitle: '安全规范、施工质量、检测认证系统学习', button: '进入专题', module: '/portal/training/courses', category: 'A' },
];
const currentBanner = computed(() => banners[activeBanner.value] ?? banners[0]);

function shiftBanner(direction: -1 | 1) {
  activeBanner.value = (activeBanner.value + direction + banners.length) % banners.length;
}
const previewPath = ref<string | null>(null);
const previewModule = computed(() => TRAINING_MODULES.find((item) => item.path === previewPath.value)
  ?? TRAINING_MODULES.find((item) => item.path === activeModulePath.value)
  ?? TRAINING_MODULES[0]);
const tabs = ['近期上新', '热门课程', '安全基础', '防腐涂装', '保温绝热'];
const categoryDescriptions: Record<string, string> = {
  A: '人员与公司认知 / HSE', B: '表面处理 / 涂料配比',
  C: '保温基础 / 保温施工', D: '内衬技术 / 修复工艺',
  E: '无损检测 / 管材检测', F: '材料认知 / 采购仓储',
  G: '工艺方案 / 质量验收',
};

const quickItems = [
  { title: '安全知识库', description: '规范标准、操作要点、典型案例', module: '/portal/training/courses', subModule: 'safety' },
  { title: '岗位入门课程', description: '从岗位职责到基础作业流程', module: '/portal/training/courses', subModule: 'beginner' },
  { title: '技能速记手册', description: '关键工艺参数与质量控制', module: '/portal/training/courses', subModule: 'manual' },
  { title: '实操训练中心', description: '设备操作、维护与故障处理', module: '/portal/training/vr', subModule: 'practice' },
  { title: '专业知识库', description: '防腐保温工艺与行业应用', module: '/portal/training/courses', subModule: 'professional' },
  { title: '能力提升路径', description: '分级学习，逐步完成认证', module: '/portal/training/tasks', subModule: 'ability-path' },
];
const quickIconPaths = [
  'M12 3 20 6v6c0 5-3.4 8-8 9-4.6-1-8-4-8-9V6l8-3z M9 12l2 2 4-4',
  'M4 7l8-4 8 4v10l-8 4-8-4V7z M4 7l8 4 8-4 M12 11v10',
  'M4 6h16 M4 11h16 M4 16h10 M4 20h8',
  'M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M16 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M3 20v-3c0-2 2-3 5-3s5 1 5 3v3 M13 20v-3c0-2 1-3 3-3 3 0 5 1 5 3v3',
  'M4 5h6a3 3 0 0 1 3 3v12a3 3 0 0 0-3-3H4V5z M20 5h-5a3 3 0 0 0-3 3v12a3 3 0 0 1 3-3h5V5z',
  'M4 20h16 M6 16l4-4 3 2 5-6 M15 8h3v3',
];
const activityItems = [
  { tag: '专题', title: '安全生产月课程上线', detail: '岗位学习地图同步更新', date: '09.15', module: '/portal/training/courses', category: 'A' },
  { tag: '实训', title: '防腐涂装实操训练', detail: '设备操作与现场注意事项', date: '09.12', module: '/portal/training/vr', category: '' },
  { tag: '认证', title: '技能等级认证报名', detail: '考试安排与报名说明', date: '09.10', module: '/portal/training/exam', category: '' },
  { tag: '分享', title: '从入门到独立上岗', detail: '优秀学员的学习案例', date: '09.08', module: '/portal/training/tasks', category: '' },
];
const catalogCourses = TRAINING_CATALOG.flatMap((category) => category.groups.flatMap((group) =>
  group.courses.map((course) => ({ ...course, categoryCode: category.code, categoryName: category.name, groupName: group.name }))));
const displayCourses = computed(() => {
  const categoryCode = activeTab.value === '安全基础' ? 'A' : activeTab.value === '防腐涂装' ? 'B' : activeTab.value === '保温绝热' ? 'C' : '';
  const source = categoryCode ? catalogCourses.filter((course) => course.categoryCode === categoryCode) :
    activeTab.value === '热门课程' ? catalogCourses.filter((course) => ['B', 'C', 'E'].includes(course.categoryCode)) : catalogCourses;
  return source.slice(0, 4);
});
const selectedCourse = ref<(typeof catalogCourses)[number] | null>(null);

async function switchModule(path: string, subModule = '') {
  activeModulePath.value = path;
  activeSubModule.value = subModule;
  previewPath.value = null;
  await nextTick();
  window.setTimeout(() => {
    const contentSection = contentSectionRef.value;
    if (!contentSection) return;
    const top = contentSection.getBoundingClientRect().top + window.scrollY - 24;
    window.scrollTo({ behavior: 'smooth', top });
  }, 300);
}

function handleQuickEntry(item: (typeof quickItems)[number]) {
  switchModule(item.module, item.subModule);
}

function coverFor(categoryCode: string) {
  return categoryCode === 'A' ? safetyCover : categoryCode === 'B' ? coatingCover : categoryCode === 'C' ? insulationCover : inspectionCover;
}

onMounted(() => {
  bannerTimer = setInterval(() => {
    if (!bannerPaused.value) activeBanner.value = (activeBanner.value + 1) % banners.length;
  }, 5500);
});
onUnmounted(() => { if (bannerTimer) clearInterval(bannerTimer); });

/** 首页内的「更多」与课程弹窗入口：不跳独立页面，内嵌切换到课程中心模块 */
function goCourses(category = '') {
  const categoryByTab: Record<string, string> = { 安全基础: 'A', 防腐涂装: 'B', 保温绝热: 'C' };
  switchModule('/portal/training/courses', category || categoryByTab[activeTab.value] || '');
}

</script>

<template>
  <div class="academy-home">
    <section class="hero-grid">
      <nav class="category-panel" aria-label="课程分类">
        <button v-for="item in TRAINING_CATALOG.slice(0, 7)" :key="item.code" @click="switchModule('/portal/training/courses', item.code)">
          <b>{{ item.name }}</b><span>{{ categoryDescriptions[item.code] || item.groups.slice(0, 2).map((x) => x.name).join(' / ') }}</span>
        </button>
      </nav>

      <div class="hero-banner" :style="{ backgroundImage: `linear-gradient(90deg, rgba(4, 26, 45, .92), rgba(4, 31, 50, .58) 54%, rgba(4, 31, 50, .05)), url('${industrialBanner}')` }" @mouseenter="bannerPaused = true" @mouseleave="bannerPaused = false">
        <button class="banner-arrow banner-arrow--prev" type="button" aria-label="上一张 Banner" @click="shiftBanner(-1)"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg></button>
        <div class="hero-copy">
          <span>云培训学院 · 岗位能力提升</span>
          <h1 :key="activeBanner">{{ currentBanner.title }}</h1>
          <p>{{ currentBanner.subtitle }}</p>
          <button type="button" @click="switchModule(currentBanner.module, currentBanner.category)">{{ currentBanner.button }} <i>›</i></button>
          <button type="button" class="hero-exam" @click="router.push('/portal/training/exam')">前往考试 <i>›</i></button>
        </div>
        <button class="banner-arrow banner-arrow--next" type="button" aria-label="下一张 Banner" @click="shiftBanner(1)"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg></button>
        <div class="dots" aria-label="切换运营专题"><button v-for="(_, index) in banners" :key="index" type="button" :class="{ active: activeBanner === index }" :aria-label="`切换到第 ${index + 1} 张专题`" @click="activeBanner = index"></button></div>
      </div>

      <aside class="welcome-card">
        <h2>开启专属学习计划</h2>
        <p>按岗位匹配课程与成长路径</p>
        <div class="welcome-art"><span><small>01</small>学</span><span><small>02</small>练</span><span><small>03</small>考</span></div>
        <button type="button" @click="router.push('/portal/training/exam')">前往考试 ›</button>
      </aside>
    </section>

    <section class="module-gateway" aria-label="云培训学院功能入口" @mouseleave="previewPath = null">
      <nav class="module-gateway-nav">
        <button
          v-for="item in TRAINING_MODULES"
          :key="item.path"
          type="button"
          :class="{ active: activeModulePath === item.path }"
          @mouseenter="previewPath = item.path"
          @focus="previewPath = item.path"
          @click="switchModule(item.path)"
        >{{ item.label }}</button>
      </nav>
      <div class="module-gateway-summary"><b>{{ previewModule.label }}</b><span>{{ previewModule.summary }}</span></div>
    </section>

    <div ref="contentSectionRef" class="tab-content">
    <template v-if="activeModulePath === TRAINING_MODULES[0].path">
    <section class="middle-grid">
      <div>
        <h2 class="section-title">免费学习</h2>
        <div class="quick-grid">
          <button v-for="(item, index) in quickItems" :key="item.title" @click="handleQuickEntry(item)">
            <span class="quick-icon" :class="`tone-${index % 3}`"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="quickIconPaths[index]" /></svg></span>
            <span><b>{{ item.title }}</b><small>{{ item.description }}</small></span>
          </button>
        </div>
      </div>
      <div>
        <h2 class="section-title">培训动态 <em>学习交流</em></h2>
        <div class="community">
          <button v-for="item in activityItems" :key="item.title" type="button" class="activity-row" @click="switchModule(item.module, item.category)"><span class="activity-tag">{{ item.tag }}</span><span class="activity-copy"><b>{{ item.title }}</b><small>{{ item.detail }}</small></span><time>{{ item.date }}</time></button>
        </div>
      </div>
    </section>
    <section class="recommend">
      <header>
        <h2 class="section-title">为你推荐</h2>
        <nav><button v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">{{ tab }}</button></nav>
        <button class="more" type="button" @click="goCourses()">更多</button>
      </header>
      <div class="course-grid">
        <article v-for="course in displayCourses" :key="course.code" role="button" tabindex="0" @click="selectedCourse = course" @keydown.enter="selectedCourse = course">
          <div class="course-cover"><img :src="coverFor(course.categoryCode)" :alt="course.name" /><span>课程</span></div>
          <div class="course-body"><h3>{{ course.name }}</h3><p>{{ course.categoryName }} · {{ course.groupName }}</p><small>{{ course.code }}<template v-if="course.stdHours"> · {{ course.stdHours }} 学时</template></small></div>
        </article>
      </div>
    </section>
    </template>
    <ModuleContent v-else :key="`${activeModulePath}-${activeSubModule}`" :module-path="activeModulePath" :sub-module="activeSubModule" />
    </div>

    <nav class="side-rail"><button @click="switchModule('/portal/training/courses')">体系课</button><button @click="switchModule('/portal/training/tasks')">训练营</button><button @click="switchModule('/portal/training/live')">直播</button><button @click="switchModule('/portal/training/courses')">公开课</button><button @click="switchModule('/portal/training/contest')">更多活动</button><hr /><button @click="switchModule('/portal/training/profile')">个人档案</button></nav>
    <button class="ai-helper" type="button" @click="router.push('/ai')"><span>AI</span><b>学习助手</b></button>
    <div v-if="selectedCourse" class="course-dialog-backdrop" @click.self="selectedCourse = null">
      <section class="course-dialog" role="dialog" aria-modal="true" :aria-label="selectedCourse.name">
        <button class="dialog-close" type="button" aria-label="关闭课程详情" @click="selectedCourse = null">×</button>
        <small>{{ selectedCourse.code }} · {{ selectedCourse.categoryName }} / {{ selectedCourse.groupName }}</small>
        <h2>{{ selectedCourse.name }}</h2>
        <p>标准学时：{{ selectedCourse.stdHours ?? '待配置' }}<br />{{ selectedCourse.required ? '岗位必修课程' : '自主选学课程' }}</p>
        <button class="dialog-cta" type="button" @click="goCourses(selectedCourse.categoryCode)">查看分类课程 ›</button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.academy-home { position: relative; padding: 20px 0 70px; color: #303030; background: #fff; font-family: "PingFang SC", "Microsoft YaHei", Arial, sans-serif; }
.hero-grid { display: grid; grid-template-columns: 344px minmax(0, 766px) 292px; gap: 18px; height: 376px; }
.category-panel, .welcome-card, .hero-banner { height: 376px; border: 1px solid #f0f0f0; border-radius: 9px; box-shadow: 0 8px 28px rgb(39 45 55 / 6%); }
.category-panel { padding: 12px 16px; background: #fff; overflow: hidden; }
.category-panel button { display: grid; grid-template-columns: 112px 1fr; align-items: center; width: 100%; height: 50px; padding: 0 4px; border: 0; background: transparent; color: #5d5d5d; cursor: pointer; text-align: left; }
.category-panel button:hover { color: #ec741c; background: #fff8f2; }
.category-panel b { color: #333; font-size: 16px; }.category-panel span { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.hero-banner { position: relative; overflow: hidden; border: 0; color: #fff; background: linear-gradient(132deg, #073e72 0%, #096c82 48%, #159586 100%); }
.hero-banner::before { position: absolute; inset: 0; background: linear-gradient(90deg, rgb(0 0 0 / 9%), transparent 60%), radial-gradient(circle at 78% 18%, rgb(255 255 255 / 18%), transparent 25%); content: ''; }
.hero-copy { position: relative; z-index: 2; padding: 62px 0 0 48px; }.hero-copy > span { font-size: 14px; opacity: .88; }.hero-copy h1 { margin: 13px 0 10px; color: #fff; font-size: 38px; font-weight: 750; line-height: 1.25; letter-spacing: 1px; }.hero-copy p { margin: 0 0 32px; font-size: 15px; opacity: .88; }.hero-copy button { min-width: 165px; height: 40px; color: #fff; font-size: 16px; font-weight: 600; background: #f47a20; border: 0; border-radius: 20px; box-shadow: 0 6px 14px rgb(244 122 32 / 34%); cursor: pointer; }.hero-copy i { font-size: 20px; font-style: normal; }
.hero-copy .hero-exam { margin-left: 12px; background: rgb(255 255 255 / 16%); border: 1px solid rgb(255 255 255 / 55%); box-shadow: none; }
.hero-copy .hero-exam:hover { background: rgb(255 255 255 / 26%); }
.hero-art { position: absolute; right: 10px; bottom: 0; width: 330px; height: 350px; }.ring { position: absolute; border: 2px solid rgb(255 255 255 / 18%); border-radius: 50%; }.ring-a { top: 55px; right: 8px; width: 265px; height: 265px; }.ring-b { top: 95px; right: 48px; width: 185px; height: 185px; }.worker { position: absolute; right: 76px; bottom: 0; width: 170px; height: 275px; }.helmet { position: absolute; z-index: 3; top: 7px; left: 34px; width: 102px; height: 43px; background: #ffd04a; border-radius: 52px 52px 12px 12px; }.head { position: absolute; z-index: 2; top: 34px; left: 48px; width: 76px; height: 82px; background: #f0b18e; border-radius: 38px; }.body { position: absolute; bottom: -28px; left: 5px; width: 164px; height: 205px; background: linear-gradient(90deg, #163c69, #2874a3); border: 12px solid #f3c330; border-bottom: 0; border-radius: 74px 74px 10px 10px; }.pipe { position: absolute; right: -30px; height: 26px; background: #b7d8df; border: 6px solid #77a7b5; border-radius: 15px; transform: rotate(-14deg); }.pipe-a { bottom: 54px; width: 185px; }.pipe-b { right: 5px; bottom: 28px; width: 110px; }.dots { position: absolute; z-index: 3; right: 24px; bottom: 17px; display: flex; gap: 7px; }.dots i { width: 20px; height: 3px; background: rgb(255 255 255 / 52%); border-radius: 3px; }.dots i.active { background: #fff; }
.welcome-card { display: flex; flex-direction: column; align-items: center; padding: 30px 26px 22px; background: linear-gradient(160deg, #fff 60%, #fff8ef); text-align: center; }.welcome-card h2 { align-self: flex-start; margin: 0; font-size: 18px; font-weight: 500; }.welcome-card > p { align-self: flex-start; margin: 8px 0 20px; color: #999; font-size: 13px; }.welcome-art { display: flex; align-items: flex-end; justify-content: center; width: 190px; height: 155px; gap: 7px; }.welcome-art span { display: grid; place-items: center; width: 55px; height: 65px; color: #fff; font-size: 25px; font-weight: 700; background: #ff9b35; box-shadow: 0 8px 18px rgb(255 128 24 / 23%); transform: rotate(-7deg); }.welcome-art span:nth-child(2) { height: 90px; background: #ff6427; transform: rotate(4deg); }.welcome-art span:nth-child(3) { height: 72px; background: #f2ba38; transform: rotate(10deg); }.welcome-card > button { width: 198px; height: 38px; color: #fff; background: #ff811b; border: 0; border-radius: 19px; cursor: pointer; }.welcome-card a { margin-top: 10px; color: #ec741c; cursor: pointer; font-size: 13px; }
.middle-grid { display: grid; grid-template-columns: minmax(0, 1076px) 292px; gap: 24px; margin-top: 48px; }.section-title { margin: 0 0 22px; color: #292929; font-size: 25px; line-height: 1; }.section-title em { color: #aaa; font-style: normal; font-weight: 400; }.quick-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 20px; }.quick-grid button { display: flex; align-items: center; height: 100px; padding: 16px; background: #fff; border: 1px solid #f0f0f0; border-radius: 9px; box-shadow: 0 6px 20px rgb(42 46 55 / 7%); cursor: pointer; text-align: left; }.quick-icon { display: grid; flex: 0 0 58px; place-items: center; width: 58px; height: 58px; margin-right: 16px; color: #fff; font-size: 25px; background: #5aa8ee; border-radius: 50%; }.quick-icon.tone-1 { background: #ffa33c; }.quick-icon.tone-2 { background: #70ca5b; }.quick-grid b { display: block; margin-bottom: 6px; color: #444; font-size: 16px; font-weight: 500; }.quick-grid small { display: block; overflow: hidden; color: #aaa; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.community { height: 214px; padding: 16px 20px; background: #fff; border: 1px solid #f0f0f0; border-radius: 9px; box-shadow: 0 6px 20px rgb(42 46 55 / 7%); }.community a { position: relative; display: -webkit-box; margin-bottom: 15px; padding-left: 17px; overflow: hidden; color: #595959; cursor: pointer; font-size: 13px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.community a::before { position: absolute; top: 5px; left: 0; width: 8px; height: 8px; border: 1px solid #ff8a31; border-radius: 50%; content: ''; }
.recommend { margin-top: 58px; }.recommend > header { display: flex; align-items: center; margin-bottom: 20px; }.recommend .section-title { margin: 0 34px 0 0; }.recommend nav { display: flex; gap: 26px; }.recommend nav button, .more { padding: 0 0 8px; color: #777; background: transparent; border: 0; cursor: pointer; font-size: 15px; }.recommend nav button.active { color: #ee741d; border-bottom: 2px solid #ee741d; }.more { margin-left: auto; padding: 7px 17px; background: #f7f7f7; border-radius: 18px; }.course-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 22px; }.course-grid article { overflow: hidden; background: #fff; border: 1px solid #ededed; border-radius: 8px; cursor: pointer; transition: transform .15s, box-shadow .15s; }.course-grid article:hover { box-shadow: 0 10px 28px rgb(30 35 45 / 12%); transform: translateY(-3px); }.course-cover { position: relative; display: grid; height: 178px; place-items: center; overflow: hidden; background: #e8d7eb; }.course-cover.cover-1 { background: #f3dcc5; }.course-cover.cover-2 { background: #d7e8ed; }.course-cover.cover-3 { background: #dbe5f3; }.course-cover > span { position: absolute; top: 12px; left: 12px; padding: 3px 7px; color: #fff; background: rgb(35 35 35 / 70%); border-radius: 3px; font-size: 11px; }.course-symbol { display: grid; width: 92px; height: 92px; place-items: center; color: #fff; font-size: 42px; font-weight: 700; background: rgb(45 74 93 / 72%); border: 8px solid rgb(255 255 255 / 45%); border-radius: 50%; box-shadow: 0 12px 25px rgb(0 0 0 / 13%); }.course-body { padding: 15px; }.course-body h3 { height: 44px; margin: 0 0 6px; overflow: hidden; color: #333; font-size: 16px; font-weight: 500; line-height: 1.4; }.course-body p, .course-body small { margin: 0; color: #999; font-size: 12px; }.course-body small { display: block; margin-top: 7px; }
.side-rail { position: fixed; z-index: 25; top: 365px; right: 18px; display: flex; width: 92px; flex-direction: column; padding: 16px 8px; background: #fff; border: 1px solid #eee; border-radius: 8px; box-shadow: 0 8px 25px rgb(25 32 45 / 9%); }.side-rail button { height: 48px; color: #888; background: transparent; border: 0; cursor: pointer; }.side-rail button:hover { color: #eb741f; }.side-rail hr { width: 56px; border: 0; border-top: 1px solid #eee; }.ai-helper { position: fixed; z-index: 25; right: 18px; bottom: 28px; display: flex; align-items: center; gap: 8px; padding: 8px 16px 8px 9px; color: #fff; background: #f47a20; border: 0; border-radius: 28px; box-shadow: 0 8px 22px rgb(244 122 32 / 28%); cursor: pointer; }.ai-helper span { display: grid; width: 35px; height: 35px; place-items: center; background: #fff; color: #ef741b; border-radius: 50%; font-weight: 800; }.ai-helper b { font-size: 15px; }
/* 2048px 视觉校准：更紧凑的门户节奏和工业课程运营 Banner。 */
.academy-home { padding-top: 18px; }
.hero-grid { height: 356px; }
.category-panel, .welcome-card, .hero-banner { height: 356px; border-radius: 7px; box-shadow: 0 5px 18px rgb(32 39 51 / 5%); }
.category-panel { padding: 13px 15px; }
.category-panel button { grid-template-columns: 142px minmax(0, 1fr); height: 47px; padding: 0 5px; }
.category-panel b { overflow: hidden; font-size: 14px; font-weight: 560; letter-spacing: -.1px; text-overflow: ellipsis; white-space: nowrap; }
.category-panel span { color: #8b8b8b; font-size: 12px; }
.hero-banner { background: linear-gradient(126deg, #052c50 0%, #074a66 55%, #096679 100%); }
.hero-banner::before { background: radial-gradient(circle at 82% 18%, rgb(61 188 187 / 25%), transparent 30%), linear-gradient(90deg, rgb(1 22 40 / 20%), transparent 62%); }
.hero-copy { padding: 51px 0 0 46px; }
.hero-copy > span { font-size: 13px; letter-spacing: .02em; }
.hero-copy h1 { margin: 12px 0 10px; font-size: 33px; line-height: 1.22; letter-spacing: .5px; }
.hero-copy p { margin-bottom: 26px; font-size: 14px; }
.hero-copy button { min-width: 148px; height: 36px; font-size: 14px; box-shadow: 0 5px 12px rgb(244 122 32 / 24%); }
.hero-art { right: 18px; width: 315px; height: 330px; }
.worker { right: 58px; bottom: 48px; width: 176px; height: 165px; border: 1px solid rgb(131 218 224 / 55%); background: linear-gradient(145deg, rgb(15 113 133 / 88%), rgb(4 45 76 / 94%)); border-radius: 9px; box-shadow: 0 18px 40px rgb(0 16 31 / 25%); }
.worker::before { position: absolute; top: 18px; left: 18px; color: #a9e5e6; content: 'COATING PROCESS'; font-size: 10px; letter-spacing: .14em; }
.worker::after { position: absolute; right: 18px; bottom: 18px; left: 18px; height: 62px; background: linear-gradient(90deg, transparent 0 7%, #37c2b4 7% 10%, transparent 10% 23%, #f2b84b 23% 26%, transparent 26% 43%, #60a9e8 43% 46%, transparent 46% 64%, #37c2b4 64% 67%, transparent 67%), repeating-linear-gradient(0deg, rgb(255 255 255 / 10%) 0 1px, transparent 1px 16px); border-bottom: 1px solid rgb(255 255 255 / 20%); content: ''; }
.helmet, .head { display: none; }
.body { top: 52px; bottom: auto; left: 18px; width: 76px; height: 76px; background: conic-gradient(#2cc8b5 0 31%, #f3bd4e 31% 63%, #4d94db 63% 82%, rgb(255 255 255 / 12%) 82%); border: 10px solid rgb(255 255 255 / 8%); border-radius: 50%; }
.ring-a { top: 36px; right: 0; width: 280px; height: 280px; }.ring-b { top: 74px; right: 38px; width: 205px; height: 205px; }
.pipe { height: 22px; border-width: 5px; opacity: .8; }.pipe-a { right: -42px; bottom: 37px; width: 210px; }.pipe-b { right: -4px; bottom: 15px; width: 138px; }
.welcome-card { padding: 24px 23px 18px; }.welcome-card h2 { font-size: 17px; }.welcome-card > p { margin: 6px 0 12px; font-size: 12px; }.welcome-art { height: 139px; }.welcome-art span { width: 50px; height: 59px; font-size: 22px; }.welcome-art span:nth-child(2) { height: 81px; }.welcome-art span:nth-child(3) { height: 66px; }.welcome-card > button { width: 188px; height: 34px; font-size: 13px; }.welcome-card a { margin-top: 8px; font-size: 12px; }
.middle-grid { margin-top: 35px; }.section-title { margin-bottom: 18px; font-size: 23px; }.quick-grid { gap: 11px 18px; }.quick-grid button { height: 82px; padding: 12px 14px; border-radius: 7px; box-shadow: 0 4px 15px rgb(42 46 55 / 5%); }.quick-icon { flex-basis: 48px; width: 48px; height: 48px; margin-right: 13px; font-size: 21px; }.quick-grid b { margin-bottom: 4px; font-size: 15px; }.quick-grid small { font-size: 11px; }
.community { height: 175px; padding: 13px 18px; border-radius: 7px; box-shadow: 0 4px 15px rgb(42 46 55 / 5%); }.community a { margin-bottom: 9px; padding-left: 14px; color: #626262; font-size: 12px; line-height: 1.45; }.community a::before { top: 4px; width: 6px; height: 6px; }
.recommend { margin-top: 36px; }.recommend > header { margin-bottom: 15px; }.recommend .section-title { margin-right: 30px; }.recommend nav { gap: 23px; }.course-cover { height: 164px; }.course-body { padding: 13px 14px; }
/* 仅云培训学院首页的视觉精修，保留现有区域与路由行为。 */
.academy-home { color: #343b43; }
.hero-grid { grid-template-columns: 258px minmax(0, 796px) 230px; gap: 18px; }
.category-panel { padding: 13px 12px; }
.category-panel button { grid-template-columns: 132px minmax(0, 1fr); gap: 4px; padding: 0 3px; }
.category-panel b { color: #38414a; font-size: 12.5px; font-weight: 550; letter-spacing: -.3px; }
.category-panel span { overflow: visible; color: #9199a2; font-size: 9.5px; letter-spacing: -.4px; text-overflow: clip; }
.hero-banner { background-color: #052b42; background-position: center center; background-size: cover; }
.hero-banner::before { display: none; }
.hero-copy { padding: 62px 0 0 43px; }
.hero-copy h1 { font-size: 34px; font-weight: 650; }
.hero-copy p { color: #d3e0e6; }
.welcome-card { align-items: flex-start; padding: 30px 17px 20px; background: linear-gradient(160deg, #fff 75%, #fffaf5); text-align: left; }
.welcome-card h2 { font-size: 16px; font-weight: 570; white-space: nowrap; }
.welcome-card > p { margin: 6px 0 0; color: #9ba1a8; font-size: 11px; }
.welcome-art { align-self: center; align-items: center; width: 194px; height: 183px; gap: 5px; }
.welcome-art span, .welcome-art span:nth-child(2), .welcome-art span:nth-child(3) { display: flex; width: 59px; height: 77px; flex-direction: column; align-items: flex-start; justify-content: space-between; padding: 11px; font-size: 22px; font-weight: 550; border-radius: 5px; box-shadow: none; transform: none; }
.welcome-art span { background: #ed935a; }.welcome-art span:nth-child(2) { background: #e87743; }.welcome-art span:nth-child(3) { background: #d9a44c; }
.welcome-art small { color: rgb(255 255 255 / 80%); font-size: 9px; letter-spacing: .1em; }
.welcome-card > button { align-self: center; width: 190px; height: 35px; background: #ed7b24; }
.welcome-card a { align-self: center; margin-top: 8px; color: #d77024; }
.middle-grid { grid-template-columns: minmax(0, 1072px) 230px; gap: 18px; }
.section-title { color: #31383e; font-size: 22px; font-weight: 550; }
.quick-grid { gap: 11px 14px; }
.quick-grid button, .community { box-shadow: 0 3px 12px rgb(31 43 52 / 4%); }
.quick-icon, .quick-icon.tone-1, .quick-icon.tone-2 { background: #edf4f8; color: #4e88a6; }
.quick-icon.tone-1 { background: #fff3e9; color: #c88547; }.quick-icon.tone-2 { background: #eef6ef; color: #69976e; }
.quick-icon svg { width: 24px; height: 24px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; }
.quick-grid b { color: #4c535a; font-weight: 520; }
.community { padding: 8px 12px; }
.activity-row { display: flex; align-items: center; min-height: 39px; gap: 7px; border-bottom: 1px solid #f3f3f3; }
.activity-row:last-child { border-bottom: 0; }
.activity-tag { flex: 0 0 auto; padding: 2px 4px; color: #bd7b44; background: #fff5ec; border-radius: 3px; font-size: 9px; }
.activity-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 1px; }
.activity-copy b { overflow: hidden; color: #565e65; font-size: 10px; font-weight: 520; text-overflow: ellipsis; white-space: nowrap; }
.activity-copy small { overflow: hidden; color: #a6adb3; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.activity-row time { color: #afb5bb; font-size: 9px; }
.recommend nav button { color: #858b90; }
.course-grid { gap: 18px; }
.course-cover { height: 162px; background: #e6ebed; }
.course-cover img { display: block; width: 100%; height: 100%; object-fit: cover; }
.course-cover::after { position: absolute; inset: 45% 0 0; background: linear-gradient(transparent, rgb(2 15 25 / 26%)); content: ''; pointer-events: none; }
.course-cover > span { z-index: 1; top: 9px; left: 9px; background: rgb(22 39 51 / 80%); }
.course-body h3 { color: #3c444b; font-weight: 550; }
.module-gateway { margin-top: 22px; overflow: hidden; background: #fff; border: 1px solid #edf0f2; border-radius: 7px; box-shadow: 0 3px 12px rgb(31 43 52 / 3%); }
.module-gateway-nav { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); padding: 0 8px; }
.module-gateway-nav button { position: relative; min-width: 0; height: 42px; padding: 0 2px; color: #5c646b; background: transparent; border: 0; cursor: pointer; font-size: 11px; font-weight: 510; white-space: nowrap; }
.module-gateway-nav button:hover, .module-gateway-nav button:focus-visible, .module-gateway-nav button.active { color: #dc722a; background: #fff9f4; }
.module-gateway-nav button.active::after { position: absolute; right: 16px; bottom: 0; left: 16px; height: 2px; background: #eb792a; content: ''; }
.module-gateway-summary { display: flex; align-items: center; min-height: 31px; padding: 0 17px; border-top: 1px solid #f1f2f3; gap: 14px; }
.module-gateway-summary b { color: #d77836; font-size: 11px; font-weight: 570; }.module-gateway-summary span { color: #939ba2; font-size: 11px; }
.module-gateway-summary .module-gateway-hint { margin-left: auto; color: #c2814b; }
.tab-content { scroll-margin-top: 24px; }
.hero-copy h1 { white-space: pre-line; }
.dots button { width: 20px; height: 3px; padding: 0; background: rgb(255 255 255 / 52%); border: 0; border-radius: 3px; cursor: pointer; }
.dots button.active { background: #fff; }
.dots button:hover { height: 5px; background: #fff; }
.banner-arrow { position: absolute; z-index: 4; top: 50%; display: grid; width: 38px; height: 38px; place-items: center; padding: 0; color: #fff; cursor: pointer; background: rgb(255 255 255 / 18%); border: 1px solid rgb(255 255 255 / 24%); border-radius: 50%; opacity: .28; transform: translateY(-50%); transition: opacity .18s ease, background .18s ease; }
.banner-arrow--prev { left: 4px; }
.banner-arrow--next { right: 10px; }
.banner-arrow svg { width: 17px; height: 17px; }
.hero-banner:hover .banner-arrow, .banner-arrow:focus-visible { opacity: .9; }
.banner-arrow:hover, .banner-arrow:focus-visible { background: rgb(255 255 255 / 30%); }
.growth-link { align-self: center; margin-top: 8px; padding: 0; color: #d77024; background: transparent; border: 0; cursor: pointer; font-size: 12px; }
.growth-link:hover { color: #f47a20; }
.module-gateway-summary .module-gateway-hint { padding: 0; color: #c2814b; background: transparent; border: 0; cursor: pointer; font-size: 11px; }
.module-gateway-summary .module-gateway-hint:hover { color: #e5792a; }
.activity-row { width: 100%; padding: 0; background: transparent; border: 0; cursor: pointer; text-align: left; }
.activity-row:hover { background: #fff9f5; }.activity-row:hover .activity-copy b { color: #dd772b; }
.quick-grid button:hover { border-color: #f3d8c5; box-shadow: 0 5px 15px rgb(31 43 52 / 7%); transform: translateY(-1px); }
.category-panel button:hover b { color: #e47729; }
.welcome-card > button:hover, .hero-copy button:hover { filter: brightness(.94); }
.course-grid article:focus-visible { outline: 2px solid #e77728; outline-offset: 2px; }
.course-dialog-backdrop { position: fixed; z-index: 50; inset: 0; display: grid; place-items: center; background: rgb(14 25 33 / 45%); }
.course-dialog { position: relative; width: min(440px, calc(100vw - 32px)); padding: 28px; background: #fff; border-radius: 8px; box-shadow: 0 18px 55px rgb(0 0 0 / 18%); }
.course-dialog small { color: #9ca3a8; font-size: 12px; }.course-dialog h2 { margin: 15px 0; color: #323a42; font-size: 23px; font-weight: 560; }.course-dialog p { color: #69727a; font-size: 13px; line-height: 1.8; }
.dialog-close { position: absolute; top: 13px; right: 17px; color: #828b92; background: transparent; border: 0; cursor: pointer; font-size: 23px; }.dialog-cta { margin-top: 10px; padding: 10px 18px; color: #fff; background: #ed7b24; border: 0; border-radius: 20px; cursor: pointer; }
.middle-grid { margin-top: 27px; }
@media (max-width: 1750px) { .hero-grid { grid-template-columns: minmax(220px, 20%) minmax(0, 1fr) minmax(205px, 18%); }.middle-grid { grid-template-columns: minmax(0, 1fr) minmax(205px, 18%); }.side-rail, .ai-helper { display: none; } }
@media (max-width: 1200px) { .hero-grid { grid-template-columns: 250px minmax(0, 1fr); }.welcome-card { display: none; }.middle-grid { grid-template-columns: 1fr; }.community { height: auto; }.course-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1080px) { .module-gateway-nav { display: flex; overflow-x: auto; }.module-gateway-nav button { flex: 0 0 124px; } }
@media (max-width: 820px) { .hero-grid { grid-template-columns: 1fr; height: auto; }.category-panel { display: none; }.hero-banner { height: 330px; }.hero-art { opacity: .55; }.quick-grid { grid-template-columns: 1fr 1fr; }.course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
