<!--
  四要素注释
  对应业务：爬虫任务工作台（菜单 6738）的「手动录入（生成测试任务）」弹窗，即解析器测试入口：
    选择解析器 + 粘贴 HTML/JSON 原文或上传 .html/.htm/.json/.pdf 文件，提交后沿真实下载→解析管线执行一次，
    结果立即生成一条 TEST 任务条（源原文/提取内容/渲染内容/过程），供管理端在接入站点下载前先校准解析器。
  实现理由：解析器为可插拔 SPI（NoticeParser/ParserRegistry），遵循"先测试解析器、再写下载器"的校准工作流；
    录入内容仅限外部站点公开可免费查阅的公告公示信息，且提交动作做身份/权限验证并留痕为可审核任务条，保证来源真实可溯。
  法规依据：
    招标公告和公示信息发布管理办法（国家发展改革委令第10号，2017）第12条【部门规章/政府文件】：
      发布媒介免费提供并允许社会公众免费、及时查阅公告公示完整信息（采集/核验对象即此类免费公开信息）；
    电子招标投标办法（八部委令第20号，2013）第24条【部门规章】：
      投标人等应在平台上注册登记、如实递交信息并经验证 —— 平台对测试/录入类动作实施身份验证与权限控制
      （bidding:crawl-source:query/update），防止非授权操作。
  解析器选择：GET /bidding/crawl-parser/list 下拉选择（code 属系统标识，禁止手填；接口不可用时用内置清单兜底）。
  关联契约：POST /bidding/crawl-parser/test（multipart：parserCode/payloadType/content/fileName/url/file），
    见 docs/bidding-notice-external/爬虫任务工作台-前后端契约-2026-09-07.md §1.3/§3.2 与
    docs/爬虫解析器插件化架构与测试接口.md §3；前端函数见 apps/web-antd/src/api/bidding/crawlExternal
    （fetchCrawlParserList / crawlParserTest），端点对账见 2026-09-08-crawl-contract-batch.md。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, onMounted, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, message, Radio, Select, Upload } from 'ant-design-vue';

import { crawlParserTest, fetchCrawlParserList } from '#/api/bidding/crawlExternal';

const props = defineProps<{ sourceOptions?: Array<{ label: string; value: string }> }>();
const emit = defineEmits(['parsed']);

const inputType = ref<'FILE' | 'HTML' | 'JSON'>('HTML');
const content = ref('');
const url = ref('');
const sourceCode = ref<string | undefined>(undefined);
const parserCode = ref('');
const parserOptions = ref<CrawlExternalApi.ParserInfo[]>([]);
const file = ref<File>();
const running = ref(false);

interface ParserChoice { code: string; name: string; supportedTypes: string[] }

/** 解析器内置清单（兜底）：GET /bidding/crawl-parser/list 不可用（后端未启/账号无 bidding:crawl-source:query）时，
 *  仍保证"只能选、不能填"——清单与后端 ParserRegistry 已注册解析器一致；新增解析器以上线接口为准。 */
const PARSER_FALLBACK: ParserChoice[] = [
  { code: 'HTML_GENERIC', name: '通用 HTML 公告解析器', supportedTypes: ['HTML'] },
  { code: 'HTML_AI', name: 'HTML + AI 结构化解析（含行业相关判定）', supportedTypes: ['HTML'] },
  { code: 'JSON_GENERIC', name: '通用 JSON 公告解析器', supportedTypes: ['JSON'] },
  { code: 'PDF_TEXT_GENERIC', name: '通用 PDF 文本公告解析器', supportedTypes: ['PDF'] },
  { code: 'PDF_AI', name: 'PDF + AI 结构化解析（含行业相关判定）', supportedTypes: ['PDF'] },
  { code: 'EBIDDING', name: '秦源电子招标平台解析器（ebidding.com.cn）', supportedTypes: ['HTML'] },
  { code: 'GX_E_BIDDING', name: '国信e采（山西）解析器（gx.e-bidding.org）', supportedTypes: ['HTML'] },
  { code: 'QYZB_JSON', name: '秦源栏目 JSON 接口解析器（qyzb.shccmg.com）', supportedTypes: ['JSON'] },
  { code: 'QYZB_SHCCMG', name: '秦源招标电子招标平台解析器（qyzb.shccmg.com）', supportedTypes: ['HTML'] },
];

