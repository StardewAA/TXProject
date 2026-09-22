<script lang="ts" setup>
import { computed, nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { CERT_FIELDS } from './cert-data';
import {
  LEVEL_INTRO,
  LEVEL_PREREQ,
  ROADMAP_GROUPS,
  ROADMAP_LEVELS,
} from './roadmap-data';

defineOptions({ name: 'PortalTrainingExamCertDetail' });

const route = useRoute();
const router = useRouter();

const fieldMeta = computed(
  () => CERT_FIELDS.find((f) => f.key === route.query.field) ?? CERT_FIELDS[0]!,
);

const group = computed(
  () =>
    ROADMAP_GROUPS.find((g) => g.key === fieldMeta.value.key) ??
    ROADMAP_GROUPS[0]!,
);

const job = computed(
  () =>
    group.value.jobs.find((j) => j.name === route.query.job) ??
    group.value.jobs[0]!,
);

const jobIndex = computed(() =>
  group.value.jobs.findIndex((j) => j.name === job.value.name),
);

/** 该工种可认证的等级（初级工 → 最高等级） */
const certLevels = computed(() =>
  ROADMAP_LEVELS.slice(0, ROADMAP_LEVELS.indexOf(job.value.top as any) + 1),
);

const LEVEL_TAGS: Record<string, string> = {
  初级工: '五级/初级工',
  中级工: '四级/中级工',
  高级工: '三级/高级工',
  技师: '二级/技师',
  高级技师: '一级/高级技师',
};

const examCode = computed(
  () =>
    `ZX-${fieldMeta.value.key.toUpperCase()}-${String(jobIndex.value + 1).padStart(2, '0')}`,
);

const overview = computed(
  () =>
    `${job.value.name}认证面向${fieldMeta.value.name}岗位序列，重点考查${job.value.skill}等核心能力。` +
    `认证沿初级工至${job.value.top}逐级进阶，每一级均配套体系化培训课程与认证考试，` +
    `通过认证即证明持证人具备对应等级岗位的规范作业与技术管理能力，可作为上岗、评优与职业晋升的重要依据。`,
);

/** 各等级考试信息 */
const examRows = computed(() =>
  certLevels.value.map((lv, i) => ({
    level: lv,
    tag: LEVEL_TAGS[lv]!,
    code: `${examCode.value}-${i + 1}`,
    name: `${job.value.name}${lv}认证考试`,
    prereq: LEVEL_PREREQ[lv]!,
    duration: i >= 3 ? '90 分钟 + 实操 120 分钟' : '60 分钟 + 实操 90 分钟',
    count: i >= 3 ? '80 题 + 实操 2 项' : '60 题 + 实操 1 项',
    pass: i >= 3 ? '70 分 / 实操 80 分' : '60 分 / 实操 70 分',
    fee: i >= 3 ? '500 元' : i >= 2 ? '300 元' : '200 元',
    lang: '中文',
  })),
);

/** 各等级培训与教材 */
const trainRows = computed(() =>
  certLevels.value.map((lv, i) => ({
    level: lv,
    course: `${job.value.name}${lv}培训课程`,
    hours: `${40 + i * 20} 学时`,
    type: i >= 3 ? '线上 + 线下 + 案例研讨' : '线上 + 线下',
    intro: LEVEL_INTRO[lv]!(job.value.skill),
  })),
);

/** 相关认证（同序列其他工种） */
const relatedJobs = computed(() =>
  group.value.jobs.filter((j) => j.name !== job.value.name).slice(0, 8),
);

const goBack = () => router.push('/portal/training/exam');
const goFaq = () => {
  activeStep.value = 0;
  activeFaq.value = 3;
  nextTick(() => {
    document.getElementById('sec-faq')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
};

const goBooking = () =>
  router.push({
    path: '/portal/training/exam/booking',
    query: { field: fieldMeta.value.key, job: job.value.name },
  });

/** 步骤 1：能力与知识 */
const ABILITY = computed(
  () =>
    `掌握${job.value.skill}等核心作业能力，能独立完成${job.value.name}岗位的规范作业、质量自检与安全文明施工，胜任对应等级岗位的技术与管理工作。`,
);

const KNOWLEDGE = computed(
  () =>
    `${job.value.skill}相关的国家标准与行业规范，常用设备与工具的原理、操作及维护方法，作业安全与职业健康知识，质量验收与环境保护要求。`,
);

const AUDIENCE = computed(
  () => `${fieldMeta.value.name}岗位从业人员、新入职员工及希望提升技能等级的人员`,
);

/** 步骤 2：培训（按路径页传入的等级预选） */
const activeTrain = ref(
  Math.max(
    0,
    certLevels.value.findIndex((l) => l === route.query.level),
  ),
);

const TRAIN_GOALS = computed(() => [
  `了解${job.value.name}岗位职责范围与各等级能力要求；`,
  `掌握${job.value.skill}等关键技能的标准作业流程；`,
  `熟悉安全生产、环境保护与质量验收相关规范；`,
  `通过实操训练达到对应等级认证考核要求。`,
]);

const TRAIN_ENTRY = [
  '具备基本的文化知识基础',
  '身体健康，能适应现场作业环境',
];

/** 步骤 2：培训内容 */
const TRAIN_CONTENT = computed(() => {
  const [s1, s2] = job.value.skill.split(/[、，,]/);
  return [
    `模块一：${job.value.name}岗位职责与等级能力标准解读（4 学时）`,
    `模块二：${s1 ?? '关键技能'}基础理论与标准作业流程（12 学时）`,
    `模块三：${s2 ?? s1 ?? '关键技能'}实操训练与工艺要点（16 学时）`,
    `模块四：安全生产、环境保护与质量验收规范（8 学时）`,
    `模块五：综合案例演练与考核要点串讲（4 学时）`,
  ];
});

/** 步骤 5：重认证表格 */
const recertRows = computed(() =>
  certLevels.value.map((lv) => ({
    item: `${job.value.name}${LEVEL_TAGS[lv]!}认证`,
    valid: '3 年',
    revalid: '3 年',
    rule: RECERT_RULES.join(' '),
    beyond: '与初认证相同',
  })),
);

function goJob(name: string) {
  router.push({
    path: '/portal/training/exam/cert-detail',
    query: { field: fieldMeta.value.key, job: name },
  });
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/** 认证流程 */
const CERT_FLOW = [
  {
    title: '了解认证',
    text: `查看${job.value.name}认证的等级体系、培训与考核内容、考试信息及证书权益，全面了解认证价值。`,
  },
  {
    title: '认证学习',
    text: '学习配套的体系化课程与实操训练，研读认证考试大纲与培训手册，让备考更高效。',
  },
  {
    title: '预约考试',
    text: '在考试中心选择对应等级的认证考试，完成预约后按时参加理论与实操考核。',
  },
  {
    title: '领取证书',
    text: '通过考试后一个工作日即可下载电子证书，证书编号支持在线验真，并纳入个人成长档案。',
  },
  {
    title: '重认证',
    text: '证书有效期 3 年，到期前完成一次重认证考试或相应继续教育课程即可延续证书。',
  },
];

const activeStep = ref(0);

/** 考试大纲（放在「了解认证」） */
const SYLLABUS = computed(() => {
  const [s1, s2] = job.value.skill.split(/[、，,]/);
  return {
    name: `${job.value.name} V2.0`,
    desc: `${job.value.name} V2.0 考试涵盖${job.value.skill}等核心知识与实操技能，具体包括岗位认知与职业素养、关键技能基础理论与规范作业、安全生产与质量控制以及实操综合应用等内容。`,
    points: [
      { name: '1. 岗位认知与职业素养', pct: '10%' },
      { name: `2. ${s1 ?? '关键技能'}基础理论`, pct: '25%' },
      { name: `3. ${s2 ?? '规范作业流程'}`, pct: '25%' },
      { name: '4. 安全生产与质量控制', pct: '15%' },
      { name: '5. 实操综合应用', pct: '25%' },
    ],
    file: '/docs/cert-placeholder.pdf',
  };
});

/** 学习资源（放在「认证学习」） */
const LEARN_RES = computed(() => [
  {
    label: '培训教材',
    links: [
      { text: `${job.value.name} V2.0 培训教材`, file: '/docs/cert-placeholder.pdf' },
      { text: `${job.value.name} V2.0 学员用书`, file: '/docs/cert-placeholder.pdf' },
    ],
  },
  {
    label: '版本说明',
    links: [{ text: `${job.value.name} V2.0 版本说明`, file: '/docs/cert-placeholder.pdf' }],
  },
  {
    label: '实验手册',
    links: [
      { text: `${job.value.name} V2.0 实验手册（现场版）`, file: '/docs/cert-placeholder.pdf' },
      { text: `${job.value.name} V2.0 实验手册（云端版）`, file: '/docs/cert-placeholder.pdf' },
    ],
  },
  {
    label: '软件包',
    links: [
      { text: '软件包part1', file: '/docs/cert-placeholder.pdf' },
      { text: '软件包part2', file: '/docs/cert-placeholder.pdf' },
    ],
    note: '（请下载两个软件包后同时解压）',
  },
  {
    label: '模拟考试',
    links: [{ text: `${job.value.name} V2.0 模拟考试`, file: '/docs/cert-placeholder.pdf' }],
  },
]);

/** 认证权益 */
const BENEFITS = [
  { title: '上岗资格', text: '证书作为岗位上岗与作业授权的重要凭证，纳入个人技能档案。' },
  { title: '技能津贴', text: '与技能等级津贴、绩效评优挂钩，等级越高激励越大。' },
  { title: '项目优先', text: '持证人员可优先参与重点工程项目与技术攻关任务。' },
  { title: '在线验真', text: '证书编号支持在线验真，企业可一键核验持证人资质。' },
];

/** 重认证规则 */
const RECERT_RULES = [
  '证书有效期为 3 年，自考试通过之日起计算；',
  '证书到期前 6 个月内可申请重认证，逾期需重新参加全等级考试；',
  '完成对应等级的继续教育课程并通过考核，即可完成重认证；',
  '重认证通过后证书有效期自原到期日起顺延 3 年。',
];

/** 常见问题 */
const FAQ = [
  {
    q: '可以直接报考高级别吗？',
    a: '本工种认证采取逐级递进方式，报考上一级需先取得下一级认证（初级工需先完成基础课程学习）。',
  },
  {
    q: '考试未通过可以补考吗？',
    a: '可以。未通过可在 15 日后预约补考，理论科目提供 1 次免费补考机会，实操科目按标准收费。',
  },
  {
    q: '证书如何验真？',
    a: '证书右下角标注唯一证书编号，在认证平台输入编号即可在线验真，亦支持扫描证书二维码核验。',
  },
  {
    q: '考试形式是怎样的？',
    a: '理论科目在在线考试系统作答，实操科目在授权考点现场完成作业考评，两科均合格方视为通过。',
  },
];
const activeFaq = ref(-1);
</script>

<template>
  <div class="cert-detail">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="goBack">首页</span>
          <em>›</em>
          <span @click="goBack">认证</span>
          <em>›</em>
          <span class="on">{{ job.name }}</span>
        </nav>

        <div class="hero-top">
          <div class="hero-main">
            <h1>{{ job.name }}</h1>
            <p class="hero-sub">
              {{ fieldMeta.name }}岗位序列认证 · 认证代码 {{ examCode }} · 版本 V2.0
            </p>
            <p class="hero-desc">
              培养与认证具备{{ job.skill }}等核心作业能力的{{ job.name }}技能人才
            </p>
          </div>
          <div class="hero-side">
            <a class="hero-back" @click="goBack">返回职业认证 »</a>
          </div>
        </div>

        <div class="hero-flow">
          <h2>认证流程</h2>
        </div>
      </div>
    </header>

    <div class="flow-sticky">
      <div class="flow-sticky-inner">
        <div class="flow-steps">
          <div
            v-for="(s, i) in CERT_FLOW"
            :key="s.title"
            :class="['flow-step', { on: activeStep === i }]"
            @click="activeStep = i"
          >
            <em>{{ i + 1 }}</em>
            <span>{{ s.title }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="content">
      <!-- 步骤 1：了解认证 -->
      <section v-show="activeStep === 0" class="panel">
        <div class="ability-grid">
          <div class="ability-card">
            <h3>通过认证验证的能力</h3>
            <span class="ability-icon icon-tool"></span>
            <p>{{ ABILITY }}</p>
          </div>
          <div class="ability-card">
            <h3>建议掌握的知识</h3>
            <span class="ability-icon icon-book"></span>
            <p>{{ KNOWLEDGE }}</p>
          </div>
        </div>
        <div class="kv">
          <h3>面向对象</h3>
          <p>希望成为能够胜任{{ job.name }}岗位的人员</p>
          <h3>认证前提</h3>
          <p>无（可直接报考初级工）</p>
          <h3>认证概述</h3>
          <p>{{ overview }}</p>
        </div>

        <div class="syllabus">
          <h3>考试大纲</h3>
          <span class="icon-doc"></span>
          <h4>{{ SYLLABUS.name }}</h4>
          <h5>考试内容</h5>
          <p>{{ SYLLABUS.desc }}</p>
          <h5>知识点</h5>
          <table class="syllabus-table">
            <tbody>
              <tr v-for="p in SYLLABUS.points" :key="p.name">
                <td>{{ p.name }}</td>
                <td class="pct">{{ p.pct }}</td>
              </tr>
            </tbody>
          </table>
          <a class="doc-link" :href="SYLLABUS.file" target="_blank" rel="noreferrer">
            下载：{{ SYLLABUS.name }} 考试大纲.pdf
          </a>
        </div>
      </section>

      <!-- 步骤 2：认证学习 -->
      <section v-show="activeStep === 1" class="panel">
        <div class="train-tabs">
          <a
            v-for="(t, i) in trainRows"
            :key="t.course"
            :class="{ on: activeTrain === i }"
            @click="activeTrain = i"
          >
            {{ t.course }}
          </a>
        </div>
        <div class="train-meta">
          <span>培训学时：{{ trainRows[activeTrain]!.hours }}</span>
          <span>授课方式：{{ trainRows[activeTrain]!.type }}</span>
          <span>课程级别：{{ trainRows[activeTrain]!.level }}</span>
          <span>更新时间：2026-05-16</span>
        </div>
        <h3>培训目标</h3>
        <p class="lead">完成本项目培训后，您将能够：</p>
        <ul class="dot-list">
          <li v-for="g in TRAIN_GOALS" :key="g">{{ g }}</li>
        </ul>
        <h3>培训对象</h3>
        <p>{{ AUDIENCE }}</p>
        <h3>入学要求</h3>
        <ul class="dot-list">
          <li v-for="e in TRAIN_ENTRY" :key="e">{{ e }}</li>
        </ul>
        <h3>培训内容</h3>
        <ul class="dot-list">
          <li v-for="c in TRAIN_CONTENT" :key="c">{{ c }}</li>
        </ul>

        <div class="learn-res">
          <h3>学习资源</h3>
          <div v-for="g in LEARN_RES" :key="g.label" class="learn-row">
            <b>{{ g.label }}</b>
            <a
              v-for="l in g.links"
              :key="l.text"
              class="doc-link"
              :href="l.file"
              target="_blank"
              rel="noreferrer"
            >
              {{ l.text }}
            </a>
            <span v-if="g.note" class="res-note">{{ g.note }}</span>
          </div>
        </div>
      </section>

      <section v-show="activeStep === 0" id="sec-faq" class="section">
        <h2 class="section-title">常见问题</h2>
        <div class="faq">
          <div
            v-for="(f, i) in FAQ"
            :key="f.q"
            :class="['faq-item', { on: activeFaq === i }]"
            @click="activeFaq = activeFaq === i ? -1 : i"
          >
            <div class="faq-q">
              <span class="faq-mark">Q</span>
              <b>{{ f.q }}</b>
              <em>{{ activeFaq === i ? '−' : '+' }}</em>
            </div>
            <p v-show="activeFaq === i" class="faq-a">{{ f.a }}</p>
          </div>
        </div>
      </section>

      <section v-show="activeStep === 0" id="sec-related" class="section">
        <h2 class="section-title">相关认证</h2>
        <div class="related-grid">
          <div
            v-for="r in relatedJobs"
            :key="r.name"
            class="related-card"
            @click="goJob(r.name)"
          >
            <b>{{ r.name }}</b>
            <span>{{ r.skill }}</span>
            <span class="related-top">最高等级：{{ r.top }}</span>
          </div>
        </div>
      </section>

      <!-- 步骤 3：预约考试 -->
      <section v-show="activeStep === 2" class="panel">
        <h3>获得此认证，您需通过以下考试</h3>
        <table class="grid">
          <thead>
            <tr>
              <th>认证等级</th>
              <th>考试代码</th>
              <th>考试名称</th>
              <th>认证前提</th>
              <th>考试时长</th>
              <th>试题数量</th>
              <th>通过分数</th>
              <th>考试费用</th>
              <th>考试语言</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in examRows" :key="r.code">
              <td>{{ r.level }}</td>
              <td>{{ r.code }}</td>
              <td>{{ r.name }}</td>
              <td>{{ r.prereq }}</td>
              <td>{{ r.duration }}</td>
              <td>{{ r.count }}</td>
              <td>{{ r.pass }}</td>
              <td>{{ r.fee }}</td>
              <td>{{ r.lang }}</td>
            </tr>
          </tbody>
        </table>
        <div class="panel-foot center">
          <Button type="primary" @click="goBooking">预约考试</Button>
          <Button @click="goFaq">考试流程与规则</Button>
        </div>
      </section>

      <!-- 步骤 4：领取证书 -->
      <section v-show="activeStep === 3" class="panel">
        <h3>领取证书</h3>
        <div class="cert-badge">
          <b>{{ job.name.slice(0, 2) }}</b>
          <span>{{ LEVEL_TAGS[certLevels[certLevels.length - 1]!] }}</span>
        </div>
        <p class="text">
          考试成绩合格后 1 个工作日，即可在平台下载电子证书；证书标注唯一编号，支持在线验真，
          并自动纳入个人技能成长档案，可作为上岗、评优与职业晋升的重要依据。
        </p>
        <div class="panel-foot center">
          <a class="dl-btn" href="/docs/cert-placeholder.pdf" target="_blank" rel="noreferrer">
            证书下载
          </a>
        </div>
        <h3>认证权益</h3>
        <ul class="benefit-list">
          <li v-for="b in BENEFITS" :key="b.title">
            <b>{{ b.title }}</b>
            <span>{{ b.text }}</span>
          </li>
        </ul>
      </section>

      <!-- 步骤 5：重认证 -->
      <section v-show="activeStep === 4" class="panel">
        <h3>重认证</h3>
        <p class="text">
          随着岗位技术与行业规范的发展，认证内容将不定期更新与优化。通过重认证，可以保证证书的有效性，
          并表明持证人的技能水平与行业最新发展趋势保持一致。
        </p>
        <table class="grid recert">
          <thead>
            <tr>
              <th>认证项目</th>
              <th>证书有效期</th>
              <th>重认证有效期</th>
              <th>重认证规则</th>
              <th>超出重认证有效期</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recertRows" :key="r.item">
              <td>{{ r.item }}</td>
              <td>{{ r.valid }}</td>
              <td>{{ r.revalid }}</td>
              <td class="left">{{ r.rule }}</td>
              <td>{{ r.beyond }}</td>
            </tr>
          </tbody>
        </table>
        <a class="doc-link" href="/docs/cert-placeholder.pdf" target="_blank" rel="noreferrer">
          查看《重认证规则说明》»
        </a>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.cert-detail {
  max-width: 1200px;
  padding-bottom: 48px;
}

/* ---------- 深色 banner 头部（含认证流程） ---------- */
.hero {
  position: relative;
  width: 100vw;
  margin-left: calc(50% - 50vw);
  background:
    radial-gradient(900px 260px at 85% -20%, rgb(96 148 224 / 28%), transparent 62%),
    radial-gradient(700px 220px at 20% 110%, rgb(70 120 200 / 18%), transparent 60%),
    repeating-linear-gradient(
      112deg,
      rgb(150 190 255 / 7%) 0 1px,
      transparent 1px 130px
    ),
    linear-gradient(105deg, #0a1729 0%, #12253f 50%, #0a1729 100%);
}

.hero-inner {
  max-width: 1200px;
  padding: 30px 0 14px;
  margin: 0 auto;
}

.crumb {
  margin-bottom: 22px;
  color: rgb(255 255 255 / 55%);
  font-size: 13px;

  span {
    cursor: pointer;

    &:hover {
      color: #fff;
    }

    &.on {
      color: rgb(255 255 255 / 85%);
      cursor: default;
    }
  }

  em {
    margin: 0 8px;
    font-style: normal;
  }
}

.hero-top {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
}

.hero-main {
  min-width: 0;

  h1 {
    margin: 0 0 10px;
    color: #fff;
    font-size: 30px;
    font-weight: 600;
  }
}

.hero-sub {
  margin: 0 0 6px;
  color: rgb(255 255 255 / 75%);
  font-size: 14px;
}

.hero-desc {
  margin: 0;
  color: rgb(255 255 255 / 55%);
  font-size: 13px;
}

.hero-side {
  flex-shrink: 0;
  padding-top: 14px;
}

.hero-back {
  color: rgb(255 255 255 / 85%);
  font-size: 14px;
  cursor: pointer;

  &:hover {
    color: #fff;
    text-decoration: underline;
  }
}

.hero-flow {
  padding-top: 20px;

  h2 {
    margin: 0 0 12px;
    color: #fff;
    font-size: 19px;
    font-weight: 500;
    letter-spacing: 1px;
  }
}

/* ---------- 章节 ---------- */
.content {
  padding-top: 8px;
}

.section {
  padding: 28px 0;
  border-bottom: 1px solid #f5f5f5;
  scroll-margin-top: 100px;

  &:last-child {
    border-bottom: none;
  }
}

.section-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.text {
  margin: 0;
  color: #595959;
  font-size: 14px;
  line-height: 1.9;
}

.link {
  color: #1257c9;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.section-foot {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 16px;

  .tip {
    color: #8c8c8c;
    font-size: 12px;
  }
}

/* ---------- 吸顶步骤条 ---------- */
.flow-sticky {
  position: sticky;
  top: 0;
  z-index: 30;
  width: 100vw;
  margin-left: calc(50% - 50vw);
  background: linear-gradient(105deg, #0a1729 0%, #12253f 50%, #0a1729 100%);
  box-shadow: 0 2px 8px rgb(10 23 41 / 35%);
}

.flow-sticky-inner {
  max-width: 1200px;
  padding: 16px 0;
  margin: 0 auto;
}

.flow-steps {
  display: flex;
  gap: 5px;
}

.flow-step {
  display: flex;
  flex: 1;
  gap: 14px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 54px;
  overflow: hidden;
  color: #fff;
  font-size: 15px;
  white-space: nowrap;
  cursor: pointer;
  background: #33363c;
  clip-path: polygon(0 0, 100% 0, calc(100% - 26px) 100%, 26px 100%);
  transition: background 0.2s;

  &:first-child {
    clip-path: polygon(0 0, 100% 0, calc(100% - 26px) 100%, 0 100%);
  }

  &:hover {
    background: #3d4148;
  }

  &.on {
    background: #1979e8;

    &:hover {
      background: #1979e8;
    }
  }

  em {
    font-size: 15px;
    font-style: normal;
  }

  span {
    margin-left: -6px;
  }
}

/* ---------- 步骤内容面板 ---------- */
.content {
  padding-top: 8px;
}

.panel {
  padding: 30px 36px;
  margin-bottom: 20px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 3%);

  > h3 {
    margin: 30px 0 14px;
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;
  }

  > h3:first-of-type {
    margin-top: 0;
  }
}

.ability-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  margin-bottom: 26px;
}

.ability-card {
  h3 {
    margin: 0 0 16px;
    color: #1f2329;
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #595959;
    font-size: 13px;
    line-height: 1.9;
  }
}

.ability-icon {
  display: block;
  width: 40px;
  height: 40px;
  margin-bottom: 14px;
  background: #e8eaee;
  border-radius: 4px;

  &.icon-tool {
    background:
      linear-gradient(45deg, transparent 46%, #b8bfc9 46% 54%, transparent 54%),
      linear-gradient(-45deg, transparent 46%, #b8bfc9 46% 54%, transparent 54%),
      #e8eaee;
  }

  &.icon-book {
    background:
      linear-gradient(#e8eaee 0 0) padding-box,
      linear-gradient(90deg, #b8bfc9 0 6px, transparent 6px),
      #e8eaee;
    border-left: 6px solid #b8bfc9;
  }
}

.kv {
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;

  h3 {
    margin: 18px 0 6px;
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #595959;
    font-size: 13px;
    line-height: 1.9;
  }
}

.train-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin-bottom: 0;
  border-bottom: 2px solid #f0f0f0;

  a {
    padding: 10px 18px;
    color: #4e5969;
    font-size: 14px;
    white-space: nowrap;
    cursor: pointer;
    border: 1px solid transparent;
    border-bottom: none;

    &:hover {
      color: #1257c9;
    }

    &.on {
      color: #1257c9;
      font-weight: 600;
      background: #fff;
      border-color: #f0f0f0;
      border-radius: 4px 4px 0 0;
      box-shadow: 0 -2px 0 #1257c9 inset;
    }
  }
}

.train-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 36px;
  padding: 16px 0 6px;
  color: #595959;
  font-size: 13px;
}

.dot-list {
  padding-left: 22px;
  margin: 0 0 14px;
  color: #595959;
  font-size: 13px;
  line-height: 2.1;
}

.lead {
  margin: 4px 0 6px;
  color: #595959;
  font-size: 13px;
}

.panel-foot {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 20px;

  &.center {
    justify-content: center;
    padding: 10px 0 4px;
  }
}

.doc-link {
  color: #1257c9;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.cert-badge {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 92px;
  height: 92px;
  margin: 6px 0 14px;
  color: #fff;
  background: radial-gradient(circle at 35% 30%, #d54a45, #b01f24 70%);
  border: 3px solid #e8c6c6;
  border-radius: 50%;

  b {
    font-size: 16px;
    font-weight: 700;
  }

  span {
    padding: 0 8px;
    font-size: 10px;
    text-align: center;
    opacity: 0.9;
  }
}

.dl-btn {
  display: inline-block;
  min-width: 120px;
  padding: 8px 28px;
  color: #fff;
  font-size: 14px;
  text-align: center;
  cursor: pointer;
  background: #b01f24;
  transition: background 0.2s;

  &:hover {
    background: #c93237;
  }
}

.grid.recert {
  margin: 16px 0;

  td.left {
    text-align: left;
  }
}

/* ---------- 表格 ---------- */
.grid {
  width: 100%;
  font-size: 13px;
  border-collapse: collapse;

  th,
  td {
    padding: 12px 14px;
    text-align: left;
    border: 1px solid #f0f0f0;
  }

  th {
    color: #1f2329;
    font-weight: 600;
    white-space: nowrap;
    background: #fafafa;
  }

  td {
    color: #595959;
  }

  tbody tr:hover td {
    background: #fcfcfd;
  }
}

/* ---------- 考试大纲 / 学习资源 ---------- */
.syllabus {
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;

  h4 {
    margin: 0 0 12px;
    color: #1f2329;
    font-size: 15px;
    font-weight: 600;
  }

  h5 {
    margin: 14px 0 6px;
    color: #1f2329;
    font-size: 13px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #595959;
    font-size: 13px;
    line-height: 1.9;
  }
}

.icon-doc {
  display: block;
  width: 40px;
  height: 40px;
  margin: 4px 0 16px;
  background:
    linear-gradient(45deg, transparent 47%, #b8bfc9 47% 53%, transparent 53%),
    #e8eaee;
  border-radius: 4px;
}

.syllabus-table {
  width: 100%;
  margin: 6px 0 14px;
  font-size: 13px;
  border-collapse: collapse;

  td {
    padding: 10px 14px;
    color: #595959;
    border: 1px solid #f0f0f0;
  }

  td.pct {
    width: 80px;
    text-align: right;
  }
}

.learn-res {
  padding-top: 20px;
  margin-top: 6px;
  border-top: 1px solid #f0f0f0;

  h3 {
    margin: 0 0 10px;
    color: #1f2329;
    font-size: 16px;
    font-weight: 600;
  }
}

.learn-row {
  display: flex;
  gap: 16px;
  align-items: baseline;
  padding: 6px 0;

  b {
    flex-shrink: 0;
    width: 70px;
    color: #1f2329;
    font-size: 13px;
    font-weight: 600;
  }

  .res-note {
    color: #8c8c8c;
    font-size: 12px;
  }
}

.benefit-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  padding: 0;
  margin: 0;
  list-style: none;

  li {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  b {
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  span {
    color: #595959;
    font-size: 13px;
    line-height: 1.8;
  }
}

.rule-list {
  padding-left: 20px;
  margin: 14px 0 12px;
  color: #595959;
  font-size: 14px;
  line-height: 2;
}

/* ---------- 常见问题 ---------- */
.faq {
  border-top: 1px solid #f5f5f5;
}

.faq-item {
  padding: 14px 0;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.faq-q {
  display: flex;
  gap: 12px;
  align-items: center;

  .faq-mark {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    color: #fff;
    font-size: 12px;
    background: #1257c9;
    border-radius: 2px;
  }

  b {
    flex: 1;
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  em {
    color: #8c8c8c;
    font-size: 16px;
    font-style: normal;
  }
}

.faq-a {
  padding: 10px 0 0 32px;
  margin: 0;
  color: #595959;
  font-size: 13px;
  line-height: 1.9;
}

/* ---------- 相关认证 ---------- */
.related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.related-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  cursor: pointer;
  border: 1px solid #f0f0f0;
  border-radius: 2px;

  &:hover {
    border-color: #1257c9;
  }

  b {
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  span {
    color: #8c8c8c;
    font-size: 12px;
  }
}
</style>
