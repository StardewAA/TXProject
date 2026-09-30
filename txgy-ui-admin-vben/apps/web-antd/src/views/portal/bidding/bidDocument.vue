<!--
  招投标前台 · 标书制作（个人工作台）
  **结构（2026-09-23 用户口径）**：白卡（`.panel.bid-card`，就在那排四个模块入口的下面）的
    主体 = **标书工坊 `components/BidStudio.vue`** —— 它原先挂在上面蓝条里、夹在标题段与入口卡
    之间，本次搬进白卡；同口径**用标书工坊替换**了原来那句「标书制作功能正在接入中」的占位提示。
  ⚠ 白卡里还有另一块「待编制标书 / 已上传标书」列表（面向「待投标」流程编制/上传投标文件，
    真实接口 POST /bidding/file/upload）：归另一个组负责、**尚未合并上来**，
    取数 / 上传 / CA 签章 / 状态流转的代码全部保留，由 script 里的 `CONTENT_READY`（现为 false）
    整块隐藏，改成 true 即恢复，模板不用动。详见那个常量上方的注释。
  视觉（2026-09-17 改版）：与公告大厅/投标流程管理同一套设计令牌（深蓝主色 + 暖金辅助、柔和阴影、
        实心状态标签、圆角按钮），条目样式取自 styles/bid-ui.css 的 .bid-item*，不再各写一套。
  口径（2026-09-20 起后端为准）：流程数据来自共享 useBiddingProcess（数据源 GET /bidding/process-track/my，
        与投标流程管理同源）；标书上传走 POST /bidding/file/upload，确认递交走 /bidding/process-track/upload。
  依据：电子招标投标办法 第13条（部门规章）；招标投标法 第26条（法律）。
-->
<script lang="ts" setup>
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, message, Modal } from 'ant-design-vue';

import type { CaApi } from '#/api/bidding/ca';

import { uploadBiddingFile } from '#/api/bidding/file';

import CaSealAuth from '../../bidding/ca/CaSealAuth.vue';
import { STATE_TEXT, useBiddingProcess, type PortalTrack } from './composables/useBiddingProcess';
import BidStudio from './components/BidStudio.vue';
import HeroMods from './components/HeroMods.vue';
import './styles/bid-ui.css';

/* 合并说明（2026-09-21 rebase 冲突）：上游那一侧是
 *   `const router = useRouter();` + `{ advance, tracks, stageDocument, stageSeal }`。
 * · `router` 丢掉：本页的「返回公告大厅」按钮早已按用户口径删除，页面里已无 `router.` 取值，
 *   而 `import { useRouter } from 'vue-router'` 也在同一批里删掉了 —— 再留着这一行就是未定义引用。
 * · `stageSeal` 保留：它是上游新加的 CA 电子签章能力，下面的 `onSealed` 要用（不保留会编译不过）。 */
const { advance, tracks, stageDocument, stageSeal } = useBiddingProcess();

const pendingTracks = computed(() => tracks.value.filter((t) => t.currentState === 'PENDING_BID'));
const biddedTracks = computed(() => tracks.value.filter((t) => t.currentState === 'BIDDED'));
const isEmpty = computed(() => pendingTracks.value.length === 0 && biddedTracks.value.length === 0);

/* ===== 内容开关：只控制白卡里那两张「待编制 / 已上传标书」列表 =====
 * 沿革：2026-09-21 这个开关管的是**整张白卡**（那时白卡里只放一句「功能正在接入中」的占位提示）；
 *   2026-09-23 标书工坊搬进白卡后，占位提示与它那条分支一并删除，开关就只剩管下面那两张列表了。
 * 原因：那两张列表的功能与接口**归另一个组负责，尚未合并上来**。
 * 做法：取数、上传、CA 签章、状态流转的代码**全部原样保留**（模板里仍然引用着它们，所以不会变成
 *   未使用变量），只由这个开关决定它们显不显示：
 *     · `false`（现状）：白卡里只有标书工坊，两张列表整块不渲染；
 *     · `true`：下面追加「待编制标书 / 已上传标书」两张列表 + 上传 / CA 认证 / 确认上传按钮。
 * **上游合并后只要把这里改成 true 就能整块恢复，不需要动模板。**
 * 写不写 `: boolean` 都不影响行为（TS 对 `const x: boolean = false` 仍可能收窄成 false，
 * 但没有任何地方依赖收窄结果，编译器也不会因此裁掉 `v-else-if` 分支）；
 * 加上它只是把「这是个运行期开关、不是一个恒为假的字面量」写在类型上，读代码时不容易误解。 */
