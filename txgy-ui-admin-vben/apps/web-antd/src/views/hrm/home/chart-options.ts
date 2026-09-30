import type { EChartsOption } from '@vben/plugins/echarts';

import type { HrmHomeApi } from '#/api/hrm/home';

/**
 * 图表配色统一走主题令牌（阶段三 3.3）
 *
 * <p>preferences 层在运行时把 --primary/--success/--warning/--destructive/--brand-accent 等
 * 以 HSL 三元组写进 :root，这里读取后拼成 hsl() 使用，暗色模式下图表自动跟随主题，
 * 避免出现「浅底浅字 / 与主题违和」。读取不到时回退到原品牌色，保证图表不空白。</p>
 */
function getThemeChartColors(): string[] {
  const fallback = [
    '#409eff',
    '#67c23a',
    '#e6a23c',
    '#f56c6c',
    '#909399',
    '#00a6a6',
    '#7b61ff',
    '#d97706',
  ];
  if (typeof document === 'undefined') {
    return fallback;
  }
  const styles = getComputedStyle(document.documentElement);
  const read = (name: string): null | string => {
    const value = styles.getPropertyValue(name).trim();
    return value ? `hsl(${value})` : null;
  };
  const mapped = [
    read('--primary'),
    read('--success'),
    read('--warning'),
    read('--destructive'),
    read('--muted-foreground'),
    read('--brand-accent'),
    read('--primary-700'),
    read('--warning-600'),
  ];
  return mapped.map((color, index) => color ?? fallback[index]!);
}

/** 部门薪资占比饼图配置 */
export function getSalaryDeptPieChartOptions(
  deptProportions: HrmHomeApi.HrHomeSalaryDept[],
): EChartsOption {
  return {
    title: {
      left: 'center',
      text: '部门薪资占比',
      textStyle: {
        fontSize: 14,
        fontWeight: 500,
      },
    },
    tooltip: {
      formatter: '{b}：{c}%',
      trigger: 'item',
    },
    legend: {
      bottom: 0,
      left: 'center',
      type: 'scroll',
    },
    series: [
      {
        center: ['50%', '46%'],
        data: deptProportions.map((item) => ({
          name: item.deptName,
          value: Number((item.proportion * 100).toFixed(2)),
        })),
        radius: '48%',
        stillShowZeroSum: false,
        type: 'pie',
      },
    ],
  };
}

/** 团队概况饼图配置 */
export function getTeamSurveyPieChartOptions(
  data: Array<{ name: string; value: number }>,
): EChartsOption {
  return {
    color: getThemeChartColors(),
    tooltip: {
      formatter: '{b}<br/>{c} 人（{d}%）',
      trigger: 'item',
    },
    legend: {
      bottom: 0,
      type: 'scroll',
    },
    series: [
      {
        avoidLabelOverlap: true,
        center: ['50%', '43%'],
        data,
        label: {
          formatter: '{b}\n{c} 人',
        },
        radius: ['42%', '68%'],
        type: 'pie',
      },
    ],
  };
}
