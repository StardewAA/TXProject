import { computed, ref } from 'vue';

import { useUserStore } from '@vben/stores';

export interface TrainingDirectionState {
  /** 成长等级下标（对应 TRAINING_LEVELS） */
  level: number;
  /** 已选课程一级大类编码，如 A/B/C */
  selected: string[];
}

/** 成长等级阶梯：与课程目录的三级分类体系配套使用 */
export const TRAINING_LEVELS = [
  '学徒',
  '初级工',
  '中级工',
  '高级工',
  '技师',
  '高级技师',
];

const state = ref<TrainingDirectionState>({ level: 0, selected: [] });
let loadedKey = '';

function resolveKey(userId?: number | string) {
  return `training-direction-${userId ?? 'visitor'}`;
}

/**
 * 云培训学院「学习方向 / 成长等级」学员偏好
 *
 * 后端暂无学员画像接口，先按用户维度存入 localStorage，供工作台、课程中心及各子模块
 * 按学习方向筛选展示；后续服务端接口就绪后，只需替换 load/save 实现即可。
 */
export function useTrainingDirection() {
  const userStore = useUserStore();
  const storageKey = computed(() => resolveKey(userStore.userInfo?.id));

  function load() {
    const key = storageKey.value;
    try {
      const raw = localStorage.getItem(key);
      state.value = raw ? JSON.parse(raw) : { level: 0, selected: [] };
    } catch {
      state.value = { level: 0, selected: [] };
    }
    loadedKey = key;
  }

  /** 首次进入或切换用户时按需加载 */
  function ensureLoaded() {
    if (loadedKey !== storageKey.value) load();
  }

  function save(next: TrainingDirectionState) {
    state.value = { level: next.level, selected: [...next.selected] };
    try {
      localStorage.setItem(storageKey.value, JSON.stringify(state.value));
    } catch {
      // 忽略隐私模式等本地存储异常
    }
  }

  return {
    LEVELS: TRAINING_LEVELS,
    levelName: computed(
      () => TRAINING_LEVELS[state.value.level] ?? TRAINING_LEVELS[0],
    ),
    state,
    storageKey,
    ensureLoaded,
    load,
    save,
  };
}