const CONTENT_READY: boolean = false;

/* ============ 标书上传（真实接口） ============ */
const uploadOpen = ref(false);
const uploadTarget = ref<PortalTrack | null>(null);
const pickedFile = ref<File | null>(null);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

/* 前端预校验：只为了省掉一次注定失败的往返，后端校验仍是权威。
 * 上限 16MB 与后端 spring.servlet.multipart.max-file-size 对齐（超限后端会回「上传文件过大，请调整后重试」）。 */
const DOC_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar,.7z';
const DOC_MAX_MB = 16;
const DOC_EXTS = DOC_ACCEPT.split(',');

/** 清空已选文件，并重置 input 自身——否则清空后再选同一个文件不会触发 change */
function resetPicker(): void {
  pickedFile.value = null;
  if (fileInput.value) fileInput.value.value = '';
}
function openUpload(t: PortalTrack): void {
  uploadTarget.value = t;
  resetPicker();
  uploadOpen.value = true;
}
function onFilePicked(e: Event): void {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  if (!file) {
    pickedFile.value = null;
    return;
  }
  const ext = '.' + (file.name.split('.').pop() ?? '').toLowerCase();
  if (!DOC_EXTS.includes(ext)) {
    resetPicker();
    message.warning('标书仅支持 PDF / Word / Excel / 压缩包（' + DOC_ACCEPT + '）');
    return;
  }
  if (file.size > DOC_MAX_MB * 1024 * 1024) {
    const sizeText = fmtSize(file.size);
    resetPicker();
    message.warning(`标书不得超过 ${DOC_MAX_MB} MB（当前 ${sizeText}）`);
    return;
  }
  pickedFile.value = file;
}
async function doUpload(): Promise<void> {
  const t = uploadTarget.value;
  if (!t || !pickedFile.value) {
    message.warning('请先选择标书文件');
    return;
  }
  // 双保险：onFilePicked 已拦一次，这里再确认，避免极端情况下带着超限文件发请求
  if (pickedFile.value.size > DOC_MAX_MB * 1024 * 1024) {
    message.warning(`标书不得超过 ${DOC_MAX_MB} MB（当前 ${fmtSize(pickedFile.value.size)}）`);
    return;
  }
  uploading.value = true;
  try {
    const resp = await uploadBiddingFile(pickedFile.value, 'bidding-document');
    // 仅暂存，不流转状态：可反复重新上传覆盖，需再点「确认上传」才进入已投标。
    // 同时记住原 File / url / sha256 —— CA 认证工作台盖章需要 multipart 原文件。
    stageDocument(t, {
      docId: resp.fileId,
      docName: resp.name || pickedFile.value.name,
      docSize: resp.size,
      file: pickedFile.value,
      sha256: resp.sha256,
      url: resp.url,
    });
    uploadOpen.value = false;
    resetPicker();
    message.success('标书已上传（暂存）；下一步点「CA 认证」完成电子签章，再点「确认上传」递交');
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    uploading.value = false;
  }
}

/** 确认上传：暂存的标书 → 已投标（流程结束） */
function confirmUpload(t: PortalTrack): void {
  if (!t.documentName && !t.documentId) {
    message.warning('请先上传标书文件');
    return;
  }
  Modal.confirm({
    title: '确认上传「' + t.projectName + '」的标书？',
    content:
      '确认后流程进入「已投标」并结束，不可再修改。递交需 CA 数字证书签章（SM3 指纹 + SM2 签名 + 证书序列号）。',
    okText: '确认上传',
    cancelText: '再检查一下',
    onOk: async () => {
      const r = await advance(t, 'UPLOAD_DOCUMENT');
      // 服务端失败时 r.error 为空：提示已由 requestClient 统一弹出，此处不再重复弹窗
      if (!r.ok) {
        if (r.error) message.warning(r.error);
        return;
      }
      message.success('已确认上传，流程进入「已投标」并结束');
    },
  });
}

/* ============ CA 认证（电子签章：二维码 → 模拟已扫码 → 盖章） ============ */
const sealOpen = ref(false);
const sealTarget = ref<null | PortalTrack>(null);

