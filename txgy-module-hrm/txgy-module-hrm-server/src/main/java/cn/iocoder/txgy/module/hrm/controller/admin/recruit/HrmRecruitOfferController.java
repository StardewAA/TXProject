package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferApprovalCallbackReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘 Offer")
@RestController
@RequestMapping("/hrm/recruit/offer")
@Validated
public class HrmRecruitOfferController {
    @Resource private HrmRecruitOfferService offerService;

    @PostMapping("/create")
    @Operation(summary = "创建 Offer")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:create')")
    public CommonResult<Long> create(@Valid @RequestBody HrmRecruitOfferSaveReqVO reqVO) {
        return success(offerService.create(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:query')")
    public CommonResult<HrmRecruitOfferDO> get(@RequestParam("id") Long id) {
        return success(offerService.get(id));
    }

    /**
     * 按候选人查询 Offer 列表（候选人详情「Offer」Tab 用，阶段四 4.4）
     */
    @GetMapping("/list-by-candidate")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:query')")
    public CommonResult<List<HrmRecruitOfferDO>> getOfferListByCandidate(
            @RequestParam("candidateId") Long candidateId) {
        return success(offerService.getOfferListByCandidateId(candidateId));
    }

    @PutMapping("/submit")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        offerService.submit(id); return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "Offer 人工审批（通过 / 驳回）")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:approve')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id,
                                          @RequestParam("approved") Boolean approved,
                                          @RequestParam(value = "reason", required = false) String reason) {
        offerService.approve(id, approved, reason); return success(true);
    }

    @PutMapping("/approval-callback")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:approve')")
    public CommonResult<Boolean> approvalCallback(@RequestParam("id") Long id,
                                                   @RequestParam("processInstanceId") String processInstanceId,
                                                   @RequestParam("approvalVersion") Integer approvalVersion,
                                                   @RequestParam("approved") @NotNull Boolean approved,
                                                   @RequestParam(value = "reason", required = false) String reason) {
        offerService.approvalCallback(id, processInstanceId, approvalVersion, approved, reason);
        return success(true);
    }

    @PostMapping("/bpm-callback")
    @Operation(summary = "BPM 回调 Offer 审批结果")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:approve')")
    public CommonResult<Boolean> bpmCallback(
            @Valid @RequestBody HrmRecruitOfferApprovalCallbackReqVO reqVO) {
        offerService.approvalCallback(reqVO.getOfferId(), reqVO.getProcessInstanceId(),
                reqVO.getApprovalVersion(), reqVO.getApproved(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/withdraw")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:withdraw')")
    public CommonResult<Boolean> withdraw(@RequestParam("id") Long id,
                                          @RequestParam(value = "reason", required = false) String reason) {
        offerService.withdraw(id, reason); return success(true);
    }

    @PutMapping("/send")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:send')")
    public CommonResult<Boolean> send(@RequestParam(value = "id", required = false) Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        // 兼容 query 与 body（{"id":123}）两种传参，避免对接时"请求参数缺失:id"
        offerService.send(resolveId(id, body)); return success(true);
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:confirm')")
    public CommonResult<Boolean> confirm(@RequestParam(value = "id", required = false) Long id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        offerService.confirm(resolveId(id, body)); return success(true);
    }

    /**
     * 兼容 query 与 body 两种传参：缺少入参时返回统一的 400，而不是 NPE
     */
    private Long resolveId(Long id, Map<String, Object> body) {
        if (id != null) {
            return id;
        }
        Object fromBody = body == null ? null : body.get("id");
        if (fromBody != null) {
            return Long.valueOf(String.valueOf(fromBody));
        }
        throw exception(BAD_REQUEST);
    }

    @PutMapping("/onboard")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:onboard')")
    public CommonResult<Long> onboard(@Valid @RequestBody HrmRecruitOfferOnboardReqVO reqVO) {
        return success(offerService.onboard(reqVO));
    }

    @PutMapping("/resend-contract-notice")
    @Operation(summary = "重新生成劳务合同文件并再次发送待签署通知")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:onboard')")
    public CommonResult<Boolean> resendContractNotice(@RequestParam("offerId") Long offerId) {
        offerService.resendContractNotice(offerId);
        return success(true);
    }

    @PutMapping("/resend-offer-notice")
    @Operation(summary = "重新发送 Offer 录用通知邮件（带录用通知书 PDF 附件）")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:send')")
    public CommonResult<Boolean> resendOfferNotice(@RequestParam("offerId") Long offerId) {
        offerService.resendOfferNotice(offerId);
        return success(true);
    }
}
