package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 资格门法律基础（集中注释，代码内逐点见下）：
//  《招标投标法》第25条（投标人是响应招标、参加投标竞争的法人或者其他组织）、第26条（投标人应当具备承担招标项目的能力；
//   国家规定或招标文件对投标人资格条件有规定的，应当具备规定条件——本平台落地为“授权+主体信息完善”）；
//  《实施条例》第19条（未通过资格审查的申请人不具有投标资格——平台以“已授权”为参与前提）；
//  《电子招标投标办法》第9条（市场主体免费注册登录）、第13条（身份识别/权限控制）、第14条（平台验证初始录入信息的真实性）、
//   第24条（投标人应当在交易平台注册登记、如实递交有关信息并经运营机构验证——被授权投标人仍须完善信息）；
//  招标人发布门：《招标投标法》第8条（招标人定义）、第16条（招标公告由招标人发布并载明其名称地址等）；
//  《招标公告和公示信息发布管理办法》第5/6条（依法必须招标项目公告/候选人公示的发布主体与必备内容）。
// 关注/订阅/管理类操作仅需登录+权限，不经过本门（NoticeFollow 域不做档案校验）。

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectEligibilityRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SubjectAuthStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.SubjectTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 主体资格判定实现（投标/发布资格门的唯一入口；档案完善度规则见 {@link SubjectCompleteness}）。
 */
@Service
public class SubjectEligibilityServiceImpl implements SubjectEligibilityService {

    private static final String REASON_NOT_AUTHORIZED = "NOT_AUTHORIZED";
    private static final String REASON_AUTH_DISABLED = "AUTH_DISABLED";
    private static final String REASON_PROFILE_MISSING = "PROFILE_MISSING";
    private static final String REASON_PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";

    @Resource
    private SubjectAuthMapper subjectAuthMapper;
    @Resource
    private SubjectProfileMapper subjectProfileMapper;

    @Override
    public SubjectEligibilityRespVO resolveEligibility(Long tenantId, String subjectType) {
        SubjectEligibilityRespVO resp = new SubjectEligibilityRespVO();
        resp.setTenantId(tenantId);
        resp.setSubjectType(subjectType);
        List<String> reasons = new ArrayList<>();
        SubjectAuthDO auth = findAuth(tenantId, subjectType);
        if (auth != null) {
            resp.setTenantName(auth.getTenantName());
            resp.setAuthorizeTime(auth.getAuthorizeTime());
            resp.setDisableTime(auth.getDisableTime());
        }
        boolean authorized = auth != null && SubjectAuthStatusEnum.ENABLED.name().equals(auth.getAuthStatus());
        resp.setAuthorized(authorized);
        resp.setAuthStatus(auth == null ? REASON_NOT_AUTHORIZED
                : SubjectAuthStatusEnum.ENABLED.name().equals(auth.getAuthStatus()) ? "ENABLED" : "DISABLED");
        if (auth == null) {
            reasons.add(REASON_NOT_AUTHORIZED);
        } else if (!authorized) {
            reasons.add(REASON_AUTH_DISABLED);
        }
        SubjectProfileDO profile = readProfile(tenantId, subjectType);
        SubjectCompleteness.Assessment assessment = SubjectCompleteness.assess(profile);
        resp.setProfileComplete(assessment.complete);
        if (profile == null) {
            reasons.add(REASON_PROFILE_MISSING);
        } else if (!assessment.complete) {
            reasons.add(REASON_PROFILE_INCOMPLETE);
        }
        resp.setReasonCodes(reasons);
        resp.setMissingRequiredNames(assessment.complete ? new ArrayList<>() : assessment.missingNames);
        boolean eligible = authorized && assessment.complete;
        resp.setEligible(eligible);
        resp.setMessage(buildMessage(eligible, resp));
        return resp;
    }

    @Override
    public void assertBidderEligible(Long tenantId) {
        // 投标资格门（供后续“本项目投标/报名递交”接线；关注/订阅等不受本门限制——见 NoticeFollowController 仅权限控制）
        SubjectEligibilityRespVO resp = resolveEligibility(tenantId, SubjectTypeEnum.BIDDER.name());
        if (!Boolean.TRUE.equals(resp.getEligible())) {
            if (resp.getReasonCodes().contains(REASON_NOT_AUTHORIZED)
                    || resp.getReasonCodes().contains(REASON_AUTH_DISABLED)) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_NOT_AUTHORIZED);
            }
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PROFILE_INCOMPLETE);
        }
    }

    @Override
    public void assertTendererIssuanceAllowed() {
        // 招标人发布门：公告 create/update/publish、备案 submit 前调用（招标投标法第8/16条：发布主体须为招标人）
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            // 非租户上下文（平台同步作业/初始化）不设门，防止批处理被主体状态阻塞（外部公告域在 G 轨聚合侧，同样不受影响）
            return;
        }
        SubjectEligibilityRespVO resp = resolveEligibility(tenantId, SubjectTypeEnum.TENDERER.name());
        if (!Boolean.TRUE.equals(resp.getEligible())) {
            if (resp.getReasonCodes().contains(REASON_NOT_AUTHORIZED)
                    || resp.getReasonCodes().contains(REASON_AUTH_DISABLED)) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_NOT_AUTHORIZED);
            }
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PROFILE_INCOMPLETE);
        }
    }

    // ========== 私有 ==========

    private String buildMessage(boolean eligible, SubjectEligibilityRespVO resp) {
        if (eligible) {
            return "主体已授权且信息完善，具备" + resp.getSubjectType() + "资格";
        }
        if (resp.getReasonCodes().contains(REASON_NOT_AUTHORIZED)) {
            return "企业尚未被平台授权为" + resp.getSubjectType() + "，请联系平台管理端授权（授权后可关注公告，投标/发布需再完善信息）";
        }
        if (resp.getReasonCodes().contains(REASON_AUTH_DISABLED)) {
            return "该主体授权已被平台停用";
        }
        return "授权已获，但主体信息未完善：" + String.join("、", resp.getMissingRequiredNames())
                + "。完善后方可投标/发布（关注公告、参与其他项目不受影响）";
    }

    private SubjectAuthDO findAuth(Long tenantId, String subjectType) {
        return subjectAuthMapper.selectOne(new LambdaQueryWrapper<SubjectAuthDO>()
                .eq(SubjectAuthDO::getTenantId, tenantId)
                .eq(SubjectAuthDO::getSubjectType, subjectType)
                .last("LIMIT 1"));
    }

    private SubjectProfileDO readProfile(Long tenantId, String subjectType) {
        return TenantUtils.executeIgnore(() -> subjectProfileMapper.selectOne(new LambdaQueryWrapper<SubjectProfileDO>()
                .eq(SubjectProfileDO::getTenantId, tenantId)
                .eq(SubjectProfileDO::getSubjectType, subjectType)
                .last("LIMIT 1")));
    }
}
