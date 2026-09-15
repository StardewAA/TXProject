package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 主体档案(自维护)语义见 controller/…/subject 与 docs/法规依据/00-索引；接口规约《招投标模块开发规范》§4.2

import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileSaveReqVO;

/**
 * 主体信息档案服务（T 轨，企业租户自维护：完善本企业 投标人/招标人 信息）。
 *
 * <p>规则（与投标资格门联动）：
 * <ul>
 *   <li>授权 ≠ 完善：仅获得平台授权（bidding_subject_auth）不足以投标/发布，档案必填项全齐（completeFlag=true）方可（需求口径）；</li>
 *   <li>完善度只影响“投标/发布”类资格；关注/订阅/管理操作仅需登录+权限，不受本档案约束（见 NoticeFollow 域）。</li>
 * </ul>
 */
public interface SubjectProfileService {

    /** 本租户主体档案详情（含完善度与缺失项）；未建档返回 null */
    SubjectProfileRespVO getOwnProfile(String subjectType);

    /** 保存本租户主体档案（无则新建）；统一社会信用代码强校验（GB 32100-2015）并实时核算完善度 */
    Long saveOwnProfile(SubjectProfileSaveReqVO reqVO);
}
