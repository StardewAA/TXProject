import { describe, expect, it } from 'vitest';
import { getUniqueTaskUsers, getVisibleCandidateUsers, shouldShowTaskUser } from './time-line-users';

describe('审批时间线人员展示', () => {
  it('同一任务人只显示一次头像，但保留每条任务记录', () => {
    const tasks = [{ assigneeUser: { id: 5, nickname: '宋凯' } }, { assigneeUser: { id: 6, nickname: '胡杰' } }, { assigneeUser: { id: 5, nickname: '宋凯' } }];
    expect(tasks.map((_, index) => shouldShowTaskUser(tasks, index))).toEqual([true, true, false]);
  });
  it('候选人与任务人重复时不再显示，并按展示姓名去重', () => {
    const tasks = [{ assigneeUser: { id: 5, nickname: '宋凯' } }];
    expect(getVisibleCandidateUsers(tasks, [{ id: 5, nickname: '宋凯' }, { id: 6, nickname: '胡杰' }, { id: 6, nickname: '胡杰' }])).toEqual([{ id: 6, nickname: '胡杰' }]);
  });
  it('同名的不同账号在同一节点只显示一个姓名', () => {
    const tasks = [{ assigneeUser: { id: 80168, nickname: '宋凯' } }, { assigneeUser: { id: 80209, nickname: '宋凯' } }];
    expect(tasks.map((_, index) => shouldShowTaskUser(tasks, index))).toEqual([true, false]);
    expect(getVisibleCandidateUsers(tasks, [
      { id: 80210, nickname: '胡杰' },
      { id: 80211, nickname: '宋凯' },
      { id: 80212, nickname: '胡杰' },
    ])).toEqual([{ id: 80210, nickname: '胡杰' }]);
  });
  it('列表人数与时间线一样按实际展示人员去重', () => {
    const tasks = [
      { assigneeUser: { id: 5, nickname: '宋凯' } },
      { assigneeUser: { id: 6, nickname: '胡杰' } },
      { assigneeUser: { id: 7, nickname: '朱琳' } },
      { assigneeUser: { id: 8, nickname: '张芊芊' } },
      { assigneeUser: { id: 9, nickname: '潘振瑞' } },
      { assigneeUser: { id: 10, nickname: '宋凯' } },
      { assigneeUser: { id: 6, nickname: '胡杰' } },
    ];
    expect(getUniqueTaskUsers(tasks).map(user => user.nickname)).toEqual([
      '宋凯', '胡杰', '朱琳', '张芊芊', '潘振瑞',
    ]);
  });
});
