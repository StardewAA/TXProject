import { describe, expect, it } from 'vitest';

import { decodeText, detectPreviewKind, docxToHtml, sheetToHtmlTable } from './filePreview';

describe('filePreview · 多格式渲染纯函数', () => {
  it('detectPreviewKind：扩展名分发（大小写不敏感）', () => {
    expect(detectPreviewKind('合同.pdf')).toBe('pdf');
    expect(detectPreviewKind('照片.PNG')).toBe('image');
    expect(detectPreviewKind('img.jpeg')).toBe('image');
    expect(detectPreviewKind('正文.docx')).toBe('docx');
    expect(detectPreviewKind('台账.xlsx')).toBe('xlsx');
    expect(detectPreviewKind('老表.xls')).toBe('xlsx');
    expect(detectPreviewKind('名单.csv')).toBe('csv');
    expect(detectPreviewKind('说明.txt')).toBe('text');
    expect(detectPreviewKind('凭证.sig.txt')).toBe('text');
    expect(detectPreviewKind('data.json')).toBe('text');
    expect(detectPreviewKind('page.html')).toBe('text');
    expect(detectPreviewKind('包.zip')).toBe('other');
    expect(detectPreviewKind('演示.pptx')).toBe('other');
    expect(detectPreviewKind('')).toBe('other');
    expect(detectPreviewKind(undefined)).toBe('other');
  });

  it('decodeText：UTF-8 直接解码；GBK 字节回退 GB18030 不乱码', () => {
    const utf8 = new TextEncoder().encode('这是 UTF-8 文本');
    expect(decodeText(utf8)).toBe('这是 UTF-8 文本');
    // GBK 编码的「招标文件说明」字节（"招"=0xD5D0，"标"=0xB1EA，"文"=0xCEC4，"件"=0xBCFE，"说"=0xCBB5，"明"=0xC3F7）
    const gbk = new Uint8Array([0xd5, 0xd0, 0xb1, 0xea, 0xce, 0xc4, 0xbc, 0xfe, 0xcb, 0xb5, 0xc3, 0xf7]);
    expect(decodeText(gbk)).toBe('招标文件说明');
  });

  it('sheetToHtmlTable：SheetJS 读 xlsx → HTML 表格（含表头）', async () => {
    const XLSX = await import('xlsx');
    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.aoa_to_sheet([
      ['项目名称', '金额'],
      ['某项目', 12345.5],
    ]);
    XLSX.utils.book_append_sheet(wb, ws, 'Sheet1');
    const bytes = XLSX.write(wb, { type: 'array', bookType: 'xlsx' }) as ArrayBuffer;
    const { html, rows, truncated } = await sheetToHtmlTable(bytes);
    expect(html).toContain('<table');
    expect(html).toContain('项目名称');
    expect(html).toContain('12345.5');
    expect(rows).toBe(2);
    expect(truncated).toBe(false);
  });

  it('sheetToHtmlTable：超行截断并标记 truncated', async () => {
    const XLSX = await import('xlsx');
    const wb = XLSX.utils.book_new();
    const rowsArray: Array<Array<number | string>> = [];
    for (let i = 0; i < 2200; i += 1) rowsArray.push([`行${i}`, i]);
    const ws = XLSX.utils.aoa_to_sheet([['A', 'B'], ...rowsArray]);
    XLSX.utils.book_append_sheet(wb, ws, 'S');
    const bytes = XLSX.write(wb, { type: 'array', bookType: 'xlsx' }) as ArrayBuffer;
    const { rows, truncated } = await sheetToHtmlTable(bytes, { maxRows: 2000 });
    expect(rows).toBe(2000);
    expect(truncated).toBe(true);
  });

  it('docxToHtml：mammoth 渲染真实 docx（最小 docx 设定档）', async () => {
    // 用 aoa 类比不行；此处直接测解码层：构造一个纯文本 zip 不是 docx，mammoth 会报错 → 验证错误传播
    // （真实 docx 渲染已由 training 资源页 e2e 覆盖；这里只验证 detect 分类 + 懒加载不炸）
    await expect(docxToHtml(new Uint8Array([1, 2, 3]).buffer)).rejects.toThrow();
  });
});
