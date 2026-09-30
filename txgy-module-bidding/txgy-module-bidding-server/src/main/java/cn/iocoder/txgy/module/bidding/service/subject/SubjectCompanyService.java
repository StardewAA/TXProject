package cn.iocoder.txgy.module.bidding.service.subject;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanySaveReqVO;

/**
 * 所属企业 Service（租户自维护的企业名录：本企业 SELF + 子公司 SUBSIDIARY / 分公司 BRANCH）。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（服务层统一转换）、§4.5（乐观锁 CAS、幂等判重）。
 */
public interface SubjectCompanyService {

    /**
     * 新增所属企业（companyType=SELF 每租户一条；USCC 同租户唯一；parentId 校验同租户且不成环）。
     *
     * @return 新主键（雪花 ID）
     */
    Long createSubjectCompany(SubjectCompanySaveReqVO reqVO);

    /**
     * 修改所属企业（乐观锁 version 必传；同租户判重排除自身）。
     */
    void updateSubjectCompany(SubjectCompanySaveReqVO reqVO);

    /**
     * 启用 / 停用所属企业（停用只是从选择器隐去，不影响已归属的历史证照/资质）。
     */
    void updateSubjectCompanyStatus(Long id, Boolean enabled);

    /**
     * 删除所属企业（逻辑删除；已挂证照/资质的企业建议改用停用）。
     */
    void deleteSubjectCompany(Long id);

    /**
     * 详情。
     */
    SubjectCompanyRespVO getSubjectCompany(Long id);

    /**
     * 分页。
     */
    PageResult<SubjectCompanyRespVO> getSubjectCompanyPage(SubjectCompanyPageReqVO reqVO);
}
