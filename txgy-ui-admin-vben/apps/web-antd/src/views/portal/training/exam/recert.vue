<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'PortalTrainingExamRecert' });

const router = useRouter();
const back = () => router.back();

/** 重认证规则 */
const RULES = [
  '证书有效期为 3 年，自考试通过之日起计算；',
  '证书到期前 6 个月内可申请重认证，逾期需重新参加全等级考试；',
  '完成对应等级的继续教育课程并通过考核，即可完成重认证；',
  '重认证通过后证书有效期自原到期日起顺延 3 年。',
];

/** 重认证流程 */
const FLOW = [
  { title: '到期提醒', text: '证书到期前 6 个月，平台将通过站内消息与短信提醒持证人。' },
  { title: '继续教育', text: '在「我的学习」中完成对应等级的继续教育课程（不少于 24 学时）。' },
  { title: '重认证考核', text: '参加重认证考试，重点考核新规范、新工艺与近年考核要点的变化内容。' },
  { title: '证书延续', text: '考核通过后 1 个工作日生成新证书，有效期自原到期日起顺延 3 年。' },
];

/** 各等级重认证费用 */
const FEE_ROWS = [
  { level: '初级工', fee: '100 元', edu: '24 学时', exam: '理论（60 题）' },
  { level: '中级工', fee: '150 元', edu: '24 学时', exam: '理论（60 题）' },
  { level: '高级工', fee: '200 元', edu: '32 学时', exam: '理论 + 实操' },
  { level: '技师', fee: '300 元', edu: '40 学时', exam: '理论 + 实操 + 答辩' },
  { level: '高级技师', fee: '400 元', edu: '40 学时', exam: '理论 + 实操 + 答辩' },
];
</script>

<template>
  <div class="recert-page">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="back">首页</span>
          <em>›</em>
          <span @click="back">认证</span>
          <em>›</em>
          <span class="on">重认证规则</span>
        </nav>
        <div class="hero-top">
          <div>
            <h1>重认证规则</h1>
            <p class="sub">证书有效期管理 · 继续教育与重认证考核说明</p>
          </div>
          <Button class="back-btn" @click="back">返回上一页 →</Button>
        </div>
      </div>
    </header>

    <div class="content">
      <section class="panel">
        <h3>证书有效期与延续</h3>
        <p class="text">
          为确保持证人的专业能力与行业最新规范、工艺保持同步，平台认证证书设置
          <b>3 年有效期</b>。有效期内持证人可通过完成继续教育并参加重认证考核延续证书；
          逾期未完成重认证的，证书将转为「待激活」状态，超过 1 年未处理的需重新参加全等级考试。
        </p>

        <h3>重认证规则</h3>
        <ul class="rules">
          <li v-for="r in RULES" :key="r">{{ r }}</li>
        </ul>

        <h3>重认证流程</h3>
        <div class="flow">
          <div v-for="(f, i) in FLOW" :key="f.title" class="flow-item">
            <span class="num">{{ i + 1 }}</span>
            <b>{{ f.title }}</b>
            <p>{{ f.text }}</p>
          </div>
        </div>

        <h3>各等级重认证标准</h3>
        <table class="grid">
          <thead>
            <tr>
              <th>认证等级</th>
              <th>重认证费用</th>
              <th>继续教育学时</th>
              <th>重认证考核形式</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in FEE_ROWS" :key="r.level">
              <td>{{ r.level }}</td>
              <td>{{ r.fee }}</td>
              <td>{{ r.edu }}</td>
              <td>{{ r.exam }}</td>
            </tr>
          </tbody>
        </table>

        <h3>注意事项</h3>
        <ul class="rules">
          <li>重认证考核内容侧重新规范、新工艺，难度低于初次认证，重点检验知识更新情况；</li>
          <li>继续教育学时可通过线上课程、行业学术活动、企业内训认定等多种方式累计；</li>
          <li>重认证期间原证书在有效期内仍可正常使用；</li>
          <li>对重认证结果有异议的，可在结果公布 15 日内向平台考试委员会申请复核。</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.recert-page {
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
    font-size: 24px;
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
  padding: 28px 34px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 3%);

  h3 {
    margin: 26px 0 12px;
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;

    &:first-of-type {
      margin-top: 0;
    }
  }
}

.text {
  margin: 0;
  color: #595959;
  font-size: 13px;
  line-height: 2;

  b {
    color: #1257c9;
  }
}

.rules {
  padding-left: 20px;
  margin: 0;
  color: #595959;
  font-size: 13px;
  line-height: 2.2;
}

.flow {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
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

@media (width <= 900px) {
  .flow {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
