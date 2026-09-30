package cn.iocoder.txgy.module.hrm.controller.admin.sandbox;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.sandbox.vo.HrmSandboxRecordRespVO;
import cn.iocoder.txgy.module.hrm.service.sandbox.HrmSandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - HRM 沙箱（演示/培训）数据（I-025）
 *
 * <p>实施/培训前点「生成演示数据」，跑完主线后点「一键清场」把演示数据清干净，
 * 不必再人工回忆逐条删除。权限沿用员工管理权限（HR 管理员级）。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 沙箱数据")
@RestController
@RequestMapping("/hrm/sandbox")
@Validated
public class HrmSandboxController {

    @Resource
    private HrmSandboxService sandboxService;

    @GetMapping("/list")
    @Operation(summary = "获得沙箱数据登记列表")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<List<HrmSandboxRecordRespVO>> getSandboxRecordList() {
        return success(BeanUtils.toBean(sandboxService.getSandboxRecordList(), HrmSandboxRecordRespVO.class));
    }

    @PostMapping("/generate")
    @Operation(summary = "生成演示数据剧本",
            description = "创建 1 个草稿招聘职位 + 1 条演示请假单（挂在在职员工上），并登记进沙箱清单")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    @ApiAccessLog(operateType = OperateTypeEnum.CREATE)
    public CommonResult<Integer> generateDemoScript() {
        return success(sandboxService.generateDemoScript());
    }

    @DeleteMapping("/clear")
    @Operation(summary = "一键清场",
            description = "把沙箱清单里的演示数据逐条软删除，并清空登记")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    @ApiAccessLog(operateType = OperateTypeEnum.DELETE)
    public CommonResult<Integer> clearSandbox() {
        return success(sandboxService.clearSandbox());
    }

}
