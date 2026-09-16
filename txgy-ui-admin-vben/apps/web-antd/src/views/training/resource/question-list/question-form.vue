<script lang="ts" setup>
import type { TrainingResourceApi } from '../api';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Checkbox,
  Input,
  message,
  Radio,
  Select,
  Tag,
  TreeSelect,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createQuestion,
  getQuestion,
  getResourceCategoryTree,
  getResourceTagList,
  updateQuestion,
} from '../api';
import {
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
} from '../constants';

defineOptions({ name: 'TrainingResourceQuestionForm' });

const emit = defineEmits(['success']);

// 表单状态
const editingId = ref<number | string>();
const qtype = ref<number>(1);
const stem = ref('');
const optionSlots = ref<{ key: string; text: string }[]>([
  { key: 'A', text: '' },
  { key: 'B', text: '' },
  { key: 'C', text: '' },
  { key: 'D', text: '' },
]);
const correctSingle = ref<string>('A');
const correctMulti = ref<string[]>([]);
const judgeAnswer = ref<string>('对');
const qaAnswer = ref<string>('');
/** 填空题答案（按空位顺序，与题干中的 ____ 一一对应） */
const fillBlankAnswers = ref<string[]>(['']);
const analysis = ref<string>('');
const difficulty = ref<number>(2);
const tagIds = ref<number[]>([]);
/** 所属分类 ID（资源分类 question 类型，支持三级） */
const categoryId = ref<number | undefined>();
const saving = ref(false);

const tagOptions = ref<any[]>([]);
async function loadTags() {
  const list = await getResourceTagList();
  tagOptions.value = list.map((item) => ({
    label: item.name,
    value: item.id,
  }));
}

const categoryOptions = ref<any[]>([]);
function buildCategoryTree(
  nodes?: TrainingResourceApi.CategoryNode[],
): any[] | undefined {
  if (!nodes?.length) {
    return undefined;
  }
  return nodes.map((node) => ({
    title: node.name,
    value: node.id,
    children: buildCategoryTree(node.children),
  }));
}

async function loadCategories() {
  const tree = await getResourceCategoryTree('question');
  categoryOptions.value = buildCategoryTree(tree) || [];
}

const isChoice = computed(() => qtype.value === 1 || qtype.value === 2);
const isJudge = computed(() => qtype.value === 3);
const isQaOrCase = computed(() => qtype.value === 4 || qtype.value === 5);
const isFillBlank = computed(() => qtype.value === 6);
const isShortAnswer = computed(() => qtype.value === 7);
const isSubjective = computed(() => isQaOrCase.value || isShortAnswer.value);

const nonEmptyKeys = computed(() =>
  optionSlots.value.filter((opt) => opt.text.trim()).map((opt) => opt.key),
);

const getTitle = computed(() => {
  return editingId.value
    ? $t('ui.actionTitle.edit', ['试题'])
    : $t('ui.actionTitle.create', ['试题']);
});

watch(qtype, () => {
  correctSingle.value = 'A';
  correctMulti.value = [];
  fillBlankAnswers.value = [''];
});

function toggleMulti(key: string, checked: boolean) {
  if (checked) {
    if (!correctMulti.value.includes(key)) {
      correctMulti.value = [...correctMulti.value, key];
    }
  } else {
    correctMulti.value = correctMulti.value.filter((item) => item !== key);
  }
}

function buildChoicePayload() {
  const options = optionSlots.value
    .filter((opt) => opt.text.trim())
    .map((opt) => {
      const isCorrect =
        qtype.value === 1
          ? correctSingle.value === opt.key
          : correctMulti.value.includes(opt.key);
      return {
        key: opt.key,
        text: opt.text.trim(),
        isCorrect,
      };
    });
  const correctKeys = options
    .filter((opt) => opt.isCorrect)
    .map((opt) => opt.key)
    .sort();
  return { optionsJson: JSON.stringify(options), answer: correctKeys.join(',') };
}

function loadChoiceFromJson(optionsJson?: string) {
  if (!optionsJson) {
    return;
  }
  try {
    const parsed = JSON.parse(optionsJson) as {
      key: string;
      text: string;
      isCorrect: boolean;
    }[];
    optionSlots.value = ['A', 'B', 'C', 'D'].map((key) => {
      const found = parsed.find((item) => item.key === key);
      return { key, text: found?.text || '' };
    });
    const correctKeys = parsed
      .filter((item) => item.isCorrect)
      .map((item) => item.key);
    if (qtype.value === 1) {
      correctSingle.value = correctKeys[0] || 'A';
    } else {
      correctMulti.value = correctKeys;
    }
  } catch {
    // 历史脏数据忽略
  }
}

