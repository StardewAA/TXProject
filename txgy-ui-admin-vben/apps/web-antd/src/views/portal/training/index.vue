<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useUserStore } from '@vben/stores';
import { TRAINING_CATALOG } from './course-catalog';
import { getMyCourses } from './learning/api';

const modules = ['学习工作台', '课程中心', '我的学习', 'VR 实操训练', '考试与证书', '直播大厅', '培训任务与上岗', '个人档案与成长', '校企合作专区', '创新大赛专区'];
const catalog = TRAINING_CATALOG;
const levels = ['学徒', '初级工', '中级工', '高级工', '技师', '高级技师'];
const active = ref(0), category = ref('全部'), group = ref('全部'), keyword = ref('');
const selected = ref<string[]>([]), level = ref(0), dialog = ref(false), message = ref('');
const myCourses = ref<any[]>([]);
const hoverOpen = ref(false);
const hoverCategory = ref<any>(null);
const userStore = useUserStore();
const storageKey = computed(() => `training-direction-${userStore.userInfo?.id ?? 'visitor'}`);
const displayCatalog = computed(() => catalog);
const courses = computed(() => {
  const source = displayCatalog.value.flatMap((c) => c.groups.flatMap((g) => g.courses.map((course) => ({ code: course.code, category: c.code, group: g.name, name: course.name, credit: course.stdHours }))));
  return source.filter((x) => (category.value === '全部' || x.category === category.value || x.category === String(category.value)) && (group.value === '全部' || x.group === group.value) && (!keyword.value || `${x.name}${x.group}`.includes(keyword.value)));
});
function toggle(code: string) { selected.value = selected.value.includes(code) ? selected.value.filter((x) => x !== code) : [...selected.value, code]; }
function save() { if (!selected.value.length) { message.value = '请至少选择一个课程大类'; return; } localStorage.setItem(storageKey.value, JSON.stringify({ selected: selected.value, level: level.value })); dialog.value = false; message.value = '学习方向已保存'; }
async function loadCenter() { hoverCategory.value = displayCatalog.value[0]; const mine = await Promise.allSettled([getMyCourses()]); if (mine[0]?.status === 'fulfilled') myCourses.value = [...(mine[0].value?.learningCourses ?? []), ...(mine[0].value?.todoCourses ?? [])]; }
onMounted(() => { const saved = localStorage.getItem(storageKey.value); if (!saved) dialog.value = true; loadCenter(); });
</script>

