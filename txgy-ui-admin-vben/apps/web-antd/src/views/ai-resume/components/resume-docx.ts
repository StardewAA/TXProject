import type { ResumeDocumentContent } from './resume-document.vue';

import { RESUME_SECTION_MAP, resolveSectionOrder } from './resume-sections';

/**
 * 简历草稿 → Word（.docx）。
 *
 * <p><b>为什么「下载」要出两份文件</b>：那条路径上其实有两个互斥的诉求。</p>
 * <ul>
 *   <li><b>版式要好看</b> —— 交给 HR 的是这份。只能走浏览器打印成 PDF：
 *       中栏预览就是最终版式（同一个组件、同一份草稿），另起一套排版必然会
 *       演变成「下载的和看到的不一样」。</li>
 *   <li><b>要能被系统再读回去</b> —— 自动存进「我的简历」、供投递和 AI 诊断。
 *       而浏览器打印**不把文件交给页面**，页面拿不到字节，存不了。
 *       何况项目里没有任何 PDF 生成库（见 views/bidding/notice/pdfGen.ts 的说明），
 *       硬上一个 html2canvas 之类只会产出「图片型 PDF」，没有文字层 ——
 *       传回来既不能诊断也不能检索。</li>
 * </ul>
 *
 * <p>所以下载时打印 PDF 给用户，同时用项目自带的 docx 库另存一份 Word 入库。</p>
 *
 * <p>Word 这份的版式不会和预览一模一样（docx 表达不了 HTML 模板），
 * 这是明知的取舍：它的用途是「机器可读的简历本体」，不是交付物。</p>
 *
 * <p>字段口径对齐 resume-document.vue，包括几个不显眼的约定：
 * 空小节整节不输出（导出时不留光标题和填写指引）、「个人优势」取
 * `summary || strengths`、技能区按换行而不是顿号分条。
 * 小节顺序取自 `resolveSectionOrder`，所以用户在中栏拖出来的顺序会跟着落到 Word 里。</p>
 */

export const DOCX_MIME =
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

/** 与 .doc-page 的 --accent 默认值一致，让 Word 里的小节色与预览同色系 */
const ACCENT = '4E6840';

/**
 * 取值并保证是可渲染的文本。
 *
 * <p>与 resume-document.vue 的 text() 同一套口径：库里的字段类型并不整齐
 * （`projects[].highlight` 有布尔值），非字符串一律当没有 ——
 * 宁可少一行，也不要在简历里印出一个字面的「true」。</p>
 */
function text(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number') return String(value);
  return '';
}

/** 与 resume-document.vue 的 range() 一致：只到月份，缺结束时间写「至今」 */
function range(start?: string, end?: string) {
  const from = text(start).slice(0, 7);
  const to = text(end).slice(0, 7);
  if (!from && !to) return '';
  return `${from || '—'} - ${to || '至今'}`;
}

/** 「公司名  |  岗位  |  时间」这类一行式抬头，跳过空项 */
function headLine(...parts: string[]) {
  return parts.filter(Boolean).join('  |  ');
}

function genderText(gender: unknown) {
  if (gender === 1) return '男';
  if (gender === 2) return '女';
  return '';
}

