package cn.iocoder.txgy.module.hrm.service.recruit.resume;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSnapshotReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;

import java.util.List;

public interface HrmRecruitResumeService {

    Long createRecruitResume(HrmRecruitResumeSaveReqVO reqVO);

    /**
     * 投递时在职位所属企业租户内生成候选人简历快照。
     *
     * <p>快照内容为求职者选定个人简历版本的固化副本，可只含在线结构化内容而没有附件地址。</p>
     *
     * @param reqVO 快照请求
     * @return 候选人简历（快照）编号
     */
    Long createRecruitResumeSnapshot(HrmRecruitResumeSnapshotReqVO reqVO);

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
