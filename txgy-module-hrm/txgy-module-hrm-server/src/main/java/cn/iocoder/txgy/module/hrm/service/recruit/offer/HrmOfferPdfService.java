package cn.iocoder.txgy.module.hrm.service.recruit.offer;

import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;

/**
 * HRM Offer 通知 PDF Service 接口
 *
 * <p>行业场景：防腐保温招工多为外地务工人员，Offer 需要以「可存档、可直接打印、微信/邮箱都能看」的 PDF 形式发出，
 * 因此不依赖 Word，直接生成 PDF 作为邮件附件。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmOfferPdfService {

    /**
     * 渲染 Offer 录用通知书 PDF
     *
     * @param offer     Offer 信息
     * @param candidate 候选人信息
     * @param post      入职职位（可为空）
     * @param company   用工主体（可为空）
     * @return PDF 文件内容
     */
    byte[] renderOfferPdf(HrmRecruitOfferDO offer, HrmRecruitCandidateDO candidate,
                          HrmRecruitPostDO post, HrmCompanyProfileDO company);

    /**
     * 构造 Offer PDF 的文件名
     *
     * @param candidateName 候选人姓名
     * @param offerNo       Offer 编号
     * @return 文件名（含 .pdf 后缀）
     */
    String buildFileName(String candidateName, String offerNo);

}