/** CaSealAuth 的 file 入参（fileId/url/sha256 来自上传响应，供 check 检测与预览） */
function sealFileOf(t: PortalTrack) {
  return {
    fileId: t.documentId,
    fileName: t.documentName,
    name: t.documentName,
    sha256: t.uploadSha256,
    url: t.uploadUrl,
  };
}

function openSeal(t: PortalTrack): void {
  if (t.documentId === undefined) {
    message.warning('请先上传标书文件，再做 CA 认证');
    return;
  }
  sealTarget.value = t;
  sealOpen.value = true;
}

/** 盖章完成：暂存签章产物（SM3 指纹/SM2 签名/证书号/红章文件），递交时作为证据链提交 */
async function onSealed(doc: CaApi.SealDocument): Promise<void> {
  const t = sealTarget.value;
  if (!t) return;
  // 证据链三件套缺一不可（递交接口对三者均 @NotBlank）：宁可在这里拦住，也不提交空签名
  if (!doc.signature || !doc.certSn || !doc.hash) {
    message.error('签章响应缺少签名值 / 证书号 / SM3 指纹，无法作为递交证据链，请重试');
    return;
  }
  const { sizeFrom } = await stageSeal(t, {
    certSn: doc.certSn,
    hash: doc.hash,
    orgName: doc.orgName,
    sealFileId: doc.sealFileId,
    sealFileName: doc.sealFileName,
    sealFileUrl: doc.sealFileUrl,
    sealTime: doc.sealTime,
    signature: doc.signature,
  });
  sealOpen.value = false;
  message.success(
    sizeFrom === 'sealed'
      ? 'CA 签章完成：红章文件与证据链已就绪，可点「确认上传」递交'
      : 'CA 签章完成（签章后文件大小回取失败，递交将按原文件大小提交）',
  );
}

function fmtSize(n?: number): string {
  if (n == null) return '';
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
  return n + ' B';
}
const p2 = (n: number) => String(n).padStart(2, '0');
function fmtT(v?: null | number | string): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
</script>

