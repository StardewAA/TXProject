package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit.HrmSalaryRateLimitRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit.HrmSalaryRateLimitSaveReqVO;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 比例上限配置：按计薪项 / 参保项分别设置比例上限，考勤计薪规则与参保方案保存时按此校验。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 比例上限配置")
@RestController
@RequestMapping("/hrm/salary/rate-limit")
@Validated
public class HrmSalaryRateLimitController {

    @Resource
    private HrmSalaryRateLimitService rateLimitService;

    @GetMapping("/list")
    @Operation(summary = "获得比例上限配置列表")
    @Parameter(name = "bizType", description = "业务类型：1 考勤计薪规则 2 参保方案", example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:salary:rate-limit:query')")
    public CommonResult<List<HrmSalaryRateLimitRespVO>> getRateLimitList(
            @RequestParam(value = "bizType", required = false) Integer bizType) {
        return success(BeanUtils.toBean(rateLimitService.getRateLimitList(bizType),
                HrmSalaryRateLimitRespVO.class));
    }

    @PutMapping("/update")
    @Operation(summary = "修改比例上限配置")
    @PreAuthorize("@ss.hasPermission('hrm:salary:rate-limit:update')")
    public CommonResult<Boolean> updateRateLimit(@Valid @RequestBody HrmSalaryRateLimitSaveReqVO reqVO) {
        rateLimitService.updateRateLimit(reqVO);
        return success(true);
    }

}