<template>
  <div class="page">
    <aside><b>云培训学院</b><button v-for="(name,index) in modules" :key="name" :class="{active:active===index}" @mouseenter="index===1&&(hoverOpen=true)" @click="active=index"><small>F{{index+1}}</small>{{name}}</button></aside>
    <div v-if="hoverOpen" class="category-popup" @mouseenter="hoverOpen=true" @mouseleave="hoverOpen=false"><div class="popup-primary"><button v-for="item in displayCatalog" :key="item.code" :class="{chosen:hoverCategory?.code===item.code}" @mouseenter="hoverCategory=item">{{item.code}} {{item.name}} <span>›</span></button></div><div class="popup-secondary"><h3>{{hoverCategory?.name}}</h3><button v-for="item in hoverCategory?.groups??[]" :key="item.code" @click="active=1;category=hoverCategory.code;group=item.name;hoverOpen=false">{{item.code}} {{item.name}}</button></div></div>
    <main>
      <header><div><small>LEARNING CENTER</small><h1>{{ modules[active] }}</h1></div><button @click="dialog = true">调整学习方向</button></header>
      <nav class="topic-strip" aria-label="课程导览">
        <span>热门方向</span>
        <button v-for="item in displayCatalog.slice(0, 5)" :key="item.code" @click="active = 1; category = item.code; group = '全部'">{{ item.code }} {{ item.name }}</button>
      </nav>
      <p v-if="message" class="message">{{ message }}</p>
      <template v-if="active === 0">
        <section><h2>继续学习</h2><div class="row"><em>B0301</em><strong>高压无气喷涂操作</strong><span>已完成 45%</span><button @click="message = '已进入课程：高压无气喷涂操作'">继续</button></div><div class="row"><em>A0301</em><strong>受限空间作业（储罐/塔罐内部）</strong><span>待完成</span><button @click="message = '已加入学习：受限空间作业'">学习</button></div></section>
        <section><h2>成长进度</h2><p>当前成长等级：<strong>{{ levels[level] }}</strong></p><ol><li v-for="(name, index) in levels" :key="name" :class="{ now: index === level }">{{ index + 1 }} · {{ name }}</li></ol></section>
      </template>
      <template v-else-if="active === 1">
        <section><div class="filters"><label>课程大类</label><button :class="{ chosen: category === '全部' }" @click="category = '全部'; group = '全部'">全部</button><button v-for="item in displayCatalog" :key="item.code" :class="{ chosen: category === item.code }" @click="category = item.code; group = '全部'">{{ item.code }} {{ item.name }}</button></div><div v-if="category !== '全部'" class="filters"><label>中类</label><button :class="{ chosen: group === '全部' }" @click="group = '全部'">全部</button><button v-for="item in displayCatalog.find((x) => x.code === category)?.groups ?? []" :key="item.code" :class="{ chosen: group === item.name }" @click="group = item.name">{{ item.code }} {{ item.name }}</button></div><input v-model="keyword" placeholder="搜索课程或中类" /></section>
        <div class="course-grid"><article v-for="item in courses" :key="item.code" class="course-card"><div class="course-cover"><img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name"><span v-else>{{ item.code }}</span></div><div class="course-body"><h3>{{ item.name }}</h3><p>{{ item.code }} · {{ item.group || item.category }}</p><small v-if="item.lecturer || item.credit">{{ item.lecturer || '课程讲师待配置' }}<template v-if="item.credit"> · {{ item.credit }} 学时</template></small><footer><span>可自主学习</span><button @click="message = '已加入学习：' + item.name">开始学习</button></footer></div></article><p v-if="!courses.length">没有匹配的课程。</p></div>
      </template>
      <section v-else><p class="blue">{{ modules[active] }}</p><strong>内容将按照你的课程方向和成长等级展示。</strong><p><button @click="active = 1">浏览课程</button></p></section>
    </main>
    <div v-if="dialog" class="mask"><section class="dialog"><small>WELCOME TO LEARNING</small><h2>想先学习什么？</h2><p>请按“一级大类 → 二级中类 → 具体小类”的课程目录选择学习方向。</p><div class="options"><button v-for="item in catalog" :key="item.code" :class="{ chosen: selected.includes(item.code) }" @click="toggle(item.code)">{{ item.code }} {{ item.name }}</button></div><label>当前成长等级<select v-model="level"><option v-for="(name, index) in levels" :key="name" :value="index">{{ index + 1 }} · {{ name }}</option></select></label><p v-if="message" class="message">{{ message }}</p><footer><button @click="dialog = false">稍后选择</button><button class="primary" @click="save">进入学习中心</button></footer></section></div>
  </div>
</template>