async function handleConfirm() {
  if (!stem.value.trim()) {
    message.warning('请填写题干');
    return;
  }
  if (isChoice.value) {
    if (nonEmptyKeys.value.length < 2) {
      message.warning('选择题至少需要填写两个选项');
      return;
    }
    if (qtype.value === 1 && !nonEmptyKeys.value.includes(correctSingle.value)) {
      message.warning('请勾选一个已填写的选项作为正确答案');
      return;
    }
    if (qtype.value === 2 && correctMulti.value.length < 2) {
      message.warning('多选题请至少勾选两个正确答案');
      return;
    }
  }
  if (isJudge.value && !judgeAnswer.value) {
    message.warning('请选择正确答案');
    return;
  }
  if (isFillBlank.value) {
    const hasAnswer = fillBlankAnswers.value.some((a) => a.trim());
    if (!hasAnswer) {
      message.warning('请至少填写一个空的答案');
      return;
    }
  }
  if (isSubjective.value && !qaAnswer.value.trim()) {
    message.warning('请填写标准答案');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingResourceApi.Question> = {
      id: editingId.value,
      qtype: qtype.value,
      stem: stem.value.trim(),
      difficulty: difficulty.value,
      categoryId: categoryId.value,
      analysis: analysis.value.trim(),
      tagIds: tagIds.value,
      answer: '',
      optionsJson: '',
    };
    if (isChoice.value) {
      const { optionsJson, answer } = buildChoicePayload();
      payload.optionsJson = optionsJson;
      payload.answer = answer;
    } else if (isJudge.value) {
      payload.answer = judgeAnswer.value;
    } else if (isFillBlank.value) {
      // 填空题：多个空的答案用竖线 | 按顺序分隔，空答案跳过
      payload.answer = fillBlankAnswers.value
        .map((a) => a.trim())
        .filter((a) => a)
        .join('|');
    } else {
      payload.answer = qaAnswer.value.trim();
    }
    if (editingId.value) {
      await updateQuestion(payload);
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      await createQuestion(payload);
      message.success($t('ui.actionMessage.createSuccess'));
    }
    modalApi.close();
    emit('success');
  } finally {
    saving.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await handleConfirm();
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      editingId.value = undefined;
      qtype.value = 1;
      stem.value = '';
      optionSlots.value = [
        { key: 'A', text: '' },
        { key: 'B', text: '' },
        { key: 'C', text: '' },
        { key: 'D', text: '' },
      ];
      correctSingle.value = 'A';
      correctMulti.value = [];
      judgeAnswer.value = '对';
      qaAnswer.value = '';
      fillBlankAnswers.value = [''];
      analysis.value = '';
      difficulty.value = 2;
      tagIds.value = [];
      categoryId.value = undefined;
      return;
    }
    loadTags();
    loadCategories();
    const data = modalApi.getData<number | string>();
    // 注意：vben modal 的 getData 在未 setData 时返回 {}（truthy），
    // 必须显式判断为数字或字符串（编辑时传入的题目 ID，后端 Long 类型前端序列化为字符串）
    if (data != null && (typeof data === 'number' || typeof data === 'string')) {
      editingId.value = data;
      const detail = await getQuestion(data);
      qtype.value = detail.qtype || 1;
      stem.value = detail.stem || '';
      analysis.value = detail.analysis || '';
      difficulty.value = detail.difficulty || 2;
      tagIds.value = detail.tagIds || [];
      categoryId.value = detail.categoryId;
      if (qtype.value === 3) {
        // 归一化：AI 导入可能存"正确/错误"，统一为表单的"对/错"
        const raw = detail.answer || '对';
        judgeAnswer.value = raw.includes('错') ? '错' : '对';
      } else if (qtype.value === 6) {
        // 填空题：答案用竖线 | 分隔，按空位顺序加载
        const raw = detail.answer || '';
        fillBlankAnswers.value = raw
          ? raw.split('|').map((a: string) => a.trim())
          : [''];
      } else if (qtype.value === 4 || qtype.value === 5 || qtype.value === 7) {
        qaAnswer.value = detail.answer || '';
      } else {
        loadChoiceFromJson(detail.optionsJson);
      }
    } else {
      editingId.value = undefined;
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[720px]">
    <div class="mx-4 flex max-h-[60vh] flex-col gap-4 overflow-y-auto pr-2">
      <!-- 题型 -->
      <div>
        <div class="mb-1 text-sm text-gray-600">
          题型 <span class="text-red-500">*</span>
        </div>
        <Radio.Group v-model:value="qtype" :options="QUESTION_TYPE_OPTIONS" />
      </div>

      <!-- 所属分类（支持三级） -->
      <div>
        <div class="mb-1 text-sm text-gray-600">所属分类</div>
        <TreeSelect
          v-model:value="categoryId"
          class="w-full"
          :allow-clear="true"
          tree-default-expand-all
          placeholder="请选择试题分类（分类与标签 → 资源分类中维护，支持三级）"
          :tree-data="categoryOptions"
        />
      </div>

      <!-- 题干 -->
      <div>
        <div class="mb-1 text-sm text-gray-600">
          题干 <span class="text-red-500">*</span>
        </div>
        <Input.TextArea
          v-model:value="stem"
          :rows="2"
          :maxlength="1000"
          placeholder="请输入题干"
        />
      </div>

      <!-- 选择题选项 -->
      <div v-if="isChoice">
        <div class="mb-1 text-sm text-gray-600">
          选项（至少填写两个，勾选/单选标出正确项）
        </div>
        <div
          v-for="opt in optionSlots"
          :key="opt.key"
          class="mb-2 flex items-center gap-2"
        >
          <Tag color="blue">{{ opt.key }}</Tag>
          <Input v-model:value="opt.text" :maxlength="500" placeholder="选项内容" />
          <Radio
            v-if="qtype === 1"
            :checked="correctSingle === opt.key"
            @change="correctSingle = opt.key"
          />
          <Checkbox
            v-else
            :checked="correctMulti.includes(opt.key)"
            @change="(e: any) => toggleMulti(opt.key, e.target.checked)"
          />
          <span class="w-10 text-xs text-gray-400">正确</span>
        </div>
      </div>

      <!-- 判断题 -->
      <div v-else-if="isJudge">
        <div class="mb-1 text-sm text-gray-600">正确答案</div>
        <Radio.Group v-model:value="judgeAnswer">
          <Radio value="对">对</Radio>
          <Radio value="错">错</Radio>
        </Radio.Group>
      </div>

      <!-- 填空题 -->
      <div v-else-if="isFillBlank">
        <div class="mb-1 flex items-center justify-between">
          <span class="text-sm text-gray-600">
            答案（按题干中 ____ 顺序填写，多个空可增删）
          </span>
          <a
            class="text-xs text-blue-500 hover:text-blue-600"
            @click="fillBlankAnswers.push('')"
          >
            + 增加空位
          </a>
        </div>
        <div
          v-for="(_, idx) in fillBlankAnswers"
          :key="idx"
          class="mb-2 flex items-center gap-2"
        >
          <Tag color="purple">空{{ idx + 1 }}</Tag>
          <Input
            v-model:value="fillBlankAnswers[idx]"
            :maxlength="500"
            placeholder="请输入该空的标准答案"
          />
          <span
            v-if="fillBlankAnswers.length > 1"
            class="cursor-pointer text-xs text-red-400 hover:text-red-500"
            @click="fillBlankAnswers.splice(idx, 1)"
          >
            删除
          </span>
        </div>
      </div>

      <!-- 实操问答 / 案例分析 / 简答 -->
      <div v-else-if="isSubjective">
        <div class="mb-1 text-sm text-gray-600">
          答案 <span class="text-red-500">*</span>
        </div>
        <Input.TextArea
          v-model:value="qaAnswer"
          :rows="4"
          :maxlength="2000"
          placeholder="请输入参考答案 / 评分要点"
        />
      </div>

      <!-- 解析 -->
      <div>
        <div class="mb-1 text-sm text-gray-600">答案解析</div>
        <Input.TextArea
          v-model:value="analysis"
          :rows="2"
          :maxlength="2000"
          placeholder="选填"
        />
      </div>

      <!-- 难度 -->
      <div>
        <div class="mb-1 text-sm text-gray-600">难度</div>
        <Radio.Group
          v-model:value="difficulty"
          :options="QUESTION_DIFFICULTY_OPTIONS"
        />
      </div>

      <!-- 标签 -->
      <div>
        <div class="mb-1 text-sm text-gray-600">标签（多选）</div>
        <Select
          v-model:value="tagIds"
          class="w-full"
          mode="multiple"
          placeholder="请选择标签，用于按标签组卷"
          :options="tagOptions"
        />
      </div>
    </div>
  </Modal>
</template>
