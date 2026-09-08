package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindRecordPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindRecordRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindReqVO;
import cn.iocoder.txgy.module.training.service.plan.RemindServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 培训班督促提醒")
@RestController
@RequestMapping("/training/plan/class/remind")
@Validated
public class PlanRemindController {

    @Resource
    private RemindServiceImpl remindService;

    @PostMapping("/send")
    @Operation(summary = "一键督促：对未完成学习任务的学员批量发送站内信提醒（含发送留痕）")
    @PreAuthorize("@ss.hasPermission('training:plan:remind:send')")
    public CommonResult<Integer> sendRemind(@Valid @RequestBody RemindReqVO reqVO) {
        return success(remindService.remind(reqVO));
    }

    @GetMapping("/record-page")
    @Operation(summary = "督促记录分页")
    @PreAuthorize("@ss.hasPermission('training:plan:remind')")
    public CommonResult<PageResult<RemindRecordRespVO>> getRemindRecordPage(@Validated RemindRecordPageReqVO pageReqVO) {
        return success(remindService.getRemindRecordPage(pageReqVO));
    }

}
