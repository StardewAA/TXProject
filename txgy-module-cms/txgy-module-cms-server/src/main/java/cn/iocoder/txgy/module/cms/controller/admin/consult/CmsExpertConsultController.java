package cn.iocoder.txgy.module.cms.controller.admin.consult;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.servlet.ServletUtils;
import cn.iocoder.txgy.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.txgy.module.pay.api.notify.dto.PayRefundNotifyReqDTO;
import jakarta.annotation.security.PermitAll;
import cn.iocoder.txgy.module.cms.service.consult.CmsExpertConsultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 专家咨询")
@RestController
@RequestMapping("/cms/expert-consult")
@Validated
public class CmsExpertConsultController {

    private final CmsExpertConsultService service;

    public CmsExpertConsultController(CmsExpertConsultService service) {
        this.service = service;
    }

    @PostMapping("/expert/invite")
    @Operation(summary = "企业聘请专家（提交后直接启用）")
    @PreAuthorize("@ss.hasPermission('cms:expert:invite')")
    public CommonResult<CmsExpertConsultService.Expert> inviteExpert(@Valid @RequestBody ExpertInviteRequest request) {
        return success(service.invite(request.expertUserId(), new CmsExpertConsultService.ExpertInput(
                request.expertName(), request.professionalTitle(), request.company(), request.serviceFields(),
                request.introduction(), request.fixedPrice())));
    }

    @GetMapping("/expert/available")
    @Operation(summary = "读取平台内可预约专家")
    public CommonResult<List<CmsExpertConsultService.Expert>> listAvailableExperts() {
        return success(service.listAvailableExperts());
    }

    @PostMapping("/expert/apply")
    @Operation(summary = "专家入驻申请（实名核验通过即自动上架）")
    public CommonResult<CmsExpertConsultService.ExpertApplication> applyExpert(
            @Valid @RequestBody ExpertApplyRequest request) {
        return success(service.apply(getLoginUserId(), new CmsExpertConsultService.ApplyInput(
                request.expertName(), request.idCard(), request.phone(), request.professionalTitle(),
                request.company(), request.serviceFields(), request.introduction(),
                request.fixedPrice() == null ? 10000 : request.fixedPrice(), request.yearsOfExperience())));
    }

    @GetMapping("/expert/apply/me")
    @Operation(summary = "读取我的专家入驻申请")
    public CommonResult<CmsExpertConsultService.ExpertApplication> myApplication() {
        return success(service.myApplication(getLoginUserId()));
    }

    @GetMapping("/expert/apply/list")
    @Operation(summary = "管理端读取专家入驻申请")
    @PreAuthorize("@ss.hasPermission('cms:expert:apply:audit')")
    public CommonResult<List<CmsExpertConsultService.ExpertApplication>> listApplications(
            @RequestParam(required = false) Integer auditStatus) {
        return success(service.listApplications(auditStatus));
    }

    @PostMapping("/expert/apply/audit")
    @Operation(summary = "审核专家入驻申请（通过 / 驳回）")
    @PreAuthorize("@ss.hasPermission('cms:expert:apply:audit')")
    public CommonResult<CmsExpertConsultService.ExpertApplication> auditApplication(
            @RequestParam long id, @RequestParam boolean pass,
            @RequestParam(required = false) String remark) {
        return success(service.auditApplication(id, pass, remark, getLoginUserId()));
    }

    @GetMapping("/expert/me")
    @Operation(summary = "读取当前账号的专家身份")
    public CommonResult<CmsExpertConsultService.Expert> myExpert() {
        return success(service.findEnabledExpertByUser(getLoginUserId()));
    }

    @GetMapping("/expert/list")
    @Operation(summary = "管理端读取专家资料")
    @PreAuthorize("@ss.hasPermission('cms:expert:info')")
    public CommonResult<List<CmsExpertConsultService.Expert>> listExperts(@RequestParam(required = false) Integer auditStatus) {
        return success(service.listExperts(auditStatus, false));
    }

