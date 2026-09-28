package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist.HrmEmployeeBlacklistDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.employee.blacklist.HrmEmployeeBlacklistService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 员工黑名单：约束录用与再入职，避免"换个项目又进来"。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 员工黑名单")
@RestController
@RequestMapping("/hrm/employee/blacklist")
@Validated
public class HrmEmployeeBlacklistController {

    @Resource
    private HrmEmployeeBlacklistService blacklistService;
    @Resource
    private HrmEmployeeService employeeService;

    @PostMapping("/create")
    @Operation(summary = "新增黑名单")
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:create')")
    public CommonResult<Long> createBlacklist(@Valid @RequestBody HrmEmployeeBlacklistSaveReqVO createReqVO) {
        return success(blacklistService.createBlacklist(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改黑名单")
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:update')")
    public CommonResult<Boolean> updateBlacklist(@Valid @RequestBody HrmEmployeeBlacklistSaveReqVO updateReqVO) {
        blacklistService.updateBlacklist(updateReqVO);
        return success(true);
    }

    @PutMapping("/release")
    @Operation(summary = "解除黑名单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:update')")
    public CommonResult<Boolean> releaseBlacklist(@RequestParam("id") Long id) {
        blacklistService.releaseBlacklist(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除黑名单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:delete')")
    public CommonResult<Boolean> deleteBlacklist(@RequestParam("id") Long id) {
        blacklistService.deleteBlacklist(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得黑名单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:query')")
    public CommonResult<HrmEmployeeBlacklistRespVO> getBlacklist(@RequestParam("id") Long id) {
        HrmEmployeeBlacklistDO blacklist = blacklistService.getBlacklist(id);
        if (blacklist == null) {
            return success(null);
        }
        return success(buildRespVOList(List.of(blacklist)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得黑名单分页")
    @PreAuthorize("@ss.hasPermission('hrm:employee:blacklist:query')")
    public CommonResult<PageResult<HrmEmployeeBlacklistRespVO>> getBlacklistPage(
            @Valid HrmEmployeeBlacklistPageReqVO pageReqVO) {
        PageResult<HrmEmployeeBlacklistDO> pageResult = blacklistService.getBlacklistPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<HrmEmployeeBlacklistRespVO> buildRespVOList(List<HrmEmployeeBlacklistDO> list) {
        if (CollUtil.isEmpty(list)) {
            return List.of();
        }
        Set<Long> employeeIds = list.stream().map(HrmEmployeeBlacklistDO::getEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HrmEmployeeDO> employeeMap = employeeIds.isEmpty() ? Map.of()
                : employeeService.getEmployeeMap(employeeIds);
        return BeanUtils.toBean(list, HrmEmployeeBlacklistRespVO.class, vo -> {
            HrmEmployeeDO employee = vo.getEmployeeId() == null ? null : employeeMap.get(vo.getEmployeeId());
            if (employee != null) {
                vo.setJobNumber(employee.getJobNumber());
            }
            vo.setStatusName(Objects.equals(vo.getStatus(), 1) ? "生效中" : "已解除");
            vo.setSourceName(switch (vo.getSource() == null ? 1 : vo.getSource()) {
                case 2 -> "现场上报";
                case 3 -> "其他";
                default -> "HRM 手工登记";
            });
            if (StrUtil.isBlank(vo.getMobile())) {
                vo.setMobile(employee != null ? employee.getMobile() : null);
            }
        });
    }

}
