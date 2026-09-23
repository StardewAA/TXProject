<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'PortalTrainingExamDoc' });

const route = useRoute();

/** 文档标题与副标题（由跳转方传入） */
const title = computed(() => (route.query.title as string) ?? '公告文档');
const sub = computed(() => (route.query.sub as string) ?? '平台公告 · 占位文档');
const docNo = computed(
  () => `ZZ-PUB-${String(route.query.id ?? '000')}`,
);

/** 占位文档骨架 */
const SECTIONS = computed(() => [
  {
    heading: '一、背景与目的',
    lines: [
      `为进一步规范认证考试组织流程，平台发布了本《${title.value}》。`,
      '文档旨在明确相关事项的适用范围、执行标准与时间安排，保障考生与授权机构权益。',
    ],
  },
  {
    heading: '二、适用范围',
    lines: [
      '本文件适用于平台全部在管职业认证项目的考生、授权培训机构及考点工作人员。',
      '涉及线上线下考试、证书发放与验真、继续教育等业务的，均参照本文件执行。',
    ],
  },
  {
    heading: '三、主要内容',
    lines: [
      '（占位）核心条款一：考试报名与资格审核的具体要求与时限；',
      '（占位）核心条款二：考试成绩公布、复核与证书发放流程；',
      '（占位）核心条款三：违规行为的认定标准与处理措施。',
    ],
  },
  {
    heading: '四、生效时间',
    lines: ['本文档自发布之日起生效，此前规定与本文件不一致的，以本文件为准。'],
  },
]);

const back = () => window.history.back();
const year = ref(new Date().getFullYear());

/** 附件 → 独立的占位文档 */
const router = useRouter();
function openAttach() {
  router.push({
    path: '/portal/training/exam/doc',
    query: {
      title: `${title.value}（全文 PDF）`,
      sub: '附件文档 · 占位文档',
      id: `${docNo.value}-A`,
    },
  });
}
</script>

<template>
  <div class="doc-page">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="back">首页</span>
          <em>›</em>
          <span @click="back">认证</span>
          <em>›</em>
          <span class="on">公告文档</span>
        </nav>
        <div class="hero-top">
          <div>
            <h1>{{ title }}</h1>
            <p class="sub">
              {{ sub }} · 文号 {{ docNo }} · 发布日期 {{ year }}-09-22
            </p>
          </div>
          <Button class="back-btn" @click="back">返回上一页 →</Button>
        </div>
      </div>
    </header>

    <div class="content">
      <section class="panel">
        <div class="placeholder">
          <b>占位文档 · 内容建设中</b>
          <p>本页面为公告占位预览，正式发布后将在此呈现完整文档内容与附件下载。</p>
        </div>

        <h3>{{ title }}</h3>
        <p class="lead">
          各有关单位、考生：现将《{{ title }}》印发给你们，请遵照执行。
        </p>

        <template v-for="s in SECTIONS" :key="s.heading">
          <h4>{{ s.heading }}</h4>
          <p v-for="l in s.lines" :key="l" class="text">{{ l }}</p>
        </template>

        <div class="sign">
          <p>平台认证考试委员会</p>
          <p>{{ year }} 年 9 月 22 日</p>
        </div>

        <div class="attach">
          <span class="doc-icon"></span>
          <a @click="openAttach">附件：{{ title }}（全文 PDF）.pdf</a>
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.doc-page {
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
  padding: 30px 40px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 3%);
}

.placeholder {
  padding: 22px;
  margin-bottom: 26px;
  text-align: center;
  background:
    repeating-linear-gradient(
      45deg,
      #fafbfd 0 14px,
      #f5f7fb 14px 28px
    );
  border: 1px dashed #c9d6ea;
  border-radius: 6px;

  b {
    color: #1f2329;
    font-size: 15px;
  }

  p {
    margin: 8px 0 0;
    color: #8c8c8c;
    font-size: 13px;
  }
}

h3 {
  margin: 0 0 18px;
  color: #1f2329;
  font-size: 19px;
  font-weight: 600;
  text-align: center;
}

.lead {
  color: #595959;
  font-size: 14px;
  line-height: 2;
}

h4 {
  margin: 22px 0 10px;
  color: #1f2329;
  font-size: 15px;
  font-weight: 600;
}

.text {
  margin: 0 0 8px;
  padding-left: 14px;
  color: #595959;
  font-size: 13px;
  line-height: 2;
  text-indent: 2em;
}

.sign {
  margin-top: 30px;
  text-align: right;

  p {
    margin: 6px 0;
    padding-right: 30%;
    color: #595959;
    font-size: 13px;
  }
}

.attach {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px 16px;
  margin-top: 24px;
  background: #f7faff;
  border: 1px solid #e6f0ff;
  border-radius: 6px;

  .doc-icon {
    width: 26px;
    height: 26px;
    background:
      linear-gradient(45deg, transparent 46%, #b8bfc9 46% 53%, transparent 53%),
      #e8eaee;
    border-radius: 4px;
  }

  a {
    color: #1257c9;
    font-size: 13px;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
