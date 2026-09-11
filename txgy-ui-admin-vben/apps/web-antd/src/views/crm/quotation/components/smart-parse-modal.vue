<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';
import type { CrmCustomerApi } from '#/api/crm/customer';
import type { CrmProductApi } from '#/api/crm/product';
import type { CrmQuotationApi } from '#/api/crm/quotation';

import { onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { erpPriceMultiply } from '@vben/utils';

import {
  Button,
  message,
  Space,
  Spin,
  Tabs,
  Textarea,
  Upload,
} from 'ant-design-vue';
import dayjs from 'dayjs';
import { recognize } from 'tesseract.js';

import { getSimpleBusinessList } from '#/api/crm/business';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getProductSimpleList } from '#/api/crm/product';

const emit = defineEmits<{
  success: [values: Partial<CrmQuotationApi.Quotation>];
}>();

const activeKey = ref<'text' | 'ocr'>('text');
const text = ref('');
const loading = ref(false);
const ocrLoading = ref(false);
const ocrProgress = ref('');

const customers = ref<CrmCustomerApi.Customer[]>([]);
const businesses = ref<CrmBusinessApi.Business[]>([]);
const products = ref<CrmProductApi.Product[]>([]);

onMounted(async () => {
  customers.value = await getCustomerSimpleList();
  products.value = await getProductSimpleList();
  businesses.value = await getSimpleBusinessList();
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      text.value = '';
      activeKey.value = 'text';
    }
  },
});

defineExpose({
  open: () => modalApi.open(),
});

/** 匹配客户 */
function matchCustomer(name?: string) {
  if (!name) {
    return undefined;
  }
  return customers.value.find(
    (item) =>
      item.name === name ||
      item.name.includes(name) ||
      name.includes(item.name),
  );
}

/** 匹配商机 */
function matchBusiness(customerId?: number) {
  if (!customerId) {
    return undefined;
  }
  return businesses.value.find((item) => item.customerId === customerId);
}

/** 匹配系统产品 */
function matchProduct(parsed: CrmQuotationApi.ParseProduct) {
  const product = products.value.find(
    (item) =>
      item.name.includes(parsed.productName) ||
      parsed.productName.includes(item.name),
  );
  if (!product) {
    return null;
  }
  const sellingPrice = parsed.price ?? product.price;
  const count = Number(parsed.count);
  const totalPrice = erpPriceMultiply(sellingPrice, count) ?? 0;
  return {
    id: Date.now() + Math.random(),
    productId: product.id,
    productNo: product.no,
    productUnit: product.unit,
    productPrice: product.price,
    quotationPrice: sellingPrice,
    sellingPrice,
    count,
    totalPrice,
  };
}

/** 组装备注 */
function buildRemark(parsed: CrmQuotationApi.ParseResult) {
  const parts: string[] = [];
  if (parsed.contactName) {
    parts.push(`联系人：${parsed.contactName}`);
  }
  if (parsed.phone) {
    parts.push(`电话：${parsed.phone}`);
  }
  if (parsed.address) {
    parts.push(`地址：${parsed.address}`);
  }
  if (parsed.email) {
    parts.push(`邮箱：${parsed.email}`);
  }
  if (parsed.qq) {
    parts.push(`QQ：${parsed.qq}`);
  }
  if (parsed.remark) {
    parts.push(parsed.remark);
  }
  return parts.join('；');
}

/**
 * 文本归一化：统一换行、全角转半角、合并 OCR 在汉字之间插入的空格。
 * 避免「联 系 人」「客户名称 : 某某公司」这类结果匹配不到。
 */
function normalizeText(raw: string) {
  return (raw || '')
    .replace(/\r\n?/g, '\n')
    .replace(/[\uFF01-\uFF5E]/g, (ch) =>
      String.fromCharCode(ch.charCodeAt(0) - 0xFEE0),
    )
    .replace(/\u3000/g, ' ')
    // 只合并「行内」空格：不能用 \s（它包含换行符，会把多行拼成一行）
    .replace(/([\u4E00-\u9FA5])[ \t\u00A0]+(?=[\u4E00-\u9FA5])/g, '$1')
    // OCR 常把 QQ 识别成「Q Q」
    .replace(/Q\s*Q/gi, 'QQ')
    .trim();
}

/**
 * 前端本地解析名片/聊天/报价单文本为报价单结构。
 * 后端暂未实现 /crm/quotation/parse-text 接口，为避免 404，此处使用正则在前端解析。
 * 解析优先级：手机号 > 邮箱 > QQ > 联系人 > 客户(公司) > 地址 > 产品行。
 */