<template>
  <div class="module-page">
    <!-- Banner（与其它模块同一套底纹）。2026-09-21 起它自己**不再画背景** ——
         整页底图已上移到 layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层），
         本元素只负责占位与排版。这样照片整页连续，滚动时蓝条下沿不会切出接缝。 -->
    <section class="hero">
      <!-- 上：平台名 + 小字。.hero-text 是共享类（styles/bid-ui.css），2026-09-21 下午起居中、宽度自适应。 -->
      <div class="hero-text">
        <!-- 标题（2026-09-21 用户口径）：四个页面统一用平台名「防腐保温招投标智慧平台」。
             小字（同口径）：四个页面的这一行也统一成公告大厅那一行 ——
             于是四页的标题段逐字相同，本页是哪个模块只由下方高亮的那张入口卡体现。
             （此前这里放的是组长 2026-09-20 给本模块写的价值句「编制到提交一气呵成…」，
               现按用户要求换掉，句子已不再使用。） -->
        <h1 class="hero-title">防腐保温招投标<span class="hl">智慧</span>平台</h1>
        <div class="hero-sub">《公告大厅》《免费查阅 · 依法公开》《智慧标书》</div>
      </div>

      <!-- 下：四个模块入口那整行（共享组件 components/HeroMods.vue）。
           标书工坊原本夹在这一行与上面的标题段之间（蓝条里），2026-09-23 已搬进下面那张白卡。 -->
      <HeroMods active="bid-document" />
    </section>    <!-- 2026-09-21 用户口径：下面的内容部分底下加一张「略大一些的半透明底圆角卡片」——
         共享类 .bid-glass-shell（见 styles/bid-ui.css），本页直接加在已有的 .content 上。 -->
    <section class="content bid-glass-shell">
      <!-- 原来这里有一行工具栏：「返回公告大厅」+「去合规审查」两个按钮。
           2026-09-21 用户口径：本页这两个按钮都去掉（合规审查 / 投标流程管理两页的
           「返回公告大厅」也一并去掉）—— 顶部蓝条里那排模块入口卡已经承担了页面间跳转，
           这行按钮是重复入口。整块 `.toolbar` 连它的样式一起删除。
           删掉这一行之后，`.content` 的第一个子元素变成 `.panel`；它原先靠 `.content` 的
           `margin-top: -32px` 压在蓝条底部 —— 这一点 2026-09-21 也一并取消了（用户口径：
           下面的卡片不要压在蓝色背景上）——不过那条口径当天又被「将下面页面往上压」取代，
           现在整块内容是往上压进蓝条，顶边落在屏幕纵向正中再往上抬 `--bid-glass-lift`
           （`margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`）。 -->

      <div class="panel bid-card">
        <!-- **白卡主体 = 标书工坊**（2026-09-23 用户口径「把新增的内容放到四个按钮下面的白卡里」）。
             它原先挂在上面蓝条里、夹在标题段与那排入口卡之间：深色 Banner 上摆一整块白卡片，
             既把 Banner 的排版撑乱，层次也和另外三个模块不一致；现在它是本页白卡的主要内容。
             组件本身一行未改（同口径确认「保留工坊自带的标题段、组件原样不动」）。 -->
        <BidStudio />

        <!-- 再往下是原有「待编制标书 / 已上传标书」两张列表 —— 归另一个组负责、**尚未合并上来**，
             仍整块藏在 script 里的 `CONTENT_READY`（现为 false）后面。
             这里在 2026-09-21 曾放一句「标书制作功能正在接入中」的占位提示，
             2026-09-23 随标书工坊落地**已删除**（用户口径：用标书工坊替换占位）。 -->
        <!-- 数据就绪后的空态（`CONTENT_READY` 就落在这条与前一条链上） -->
        <div v-if="CONTENT_READY && isEmpty" class="bid-empty">
          <IconifyIcon class="bid-empty-icon" icon="lucide:file-text" />
          <div class="bid-empty-text">
            暂无待编制或已上传的标书 —— 到「公告大厅」关注项目、并走完报名与资质审核后，会出现在这里
          </div>
        </div>

        <!-- 待编制 / 已上传两张列表（同上，随 CONTENT_READY 一起恢复） -->
        <template v-else-if="CONTENT_READY">
          <!-- 待编制标书（待投标） -->
          <section v-if="pendingTracks.length">
            <div class="bid-sec-title">待编制标书<span class="bid-tag bid-tag--gray">{{ pendingTracks.length }}</span></div>
            <article v-for="t in pendingTracks" :key="t.id" class="bid-item">
              <div class="bid-item-main">
                <div class="bid-item-head">
                  <h3 class="bid-item-title">{{ t.projectName }}</h3>
                  <span class="bid-tag bid-tag--purple">{{ STATE_TEXT[t.currentState] }}</span>
                  <span v-if="t.issuerName" class="bid-item-issuer">{{ t.issuerName }}</span>
                </div>
                <div class="bid-item-meta">
                  <span class="bid-chip"><b>待投标</b>{{ fmtT(t.pendingBidTime) }}</span>
                  <span v-if="t.documentName" class="bid-chip">
                    <b>已暂存</b>{{ t.documentName }}<template v-if="t.documentSize">（{{ fmtSize(t.documentSize) }}）</template>
                  </span>
                </div>
              </div>
              <div class="bid-item-actions">
                <button class="bid-btn bid-btn--ghost bid-btn--sm" type="button" @click="openUpload(t)">
                  <IconifyIcon icon="lucide:upload" /> 上传标书
                </button>
                <button class="bid-btn bid-btn--ghost bid-btn--sm" type="button" @click="openSeal(t)">
                  <IconifyIcon icon="lucide:shield-check" /> {{ t.seal ? 'CA 签章 ✓' : 'CA 认证' }}
                </button>
                <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="confirmUpload(t)">
                  确认上传
                </button>
              </div>
            </article>
          </section>

          <!-- 已上传标书（已投标） -->
          <section v-if="biddedTracks.length">
            <div class="bid-sec-title">已上传标书<span class="bid-tag bid-tag--gray">{{ biddedTracks.length }}</span></div>
            <article v-for="t in biddedTracks" :key="t.id" class="bid-item">
              <div class="bid-item-main">
                <div class="bid-item-head">
                  <h3 class="bid-item-title">{{ t.projectName }}</h3>
                  <span class="bid-tag bid-tag--green">{{ STATE_TEXT[t.currentState] }}</span>
                  <span v-if="t.issuerName" class="bid-item-issuer">{{ t.issuerName }}</span>
                </div>
                <div class="bid-item-meta">
                  <span class="bid-chip">
                    <b>标书</b>{{ t.documentName || '—' }}<template v-if="t.documentSize">（{{ fmtSize(t.documentSize) }}）</template>
                  </span>
                  <span class="bid-chip"><b>上传</b>{{ fmtT(t.biddedTime) }}</span>
                </div>
              </div>
            </article>
          </section>
        </template>
      </div>
    </section>

    <!-- 上传标书弹窗 -->
    <Modal v-model:open="uploadOpen" title="上传标书" :footer="null" :width="550" wrap-class-name="white-modal">
      <div class="modal-body">
        <div class="modal-hint">上传「{{ uploadTarget?.projectName }}」的投标文件（暂存，可反复重新上传），需在列表点「确认上传」后才进入「已投标」；递交需 CA 数字证书签章。</div>
        <label class="file-pick">
          选择文件
          <input ref="fileInput" type="file" :accept="DOC_ACCEPT" @change="onFilePicked" />
        </label>
        <div v-if="pickedFile" class="modal-hint">已选择：{{ pickedFile.name }}（{{ fmtSize(pickedFile.size) }}）</div>
        <div v-else class="modal-hint">支持 PDF / Word / Excel / 压缩包，单个文件不超过 {{ DOC_MAX_MB }} MB。</div>
        <div class="modal-footer">
          <Button @click="uploadOpen = false">取消</Button>
          <Button type="primary" :loading="uploading" @click="doUpload">上传</Button>
        </div>
      </div>
    </Modal>
    <!-- 合并说明（2026-09-21 rebase 冲突）：上游这一段是「CA 认证工作台 + 服务导航 FloatBar」。
         前者是上游新加的功能（本页的 onSealed / stageSeal 都服务它），**整段保留**；
         `FloatBar` 按用户口径已彻底不显示（另两页也已无引用），而且上游那行所依赖的
         `import FloatBar` 在合并中已被本分支的删除带走 —— 留着就是个解析不到的组件，故删掉。 -->

    <!-- CA 认证工作台：二维码（测试态本地渲染，可真实扫出）+「模拟已扫码」→ 红章 PDF + SM2 签名 + 落库台账 -->
    <CaSealAuth
      v-if="sealTarget"
      :open="sealOpen"
      :file="sealFileOf(sealTarget)"
      :uploaded="sealTarget.uploadFile"
      :business="sealTarget.id"
      hide-trigger
      @update:open="(v: boolean) => { sealOpen = v }"
      @sealed="onSealed"
    />

  </div>
