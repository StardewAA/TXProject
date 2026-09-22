<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { CERT_FIELDS } from './cert-data';
import { ROADMAP_GROUPS, ROADMAP_LEVELS } from './roadmap-data';

defineOptions({ name: 'PortalTrainingExamBooking' });

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

const certLevels = computed(() =>
  ROADMAP_LEVELS.slice(0, ROADMAP_LEVELS.indexOf(job.value.top as any) + 1),
);

const goBack = () => router.back();

/** 预约考试：占位（暂未开放在线预约） */
const SLOTS = [
  { date: '2026-10-15', site: '合肥考点 · 理论+实操', state: '可预约', on: true },
  { date: '2026-10-22', site: '合肥考点 · 理论+实操', state: '可预约', on: true },
  { date: '2026-11-05', site: '郑州考点 · 理论+实操', state: '已满员', on: false },
  { date: '2026-11-12', site: '合肥考点 · 理论+实操', state: '可预约', on: true },
];

/** 考试流程（根据具体职业生成） */
const FLOW_STEPS = computed(() => [
  {
    title: '选择考试科目',
    text: `在考试中心选择「${job.value.name}」对应等级的认证考试（当前可报：${certLevels.value.join('、')}）。`,
  },
  {
    title: '资格审核',
    text: `提交学历/从业年限等证明材料，审核${fieldMeta.value.name}岗位的报考条件，1-3 个工作日出结果。`,
  },
  {
    title: '缴纳考试费',
    text: '按等级缴纳考试费用（初级/中级 200 元、高级 300 元、技师及以上 500 元），支持在线支付。',
  },
  {
    title: '理论考试',
    text: `在线闭卷作答，考查${job.value.skill}等理论知识与规范，时长 60-90 分钟。`,
  },
  {
    title: '实操考核',
    text: `在授权考点完成${job.value.name}典型作业项目的现场操作与质量自检考评，时长 90-120 分钟。`,
  },
  {
    title: '成绩与证书',
    text: '两科均合格后 1 个工作日颁发电子证书，证书编号支持在线验真。',
  },
]);

/** 考试规则（根据具体职业生成） */
const RULES = computed(() => [
  `报考「${job.value.name}」认证实行逐级递进，报考上一等级须先取得下一等级证书；`,
  `理论科目重点考查${job.value.skill}相关的国家标准、作业规范与安全环保要求；`,
  `实操科目按${fieldMeta.value.name}岗位典型工况命题，由考评员现场评分；`,
  '理论 60 分、实操 70 分为合格线（技师及以上等级：理论 70 分、实操 80 分）；',
  '考试迟到 15 分钟不得入场，缺考视为放弃本次考试且费用不予退还；',
  '未通过可在 15 日后预约补考，理论科目提供 1 次免费补考机会；',
  '考试全程录像监控，发现违纪将取消成绩并限制半年内再次报考。',
]);

const PLACEHOLDER_TIPS = [
  '在线预约功能正在建设中，当前页面为占位预览；',
  '正式开放后可在本页选择考点、场次并完成在线缴费；',
  '开放前如有紧急报考需求，请联系培训教务老师线下登记。',
];
</script>

