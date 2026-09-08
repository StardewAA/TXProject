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
} from 'ant-design-vue';

import { $t } from '#/locales';

import { createQuestion, getQuestion, getResourceTagList, updateQuestion } from '../api';
import {
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
} from '../constants';

defineOptions({ name: 'TrainingResourceQuestionForm' });

const emit = defineEmits(['success']);

// 表单状态
const editingId = ref<number>();
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
const analysis = ref<string>('');
const difficulty = ref<number>(2);
const tagIds = ref<number[]>([]);
const saving = ref(false);

const tagOptions = ref<any[]>([]);
async function loadTags() {
  const list = await getResourceTagList();
  tagOptions.value = list.map((item) => ({
    label: item.name,
    value: item.id,
  }));
}

const isChoice = computed(() => qtype.value === 1 || qtype.value === 2);
const isJudge = computed(() => qtype.value === 3);
const isQaOrCase = computed(() => qtype.value === 4 || qtype.value === 5);

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
  if (isQaOrCase.value && !qaAnswer.value.trim()) {
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
      analysis.value = '';
      difficulty.value = 2;
      tagIds.value = [];
      return;
    }
    loadTags();
    const data = modalApi.getData<number>();
    if (data) {
      editingId.value = data;
      const detail = await getQuestion(data);
      qtype.value = detail.qtype || 1;
      stem.value = detail.stem || '';
      analysis.value = detail.analysis || '';
      difficulty.value = detail.difficulty || 2;
      tagIds.value = detail.tagIds || [];
      if (qtype.value === 3) {
        judgeAnswer.value = detail.answer || '对';
      } else if (qtype.value === 4 || qtype.value === 5) {
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

      <!-- 实操问答 / 案例分析 -->
      <div v-else-if="isQaOrCase">
        <div class="mb-1 text-sm text-gray-600">
          标准答案（评分要点） <span class="text-red-500">*</span>
        </div>
        <Input.TextArea
          v-model:value="qaAnswer"
          :rows="4"
          :maxlength="2000"
          placeholder="请输入标准答案 / 评分要点"
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
