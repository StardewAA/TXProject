import { describe, expect, it } from 'vitest';

import { buildApprovalDetailLocation } from '#/views/bpm/approval-route';

describe('WMS approval routes', () => {
  it('keeps process details inside WMS approval management', () => {
    expect(buildApprovalDetailLocation('WmsApprovalDetail', 'process-1', 'task-1')).toEqual({
      name: 'WmsApprovalDetail',
      query: { id: 'process-1', taskId: 'task-1' },
    });
  });

  it('omits optional detail query values', () => {
    expect(buildApprovalDetailLocation('WmsApprovalDetail', 'process-1')).toEqual({
      name: 'WmsApprovalDetail',
      query: { id: 'process-1' },
    });
  });
});