<template>
  <div class="booking">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="goBack">首页</span>
          <em>›</em>
          <span @click="goBack">认证</span>
          <em>›</em>
          <span @click="goBack">{{ job.name }}</span>
          <em>›</em>
          <span class="on">预约考试</span>
        </nav>
        <div class="hero-top">
          <div>
            <h1>预约考试</h1>
            <p class="sub">{{ job.name }} · {{ fieldMeta.name }}岗位序列 · 版本 V2.0</p>
          </div>
          <Button class="back-btn" @click="goBack">返回上一页 →</Button>
        </div>
      </div>
    </header>

    <div class="content">
      <!-- 占位区 -->
      <section class="panel">
        <h3>考试场次</h3>
        <div class="placeholder">
          <div class="ph-icon">
            <svg viewBox="0 0 24 24" width="42" height="42" fill="none">
              <rect
                x="3.5"
                y="5"
                width="17"
                height="15"
                rx="2.5"
                stroke="#94a7c4"
                stroke-width="1.6"
              />
              <path d="M3.5 9.5h17" stroke="#94a7c4" stroke-width="1.6" />
              <path
                d="M8 3v3.5M16 3v3.5"
                stroke="#94a7c4"
                stroke-width="1.6"
                stroke-linecap="round"
              />
              <path
                d="m9.3 14.6 2 2 3.4-3.6"
                stroke="#1257c9"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>
          <b>在线预约功能即将上线</b>
          <ul>
            <li v-for="t in PLACEHOLDER_TIPS" :key="t">{{ t }}</li>
          </ul>
        </div>

        <table class="grid">
          <thead>
            <tr>
              <th>考试日期</th>
              <th>考点 / 形式</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in SLOTS" :key="s.date">
              <td>{{ s.date }}</td>
              <td>{{ s.site }}</td>
              <td>{{ s.state }}</td>
              <td>
                <Button size="small" type="primary" :disabled="!s.on">
                  {{ s.on ? '预约' : '已满' }}
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 考试流程与规则 -->
      <section class="panel">
        <h3>考试流程</h3>
        <div class="flow">
          <div v-for="(f, i) in FLOW_STEPS" :key="f.title" class="flow-item">
            <span class="num">{{ i + 1 }}</span>
            <b>{{ f.title }}</b>
            <p>{{ f.text }}</p>
          </div>
        </div>

        <h3>考试规则</h3>
        <ul class="rules">
          <li v-for="r in RULES" :key="r">{{ r }}</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.booking {
  max-width: 1200px;
  padding-bottom: 48px;
}

.hero {
  position: relative;
  width: 100vw;
  margin-left: calc(50% - 50vw);
  background: linear-gradient(105deg, #0a1729 0%, #12253f 50%, #0a1729 100%);

  .hero-inner {
    max-width: 1200px;
    padding: 24px 0 20px;
    margin: 0 auto;
  }

  h1 {
    margin: 0 0 8px;
    color: #fff;
    font-size: 26px;
    font-weight: 600;
  }

  .sub {
    margin: 0;
    color: rgb(255 255 255 / 65%);
    font-size: 13px;
  }

  .hero-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
  }

  .back-btn {
    color: rgb(255 255 255 / 85%);
    background: rgb(255 255 255 / 12%);
    border-color: rgb(255 255 255 / 40%);

    &:hover {
      color: #0a1729;
      background: #fff;
      border-color: #fff;
    }
  }
}

.crumb {
  margin-bottom: 18px;
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

.content {
  padding-top: 20px;
}

.panel {
  padding: 26px 32px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 3%);

  > h3 {
    margin: 0 0 16px;
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;
  }
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 20px;
  margin-bottom: 22px;
  text-align: center;
  background:
    repeating-linear-gradient(
      45deg,
      #fafbfd 0 14px,
      #f5f7fb 14px 28px
    );
  border: 1px dashed #c9d6ea;
  border-radius: 6px;

  .ph-icon {
    margin-bottom: 12px;
  }

  b {
    color: #1f2329;
    font-size: 16px;
  }

  ul {
    padding: 0;
    margin: 10px 0 0;
    list-style: none;

    li {
      color: #8c8c8c;
      font-size: 13px;
      line-height: 2;
    }
  }
}

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
    background: #fafafa;
  }

  td {
    color: #595959;
  }

  tbody tr:hover td {
    background: #fcfcfd;
  }
}

.flow {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 10px;
}

.flow-item {
  padding: 16px;
  background: linear-gradient(160deg, #f4f8ff, #fff);
  border: 1px solid #e6f0ff;
  border-radius: 10px;

  .num {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    margin-bottom: 8px;
    color: #fff;
    font-size: 12px;
    font-weight: 700;
    background: linear-gradient(135deg, #2f8bff, #0a2a6b);
    border-radius: 50%;
  }

  b {
    display: block;
    margin-bottom: 6px;
    color: #1f2329;
    font-size: 14px;
  }

  p {
    margin: 0;
    color: #8c8c8c;
    font-size: 13px;
    line-height: 1.7;
  }
}

.rules {
  padding-left: 20px;
  margin: 0;
  color: #595959;
  font-size: 13px;
  line-height: 2.2;
}
</style>
