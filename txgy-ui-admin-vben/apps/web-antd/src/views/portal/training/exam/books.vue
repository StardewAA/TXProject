<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'PortalTrainingExamBooks' });

const router = useRouter();
const back = () => router.back();

/** 防腐保温行业参考书籍（真实出版信息） */
const BOOKS = [
  {
    title: '《防腐蚀工程》',
    author: '沈春林 编著',
    press: '中国建筑工业出版社',
    tag: '工程设计施工图集',
    desc: '系统整合建筑防腐蚀工程材料与工艺技术，涵盖楼地面防腐蚀工程、建筑结构防腐等设计施工内容，适合设计与施工技术人员。',
  },
  {
    title: '《防腐、隔热、保温工程》',
    author: '张国栋 编著',
    press: '河南科学技术出版社',
    tag: '工程造价指导',
    desc: '建筑工程工程造价指导金典系列图书，围绕防腐、隔热、保温工程的计价与定额应用展开，适合预算与造价岗位人员。',
  },
  {
    title: '《防腐蚀施工安全技术》',
    author: '化学工业出版社组织编写',
    press: '化学工业出版社',
    tag: '施工安全',
    desc: '聚焦防腐蚀施工作业中的安全管理、危险源辨识与防护措施，是安全技术交底与现场管理的实用参考书。',
  },
  {
    title: '《全国建筑防腐、保温、隔热工程定额手册》',
    author: '沈春林 主编',
    press: '中国标准出版社',
    tag: '定额手册',
    desc: '收录建筑防腐、保温、隔热工程定额资料，满足保温工程概预算编制与定额查询需求。',
  },
  {
    title: '《防腐保温实用技术》',
    author: '耿浩 著',
    press: '安徽科学技术出版社',
    tag: '实用技术',
    desc: '面向一线施工人员，讲解防腐保温工程的实用操作技术与质量要点，通俗易学。',
  },
  {
    title: '《建筑外墙保温施工技术》',
    author: '职业院校系列教材',
    press: '职业教育规划教材',
    tag: '院校教材',
    desc: '可作为职业院校土木建筑类专业教材，也可作为建筑节能行业外墙保温施工的培训参考书。',
  },
];
</script>

<template>
  <div class="books-page">
    <header class="hero">
      <div class="hero-inner">
        <nav class="crumb">
          <span @click="back">首页</span>
          <em>›</em>
          <span @click="back">认证</span>
          <em>›</em>
          <span class="on">参考书籍</span>
        </nav>
        <div class="hero-top">
          <div>
            <h1>参考书籍 · 平台认证系列丛书</h1>
            <p class="sub">防腐保温行业经典教材与工具书推荐</p>
          </div>
          <Button class="back-btn" @click="back">返回上一页 →</Button>
        </div>
      </div>
    </header>

    <div class="content">
      <section class="panel">
        <h3>推荐书目</h3>
        <p class="page-desc">
          以下书目覆盖防腐保温工程的设计、施工、造价与安全管理等方向，可作为各等级认证的备考与自学参考资料。
        </p>
        <div class="grid">
          <div v-for="b in BOOKS" :key="b.title" class="book">
            <div class="cover">
              <b>防腐保温</b>
              <span>认证丛书</span>
            </div>
            <div class="info">
              <b class="name">{{ b.title }}</b>
              <p class="meta">
                <span>{{ b.author }}</span>
                <em>·</em>
                <span>{{ b.press }}</span>
              </p>
              <span class="tag">{{ b.tag }}</span>
              <p class="desc">{{ b.desc }}</p>
            </div>
          </div>
        </div>

        <div class="tip">
          注：书目信息整理自公开出版资料，具体版本与购买渠道请以各大电商平台及出版社官方信息为准。
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.books-page {
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
    margin: 0 0 8px;
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;
  }
}

.page-desc {
  margin: 0 0 20px;
  color: #8c8c8c;
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.book {
  display: flex;
  gap: 16px;
  padding: 18px;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    border-color: #bcd4ff;
    box-shadow: 0 6px 16px rgb(18 87 201 / 10%);
  }
}

.cover {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 118px;
  background: linear-gradient(160deg, #123a73, #0a1f3f 70%);
  border-radius: 4px;

  b {
    color: #fff;
    font-size: 14px;
    letter-spacing: 2px;
  }

  span {
    padding: 1px 8px;
    color: #ffd58a;
    font-size: 10px;
    border: 1px solid rgb(255 213 138 / 60%);
    border-radius: 999px;
  }
}

.info {
  min-width: 0;

  .name {
    display: block;
    color: #1f2329;
    font-size: 15px;
  }

  .meta {
    display: flex;
    gap: 8px;
    align-items: center;
    margin: 6px 0;
    color: #8c8c8c;
    font-size: 12px;

    em {
      font-style: normal;
    }
  }

  .tag {
    display: inline-block;
    padding: 2px 10px;
    color: #1257c9;
    font-size: 12px;
    background: #f0f6ff;
    border-radius: 999px;
  }

  .desc {
    margin: 8px 0 0;
    color: #595959;
    font-size: 13px;
    line-height: 1.8;
  }
}

.tip {
  padding: 12px 16px;
  margin-top: 20px;
  color: #8c8c8c;
  font-size: 12px;
  background: #f7f8fa;
  border-radius: 6px;
}

@media (width <= 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
