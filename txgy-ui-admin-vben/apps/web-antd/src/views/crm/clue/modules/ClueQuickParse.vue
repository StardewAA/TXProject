<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { message } from 'ant-design-vue';

import {
  checkDuplicate,
  fetchWebPage,
  getCustomerPage,
  type CrmCustomerApi,
} from '#/api/crm/customer';
import { getBusinessPage } from '#/api/crm/business';
import { getBusinessStatusTypeSimpleList } from '#/api/crm/business/status';
import { getContractSimpleList } from '#/api/crm/contract';
import { getProductCategoryList } from '#/api/crm/product/category';
import { getSimpleUserList } from '#/api/system/user';

import {
  parseClue,
  parseContact,
  parseContract,
  parseBusiness,
  parseReceivable,
  parseProduct,
  parseReceivablePlan,
} from '#/utils/crm/clue-parse';
import {
  getAiClueConfig,
  saveAiClueConfig,
  type AiClueConfig,
} from '#/utils/crm/ai-config';
import {
  checkContractFile,
  extractPdfContent,
  formatFileSize,
  MAX_FILE_COUNT,
  type ContractFileItem,
} from '#/utils/crm/contract-file';

const emit = defineEmits<{
  apply: [payload: { fields: any }];
}>();

// mode: clue=线索/通用；contact=联系人（姓名取人名，公司名放 customerName）；
//       contract=合同（提取合同名、客户、商机、日期、产品与金额）
const props = withDefaults(
  defineProps<{
    mode?:
      | 'clue'
      | 'contact'
      | 'contract'
      | 'business'
      | 'receivable'
      | 'product'
      | 'plan';
  }>(),
  { mode: 'clue' },
);

type ActiveTab = 'file' | 'image' | 'text' | 'web';

/** 合同文件识别入口仅对合同表单开放，其它模块的界面与交互保持不变 */
const isContractMode = computed(() => props.mode === 'contract');
/** 网页链接识别入口：线索 / 客户 / 客户公海 三个入口都复用 clue 模式 */
const isWebTabVisible = computed(() => props.mode === 'clue');
/** 单次提交给视觉模型的图片上限（含 PDF 渲染出的页面），防止请求体过大 */
const MAX_OCR_IMAGES = 6;

const router = useRouter();

const collapsed = ref(false);
const activeTab = ref<ActiveTab>('text');
const content = ref('');
const loading = ref(false);
/** 企业网页链接（「网页链接」识别用） */
const webUrl = ref('');
/** 查重命中的系统已有记录 */
const duplicateItems = ref<CrmCustomerApi.DuplicateCheckItem[]>([]);
/** 查重结果弹窗是否可见 */
const duplicateVisible = ref(false);
/** 查重弹窗中待合并 / 待回填的表单字段 */
let pendingFields: any = null;
const images = ref<{ file: File; url: string }[]>([]);
/** 待识别的合同文件（PDF / 图片） */
const contractFiles = ref<ContractFileItem[]>([]);
/** 文件解析进度（PDF 逐页读取/渲染时展示） */
const fileProgress = ref('');

// AI 识别配置（界面内可填，存浏览器本地）
const aiCfg = ref<AiClueConfig>(getAiClueConfig());
const showAiCfg = ref(false);
const aiReady = computed(
  () => aiCfg.value.enabled && !!aiCfg.value.baseUrl && !!aiCfg.value.model,
);
function saveAiCfg() {
  aiCfg.value = saveAiClueConfig({
    baseUrl: aiCfg.value.baseUrl.trim(),
    model: aiCfg.value.model.trim(),
    apiKey: aiCfg.value.apiKey.trim(),
  });
  message.success(
    aiReady.value
      ? 'AI 识别配置已保存，现在可以识别图片文字了'
      : 'AI 识别配置已保存，但 baseUrl 与 model 仍需填写才能启用识别',
  );
}

/** 将文件转为 data URL（base64），用于送给视觉大模型做 OCR 识别 */
function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * 上传图片可能很大，直接转 base64 会导致请求体过大、浏览器超时失败。
 * 这里用 canvas 把图片缩放到最大边 maxDim 并压成 jpeg，显著降低体积，
 * 既保证视觉模型能看清文字，又避免“上传图片点识别没反应”的问题。
 */