    @PostMapping("/order/create")
    @Operation(summary = "创建专家咨询待支付订单")
    public CommonResult<CmsExpertConsultService.Order> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return success(service.createOrder(getLoginUserId(), request.expertId(), request.subject(), request.question(),
                request.appointmentTime(), ServletUtils.getClientIP()));
    }

    @PostMapping("/order/update-paid")
    @PermitAll
    @Operation(summary = "支付成功回调（支付模块调用）")
    public CommonResult<Boolean> updatePaid(@RequestBody PayOrderNotifyReqDTO reqDTO) {
        service.markPaid(reqDTO.getMerchantOrderId(), reqDTO.getPayOrderId());
        return success(true);
    }

    @PostMapping("/order/update-refunded")
    @PermitAll
    @Operation(summary = "退款成功回调（支付模块调用）")
    public CommonResult<Boolean> updateRefunded(@RequestBody PayRefundNotifyReqDTO reqDTO) {
        service.markRefunded(reqDTO.getMerchantOrderId());
        return success(true);
    }

    @GetMapping("/order/my")
    @Operation(summary = "读取我的咨询订单")
    public CommonResult<List<CmsExpertConsultService.Order>> myOrders() {
        return success(service.myOrders(getLoginUserId()));
    }

    @GetMapping("/order/expert-requests")
    @Operation(summary = "专家读取发给自己的咨询请求")
    public CommonResult<List<CmsExpertConsultService.Order>> expertRequests() {
        return success(service.expertRequests(getLoginUserId()));
    }

    @GetMapping("/order/contact")
    @Operation(summary = "专家读取咨询人联系方式与 IM 会话占位信息")
    public CommonResult<CmsExpertConsultService.Contact> expertContact(@RequestParam long orderId) {
        return success(service.expertContact(orderId, getLoginUserId()));
    }

    @GetMapping("/order/im-session")
    @Operation(summary = "咨询双方读取或补建一对一 IM 会话")
    public CommonResult<CmsExpertConsultService.Contact> consultationSession(@RequestParam long orderId) {
        return success(service.consultationSession(orderId, getLoginUserId()));
    }

    @GetMapping("/order/list")
    @Operation(summary = "管理端读取全部咨询订单")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsExpertConsultService.Order>> listOrders() {
        return success(service.allOrders());
    }

    @PostMapping("/order/action")
    @Operation(summary = "执行接单、开始、完成、取消或拒单动作")
    public CommonResult<CmsExpertConsultService.Order> actionOrder(@RequestParam long orderId,
            @Valid @RequestBody OrderActionRequest request) {
        return success(service.action(orderId, getLoginUserId(), request.action(), request.reason()));
    }

    @PostMapping("/order/evaluate")
    @Operation(summary = "评价已完成咨询")
    public CommonResult<CmsExpertConsultService.Evaluation> evaluateOrder(@RequestParam long orderId,
            @Valid @RequestBody OrderEvaluationRequest request) {
        return success(service.evaluate(orderId, getLoginUserId(), request.rating(), request.content()));
    }

    @GetMapping("/settlement/list")
    @Operation(summary = "读取专家结算单")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsExpertConsultService.Settlement>> settlements(@RequestParam(required = false) Integer status) {
        return success(service.settlements(status));
    }

    @PostMapping("/settlement/settle")
    @Operation(summary = "登记专家线下结算凭证")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsExpertConsultService.Settlement> settle(@RequestParam long settlementId,
            @Valid @RequestBody SettlementRequest request) {
        return success(service.settle(settlementId, getLoginUserId(), request.proofUrl(), request.proofNo(), request.remark()));
    }

    @PostMapping("/settlement/reject")
    @Operation(summary = "驳回专家结算单")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsExpertConsultService.Settlement> rejectSettlement(@RequestParam long settlementId,
            @Valid @RequestBody SettlementRejectRequest request) {
        return success(service.rejectSettlement(settlementId, getLoginUserId(), request.remark()));
    }

    public record ExpertApplyRequest(@NotBlank @Size(max = 64) String expertName,
            @NotBlank @Size(max = 32) String idCard,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 64) String professionalTitle,
            @Size(max = 128) String company,
            @NotBlank @Size(max = 500) String serviceFields,
            @Size(max = 4000) String introduction,
            @Min(1) Integer fixedPrice,
            @Min(0) @Max(60) Integer yearsOfExperience) { }

    public record ExpertInviteRequest(@NotNull Long expertUserId,
            @NotBlank @Size(max = 64) String expertName,
            @Size(max = 64) String professionalTitle, @Size(max = 128) String company,
            @NotBlank @Size(max = 500) String serviceFields, @Size(max = 4000) String introduction,
            @Min(1) int fixedPrice) { }

    public record OrderCreateRequest(@NotNull Long expertId, @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String question,
            @NotNull @Future LocalDateTime appointmentTime) { }

    public record OrderActionRequest(@NotBlank @Size(max = 32) String action,
            @Size(max = 500) String reason) { }

    public record OrderEvaluationRequest(@Min(1) @Max(5) int rating,
            @Size(max = 1000) String content) { }

    public record SettlementRequest(@NotBlank @Size(max = 500) String proofUrl,
            @NotBlank @Size(max = 128) String proofNo, @Size(max = 500) String remark) { }

    public record SettlementRejectRequest(@NotBlank @Size(max = 500) String remark) { }
}