</template>

<style scoped>
.module-page {
  min-height: 100%;
  /* 底色透明（2026-09-21）：整页的底不归本页管 —— 底图钉在 layouts/portal.vue 的
     .portal-shell--bidhero 上。原先这里是浅灰 --bid-bg，滚动时蓝条下沿会露出它，
     形成一道"截断"的浅色带。 */
  background: transparent;
  color: var(--bid-text-1);
}

/* Banner：底纹与**尺寸规格**均与公告大厅 .hero 一致
 *   （2026-09-21 用户口径：四页蓝色背景先统一压到 160px 窄条、文字改左上角并缩小，
 *    当天又统一回到 200px，见下）。
 * 高度：**min-height: 100vh（满屏，2026-09-21 用户口径「加高背景部分高度，铺满试试」）**。
 *   · 沿革：固定内边距 40/20/66（≈190px）→ 1/3 屏高（33.333vh ≈ 360px）→ 160px → 本次回到 200px；
 *     本轮是配合公告大厅「四个模块入口移进蓝条 + 按钮放大」一起加的，四页同时改，
 *     否则切页时蓝条高度会跳。（当时本页蓝条里还没有那排入口卡；后来四页都放了 HeroMods，已一致。）
 *   · 仍用 min-height 而非 height —— 屏幕特别矮时不至于把文字挤出容器。
 * 文字位置（2026-09-21 下午口径）：标题段**水平居中**、标题 26 → 44px、副标题 14 → 20px
 *   （沿革：曾居中 → 左上角 40/16 → 左上角 26/14 → 本次居中 44/20）。
 *   左右内边距取 --bid-side-inset 的一半 —— 于是内容盒宽 = 100% − inset，
 *   与下方 .content（`width: calc(100% - var(--bid-side-inset))`）同宽同左右边缘
 *   （两者包含块同为 .module-page，百分比同源，必然对齐）；
 *   标题段居中后，这条列宽只由下方那排按钮体现 —— 而且它还要再往里收一圈去对齐玻璃卡里的**白卡**
 *   （用户 2026-09-21 口径「四个按钮的宽度与白卡对齐而不是玻璃卡」），见 HeroMods.vue。
 * 内容区与 Banner 的关系：**压在蓝条上、顶边落在屏幕纵向正中**
 *   （2026-09-21 最新口径「内容往上压，在屏幕页面 1/2 的位置」），
 *   `.content` 上外边距 `calc(-50vh - 64px - var(--bid-glass-lift))`，见其注释。
 *   （同一天早些时候还要求过「不压在蓝条上」，那是蓝条只有 200px 时的口径，已被取代。） */
