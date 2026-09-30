package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.piece.HrmSalaryPieceRecordDO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.salary.piece.HrmSalaryPieceRecordService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 计件计量 Controller
 *
 * <p>一线工人计件工资的数据来源：按月按员工录入「工程量 × 计件单价」，
 * 工资表核算时自动汇总进「计件工资」薪资项（金额由后端计算，不信任前端传值）。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 计件计量")
@RestController
@RequestMapping("/hrm/salary/piece-record")
@Validated
public class HrmSalaryPieceRecordController {

    @Resource
    private HrmSalaryPieceRecordService pieceRecordService;
    @Resource
    private HrmEmployeeService employeeService;

    @PostMapping("/save")
    @Operation(summary = "保存计件计量明细", description = "无编号新增、有编号修改；计件金额由后端按 工程量 × 单价 计算")
    @PreAuthorize("@ss.hasPermission('hrm:salary:piece-record:update')")
    public CommonResult<Long> savePieceRecord(@Valid @RequestBody HrmSalaryPieceRecordSaveReqVO reqVO) {
        return success(pieceRecordService.savePieceRecord(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除计件计量明细")
    @Parameter(name = "id", description = "明细编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:salary:piece-record:update')")
    public CommonResult<Boolean> deletePieceRecord(@RequestParam("id") Long id) {
        pieceRecordService.deletePieceRecord(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得计件计量明细分页")
    @PreAuthorize("@ss.hasPermission('hrm:salary:piece-record:query')")
    public CommonResult<PageResult<HrmSalaryPieceRecordRespVO>> getPieceRecordPage(
            @Valid HrmSalaryPieceRecordPageReqVO pageReqVO) {
        PageResult<HrmSalaryPieceRecordDO> pageResult = pieceRecordService.getPieceRecordPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "获得某员工某月的计件明细")
    @PreAuthorize("@ss.hasPermission('hrm:salary:piece-record:query')")
    public CommonResult<List<HrmSalaryPieceRecordRespVO>> getPieceRecordList(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("year") Integer year,
            @RequestParam("month") Integer month) {
        return success(buildRespVOList(pieceRecordService.getPieceRecordList(employeeId, year, month)));
    }

    private List<HrmSalaryPieceRecordRespVO> buildRespVOList(List<HrmSalaryPieceRecordDO> list) {
        List<HrmSalaryPieceRecordRespVO> result = BeanUtils.toBean(list, HrmSalaryPieceRecordRespVO.class);
        List<Long> employeeIds = cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList(
                list, HrmSalaryPieceRecordDO::getEmployeeId);
        java.util.Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(employeeIds);
        result.forEach(item -> {
            HrmEmployeeDO employee = employeeMap.get(item.getEmployeeId());
            if (employee != null) {
                item.setEmployeeName(employee.getName()).setJobNumber(employee.getJobNumber());
            }
        });
        return result;
    }

}