/** 当前输入口径对应的载荷类型（FILE 按扩展名归一：pdf→PDF / json→JSON / 其余→HTML） */
const payloadKind = computed<'HTML' | 'JSON' | 'PDF'>(() => {
  if (inputType.value === 'JSON') return 'JSON';
  if (inputType.value !== 'FILE') return 'HTML';
  const n = (file.value?.name ?? '').toLowerCase();
  if (n.endsWith('.pdf')) return 'PDF';
  if (n.endsWith('.json')) return 'JSON';
  return 'HTML';
});
/** 展示用解析器清单：接口优先（含站点新增），为空则内置兜底 —— 下拉永不为空，杜绝"退回手填 code" */
const parserCatalog = computed<ParserChoice[]>(() => {
  const list: ParserChoice[] = [];
  for (const p of parserOptions.value) {
    if (!p?.code) continue;
    list.push({ code: p.code, name: p.name || p.code, supportedTypes: p.supportedTypes ?? [] });
  }
  return list.length > 0 ? list : PARSER_FALLBACK;
});
/** 是否在用内置兜底清单（用于给出"清单接口不可用"的提示） */
const usingFallbackCatalog = computed(() => parserCatalog.value === PARSER_FALLBACK);
const parserCodeOptions = computed(() =>
  parserCatalog.value.map((p) => ({
    label: p.code + ' · ' + p.name,
    value: p.code,
    /* 不支持当前口径的解析器置灰（如 PDF 专用解析器在 HTML 口径下不可选） */
    disabled: p.supportedTypes.length > 0 && !p.supportedTypes.includes(payloadKind.value),
  })),
);
/** 所选解析器是否支持当前口径（提交前软校验，省一次必然失败的后端调用） */
function parserMatchesPayload(): boolean {
  const hit = parserCatalog.value.find((p) => p.code === parserCode.value);
  if (!hit || hit.supportedTypes.length === 0) return true;
  return hit.supportedTypes.includes(payloadKind.value);
}

/** 按输入口径建议缺省解析器 */
function suggestParser(): string {
  if (inputType.value === 'JSON') return 'JSON_GENERIC';
  if (inputType.value === 'FILE') {
    const n = (file.value?.name ?? '').toLowerCase();
    if (n.endsWith('.pdf')) return 'PDF_TEXT_GENERIC';
    if (n.endsWith('.json')) return 'JSON_GENERIC';
    return 'HTML_GENERIC';
  }
  return 'HTML_GENERIC';
}
watch([inputType, () => file.value?.name], () => {
  parserCode.value = suggestParser();
});

onMounted(async () => {
  try {
    parserOptions.value = (await fetchCrawlParserList()) || [];
  } catch {
    /* 清单接口不可用：置空即走内置兜底清单（见 parserCatalog），下拉仍可选项，绝不退回手填 code */
    parserOptions.value = [];
  }
  parserCode.value = suggestParser();
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (running.value) return;
    if (!parserCode.value) {
      message.warning('请选择解析器');
      return;
    }
    if (!parserMatchesPayload()) {
      const hit = parserCatalog.value.find((p) => p.code === parserCode.value);
      message.warning('所选解析器不支持当前输入口径（支持：' + (hit?.supportedTypes.join('/') ?? '—') + '），请改选或调整输入口径');
      return;
    }
    if (inputType.value === 'FILE') {
      if (!file.value) {
        message.warning('请先选择要上传的文件（.html/.htm/.json/.pdf）');
        return;
      }
    } else if (!content.value.trim()) {
      message.warning('请粘贴源代码/原文内容');
      return;
    }
    running.value = true;
    modalApi.lock();
    try {
      // 契约映射（见文件头四要素注释）：payloadType=HTML|JSON|FILE，content 仅文本口径下发，
      // fileName+file 仅 FILE 口径下发；url 用于附件相对链接绝对化与站点上下文提示。
      const resp = await crawlParserTest({
        parserCode: parserCode.value,
        payloadType: inputType.value === 'FILE' ? 'FILE' : inputType.value,
        content: inputType.value === 'FILE' ? undefined : content.value,
        fileName: inputType.value === 'FILE' ? file.value!.name : undefined,
        file: inputType.value === 'FILE' ? file.value : undefined,
        url: url.value.trim() || undefined,
      });
      const ok = resp && (resp.status === 'SUCCESS' || resp.status === 'PARTIAL');
      message.success(ok ? '解析完成，已生成测试任务条' : '解析未成功（生成失败任务条）');
      modalApi.close();
      emit('parsed', resp, { inputType: inputType.value, fileName: file.value?.name });
    } catch (e) {
      message.error(e instanceof Error ? e.message : String(e));
    } finally {
      running.value = false;
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      content.value = '';
      url.value = '';
      sourceCode.value = undefined;
      file.value = undefined;
      inputType.value = 'HTML';
      parserCode.value = suggestParser();
    }
  },
});
</script>

