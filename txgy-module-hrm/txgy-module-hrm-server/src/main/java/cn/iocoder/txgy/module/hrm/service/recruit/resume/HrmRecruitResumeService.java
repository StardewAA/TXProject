package cn.iocoder.txgy.module.hrm.service.recruit.resume;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;

import java.util.List;

public interface HrmRecruitResumeService {

    Long createRecruitResume(HrmRecruitResumeSaveReqVO reqVO);

    List<HrmRecruitResumeDO> getRecruitResumeList(Long candidateId);

    HrmRecruitResumeDO getAuthorizedRecruitResume(Long id);

    /**
     * 获得旧候选人档案中的简历附件授权地址。
     *
     * @param candidateId 候选人编号
     * @param legacyIndex 旧 resume_urls 中的附件下标
     */
    HrmRecruitResumeDO getAuthorizedLegacyRecruitResume(Long candidateId, Integer legacyIndex);
}