export async function buildResumeDocx(
  content: ResumeDocumentContent,
): Promise<Blob> {
  // 动态 import：docx 只在点「下载」时才需要，不该进首屏包
  const docxModule: any = await import('docx');
  const docx = docxModule.default ?? docxModule;

  const hidden = new Set(
    Array.isArray(content.hiddenSections) ? content.hiddenSections : [],
  );

  const children: any[] = [];

  // ── 抬头 ──
  children.push(
    new docx.Paragraph({
      alignment: docx.AlignmentType.CENTER,
      heading: docx.HeadingLevel.TITLE,
      text: text(content.name) || '我的简历',
    }),
  );
  const contactLine = headLine(
    genderText(content.gender),
    text(content.birthMonth) && `出生：${text(content.birthMonth)}`,
    text(content.mobile),
    text(content.email),
    text(content.city),
  );
  if (contactLine) {
    children.push(
      new docx.Paragraph({
        alignment: docx.AlignmentType.CENTER,
        text: contactLine,
      }),
    );
  }
  if (text(content.expectedPostName)) {
    children.push(
      new docx.Paragraph({
        alignment: docx.AlignmentType.CENTER,
        text: `求职意向：${text(content.expectedPostName)}`,
      }),
    );
  }

  const addSection = (label: string, paragraphs: any[]) => {
    if (!paragraphs.length) return;
    children.push(
      new docx.Paragraph({
        children: [
          new docx.TextRun({ bold: true, color: ACCENT, size: 26, text: label }),
        ],
        spacing: { after: 60, before: 200 },
      }),
    );
    children.push(...paragraphs);
  };
  const para = (value: string, options: Record<string, any> = {}) =>
    new docx.Paragraph({ spacing: { after: 90 }, text: value, ...options });

  /** 逐条成段的多行文本：「一、…\n二、…」这类，按换行分条 */
  const lines = (value: unknown) =>
    text(value)
      .split(/\n+/)
      .map((item) => item.trim())
      .filter(Boolean);

  // 顺序与标题都取自唯一来源，用户拖过小节顺序后 Word 会跟着变
  for (const key of resolveSectionOrder(content)) {
    const label = RESUME_SECTION_MAP.get(key)?.label;
    if (!label || hidden.has(key)) continue;

    if (key === 'strengths') {
      // 与文档一致：个人优势优先取 summary，没有才退回 strengths
      const body = text(content.summary) || text(content.strengths);
      addSection(label, body ? lines(body).map((l) => para(l)) : []);
      continue;
    }

    if (key === 'skills') {
      const skillLines = lines(content.skills);
      if (!skillLines.length) continue;
      // 单行短串当标签看，用顿号连成一行；多行是人工分条，照原文逐条成段
      const isTagLine =
        skillLines.length === 1 && (skillLines[0]?.length ?? 0) <= 60;
      addSection(
        label,
        isTagLine ? [para(skillLines[0]!)] : skillLines.map((l) => para(l)),
      );
      continue;
    }

    const items = Array.isArray((content as any)[key])
      ? ((content as any)[key] as any[])
      : [];
    if (!items.length) continue;

    const paragraphs: any[] = [];
    for (const item of items) {
      if (key === 'honors') {
        paragraphs.push(
          para(headLine(text(item.name), text(item.date)) || '—'),
        );
        continue;
      }

      if (key === 'educationExperiences') {
        paragraphs.push(
          para(headLine(text(item.school), range(item.start, item.end)), {
            spacing: { after: 20 },
          }),
        );
        const sub = [text(item.major), text(item.degree)]
          .filter(Boolean)
          .join(' · ');
        if (sub) paragraphs.push(para(sub, { spacing: { after: 40 } }));
        for (const line of lines(item.experience)) paragraphs.push(para(line));
        continue;
      }

      // 实习 / 工作 / 项目：抬头字段各不相同，其余描述类字段同形
      const head =
        key === 'projects'
          ? headLine(text(item.name), range(item.start, item.end))
          : headLine(text(item.company), range(item.start, item.end));
      if (head) paragraphs.push(para(head, { spacing: { after: 20 } }));

      // 项目与社团存的是 role，实习与工作存的是 position
      const role =
        key === 'clubs' || key === 'projects'
          ? text(item.role)
          : text(item.position);
      if (role) paragraphs.push(para(role, { spacing: { after: 40 } }));

      for (const field of ['description', 'achievements', 'highlight']) {
        for (const line of lines(item[field])) paragraphs.push(para(line));
      }
    }
    addSection(label, paragraphs);
  }

  const document = new docx.Document({ sections: [{ children }] });
  return docx.Packer.toBlob(document);
}