.hero {
  position: relative;
  /* **一列两段、水平居中**（2026-09-21 下午用户口径：标题与小字移到正中间、放大字号；
     四个模块入口从标题右侧搬到标题下方那**整行**），与公告大厅 `.hero` 逐项一致。
     `align-items: center` 管的是交叉轴（＝水平）对齐 —— 标题段与下方那排按钮都居中
     （按钮排宽度比内容盒窄一圈，靠这条居中）。
     沿革（别再绕回去）：曾是「一行两段」（左标题 + 右按钮）+ `align-items: flex-start` +
     `justify-content: space-between` + `flex-wrap: nowrap` —— 那套全是「按钮在标题右边」时的口径。 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  /* **`gap: 0`**：标题段的纵向位置交给它自己的 `margin-top: auto`（见 styles/bid-ui.css 里
     `.hero-text` 的注释，算式也在那儿）。这里若还留 16px 行间距，标题与按钮之间就比上方多 16px，
     标题会被顶到中点上方约 8px —— 用户口径要的是「导航栏与按钮之间的正中间」。 */
  gap: 0;
  /* 满屏（2026-09-21 用户口径「加高背景部分高度，铺满试试」）：200px → min-height: 100vh。
     蓝条占满整个首屏（顶上门户导航条 64px 后，下缘落到首屏之外一点点，下面内容要往下滚一点）。
     仍用 min-height 而非 height：屏幕特别矮、或入口按钮降列数后内容变高时，条会跟着变高。
     想「门户导航条 + 蓝条」正好铺满首屏，改成 `calc(100vh - 64px)` 即可。 */
  min-height: 100vh;
  /* **上内边距 0**（原 26px）：标题段靠 `margin-top: auto` 定位，「导航栏与按钮之间的正中间」
     是拿本元素的内容盒顶边当上界的 —— 内容盒顶边必须正好等于门户导航栏下沿，留 26px 就会
     让标题比中点低 13px。左右仍是 `--bid-side-inset / 2`（决定内容盒宽＝内容列宽），下 22px 不变。 */
  padding: 0 calc(var(--bid-side-inset, 64px) / 2) 22px;
  overflow: hidden;
  color: #fff;
  /* 居中（2026-09-21 下午用户口径）。原来这里是 left（标题在左上角的口径）。 */
  text-align: center;
  /* 背景**透明**（2026-09-21 用户口径「照片铺满整页、钉住不滚」）：底图与蒙版已上移到
     layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层），取值一字未改 ——
     11 47 99 / 82% → 23 72 154 / 68%、裁切 `center top`，故首屏观感与改动前一致。
     本元素只留 `overflow: hidden` 与 `color: #fff`：白字仍压在那层照片上。 */
  background: transparent;
}

/* 标题与下面那行小字：2026-09-21 下午放大（用户口径「都移到正中间并放大字号」），
 *   26px → 44px、14px → 20px；**四页必须同值**（与公告大厅 .hero-title / .hero-sub 一致）。 */
.hero-title {
  margin: 0;
  font-size: 44px;
  font-weight: 800;
  /* 字距随字号收一档：字号 26px 时 1px 约 3.8%，44px 时 1px 只有 2.3%、显得松散 */
  letter-spacing: 2px;
  line-height: 1.25;
}

.hero-title .hl {
  color: var(--bid-accent-on-dark);
}

/* Banner 小字：浅白字；句子内容 2026-09-21 起与公告大厅统一
   （原来是组长给本模块单独写的那句价值话，已按用户口径换掉） */
