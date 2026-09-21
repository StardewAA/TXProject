package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount.HrmDeptHeadcountDO;
import cn.iocoder.txgy.module.hrm.service.employee.headcount.HrmDeptHeadcountService;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
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
 * 部门编制：按部门设定编制人数，与在册人数对比得出缺口/超编。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 部门编制")
@RestController
@RequestMapping("/hrm/employee/headcount")
@Validated
public class HrmDeptHeadcountController {

    @Resource
    private HrmDeptHeadcountService deptHeadcountService;
    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "新增部门编制")
    @PreAuthorize("@ss.hasPermission('hrm:employee:headcount:create')")
    public CommonResult<Long> createDeptHeadcount(@Valid @RequestBody HrmDeptHeadcountSaveReqVO createReqVO) {
        return success(deptHeadcountService.createDeptHeadcount(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改部门编制")
    @PreAuthorize("@ss.hasPermission('hrm:employee:headcount:update')")
    public CommonResult<Boolean> updateDeptHeadcount(@Valid @RequestBody HrmDeptHeadcountSaveReqVO updateReqVO) {
        deptHeadcountService.updateDeptHeadcount(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除部门编制")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:headcount:delete')")
    public CommonResult<Boolean> deleteDeptHeadcount(@RequestParam("id") Long id) {
        deptHeadcountService.deleteDeptHeadcount(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得部门编制")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:headcount:query')")
    public CommonResult<HrmDeptHeadcountRespVO> getDeptHeadcount(@RequestParam("id") Long id) {
        HrmDeptHeadcountDO headcount = deptHeadcountService.getDeptHeadcount(id);
        if (headcount == null) {
            return success(null);
        }
        return success(buildRespVOList(List.of(headcount)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得部门编制分页")
    @PreAuthorize("@ss.hasPermission('hrm:employee:headcount:query')")
    public CommonResult<PageResult<HrmDeptHeadcountRespVO>> getDeptHeadcountPage(
            @Valid HrmDeptHeadcountPageReqVO pageReqVO) {
        PageResult<HrmDeptHeadcountDO> pageResult = deptHeadcountService.getDeptHeadcountPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<HrmDeptHeadcountRespVO> buildRespVOList(List<HrmDeptHeadcountDO> list) {
        if (CollUtil.isEmpty(list)) {
            return List.of();
        }
        Set<Long> deptIds = list.stream().map(HrmDeptHeadcountDO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> deptMap = deptIds.isEmpty() ? Map.of() : deptApi.getDeptMap(deptIds);
        return BeanUtils.toBean(list, HrmDeptHeadcountRespVO.class, vo -> {
            DeptRespDTO dept = vo.getDeptId() == null ? null : deptMap.get(vo.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getName());
            long onboard = deptHeadcountService.getOnboardCount(vo.getDeptId());
            vo.setOnboardCount(onboard);
            long headcount = vo.getHeadcount() == null ? 0 : vo.getHeadcount();
            vo.setGapCount(headcount - onboard);
            vo.setOverStaffed(headcount > 0 && onboard > headcount);
        });
    }

}
