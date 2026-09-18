package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 主体档案（企业租户自维护）：完善后才具备投标/发布资格；关注/订阅仅需权限不受影响（需求口径）。
// 字段口径：国家/陕西 V2.0 数据规范 表89 法人基本信息；法规依据见 docs/法规依据/00-索引与引用说明.md。

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SubjectTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 主体信息档案服务实现（T 轨：租户上下文自维护本企业档案；一租户一类型一档案）。
 */
@Service
public class SubjectProfileServiceImpl implements SubjectProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private SubjectProfileMapper subjectProfileMapper;

    @Override
    public SubjectProfileRespVO getOwnProfile(String subjectType) {
        SubjectProfileDO profile = findByType(subjectType);
        if (profile == null) {
            return null;
        }
        return toRespVO(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOwnProfile(SubjectProfileSaveReqVO reqVO) {
        validateType(reqVO.getSubjectType());
        // GB 32100-2015 强校验（格式+校验码；RPC/直连双路径都在 Service 兜底）
        if (!UsccUtils.isValid(reqVO.getUscc())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_CREDIT_CODE_INVALID);
        }
        validateOptionalPayload(reqVO);
        SubjectProfileDO profile = findByType(reqVO.getSubjectType());
        boolean isNew = profile == null;
        if (profile == null) {
            profile = new SubjectProfileDO();
            profile.setSubjectType(reqVO.getSubjectType());
        }
        applyFields(profile, reqVO);
        // 完善度实时核算（必填清单=SubjectCompleteness，与资格门一致）
        SubjectCompleteness.Assessment assessment = SubjectCompleteness.assess(profile);
        boolean complete = assessment.complete;
        if (complete) {
            if (profile.getCompleteFlag() == null || !Boolean.TRUE.equals(profile.getCompleteFlag())) {
                profile.setCompleteTime(LocalDateTime.now());
            }
        } else {
            profile.setCompleteTime(null);
        }
        profile.setCompleteFlag(complete);
        if (isNew) {
            subjectProfileMapper.insert(profile);
        } else {
            subjectProfileMapper.updateById(profile);
        }
        return profile.getId();
    }

    // ========== 私有 ==========

    private SubjectProfileDO findByType(String subjectType) {
        validateType(subjectType);
        return subjectProfileMapper.selectOne(new LambdaQueryWrapper<SubjectProfileDO>()
                .eq(SubjectProfileDO::getSubjectType, subjectType)
                .last("LIMIT 1"));
    }

    private void validateType(String subjectType) {
        if (!SubjectTypeEnum.isSupported(subjectType)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_TYPE_INVALID);
        }
    }

    /** 可选项格式校验（行政区划/法人类别由 @Pattern 前置校验，此处兜底行业码/币种/JSON） */
    private void validateOptionalPayload(SubjectProfileSaveReqVO reqVO) {
        if (StringUtils.hasText(reqVO.getIndustryCode())
                && !reqVO.getIndustryCode().matches("^[A-Z][0-9]{2}$")) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PAYLOAD_INVALID);
        }
        if (StringUtils.hasText(reqVO.getRegCapCurrency())
                && !reqVO.getRegCapCurrency().matches("^[0-9]{3}$")) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PAYLOAD_INVALID);
        }
        if (StringUtils.hasText(reqVO.getExtJson())) {
            try {
                MAPPER.readTree(reqVO.getExtJson());
            } catch (Exception e) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PAYLOAD_INVALID);
            }
        }
    }

    private void applyFields(SubjectProfileDO profile, SubjectProfileSaveReqVO reqVO) {
        profile.setSubjectName(reqVO.getSubjectName().trim());
        profile.setUscc(reqVO.getUscc().toUpperCase());
        profile.setLegalPersonName(reqVO.getLegalPersonName().trim());
        profile.setLegalPersonTypeCode(reqVO.getLegalPersonTypeCode());
        profile.setRegionCode(reqVO.getRegionCode());
        profile.setIndustryCode(StringUtils.hasText(reqVO.getIndustryCode()) ? reqVO.getIndustryCode() : null);
        profile.setRegCapital(reqVO.getRegCapital());
        profile.setRegCapCurrency(StringUtils.hasText(reqVO.getRegCapCurrency()) ? reqVO.getRegCapCurrency() : "156");
        profile.setContactName(reqVO.getContactName().trim());
        profile.setContactPhone(reqVO.getContactPhone().trim());
        profile.setLicenseFileId(reqVO.getLicenseFileId());
        profile.setLicenseFileName(reqVO.getLicenseFileName());
        profile.setExtJson(StringUtils.hasText(reqVO.getExtJson()) ? reqVO.getExtJson() : null);
        profile.setRemark(reqVO.getRemark());
    }

    private SubjectProfileRespVO toRespVO(SubjectProfileDO profile) {
        SubjectProfileRespVO resp = new SubjectProfileRespVO();
        resp.setId(profile.getId());
        resp.setSubjectType(profile.getSubjectType());
        resp.setSubjectName(profile.getSubjectName());
        resp.setUscc(profile.getUscc());
        resp.setLegalPersonName(profile.getLegalPersonName());
        resp.setLegalPersonTypeCode(profile.getLegalPersonTypeCode());
        resp.setRegionCode(profile.getRegionCode());
        resp.setIndustryCode(profile.getIndustryCode());
        resp.setRegCapital(profile.getRegCapital());
        resp.setRegCapCurrency(profile.getRegCapCurrency());
        resp.setContactName(profile.getContactName());
        resp.setContactPhone(profile.getContactPhone());
        resp.setLicenseFileId(profile.getLicenseFileId());
        resp.setLicenseFileName(profile.getLicenseFileName());
        resp.setExtJson(profile.getExtJson());
        SubjectCompleteness.Assessment assessment = SubjectCompleteness.assess(profile);
        resp.setComplete(assessment.complete);
        resp.setMissingRequiredCodes(assessment.complete ? new ArrayList<>() : assessment.missingCodes);
        resp.setMissingRequiredNames(assessment.complete ? new ArrayList<>() : assessment.missingNames);
        resp.setCompleteTime(profile.getCompleteTime());
        resp.setCreateTime(profile.getCreateTime());
        resp.setUpdateTime(profile.getUpdateTime());
        return resp;
    }
}
