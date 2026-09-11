import type { FieldRow } from './PayloadCell.vue';

import type { NoticeApi } from '#/api/bidding/notice';

// 类型字段行规约：meta/types（权威 label/required）∪ 本地静态字典（pub/控件类别/备注）。
// 四张业务表组件共享本工具；字段分组（表格分区）由各表组件自定（按各公告类型的业务文书结构）。
import { NOTICE_PAYLOAD_FIELDS, type PayloadFieldMeta } from './payloadFieldMeta';

export interface RowSpec extends FieldRow {
  pub: PayloadFieldMeta['pub'];
}

/** 构建 code → 行规约（含输入控件类别/公开范围/中文名/必填） */
export function buildRowMap(typeCode: string, remote?: NoticeApi.NoticeTypeMeta | null): Map<string, RowSpec> {
  const local = (NOTICE_PAYLOAD_FIELDS[typeCode] ?? NOTICE_PAYLOAD_FIELDS.TENDER_NOTICE)!.fields;
  const map = new Map<string, RowSpec>();
  const byLocal = new Map(local.map((f) => [f.key, f]));
  const remoteFields = remote?.fields ?? [];
  if (remoteFields.length) {
    for (const f of remoteFields) {
      const l = byLocal.get(f.code);
      map.set(f.code, {
        key: f.code,
        label: f.label,
        required: !!f.required,
        input: l?.input === 'time14' ? 'time' : l?.input === 'multiline' ? 'multiline' : l?.input === 'boolean' ? 'boolean' : 'text',
        remark: l?.remark,
        dtype: l?.dtype,
        pub: l?.pub ?? (f.required ? 'GOVERNMENT' : ''),
      });
    }
  } else {
    for (const f of local) {
      map.set(f.key, {
        key: f.key,
        label: f.label,
        required: f.required,
        input: f.input === 'time14' ? 'time' : f.input,
        remark: f.remark,
        dtype: f.dtype,
        pub: f.pub,
      });
    }
  }
  return map;
}

/** 分区定义：title=表内区块标题；hint=区块脚注；keys=该区块按序展示的权威字段 */
export interface TableGroup {
  title: string;
  hint?: string;
  keys: string[];
}
