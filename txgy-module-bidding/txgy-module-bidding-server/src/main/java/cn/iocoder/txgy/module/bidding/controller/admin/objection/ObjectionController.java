package cn.iocoder.txgy.module.bidding.controller.admin.objection;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionReplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.objection.ObjectionDO;
import cn.iocoder.txgy.module.bidding.service.objection.ObjectionService;
import io.swagger.v3.oas.annotations.Operation;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 投标异议（条例第22条：资格预审/招标文件异议，提出时限 + 3 日答复）。
 *
 * <p>提出异议由投标方调用（校验截止时限），答复由招标方调用（记录答复事实）。
 */
@Tag(name = "管理后台 - 投标异议")
@RestController
@RequestMapping("/bidding/objection")
@Validated
public class ObjectionController {

    @Resource
    private ObjectionService objectionService;

    @PostMapping("/propose")
    @PreAuthorize("@ss.hasPermission('bidding:objection:propose')")
    @Operation(summary = "提出异议（校验截止时限：资格预审文件申请截止前2日/招标文件投标截止前10日）")
    @Idempotent
    public CommonResult<Long> propose(@Valid @RequestBody ObjectionSaveReqVO reqVO) {
        return success(objectionService.propose(reqVO));
    }

    @PostMapping("/reply")
    @PreAuthorize("@ss.hasPermission('bidding:objection:reply')")
    @Operation(summary = "答复异议（收到后3日内；已答复不可重复答复）")
    @Idempotent
    public CommonResult<Long> reply(@Valid @RequestBody ObjectionReplyReqVO reqVO) {
        return success(objectionService.reply(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:objection:query')")
    @Operation(summary = "异议详情")
    public CommonResult<ObjectionRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(objectionService.get(id), ObjectionRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:objection:query')")
    @Operation(summary = "异议台账分页（含答复超期标记 replyOverdue）")
    public CommonResult<PageResult<ObjectionRespVO>> page(@Valid ObjectionPageReqVO reqVO) {
        PageResult<ObjectionDO> page = objectionService.page(reqVO);
        return success(BeanUtils.toBean(page, ObjectionRespVO.class));
    }
}
