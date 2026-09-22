package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;

/**
 * 简历与职位内容指纹工具。
 *
 * <p>指纹用于判断「分析或投递时依据的内容」是否已经变化：
 * 职位内容变化后，原有分析结论需要标记过期；投递快照也需要固定当时的职位内容指纹。</p>
 *
 * <p>指纹只用于内部一致性判断，不包含任何个人敏感信息。</p>
 */
public final class HrmResumeFingerprintUtils {

    private HrmResumeFingerprintUtils() {
    }

    /**
     * 计算简历内容的指纹。内容为空时返回 null，由调用方决定如何处理。
     */
    public static String fingerprintResumeContent(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        // 去掉首尾空白与换行差异，避免仅格式变化导致指纹抖动
        return DigestUtil.sha256Hex(content.trim());
    }

    /**
     * 计算职位内容指纹。
     *
     * <p>只取影响岗位匹配判断的字段，避免薪资区间、招聘人数等与匹配无关的调整导致指纹变化。</p>
     */
    public static String fingerprintPost(HrmRecruitPostDO post) {
        if (post == null) {
            return null;
        }
        String normalized = StrUtil.join("|",
                StrUtil.nullToEmpty(post.getPostName()),
                post.getPostTypeId() == null ? "" : String.valueOf(post.getPostTypeId()),
                post.getEducationRequire() == null ? "" : String.valueOf(post.getEducationRequire()),
                post.getWorkTime() == null ? "" : String.valueOf(post.getWorkTime()),
                post.getJobNature() == null ? "" : String.valueOf(post.getJobNature()),
                StrUtil.nullToEmpty(post.getDescription()));
        return DigestUtil.sha256Hex(normalized);
    }
}