<template>
  <Modal title="手动录入（生成测试任务）" class="w-2/3 bid-ind">
    <div class="mb-2 text-xs text-muted-foreground">
      粘贴 HTML/JSON 源代码，或上传文件（.html/.htm/.json/.pdf）。提交后走真实下载→解析管线，
      结果即刻生成一条“测试任务”任务条：源（原文）· 提取内容 · 渲染内容 · 过程。
    </div>
    <div class="mb-3 flex items-center gap-2">
      <span class="text-sm text-muted-foreground">输入口径：</span>
      <Radio.Group v-model:value="inputType" :options="[{ label: 'HTML 源代码', value: 'HTML' }, { label: 'JSON 原文', value: 'JSON' }, { label: '上传文件', value: 'FILE' }]" option-type="button" />
    </div>

    <div v-if="inputType !== 'FILE'" class="mb-2">
      <div class="mb-1 text-xs font-medium text-muted-foreground">源代码 / 原文内容</div>
      <Input.TextArea v-model:value="content" :rows="8" placeholder="将浏览器“查看源代码”全选复制粘贴于此，或粘贴页面接口返回的 JSON 原文…" />
    </div>

    <div v-else class="mb-2">
      <div class="mb-1 text-xs font-medium text-muted-foreground">上传文件（PDF/HTML/JSON）</div>
      <Upload.Dragger :max-count="1" accept=".html,.htm,.json,.pdf" :before-upload="(f: File) => { file = f; return false; }" :file-list="[]">
        <p class="ant-upload-text">{{ file ? `${file.name }（${ file.size } 字节）` : '点击或拖拽文件到此处' }}</p>
        <p class="ant-upload-hint">PDF 需含文字层（扫描件将解析失败并如实标记）；HTML/JSON 文件同文本口径。</p>
      </Upload.Dragger>
    </div>

    <div class="grid grid-cols-2 gap-3">
      <div>
        <div class="mb-1 text-xs font-medium text-muted-foreground">解析器</div>
        <Select
          v-model:value="parserCode"
          :options="parserCodeOptions"
          option-filter-prop="label"
          placeholder="选择解析器（按口径自动建议，可搜索）"
          show-search
          allow-clear
          style="width: 100%"
        />
        <div v-if="usingFallbackCatalog" class="mt-1 text-[11px] leading-4 text-amber-600 dark:text-amber-400">
          解析器清单接口不可用，当前为内置清单（{{ PARSER_FALLBACK.length }} 个）；如站点已新增解析器，请检查账号权限 bidding:crawl-source:query。
        </div>
        <div v-else class="mt-1 text-[11px] leading-4 text-muted-foreground">
          共 {{ parserCatalog.length }} 个解析器；与当前口径不匹配的项已置灰。
        </div>
      </div>
      <div>
        <div class="mb-1 text-xs font-medium text-muted-foreground">归属信息源（可选，MANUAL 直测）</div>
        <Select v-model:value="sourceCode" :options="props.sourceOptions || []" placeholder="不选=手动直测，不入源" allow-clear style="width: 100%" />
      </div>
      <div class="col-span-2">
        <div class="mb-1 text-xs font-medium text-muted-foreground">来源页 URL（可选，用于附件相对链接绝对化与站点上下文提示）</div>
        <Input v-model:value="url" placeholder="https://…" allow-clear />
      </div>
    </div>
  </Modal>
</template>