async function compressImage(
  file: File,
  maxDim = 1280,
  quality = 0.82,
): Promise<string> {
  const original = await fileToDataUrl(file);
  const canCompress =
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    file.type === 'image/webp';
  if (!canCompress) {
    return original;
  }
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const im = new Image();
      im.onload = () => resolve(im);
      im.onerror = reject;
      im.src = original;
    });
    let { width, height } = img;
    if (width > maxDim || height > maxDim) {
      const scale = maxDim / Math.max(width, height);
      width = Math.round(width * scale);
      height = Math.round(height * scale);
    }
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return original;
    }
    ctx.drawImage(img, 0, 0, width, height);
    return canvas.toDataURL('image/jpeg', quality);
  } catch {
    return original;
  }
}

function addImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    return;
  }
  images.value.push({ file, url: URL.createObjectURL(file) });
}

/**
 * 加入待识别合同文件。
 * 逐个校验（空文件 / 超大 / 不支持的格式 / 重复），校验失败直接提示并跳过；
 * 返回是否至少成功加入一个文件，供粘贴、拖拽场景判断。
 */
function addContractFiles(list: File[]): boolean {
  let added = false;
  for (const file of list) {
    if (contractFiles.value.length >= MAX_FILE_COUNT) {
      message.warning(`一次最多识别 ${MAX_FILE_COUNT} 个文件，多余的已忽略`);
      break;
    }
    const { ok, kind, message: errMsg } = checkContractFile(file);
    if (!ok) {
      message.error(errMsg ?? '文件校验未通过，请更换文件');
      continue;
    }
    const name = file.name || '未命名文件';
    if (
      contractFiles.value.some((f) => f.name === name && f.size === file.size)
    ) {
      message.warning(`「${name}」已在待识别列表中，无需重复添加`);
      continue;
    }
    contractFiles.value.push({
      uid: `cf-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name,
      size: file.size,
      kind: kind!,
      file,
      url: kind === 'image' ? URL.createObjectURL(file) : undefined,
    });
    added = true;
  }
  return added;
}

function onFilePickContract(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.length) {
    addContractFiles(Array.from(input.files));
  }
  input.value = '';
}

function removeContractFile(index: number) {
  const [removed] = contractFiles.value.splice(index, 1);
  if (removed?.url) {
    URL.revokeObjectURL(removed.url);
  }
}

function clearContractFiles() {
  contractFiles.value.forEach((f) => f.url && URL.revokeObjectURL(f.url));
  contractFiles.value = [];
}

/** 文件识别模式下：从剪贴板收集 PDF / 图片文件 */
function collectPastedFiles(dt: DataTransfer): boolean {
  let added = false;
  if (dt.files?.length) {
    added = addContractFiles(Array.from(dt.files));
  }
  if (!added) {
    // 部分浏览器（截图粘贴）只在 items 中暴露图片，兜底再取一次
    for (const it of Array.from(dt.items ?? [])) {
      if (it.type.startsWith('image/')) {
        const f = it.getAsFile();
        if (f && addContractFiles([f])) {
          added = true;
        }
      }
    }
  }
  return added;
}

function onPaste(e: ClipboardEvent) {
  const dt = e.clipboardData;
  if (!dt) {
    return;
  }
  // 合同文件识别模式：粘贴内容作为合同文件处理，不切换标签
  if (activeTab.value === 'file') {
    collectPastedFiles(dt);
    return;
  }
  // 网页链接识别模式：粘贴内容作为网址填入，且不切换标签
  // （否则会被下面的通用逻辑塞进「粘贴文本」并自动切走标签）
  if (activeTab.value === 'web') {
    const text = dt.getData('text/plain')?.trim();
    if (text) {
      webUrl.value = text;
    }
    return;
  }
  let handled = false;
  let hasImage = false;
  let hasText = false;
  for (const it of dt.items) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile();
      if (f) {
        addImageFile(f);
        hasImage = true;
        handled = true;
      }
    } else if (it.kind === 'string' && it.type === 'text/plain') {
      const text = dt.getData('text/plain');
      if (text) {
        content.value += (content.value ? '\n' : '') + text;
        hasText = true;
        handled = true;
      }
    }
  }
  if (handled) {
    e.preventDefault();
    // 根据粘贴内容自动切换对应方案：纯图片→图片 OCR，纯文本→粘贴文本
    if (hasImage && !hasText) {
      activeTab.value = 'image';
    } else if (hasText && !hasImage) {
      activeTab.value = 'text';
      // 粘贴的是单个网址时，提醒用户可以直接用「网页链接识别」抓取工商信息
      if (
        isWebTabVisible.value &&
        /^https?:\/\/\S+$/i.test(content.value.trim())
      ) {
        message.info(
          '检测到网址：可切换到「网页链接识别」，由系统自动抓取该网页上的公司信息',
        );
      }
    }
  }
}

function onFilePick(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) {
    Array.from(input.files).forEach(addImageFile);
  }
  input.value = '';
}

function onDrop(e: DragEvent) {
  if (e.dataTransfer?.files?.length) {
    const files = Array.from(e.dataTransfer.files);
    // 合同文件识别模式：拖入的 PDF / 图片都作为合同文件处理
    if (activeTab.value === 'file') {
      addContractFiles(files);
      return;
    }
    if (files.some((f) => f.type.startsWith('image/'))) {
      activeTab.value = 'image';
      files.forEach(addImageFile);
    }
  }
}

function removeImage(i: number) {
  const [removed] = images.value.splice(i, 1);
  if (removed) {
    URL.revokeObjectURL(removed.url);
  }
}

function clearAll() {
  images.value.forEach((i) => URL.revokeObjectURL(i.url));
  images.value = [];
  clearContractFiles();
  content.value = '';
  webUrl.value = '';
  fileProgress.value = '';
  // 文件识别模式下清空后保留当前标签，方便继续上传下一份合同；文本/图片模式行为不变
  if (activeTab.value !== 'file') {
    activeTab.value = 'text';
  }
}

/**
 * 逐个解析待识别合同文件：
 * - PDF：有文字层则取文本；扫描件则渲染成页面图片走 OCR；
 * - 图片：压缩后交给视觉大模型 OCR。
 * 单个文件失败不会中断其它文件，失败与降级信息统一汇总到 warnings 提示用户。
 */
async function collectContractFiles(
  warnings: string[],
): Promise<{ images: string[]; text: string }> {
  const texts: string[] = [];
  let images: string[] = [];
  for (const item of contractFiles.value) {
    try {
      if (item.kind === 'pdf') {
        fileProgress.value = `正在解析「${item.name}」…`;
        const r = await extractPdfContent(item.file, (msg) => {
          fileProgress.value = `「${item.name}」${msg}`;
        });
        if (r.truncated) {
          warnings.push(
            `「${item.name}」共 ${r.pageCount} 页，仅识别前 ${r.usedPages} 页`,
          );
        }
        if (r.mode === 'text') {
          // 矢量 PDF 直接用文本层，比 OCR 更准且更快
          texts.push(`【文件：${item.name}】\n${r.text}`);
        } else {
          warnings.push(`「${item.name}」未检测到文字，已按扫描件转图片识别`);
          images.push(...r.images);
        }
      } else {
        fileProgress.value = `正在处理图片「${item.name}」…`;
        images.push(await compressImage(item.file));
      }
    } catch (e: any) {
      warnings.push(`「${item.name}」识别失败：${e?.message ?? '未知错误'}`);
    }
  }
  if (images.length > MAX_OCR_IMAGES) {
    warnings.push(
      `图片数量较多，仅识别前 ${MAX_OCR_IMAGES} 张（共 ${images.length} 张）`,
    );
    images = images.slice(0, MAX_OCR_IMAGES);
  }
  fileProgress.value = '';
  return { images, text: texts.join('\n\n') };
}

async function handleRecognize() {
  const isImageMode = activeTab.value === 'image';
  const isFileMode = activeTab.value === 'file';
  let isWebMode = activeTab.value === 'web';
  // 兜底：若用户把网址粘进了「粘贴文本」，自动改成网页抓取，避免被当成普通文本解析
  if (
    !isWebMode &&
    !isImageMode &&
    !isFileMode &&
    isWebTabVisible.value &&
    /^https?:\/\/\S+$/i.test(content.value.trim())
  ) {
    webUrl.value = content.value.trim();
    activeTab.value = 'web';
    isWebMode = true;
  }
  if (!isImageMode && !isFileMode && !isWebMode && !content.value.trim()) {
    message.warning('请先粘贴文本');
    return;
  }
  if (isImageMode && images.value.length === 0) {
    message.warning('请先上传或粘贴图片');
    return;
  }
  if (isFileMode && contractFiles.value.length === 0) {
    message.warning('请先上传合同文件（PDF / 图片）');
    return;
  }
  if (isWebMode && !webUrl.value.trim()) {
    message.warning('请先粘贴企业网页链接');
    return;
  }
  // 网页正文必须靠 AI 理解，未配置 AI 时规则解析会把导航文字当成公司名，直接拦住
  if (isWebMode && !aiReady.value) {
    message.warning(
      '网页内容需要 AI 解析：请先点右上角「AI 配置」填写 Base URL 与模型',
    );
    return;
  }

  loading.value = true;
  fileProgress.value = '';
  try {
    let text = isImageMode || isFileMode || isWebMode ? '' : content.value;
    let imageDataUrls: string[] = [];
    // 文件相关的异常 / 降级提示
    const fileWarnings: string[] = [];
    if (isImageMode && images.value.length) {
      try {
        imageDataUrls = await Promise.all(
          images.value.map((i) => compressImage(i.file)),
        );
      } catch {
        // 转换失败不影响文本识别
      }
    }
    if (isFileMode) {
      const collected = await collectContractFiles(fileWarnings);
      text = collected.text;
      imageDataUrls = collected.images;
      // 全部文件都读不出内容时，明确提示而不是回填空表单
      if (!text.trim() && imageDataUrls.length === 0) {
        message.error(
          '未能从所选文件中读取到内容，请确认上传的是完整合同 PDF 或清晰图片',
        );
        return;
      }
    }
    // 网页链接：由后端代理抓取正文（绕开浏览器跨域限制），再交给大模型提取工商信息
    if (isWebMode) {
      try {
        const page: any = await fetchWebPage(webUrl.value.trim());
        const body = String(page?.content ?? '').trim();
        if (!body) {
          message.error(
            '未能从该网页读取到内容（部分网页由 JS 动态渲染），请改用「粘贴文本」方式',
          );
          return;
        }
        // 网页场景专用引导：告诉模型要找的是「页面主体公司」，
        // 否则按默认 prompt（提取客户资料）会认为页面里没有客户公司名而全部返回 null
        text = [
          '以下内容是从企业官方网站抓取的页面文本。该页面的主体公司就是本次要录入的客户/线索，',
          '请提取该公司的名称、联系电话、手机、地址、邮箱、联系人等信息。',
          '请忽略页面上的导航菜单、栏目名称、合作方链接、其他公司名称、广告与版权信息。',
          `网页标题：${page?.title ?? ''}`,
          '网页正文：',
          body,
        ].join('\n');
        if (body.replace(/\s/g, '').length < 100) {
          message.warning(
            '该网页正文内容较少（可能是纯前端渲染的页面），识别结果可能不完整，建议改用「粘贴文本」',
          );
        }
      } catch (e: any) {
        message.error(
          `网页抓取失败：${e?.message ?? '请确认链接能正常打开，或改用手动粘贴'}`,
        );
        return;
      }
    }

    const aiReadyFlag = aiReady.value;
    // 需要「未配置 AI」提示的场景：图片识别，或文件识别产生了待 OCR 图片
    const needAi =
      isImageMode || isWebMode || (isFileMode && imageDataUrls.length > 0);
    const isContract = props.mode === 'contract';
    // 按当前模块选用专属解析 schema
    const parsed: any =
      isContract
        ? await parseContract(text, imageDataUrls)
        : props.mode === 'contact'
          ? await parseContact(text, imageDataUrls)
          : props.mode === 'business'
            ? await parseBusiness(text, imageDataUrls)
            : props.mode === 'receivable'
              ? await parseReceivable(text, imageDataUrls)
              : props.mode === 'product'
                ? await parseProduct(text, imageDataUrls)
                : props.mode === 'plan'
                  ? await parseReceivablePlan(text, imageDataUrls)
                  : await parseClue(text, imageDataUrls);
    const fields: any = parsed.fields ?? {};
    // parseClue / parseContact 用 aiError 命名，其余解析函数用 error，这里统一兼容
    const aiError = parsed.aiError ?? parsed.error;

    // 客户匹配：商机/回款/合同/回款计划取 customerName，线索取 name；产品不需要
    if (props.mode !== 'product') {
      try {
        const name = String(
          (props.mode === 'clue' ? fields?.name : fields?.customerName) ?? '',
        ).trim();
        if (name) {
          const page: any = await getCustomerPage({
            pageNo: 1,
            pageSize: 5,
            name,
            sceneType: 3,
          } as any);
          const hit = page?.list?.[0];
          if (hit?.id) {
            fields.customerId = hit.id;
          }
        }
      } catch {
        // 匹配失败不阻塞回填
      }
    }

    // 合同兜底：若 AI 未提取到负责人，从原文正则提取“负责人：XXX”
    if (isContract && !fields?.ownerUserName) {
      const m = String(text).match(
        /(?:负责人|销售员|销售业务员|业务员|销售)\s*[:：]?\s*([\u4e00-\u9fa5·A-Za-z]{2,4})/,
      );
      if (m?.[1]) {
        fields.ownerUserName = m[1];
      }
    }

    // 按名称匹配系统用户：负责人 ownerUserName -> ownerUserId（商机/回款/合同）
    // 用 getSimpleUserList（与负责人下拉同源）避免分页/数据权限过滤导致匹配不到
    try {
      const ownerName = String(fields?.ownerUserName ?? '').trim();
      if (ownerName) {
        const userList: any[] = await getSimpleUserList();
        const hit =
          userList.find((u: any) => u?.nickname === ownerName) ??
          userList.find((u: any) =>
            String(u?.nickname ?? '').includes(ownerName),
          );
        if (hit?.id) fields.ownerUserId = hit.id;
      }
    } catch {}

    // 合同：再按名称匹配关联商机
    if (isContract) {
      try {
        const bn = String(fields?.businessName ?? '').trim();
        if (bn) {
          const bp: any = await getBusinessPage({
            pageNo: 1,
            pageSize: 5,
            name: bn,
            sceneType: 3,
          } as any);
          const bhit = bp?.list?.[0];
          if (bhit?.id) {
            fields.businessId = bhit.id;
          }
        }
      } catch {
        // 匹配失败不阻塞回填
      }
      // 公司签约人 signUserName -> signUserId（同样用简单用户列表匹配）
      try {
        const signName = String(fields?.signUserName ?? '').trim();
        if (signName) {
          const userList: any[] = await getSimpleUserList();
          const hit =
            userList.find((u: any) => u?.nickname === signName) ??
            userList.find((u: any) =>
              String(u?.nickname ?? '').includes(signName),
            );
          if (hit?.id) fields.signUserId = hit.id;
        }
      } catch {}
    }

    // 产品：匹配产品类型(树)、负责人；上架状态/单位已在 parse 阶段字典映射
    if (props.mode === 'product') {
      try {
        const catName = String(fields?.categoryName ?? '').trim();
        if (catName) {
          const tree: any[] = await getProductCategoryList();
          const flat: any[] = [];
          const walk = (arr: any[]) => arr.forEach((n) => { flat.push(n); n.children?.length && walk(n.children); });
          walk(tree);
          const hit = flat.find((n: any) => String(n?.name ?? '').includes(catName))
            ?? flat.find((n: any) => catName.includes(String(n?.name ?? '')));
          if (hit?.id) fields.categoryId = hit.id;
        }
      } catch {}
    }

    // 回款计划：匹配合同（同 receivable 处理）
    if (props.mode === 'plan') {
      try {
        const cid = fields?.customerId;
        const cn = String(
          (fields?.contractNo ?? '') || (fields?.contractName ?? ''),
        ).trim();
        if (cid && cn) {
          const cp: any = await getContractSimpleList(cid);
          const hit = cp?.find(
            (c: any) =>
              (fields?.contractNo && c?.no === fields.contractNo) ||
              (fields?.contractName && c?.name?.includes(fields.contractName)),
          );
          if (hit?.id) fields.contractId = hit.id;
        }
      } catch {}
    }

    // 商机：匹配状态组
    if (props.mode === 'business') {
      try {
        const statusName = String(fields?.businessStatusTypeName ?? '').trim();
        if (statusName) {
          const stList: any[] = await getBusinessStatusTypeSimpleList();
          const hit = stList.find((x: any) =>
            String(x?.name ?? '').includes(statusName),
          );
          if (hit?.id) fields.statusTypeId = hit.id;
        }
      } catch {}
    }

    // 回款：匹配合同
    if (props.mode === 'receivable') {
      try {
        const cid = fields?.customerId;
        const cn = String(
          (fields?.contractNo ?? '') || (fields?.contractName ?? ''),
        ).trim();
        if (cid && cn) {
          const cp: any = await getContractSimpleList(cid);
          const hit = cp?.find(
            (c: any) =>
              (fields?.contractNo && c?.no === fields.contractNo) ||
              (fields?.contractName && c?.name?.includes(fields.contractName)),
          );
          if (hit?.id) fields.contractId = hit.id;
        }
      } catch {}
    }

    // 图片仅用于识别信息，不作为附件保存
    emit('apply', { fields });

    // 智能查重：线索 / 客户 / 联系人场景下，按手机号、名称比对系统已有数据，避免重复新建脏数据
    if (props.mode === 'clue') {
      try {
        const mobile = String(fields?.mobile ?? '').trim();
        const name = String(fields?.name ?? '').trim();
        if (mobile || name) {
          const res: any = await checkDuplicate({ mobile, name });
          if (res?.duplicated && res.items?.length) {
            duplicateItems.value = res.items;
            pendingFields = fields;
            duplicateVisible.value = true;
          }
        }
      } catch {
        // 查重失败不阻塞回填
      }
    }

    // 文件相关的异常 / 降级提示优先展示，让用户明确知道哪些文件未被完整识别
    if (fileWarnings.length > 0) {
      message.warning(
        `识别完成，但存在以下情况：${fileWarnings.slice(0, 2).join('；')}`,
      );
      if (fileWarnings.length > 2) {
        message.info(`另有 ${fileWarnings.length - 2} 条文件提示未展示`);
      }
    }
    if (aiError) {
      // AI 真正调用失败（网络 / CORS / 接口报错 / 超大图片超时等），明确告知用户
      message.warning(
        `AI 识别失败：${aiError}。已按规则识别文本；如需识别图片请检查网络或点右上角“AI 配置”核对 Base URL 与模型。`,
      );
    } else if (!aiReadyFlag && needAi) {
      message.info(
        '当前未配置 AI，图片/扫描件内容无法被识别。点右上角“AI 配置”填写 Base URL 与视觉模型后，即可自动识别图片文字。',
      );
    } else if (fileWarnings.length === 0) {
      message.success('识别完成，已自动填充表单字段，请核对后提交');
    }
  } finally {
    loading.value = false;
  }
}

/** 合并查重命中的记录信息（仅补齐表单空白字段，不覆盖已填内容） */
function mergeDuplicate() {
  const fields: any = pendingFields ?? {};
  const first = duplicateItems.value[0];
  if (first) {
    if (!fields.name && first.name) {
      fields.name = first.name;
    }
    if (!fields.mobile && first.mobile) {
      fields.mobile = first.mobile;
    }
    // 命中的是客户时，关联客户编号，便于后续挂到已有客户下
    if (first.bizType === 'customer' && first.id && !fields.customerId) {
      fields.customerId = first.id;
    }
  }
  emit('apply', { fields });
  duplicateVisible.value = false;
  message.success('已合并已有记录的信息，请核对后保存');
}

/** 忽略查重结果，按识别结果继续新建 */
function ignoreDuplicate() {
  duplicateVisible.value = false;
}

/** 打开查重命中的记录详情（新标签页，避免丢失当前表单已填内容） */
function openDuplicate(item: CrmCustomerApi.DuplicateCheckItem) {
  const routeNameMap: Record<string, string> = {
    customer: 'CrmCustomerDetail',
    clue: 'CrmClueDetail',
  };
  const routeName = routeNameMap[item.bizType];
  if (!routeName) {
    message.info(
      `${item.bizTypeName}「${item.name ?? ''}」请在对应列表页搜索查看`,
    );
    return;
  }
  const href = router.resolve({ name: routeName, params: { id: item.id } })
    .href;
  window.open(href, '_blank');
}
</script>

<template>
  <div
    class="mb-4 rounded-md border border-dashed border-primary/40 bg-primary/5 p-3"
    @paste.prevent="onPaste"
    @dragover.prevent
    @drop.prevent="onDrop"
  >
    <!-- 头部：与截图保持一致 -->
    <div class="mb-3 flex items-start justify-between gap-3">
      <div class="flex items-start gap-3">
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-primary text-lg text-primary-foreground"
        >
          ✨
        </span>
        <div>
          <div class="text-base font-medium leading-tight">一键识别</div>
          <p class="mt-0.5 text-xs text-muted-foreground">
            粘贴微信聊天/名片/邮件文本自动填入；粘贴或拖入名片照片可 OCR 识别图中文字；也可粘贴企业官网链接自动抓取工商信息
          </p>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-1">
        <a-button type="link" size="small" @click="showAiCfg = !showAiCfg">
          AI 配置
        </a-button>
        <a-button type="link" size="small" @click="collapsed = !collapsed">
          {{ collapsed ? '展开' : '收起' }}
        </a-button>
      </div>
    </div>

    <div
      v-if="showAiCfg"
      class="mb-3 rounded border border-border bg-muted/40 p-2"
    >
      <div class="grid grid-cols-1 gap-2">
        <div>
          <label class="text-xs text-muted-foreground">Base URL（以 /v1 结尾）</label>
          <a-input
            v-model:value="aiCfg.baseUrl"
            placeholder="例如 https://api.openai.com/v1 或你的代理地址"
            size="small"
          />
        </div>
        <div>
          <label class="text-xs text-muted-foreground">视觉模型名</label>
          <a-input
            v-model:value="aiCfg.model"
            placeholder="例如 gpt-4o / gpt-4o-mini"
            size="small"
          />
        </div>
        <div>
          <label class="text-xs text-muted-foreground">API Key</label>
          <a-input
            v-model:value="aiCfg.apiKey"
            placeholder="sk-..."
            size="small"
            type="password"
          />
        </div>
      </div>
      <div class="mt-2 flex items-center gap-2">
        <a-button type="primary" size="small" @click="saveAiCfg">保存配置</a-button>
        <span class="text-xs text-muted-foreground">
          保存后本浏览器生效；填好 Base URL 与模型即可识别图片文字
        </span>
      </div>
    </div>

    <template v-if="!collapsed">
      <!-- 两种方案切换 -->
      <div class="mb-3 flex items-center gap-2">
        <a-button
          :type="activeTab === 'text' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'text'"
        >
          粘贴文本
        </a-button>
        <a-button
          :type="activeTab === 'image' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'image'"
        >
          图片 OCR 识别
        </a-button>
        <a-button
          v-if="isContractMode"
          :type="activeTab === 'file' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'file'"
        >
          合同文件识别
        </a-button>
        <a-button
          v-if="isWebTabVisible"
          :type="activeTab === 'web' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'web'"
        >
          网页链接识别
        </a-button>
        <a-tag v-if="aiReady" color="green" class="ml-auto">AI 已启用</a-tag>
        <a-tag v-else color="orange" class="ml-auto">AI 未配置</a-tag>
      </div>

      <!-- 方案一：粘贴文本 -->
      <template v-if="activeTab === 'text'">
        <a-textarea
          v-model:value="content"
          :rows="4"
          placeholder="例:王经理 13800138000 中石化四公司,北京朝阳区xx路 123号,邮箱 wang@example.com,QQ 1234567"
        />
      </template>

      <!-- 方案二：图片 OCR 识别 -->
      <template v-else-if="activeTab === 'image'">
        <div
          class="rounded border border-dashed border-border bg-muted/20 p-4"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <div class="flex flex-wrap items-center justify-center gap-2">
            <div v-for="(img, i) in images" :key="i" class="relative">
              <img
                :src="img.url"
                class="h-16 w-16 rounded border object-cover"
                alt="预览"
              />
              <span
                class="absolute -right-1.5 -top-1.5 flex h-4 w-4 cursor-pointer items-center justify-center rounded-full bg-red-500 text-xs text-white"
                @click="removeImage(i)"
              >
                ×
              </span>
            </div>
            <label
              class="flex h-16 w-16 cursor-pointer items-center justify-center rounded border border-dashed text-xs text-muted-foreground hover:bg-muted/40"
            >
              + 图片
              <input
                type="file"
                accept="image/*"
                class="hidden"
                @change="onFilePick"
              />
            </label>
          </div>
          <p class="mt-2 text-center text-xs text-muted-foreground">
            点击“+ 图片”上传，或拖拽 / 粘贴图片到此处
          </p>
          <p class="mt-1 text-center text-xs text-muted-foreground">
            {{ aiReady ? 'AI 已配置：上传后点击“智能解析并填充”即可识别图中文字。' : '当前未配置 AI，图片无法被识别；请先点右上角“AI 配置”。' }}
          </p>
        </div>
      </template>

      <!-- 方案三：合同文件识别（仅合同表单） -->
      <template v-else-if="activeTab === 'file'">
        <div
          class="rounded border border-dashed border-border bg-muted/20 p-4"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <!-- 待识别文件列表 -->
          <div v-if="contractFiles.length > 0" class="mb-3 space-y-2">
            <div
              v-for="(f, i) in contractFiles"
              :key="f.uid"
              class="flex items-center gap-2 rounded border border-border bg-background px-2 py-1.5"
            >
              <img
                v-if="f.url"
                :src="f.url"
                class="h-8 w-8 rounded border object-cover"
                alt="预览"
              />
              <span
                v-else
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-red-50 text-[10px] font-medium text-red-500"
              >
                PDF
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-xs" :title="f.name">{{ f.name }}</div>
                <div class="text-[11px] text-muted-foreground">
                  {{ formatFileSize(f.size) }}
                </div>
              </div>
              <span
                class="cursor-pointer text-muted-foreground hover:text-red-500"
                @click="removeContractFile(i)"
              >
                ×
              </span>
            </div>
          </div>

          <div class="flex flex-wrap items-center justify-center gap-2">
            <label
              class="flex h-16 w-20 cursor-pointer flex-col items-center justify-center rounded border border-dashed text-xs text-muted-foreground hover:bg-muted/40"
            >
              + 合同文件
              <input
                type="file"
                accept="application/pdf,image/*"
                multiple
                class="hidden"
                @change="onFilePickContract"
              />
            </label>
          </div>
          <p class="mt-2 text-center text-xs text-muted-foreground">
            支持 PDF 合同与合同图片，可点击选择、拖拽或直接粘贴；单文件 ≤ 20MB，最多 {{ MAX_FILE_COUNT }} 个
          </p>
          <p class="mt-1 text-center text-xs text-muted-foreground">
            {{ aiReady ? 'PDF 会自动读取文字，扫描件转图片 OCR 识别；上传后点击“智能解析并填充”。' : '当前未配置 AI，扫描件/图片无法识别；请先点右上角“AI 配置”。' }}
          </p>
          <p
            v-if="fileProgress"
            class="mt-1 text-center text-xs text-primary"
          >
            {{ fileProgress }}
          </p>
        </div>
      </template>

      <!-- 方案四：网页链接识别（线索 / 客户 / 客户公海） -->
      <template v-else>
        <a-textarea
          v-model:value="webUrl"
          :rows="2"
          placeholder="粘贴企业官网「关于我们 / 联系我们」页或官网首页的链接，例如 https://www.abc.com/about"
        />
        <div class="mt-2 space-y-1 rounded border border-border bg-muted/20 p-2">
          <p class="text-xs">
            <span class="text-green-600">✓ 推荐这样填</span>
            <span class="text-muted-foreground">
              ：页面上能看到「公司全称、联系电话、地址、邮箱」的链接。
              优先用「关于我们 / 联系我们 / 公司简介」页，或企业官网首页（底部一般有公司全称、电话、地址、备案号）。
            </span>
          </p>
          <p class="text-xs">
            <span class="text-red-500">✗ 不要这样填</span>
            <span class="text-muted-foreground">
              ：百度/搜狗等搜索页、行业门户与资讯列表页、新闻详情页、需登录才能看的页面。
              这类页面本身没有企业信息，识别不出结果是正常现象。
            </span>
          </p>
          <p class="text-xs">
            <span class="text-muted-foreground">
              网页内容由系统抓取后交给 AI 提取，请先完成右上角「AI 配置」；
              若页面文字是脚本动态加载的（打开后内容一闪才出现），可能抓不到，请改用「粘贴文本」。
            </span>
          </p>
        </div>
      </template>

      <!-- 操作按钮 -->
      <div class="mt-3 flex items-center gap-2">
        <a-button type="primary" :loading="loading" @click="handleRecognize">
          智能解析并填充
        </a-button>
        <a-button @click="clearAll">清空</a-button>
      </div>
    </template>

    <!-- 智能查重结果弹窗：识别完成后自动比对系统已有数据 -->
    <a-modal
      v-model:open="duplicateVisible"
      :footer="null"
      :width="640"
      title="检测到系统里可能已存在相同数据"
    >
      <p class="mb-2 text-xs text-muted-foreground">
        以下记录与本次识别的手机号或名称一致，建议先查看，避免重复新建脏数据。
      </p>
      <div class="max-h-72 space-y-2 overflow-y-auto">
        <div
          v-for="(item, i) in duplicateItems"
          :key="`${item.bizType}-${item.id}-${i}`"
          class="flex items-center gap-3 rounded border border-border bg-muted/20 px-3 py-2"
        >
          <a-tag color="orange">{{ item.bizTypeName }}</a-tag>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm">
              {{ item.name || '（未填写名称）' }}
              <span class="ml-2 text-xs text-muted-foreground">
                {{ item.mobile || '无手机号' }}
              </span>
            </div>
            <div class="text-xs text-muted-foreground">
              {{ item.matchReason || '信息一致' }}
              <span v-if="item.bizType === 'customer' && item.inPool">
                · 公海客户
              </span>
            </div>
          </div>
          <a-button type="link" size="small" @click="openDuplicate(item)">
            打开原有记录
          </a-button>
        </div>
      </div>
      <div class="mt-4 flex justify-end gap-2">
        <a-button @click="ignoreDuplicate">忽略，继续新建</a-button>
        <a-button type="primary" @click="mergeDuplicate">合并信息</a-button>
      </div>
    </a-modal>
  </div>
</template>
