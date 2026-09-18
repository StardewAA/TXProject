/** 云培训学院 F1–F10 的真实子路由；首页与原模块导航共用。 */
export const TRAINING_MODULES = [
  { label: '学习工作台', path: '/portal/training/workbench', summary: '继续学习 · 成长进度 · 已获学时' },
  { label: '课程中心', path: '/portal/training/courses', summary: '岗位课程 · 专业知识 · 分类检索' },
  { label: '我的学习', path: '/portal/training/learning', summary: '学习中 · 待学习 · 已完成' },
  { label: 'VR 实操训练', path: '/portal/training/vr', summary: '实训场景 · 我的实训记录' },
  { label: '考试与证书', path: '/portal/training/exam', summary: '在线考试 · 结业与证书' },
  { label: '直播大厅', path: '/portal/training/live', summary: '直播课程 · 预告与回放' },
  { label: '培训任务与上岗', path: '/portal/training/tasks', summary: '任务清单 · 上岗达标进度' },
  { label: '个人档案与成长', path: '/portal/training/profile', summary: '课程进度 · 学习轨迹 · 打卡记录' },
  { label: '校企合作专区', path: '/portal/training/cooperation', summary: '合作院校 · 定向培训班' },
  { label: '创新大赛专区', path: '/portal/training/contest', summary: '赛事活动 · 作品与成果' },
] as const;
