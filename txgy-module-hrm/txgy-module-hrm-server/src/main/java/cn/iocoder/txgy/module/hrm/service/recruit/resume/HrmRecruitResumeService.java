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
     * 保存简历文本内容（扫描件 OCR 识别结果 / 人工粘贴）
     *
     * <p>扫描件 PDF 没有文字层，服务端无法直接解析；HR 在「简历版本 → 预览」里点「识别文字」后，
     * 前端用本地 OCR 得到文本并调用本方法落库。落库后：入职建档会按规则自动补全员工档案字段，
     * AI 简历分析也有内容可用。</p>
     *
     * @param id      简历编号
     * @param content 文本内容
     * @param source  来源：ocr / manual
     */
    void saveRecruitResumeText(Long id, String content, String source);

    /**
     * 获得旧候选人档案中的简历附件授权地址。
     *
     * @param candidateId 候选人编号
     * @param legacyIndex 旧 resume_urls 中的附件下标
     */
    HrmRecruitResumeDO getAuthorizedLegacyRecruitResume(Long candidateId, Integer legacyIndex);
}