async function parseQuotationText(
  content: string,
): Promise<CrmQuotationApi.ParseResult> {
  const text = normalizeText(content);
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  /** 按行匹配「标签: 值」，返回首个命中的值 */
  const pickLine = (labelRe: RegExp): string | undefined => {
    for (const line of lines) {
      const value = line.match(labelRe)?.[1]?.trim();
      if (value) {
        return value;
      }
    }
    return undefined;
  };

  const phone =
    pickLine(
      /(?:联系电话|电话|手机号码|手机号|手机|联系方式)\s*[:：]?\s*([\d\s\-()+]{7,20})/,
    )?.replace(/[\s-]/g, '') ??
    text.match(/(?:^|\D)(1[3-9]\d{9})(?=\D|$)/)?.[1];
  const email = text.match(/([\w.+-]+@[\w-]+\.[\w.-]+)/)?.[1];
  const qq = pickLine(/(?:QQ|qq)\s*[:：]?\s*(\d{5,})/);
  const contactName =
    pickLine(/(?:联系人|姓名|名字)\s*[:：]?\s*([^\s,;:]{1,10})/) ??
    text.match(
      /([\u4E00-\u9FA5]{1,3}(?:经理|老师|先生|女士|总监|主任|总|工))/,
    )?.[1];
  const customerName =
    pickLine(
      /^(?:客户名称|客户|公司名称|公司名|公司|单位名称|单位|甲方)\s*[:：]?\s*(.+)$/,
    ) ??
    text.match(
      /([\u4E00-\u9FA5A-Za-z0-9·.]+(?:公司|集团|厂|中心|店|院|所))/,
    )?.[1];
  const address =
    pickLine(/^(?:地址|住址|地点|所在地|送货地址|收货地址)\s*[:：]?\s*(.+)$/) ??
    text.match(
      /([\u4E00-\u9FA5]{4,}?(?:路|街|号|区|县|市|省|镇|村|栋|楼|室))/,
    )?.[1];

  // 产品行：支持「名称 数量 单价」/「名称 x数量 单价」，忽略行首序号
  const products: CrmQuotationApi.ParseProduct[] = [];
  for (const line of lines) {
    // 跳过字段类行，避免把客户/联系人误判成产品
    if (
      /^(?:客户|公司|单位|甲方|联系|电话|手机|邮箱|地址|QQ|报价|备注|日期|有效期|销售|负责)/i.test(
        line,
      )
    ) {
      continue;
    }
    // 允许行首序号（如「1 商品001 20 350」）；序号后必须跟空白，避免误伤「3M胶带」
    const matched =
      line.match(/^(?:\d{1,3}\s*[.、)\]]?\s+)?(.+?)\s+(\d+)\s+(\d+(?:\.\d+)?)/) ??
      line.match(
        /^(?:\d{1,3}\s*[.、)\]]?\s+)?(.+?)\s*[xX*×]\s*(\d+)\s*[xX*×]?\s*(\d+(?:\.\d+)?)/,
      );
    if (!matched) {
      continue;
    }
    const productName = matched[1].replace(/[:]\s*$/, '').trim();
    if (!productName) {
      continue;
    }
    products.push({
      productName,
      count: Number(matched[2]),
      unit: '',
      price: Number(matched[3]),
    });
  }

  return {
    contactName,
    customerName,
    address,
    phone,
    email,
    qq,
    remark: '',
    products,
  };
}

/** 文本智能解析 */
async function handleParse() {
  const content = text.value.trim();
  if (!content) {
    message.warning('请先粘贴文本或识别图片');
    return;
  }
  loading.value = true;
  try {
    const parsed = await parseQuotationText(content);
    const customer = matchCustomer(parsed.customerName);
    const business = matchBusiness(customer?.id);
    const matchedProducts =
      parsed.products
        ?.map((item) => matchProduct(item))
        .filter(Boolean as any) ?? [];

    const values: Partial<CrmQuotationApi.Quotation> = {
      name: `${parsed.customerName || parsed.contactName || '新'}报价单`,
      customerId: customer?.id,
      customerName: customer?.name ?? parsed.customerName,
      businessId: business?.id,
      orderDate: dayjs().valueOf().toString(),
      validUntil: dayjs().add(7, 'day').valueOf().toString(),
      remark: buildRemark(parsed),
      discountPercent: 0,
      products: matchedProducts,
    };
    emit('success', values);
    await modalApi.close();
    message.success('解析完成，已自动填充');
  } finally {
    loading.value = false;
  }
}

