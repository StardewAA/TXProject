<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'PortalTrainingExamTraining' });

const router = useRouter();
const back = () => router.back();

/** 防腐保温行业权威机构与官方平台（真实可访问） */
const ORGS = [
  {
    name: '中国工业防腐蚀技术协会（CIATA）',
    url: 'http://www.ciata.org.cn/',
    desc: '全国性行业协会，常年接受防腐蚀职业培训与考核报名，主导"绿色防腐、科技防腐"行业理念与防腐蚀职业能力评价。',
    tags: ['国家级行业协会', '防腐蚀职业培训', '考核报名'],
  },
  {
    name: '广东省防腐蚀协会',
    url: 'https://www.gdaa.org.cn/',
    desc: '区域性行业协会，组织腐蚀控制与涂装技术行业职业技能竞赛、团体标准立项及系列技术培训。',
    tags: ['行业协会', '技能竞赛', '技术培训'],
  },
  {
    name: '全国建筑市场监管公共服务平台（四库一平台）',
    url: 'https://jzsc.mihuid.gov.cn/',
    desc: '住房和城乡建设部官方平台，可查询防水防腐保温工程专业承包企业资质及从业人员持证情况。',
    tags: ['官方平台', '企业资质查询', '人员持证查询'],
  },
];

const FLOW = [
  '选择目标认证等级与工种，确认对应的培训要求；',
  '在上述官方渠道查询授权培训机构/企业资质；',
  '与培训机构确认开班时间、培训内容与收费标准；',
  '完成培训后回平台预约对应等级认证考试。',
];
</script>

<template>
  <div class="training-page">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="back">首页</span>
          <em>›</em>
          <span @click="back">认证</span>
          <em>›</em>
          <span class="on">查找培训</span>
        </nav>
        <div class="hero-top">
          <div>
            <h1>查找培训 · 授权培训机构查询</h1>
            <p class="sub">防腐保温行业权威培训机构与官方查询渠道</p>
          </div>
          <Button class="back-btn" @click="back">返回上一页 →</Button>
        </div>
      </div>
    </header>

    <div class="content">
      <section class="panel">
        <h3>推荐机构与官方平台</h3>
        <a
          v-for="o in ORGS"
          :key="o.name"
          class="org"
          :href="o.url"
          target="_blank"
          rel="noreferrer"
        >
          <b>
            {{ o.name }}
            <em>官网直达 ↗</em>
          </b>
          <p>{{ o.desc }}</p>
          <div class="tags">
            <span v-for="t in o.tags" :key="t">{{ t }}</span>
          </div>
        </a>

        <h3>培训报名流程</h3>
        <ol class="flow">
          <li v-for="f in FLOW" :key="f">{{ f }}</li>
        </ol>

        <div class="tip">
          注：以上外部链接为行业公开信息渠道，请在报名前自行核实机构资质与收费标准，谨防虚假培训宣传。
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.training-page {
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
    margin: 0 0 16px;
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;
  }
}

.org {
  display: block;
  padding: 18px 20px;
  margin-bottom: 12px;
  border: 1px solid #e6f0ff;
  border-radius: 8px;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    border-color: #1257c9;
    box-shadow: 0 6px 16px rgb(18 87 201 / 10%);
  }

  b {
    display: flex;
    gap: 12px;
    align-items: center;
    color: #1f2329;
    font-size: 15px;

    em {
      color: #1257c9;
      font-size: 12px;
      font-style: normal;
    }
  }

  p {
    margin: 8px 0;
    color: #595959;
    font-size: 13px;
    line-height: 1.8;
  }

  .tags {
    display: flex;
    gap: 8px;

    span {
      padding: 2px 10px;
      color: #1257c9;
      font-size: 12px;
      background: #f0f6ff;
      border-radius: 999px;
    }
  }
}

.flow {
  padding-left: 22px;
  margin: 0;
  color: #595959;
  font-size: 13px;
  line-height: 2.2;
}

.tip {
  padding: 12px 16px;
  margin-top: 20px;
  color: #a06b00;
  font-size: 12px;
  background: #fff9ec;
  border: 1px solid #ffe7ad;
  border-radius: 6px;
}
</style>
