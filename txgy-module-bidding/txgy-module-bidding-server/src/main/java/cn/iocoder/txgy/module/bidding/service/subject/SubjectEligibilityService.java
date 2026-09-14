package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 投标资格门：《招标投标法》第25/26条、《实施条例》第19条、《电子招标投标办法》第9/13/14/24条（见 ErrorCodeConstants 106_030+ 注释）；
//       招标人发布门：《招标投标法》第8/16条、《招标公告和公示信息发布管理办法》第5/6条。
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectEligibilityRespVO;

/**
 * 主体资格判定服务（投标/发布前置门 + 只读状态）。
 *
 * <p>两条铁律：
 * <ul>
 *   <li>投标人：已授权（ENABLED）且档案完善 → 可在本项目投标；仅授权未完善 → 不可投标，但关注/订阅等照常；</li>
 *   <li>招标人：已授权（ENABLED）且档案完善 → 可发布其项目公告/提交备案；否则拦截。</li>
 * </ul>
 */
public interface SubjectEligibilityService {

    /** 解析某企业主体资格状态（只读；不抛错） */
    SubjectEligibilityRespVO resolveEligibility(Long tenantId, String subjectType);

    /** 投标人资格硬断言（供后续“投标/报名递交”流程调用；不具备则抛 106_031/106_034） */
    void assertBidderEligible(Long tenantId);

    /** 招标人发布硬断言（公告 create/publish、备案 submit 接线点；当前租户上下文执行） */
    void assertTendererIssuanceAllowed();
}
