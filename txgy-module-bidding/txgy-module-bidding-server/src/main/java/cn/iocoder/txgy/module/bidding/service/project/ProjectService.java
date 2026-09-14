package cn.iocoder.txgy.module.bidding.service.project;

// [规范依据]《招投标模块开发规范》§3.1/3.2（分层：controller→service→dal；页面 VO 与全量 VO 分离）
//           + 数据规范 V2.0 数据集 ProjectVO / TenderProjectVO / BidSectionVO（唯一键与必填口径）
// [业务目标] 项目域三档主档（项目 / 招标项目 / 标段）的登记、查询、删除；
//           登记=按租户内唯一编号 upsert（幂等，重复提交不产生脏数据）
// [为什么] 追溯能力依赖三档编号的存在性与唯一性；服务层集中校验（唯一键冲突、上级存在、删除守卫），
//           避免各接口自行拼装导致口径不一致

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import jakarta.validation.Valid;

public interface ProjectService {

    /** 项目登记/修改（编号租户内唯一；返回主键 id） */
    Long saveProject(@Valid ProjectSaveReqVO reqVO);

    /** 项目分页 */
    PageResult<ProjectRespVO> pageProject(ProjectPageReqVO reqVO);

    /** 项目详情（不存在抛 106_106_050） */
    ProjectRespVO getProject(Long id);

    /** 按项目编号查询（追溯/联动用；不存在返回 null） */
    ProjectRespVO getProjectByCode(String projectCode);

    /** 项目删除（存在招标项目时拒绝，保追溯链完整） */
    void deleteProject(Long id);

    /** 招标项目登记/修改（编号租户内唯一） */
    Long saveTenderProject(@Valid TenderProjectSaveReqVO reqVO);

    /** 招标项目分页 */
    PageResult<TenderProjectRespVO> pageTenderProject(TenderProjectPageReqVO reqVO);

    /** 招标项目详情 */
    TenderProjectRespVO getTenderProject(Long id);

    /** 按招标项目编号查询（追溯入口；不存在返回 null） */
    TenderProjectRespVO getTenderProjectByCode(String tenderProjectCode);

    /** 招标项目删除（存在标段时拒绝） */
    void deleteTenderProject(Long id);

    /** 标段登记/修改（编号租户内唯一；所属招标项目必须已登记） */
    Long saveBidSection(@Valid BidSectionSaveReqVO reqVO);

    /** 标段分页 */
    PageResult<BidSectionRespVO> pageBidSection(BidSectionPageReqVO reqVO);

    /** 标段详情 */
    BidSectionRespVO getBidSection(Long id);

    /** 按标段编号查询（不存在返回 null） */
    BidSectionRespVO getBidSectionByCode(String bidSectionCode);
}
