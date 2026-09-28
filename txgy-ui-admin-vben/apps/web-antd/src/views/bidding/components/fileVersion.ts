/* 文件版本行（详情页「文件版本」分区共用数据结构）
 * 来源：证照文件版本 / 生命周期佐证材料 / 员工证书文件 / 履历附件，字段同构。 */
export interface FileVersionRow {
  fileId?: number | string;
  name: string;
  categoryLabel?: string;
  uploadTime?: null | number | string;
  url?: string;
  /** 补充说明（如"当前生效版本""加注件"） */
  note?: string;
}
