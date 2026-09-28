package cn.iocoder.txgy.module.bidding.controller.admin.prequal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalApplyDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalApplyService;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 资格预审（招标方发起 + 投标方申请 + 资格审查委员会审查）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6；《实施条例》第15/16/17/19条。
 */
@Tag(name = "管理后台 - 资格预审")
@RestController
@RequestMapping("/bidding/prequal")
@Validated
public class PrequalController {

    @Resource
    private PrequalService prequalService;
    @Resource
    private PrequalApplyService prequalApplyService;
    /** 资格预审材料集附件（契约《投标与资格预审多文件化-2026-10-02》R3：bizType=PREQUAL_APPLY） */
    @Resource
    private AttachmentService attachmentService;

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:save')")
    @Operation(summary = "创建/修改资格预审（草稿态）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody PrequalSaveReqVO reqVO) {
        return success(prequalService.save(reqVO));
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:publish')")
    @Operation(summary = "发布资格预审（DRAFT→PUBLISHED，校验申请期≥5日）")
    @Idempotent
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        prequalService.publish(id);
        return success(true);
    }

    @PostMapping("/close")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:close')")
    @Operation(summary = "关闭资格预审（→CLOSED）")
    @Idempotent
    public CommonResult<Boolean> close(@RequestParam("id") Long id) {
        prequalService.close(id);
        return success(true);
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:query')")
    @Operation(summary = "资格预审详情")
    public CommonResult<PrequalRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(prequalService.get(id), PrequalRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:query')")
    @Operation(summary = "资格预审分页")
    public CommonResult<PageResult<PrequalRespVO>> page(@Valid PrequalPageReqVO reqVO) {
        return success(BeanUtils.toBean(prequalService.page(reqVO), PrequalRespVO.class));
    }

    @PostMapping("/apply")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:apply')")
    @Operation(summary = "提交资格预审申请（投标方，申请截止前可提交）")
    @Idempotent
    public CommonResult<Long> apply(@Valid @RequestBody PrequalApplyReqVO reqVO) {
        return success(prequalApplyService.apply(reqVO));
    }

    /** 资格预审材料集附件填充（契约多文件化 R3；可为空数组）。 */
    private void fillApplyAttachments(List<PrequalApplyRespVO> vos) {
        if (vos == null) {
            return;
        }
        for (PrequalApplyRespVO vo : vos) {
            if (vo.getId() != null) {
                vo.setAttachments(attachmentService.list("PREQUAL_APPLY", String.valueOf(vo.getId())));
            }
        }
    }

    @PostMapping("/review")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:review')")
    @Operation(summary = "审查资格预审申请（资格审查委员会，通过/不通过）")
    @Idempotent
    public CommonResult<Boolean> review(@Valid @RequestBody PrequalReviewReqVO reqVO) {
        prequalApplyService.review(reqVO);
        return success(true);
    }

    @GetMapping("/apply-list")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:query')")
    @Operation(summary = "资格预审申请列表（按预审）")
    public CommonResult<List<PrequalApplyRespVO>> applyList(@RequestParam("prequalId") Long prequalId) {
        List<PrequalApplyDO> list = prequalApplyService.listByPrequal(prequalId);
        List<PrequalApplyRespVO> vos = BeanUtils.toBean(list, PrequalApplyRespVO.class);
        fillApplyAttachments(vos);
        return success(vos);
    }

    @GetMapping("/apply-page")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:query')")
    @Operation(summary = "资格预审申请分页")
    public CommonResult<PageResult<PrequalApplyRespVO>> applyPage(@Valid PrequalApplyPageReqVO reqVO) {
        PageResult<PrequalApplyRespVO> result = BeanUtils.toBean(prequalApplyService.page(reqVO), PrequalApplyRespVO.class);
        if (result != null && result.getList() != null) {
            fillApplyAttachments(result.getList());
        }
        return success(result);
    }
}