<style scoped>
.page{display:grid;grid-template-columns:210px 1fr;min-height:calc(100vh - 72px);background:#f7f8fa;color:#172033}aside{padding:28px 14px;background:#1f2430;color:#ced5e1}aside b{display:block;margin:0 12px 20px;color:#fff;font-size:18px}aside button{display:flex;gap:10px;width:100%;padding:11px 12px;border:0;border-left:2px solid transparent;background:transparent;color:inherit;text-align:left;cursor:pointer}aside button.active,aside button:hover{border-left-color:#2f6bff;background:#2b3240;color:#fff}aside small{width:24px;color:#91a2c4}main{max-width:1180px;width:100%;margin:auto;padding:36px 44px 64px}header{display:flex;justify-content:space-between;align-items:end;padding-bottom:22px;border-bottom:1px solid #dfe3eb}small{color:#2f6bff;font-size:12px;font-weight:700;letter-spacing:.08em}h1,h2{margin:0}h1{font-size:28px}h2{font-size:18px}header button,section button,.row button,footer button{padding:8px 13px;border:1px solid #cdd6e6;background:#fff;color:#245fec;cursor:pointer}.message,.blue{color:#245fec}section{padding:24px 0;border-bottom:1px solid #dfe3eb}.row{display:grid;grid-template-columns:84px 1fr 120px auto;gap:16px;align-items:center;padding:16px 0;border-bottom:1px solid #eaedf2}.row em{color:#74819a;font-family:monospace;font-size:12px;font-style:normal}.row span,.row small{color:#7d899f;font-size:13px}.row small{display:block;margin-top:5px}ol{display:flex;list-style:none;padding:0;border-top:2px solid #ccd6eb}li{flex:1;padding-top:10px;color:#7d899f;font-size:13px}li.now{color:#245fec;font-weight:700}.filters,.options{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:14px 0}.filters label{width:64px;color:#67748b;font-size:13px}.filters button,.options button{padding:6px 10px;border:1px solid #d9e0ec;background:#fff;color:#43516a;cursor:pointer}.filters button.chosen,.options button.chosen{border-color:#2f6bff;background:#eef4ff;color:#245fec}input,select{padding:9px;border:1px solid #d9e0ec;background:#fff}.mask{position:fixed;z-index:1000;inset:0;display:grid;place-items:center;padding:24px;background:rgb(15 23 42 / 42%)}.dialog{width:min(620px,100%);padding:30px;background:#fff;box-shadow:0 18px 48px rgb(15 23 42 / 24%)}.dialog>p{color:#67748b}.dialog label{display:grid;gap:8px;color:#43516a}.dialog select{margin:0}footer{display:flex;justify-content:end;gap:10px;margin-top:24px}.primary{border-color:#2f6bff!important;background:#2f6bff!important;color:#fff!important}@media(max-width:900px){.page{grid-template-columns:1fr}aside{display:flex;overflow-x:auto;padding:10px}aside b{display:none}aside button{width:auto;white-space:nowrap}main{padding:28px 20px}.row{grid-template-columns:72px 1fr auto}.row span{display:none}}
.page {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 8%, rgb(53 112 255 / 12%), transparent 29rem),
    radial-gradient(circle at 42% 92%, rgb(102 212 185 / 10%), transparent 28rem),
    repeating-linear-gradient(90deg, transparent 0, transparent 79px, rgb(38 78 148 / 3%) 80px);
}
.page::before {
  position: absolute;
  inset: 0 0 auto 210px;
  height: 168px;
  pointer-events: none;
  content: '';
  background: linear-gradient(115deg, rgb(239 245 255 / 90%), rgb(247 250 255 / 45%));
  border-bottom: 1px solid rgb(210 222 242 / 60%);
}
aside { position: relative; z-index: 1; background: linear-gradient(180deg, #202633 0%, #18202f 100%); }
aside::after { display: block; width: 42px; height: 2px; margin: 22px 12px 0; content: ''; background: #3a7aff; }
main { position: relative; z-index: 1; }
header { padding: 22px 22px 24px; margin: -8px -22px 0; border-bottom-color: rgb(196 213 240 / 80%); }
header::after { width: 86px; height: 3px; position: absolute; margin: 60px 0 0; content: ''; background: linear-gradient(90deg, #2f6bff, #6ca5ff); }
section { position: relative; }
section h2 { display: flex; align-items: center; gap: 10px; }
section h2::before { width: 4px; height: 18px; content: ''; background: #2f6bff; }
.row { transition: transform .18s ease, background-color .18s ease; }
.row:hover { background: rgb(255 255 255 / 58%); transform: translateX(4px); }
.row button, header button { box-shadow: 0 4px 12px rgb(47 107 255 / 8%); }
ol { position: relative; }
ol::before { position: absolute; top: -2px; left: 0; width: 16.666%; height: 2px; content: ''; background: #2f6bff; }
.message { display: inline-block; padding: 6px 10px; margin: 16px 0 0; background: rgb(234 242 255 / 74%); }
.topic-strip { display: flex; flex-wrap: wrap; gap: 8px 18px; align-items: center; padding: 18px 0 10px; color: #66738a; border-bottom: 1px dashed rgb(173 193 227 / 70%); }
.course-hover{position:relative}.course-flyout{position:absolute;z-index:20;top:0;left:100%;display:grid;grid-template-columns:210px 250px;min-height:280px;background:#fff;color:#24324a;box-shadow:0 14px 34px rgb(20 42 82 / 22%);border:1px solid #dce6f6}.flyout-primary,.flyout-secondary{padding:12px}.flyout-primary{border-right:1px solid #e5ebf5}.course-flyout button{display:block;width:100%;padding:9px 10px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer}.course-flyout button:hover,.course-flyout button.chosen{background:#edf4ff;color:#2464ed}.flyout-secondary b{display:block;padding:9px 10px;color:#2464ed}.course-hover:hover>button{border-left-color:#2f6bff;background:#2b3240;color:#fff}
.category-popup{position:absolute;z-index:90;top:184px;left:calc(50% - 710px);display:grid;width:660px;max-height:360px;grid-template-columns:195px minmax(0,1fr);overflow:auto;border:1px solid #cfdced;background:#fff;box-shadow:0 18px 42px rgb(20 42 82 / 28%)}.popup-primary{padding:10px;background:#f3f7fd;border-right:1px solid #dce5f1}.popup-primary button,.popup-secondary button{display:flex;width:100%;min-height:40px;justify-content:space-between;align-items:center;padding:8px 12px;border:0;background:transparent;color:#34435b;text-align:left;cursor:pointer}.popup-primary button.chosen,.popup-primary button:hover{background:#dfeeff;color:#2464ed;font-weight:600}.popup-secondary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:4px;padding:16px 18px}.popup-secondary h3{grid-column:1/-1;margin:0 0 8px;padding-bottom:10px;border-bottom:1px solid #e7edf5;font-size:16px}.popup-secondary button:hover{background:#f2f7ff;color:#2464ed}.course-card{min-height:184px}.course-cover{height:72px;justify-items:start;padding:12px;background:linear-gradient(135deg,#edf4ff,#f7fbff);font-size:12px}.course-cover:has(img){height:96px;padding:0}.course-body{padding:12px}.course-body h3{min-height:40px}.course-grid{gap:14px;padding:14px 0}.course-body footer{padding-top:10px}
.page::before{display:none}.academy-content{max-width:none}.course-flyout{width:680px;grid-template-columns:200px minmax(0,1fr);min-height:340px;top:0;left:100%;background:#fff}.flyout-primary{padding:14px 10px}.flyout-secondary{padding:18px 20px}.flyout-secondary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:8px}.flyout-secondary b{grid-column:1/-1;padding:0 0 10px;border-bottom:1px solid #e7edf7}.course-flyout button{min-height:38px;border-radius:4px}.flyout-primary button::after{float:right;content:'›';font-size:18px}.course-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px;padding:20px 0}.course-card{display:flex;flex-direction:column;min-width:0;min-height:292px;overflow:hidden;border:1px solid #dbe5f4;border-radius:10px;background:#fff;transition:transform .18s,box-shadow .18s}.course-card:hover{transform:translateY(-3px);box-shadow:0 12px 24px rgb(31 72 135 / 14%)}.course-cover{display:grid;height:104px;place-items:center;background:linear-gradient(135deg,#eaf2ff,#d9e9ff);color:#2f6bff;font-family:ui-monospace,monospace;font-weight:700}.course-cover img{width:100%;height:100%;object-fit:cover}.course-body{display:flex;flex:1;flex-direction:column;padding:14px}.course-body h3{display:-webkit-box;min-height:44px;margin:0;color:#182742;font-size:15px;line-height:22px;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.course-body p,.course-body small{margin:8px 0 0;color:#78869c;font-size:12px}.course-body footer{display:flex;justify-content:space-between;align-items:center;margin-top:auto;padding-top:14px}.course-body footer span{color:#4d83ed;font-size:12px}.course-body footer button{padding:6px 9px;border:0;border-radius:4px;background:#edf4ff;color:#2865ec}.academy-content,main{max-width:none;width:auto;margin:0;padding:24px 32px 56px}.academy-header,header{margin:0;padding:0 0 16px}.topic-strip{padding:12px 0}.compact-section,section{padding:16px 0}@media(max-width:1450px){.course-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:900px){.course-flyout{left:0;top:100%;width:min(680px,calc(100vw - 24px))}.course-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
.topic-strip span { color: #172033; font-weight: 700; }
.topic-strip button { padding: 0; border: 0; background: transparent; color: #56657d; cursor: pointer; }
.topic-strip button:hover { color: #2f6bff; }
@media(max-width:900px){.page::before{inset:0;height:120px}.page::after{display:none}header{margin:0;padding:10px 0 22px}}
</style>
