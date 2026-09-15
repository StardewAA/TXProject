import { getHrmRecruitFilePreviewUrl } from './file-url';

export type HrmRecruitChatAttachment = {
  fileName: string;
  fileUrl: string;
  previewUrl: string;
  title: string;
  type: 'image' | 'resume';
};

function decodeFileName(value: string | undefined, fallback: string) {
  if (!value) return fallback;
  try {
    return decodeURIComponent(value) || fallback;
  } catch {
    return value || fallback;
  }
}

function createAttachment(
  type: HrmRecruitChatAttachment['type'],
  title: string,
  fileName: string,
  fileUrl: string,
) {
  const normalizedUrl = getHrmRecruitFilePreviewUrl(fileUrl);
  if (!normalizedUrl) return undefined;
  return {
    type,
    title: title || (type === 'image' ? '聊天图片' : '简历'),
    fileName: fileName || (type === 'image' ? '聊天图片' : '附件简历'),
    fileUrl,
    previewUrl: normalizedUrl,
  } satisfies HrmRecruitChatAttachment;
}

export function getHrmRecruitChatAttachment(
  content: string | undefined,
  contentType?: number,
) {
  if (!content) return undefined;

  const firstSeparator = content.indexOf('|');
  if (contentType === 2) {
    const secondSeparator = content.indexOf('|', firstSeparator + 1);
    if (firstSeparator < 0 || secondSeparator < 0) return undefined;
    const title = content.slice(0, firstSeparator);
    const fileName = content.slice(firstSeparator + 1, secondSeparator);
    const fileUrl = content.slice(secondSeparator + 1);
    return createAttachment(
      'resume',
      title || '简历',
      fileName || title || '附件简历',
      fileUrl,
    );
  }

  if (!content.startsWith('__HRM_IMAGE__|') || firstSeparator < 0) {
    return undefined;
  }
  const secondSeparator = content.indexOf('|', firstSeparator + 1);
  if (secondSeparator < 0) return undefined;
  const encodedName = content.slice(firstSeparator + 1, secondSeparator);
  const fileUrl = content.slice(secondSeparator + 1);
  return createAttachment(
    'image',
    '聊天图片',
    decodeFileName(encodedName, '聊天图片'),
    fileUrl,
  );
}