/**
 * 图片预处理：灰度化 + 放大。
 * tesseract 对尺寸偏小的图识别率很低，放大到长边约 1600px 可显著提升中文识别效果。
 */
async function preprocessImage(file: File): Promise<Blob | File> {
  try {
    const bitmap = await createImageBitmap(file);
    const longSide = Math.max(bitmap.width, bitmap.height);
    const scale = Math.min(3, Math.max(1, 1600 / longSide));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return file;
    }
    ctx.drawImage(bitmap, 0, 0, width, height);
    const imageData = ctx.getImageData(0, 0, width, height);
    const { data } = imageData;
    for (let i = 0; i < data.length; i += 4) {
      // 灰度化（ITU-R BT.601）
      const gray =
        0.299 * (data[i] ?? 0) +
        0.587 * (data[i + 1] ?? 0) +
        0.114 * (data[i + 2] ?? 0);
      // 轻度对比度拉伸，保留笔画边缘
      const value = gray > 200 ? 255 : gray < 60 ? 0 : Math.round(gray);
      data[i] = value;
      data[i + 1] = value;
      data[i + 2] = value;
    }
    ctx.putImageData(imageData, 0, 0);
    return await new Promise<Blob>((resolve) => {
      canvas.toBlob((blob) => resolve(blob ?? file), 'image/png');
    });
  } catch {
    // 预处理失败时退回原图，不影响主流程
    return file;
  }
}

/** 图片 OCR 识别 */
async function handleOcr(file: File) {
  if (!file.type.startsWith('image/')) {
    message.error('请上传图片文件');
    return false;
  }
  ocrLoading.value = true;
  ocrProgress.value = '正在初始化 OCR 引擎...';
  try {
    const prepared = await preprocessImage(file);
    const result = await recognize(prepared, 'chi_sim+eng', {
      logger: (m: any) => {
        if (m.status === 'recognizing text') {
          ocrProgress.value = `OCR 识别中 ${(m.progress * 100).toFixed(0)}%`;
        }
      },
    });
    text.value = result.data.text;
    activeKey.value = 'text';
    message.success('图片识别完成，已自动解析');
    // 自动触发解析
    await handleParse();
  } catch (error) {
    message.error('图片识别失败，请尝试粘贴文本');
    // eslint-disable-next-line no-console
    console.error(error);
  } finally {
    ocrLoading.value = false;
    ocrProgress.value = '';
  }
  return false;
}

function handleClear() {
  text.value = '';
}
</script>

<template>
  <Modal
    title="一键识别"
    class="w-[640px]"
    :footer="false"
    :fullscreen-button="false"
  >
    <div class="mb-4 text-sm text-gray-500">
      粘贴微信聊天/名片/邮件文本可自动填入（最准确）；拖入报价单/名片图片做
      OCR 识别时，请使用清晰、正对拍摄的真实图片，截图效果最佳
    </div>
    <Tabs v-model:active-key="activeKey">
      <Tabs.TabPane key="text" tab="粘贴文本">
        <Textarea
          v-model:value="text"
          :rows="8"
          placeholder="例:王经理 13800138000 中石化四公司,北京朝阳区xx路 123号,邮箱 wang@example.com,QQ 1234567"
        />
      </Tabs.TabPane>
      <Tabs.TabPane key="ocr" tab="图片 OCR 识别">
        <Spin :spinning="ocrLoading" :tip="ocrProgress || '正在识别图片...'">
          <Upload.Dragger
            name="file"
            :multiple="false"
            :show-upload-list="false"
            :before-upload="handleOcr"
          >
            <p class="ant-upload-drag-icon">
              <IconifyIcon
                icon="ant-design:inbox-outlined"
                :width="48"
                :height="48"
                class="mx-auto text-gray-400"
              />
            </p>
            <p class="ant-upload-text">点击或拖拽名片/报价单图片到此处</p>
            <p class="ant-upload-hint">支持 jpg、png、gif 等常见图片格式</p>
          </Upload.Dragger>
        </Spin>
      </Tabs.TabPane>
    </Tabs>
    <Space class="mt-4 flex justify-end">
      <Button @click="handleClear">清空</Button>
      <Button type="primary" :loading="loading" @click="handleParse">
        智能解析并填充
      </Button>
    </Space>
  </Modal>
</template>