.hero-sub {
  color: rgb(255 255 255 / 82%);
  font-size: 20px;
  line-height: 1.7;
  letter-spacing: 0.8px;
}

/* 内容区（工具栏 + 面板）：
 * 上外边距：**压在蓝条上、顶边落在屏幕纵向正中再往上抬 `--bid-glass-lift`**（2026-09-21 用户口径
 *   「内容往上压，在屏幕页面 1/2 的位置」＋同天下午「增加玻璃卡的高度，让四个按钮上移后可以压到
 *   玻璃卡」）—— `margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`，算式见下面那段注释。
 *   沿革（别再绕回去）：-44px → -32px（压进蓝条）→ 20px（蓝条下方，当天的口径）→ 现在压在蓝条上、顶边在屏幕 1/2 处。
 * position/z-index 是**必需的**：Banner 是 position:relative（叠了几何纹理与底图），
 *   带定位的兄弟会盖在普通块之上；不给自己定位就会被蓝条盖住。
 * 宽度：左右对称收窄，两侧边距取共享变量 --bid-side-inset（随窗口缩放，每边 32~240px）。 */
.content {
  position: relative;
  z-index: 1;
  width: calc(100% - var(--bid-side-inset));
  /* 顶上位置（2026-09-21 用户口径「内容往上压，在屏幕页面 1/2 的位置」）：
     `margin-top: calc(-50vh - 64px)` —— 让本区的**顶边落在屏幕纵向正中**。
     算式：文档流里它的自然位置在蓝条下方 = 顶栏 64px + 蓝条 100vh，要把它提到 50vh 处，
     需要回压 `-(64px + 50vh)`。64px 是门户顶栏的高度（layouts/portal.vue 的 min-height）。
     2026-09-21 下午又**往上抬了 `--bid-glass-lift`（20px）**，所以末项是 `- var(--bid-glass-lift)` ——
     抬手同时把 `.bid-glass-shell` 的上内边距加同样的量，于是卡是**往上长高**、里面的白卡原地不动；
     按钮排那边也同步上移（见 components/HeroMods.vue）。**要调高度只改 --bid-glass-lift 那一处。**
     想微调落点：想落在 40% 处就把 `-50vh` 换成 `-60vh`（绝对值更大＝更往上）。
     （早先的「内容不要压在蓝条上」是蓝条只有 200px 时的口径，已被本条取代。）
     position/z-index 一直是必需的：Banner 是 position:relative，不给自己定位就会被蓝色盖住。 */
  margin: calc(-50vh - 64px - var(--bid-glass-lift, 0px)) auto 40px;
}

/* `.toolbar` / `.toolbar-actions` 的样式已在 2026-09-21 随那行按钮一起删除（用户口径：
   顶部蓝条里那排模块入口卡已承担页面间跳转，「返回公告大厅」「去合规审查」都是重复入口）。 */

/* 白卡内边距：原值 `6px 22px 20px` 是配「列表 / 占位提示」那套内容的（标题自带 margin-top）。
 * 2026-09-23 白卡主体换成标书工坊：它第一行是 `Tender Document` 那行小字（margin 只有 6px），
 * 6px 上边距会让它几乎贴住卡片顶边，所以抬到 18px；左右 22px 与上下都在原值附近，未另起一套。 */
.panel {
  padding: 18px 22px 22px;
}

/* 条目 / 提示条 / 空态样式见 styles/bid-ui.css */

/* 弹窗内部（antd Modal 由 white-modal 全局类强制白底） */
.modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fff;
}

.modal-hint {
  color: var(--bid-text-1);
  font-size: 12.5px;
  line-height: 1.7;
}

.file-pick {
  display: inline-block;
  align-self: flex-start;
  padding: 8px 16px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: #fff;
  color: var(--bid-primary-500);
  font-size: 13px;
  cursor: pointer;
}

.file-pick:hover {
  border-color: var(--bid-primary);
}

.file-pick input {
  display: none;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 弹窗的白底与深色字统一在 styles/bid-ui.css 的 .white-modal 段处理
 * （antd Modal 挂到 body，令牌需在弹窗容器上重新定义，页面内不再重复写）。 */

/* 两侧边距由共享变量 --bid-side-inset 自动收敛，无需额外媒体查询（见 styles/bid-ui.css）。 */
</style>
