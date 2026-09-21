package cn.iocoder.txgy.module.hrm.controller.admin.contract;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 合同签署记录")
@RestController
@RequestMapping("/hrm/contract/sign-record")
@Validated
public class HrmContractSignRecordController {

    @Resource
    private HrmContractSignRecordService signRecordService;
    @Resource
    private HrmEmployeeService employeeService;

    @PostMapping("/create")
    @Operation(summary = "创建合同签署记录")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:create')")
    public CommonResult<Long> createSignRecord(@Valid @RequestBody HrmContractSignRecordSaveReqVO reqVO) {
        return success(signRecordService.createSignRecord(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新合同签署记录")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:update')")
    public CommonResult<Boolean> updateSignRecord(@Valid @RequestBody HrmContractSignRecordSaveReqVO reqVO) {
        signRecordService.updateSignRecord(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除合同签署记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:delete')")
    public CommonResult<Boolean> deleteSignRecord(@RequestParam("id") Long id) {
        signRecordService.deleteSignRecord(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得合同签署记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:query')")
    public CommonResult<HrmContractSignRecordRespVO> getSignRecord(@RequestParam("id") Long id) {
        HrmContractSignRecordDO record = signRecordService.validateSignRecordExists(id);
        List<HrmContractSignRecordRespVO> list = buildRespList(List.of(record));
        return success(list.get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同签署记录分页")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:query')")
    public CommonResult<PageResult<HrmContractSignRecordRespVO>> getSignRecordPage(
            @Valid HrmContractSignRecordPageReqVO pageReqVO) {
        PageResult<HrmContractSignRecordDO> pageResult = signRecordService.getSignRecordPage(pageReqVO);
        return success(new PageResult<>(buildRespList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/list-by-contract")
    @Operation(summary = "获得指定合同的签署记录列表")
    @Parameter(name = "contractId", description = "合同编号", required = true, example = "8")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:query')")
    public CommonResult<List<HrmContractSignRecordRespVO>> getSignRecordListByContract(
            @RequestParam("contractId") Long contractId) {
        List<HrmContractSignRecordDO> list = signRecordService.getSignRecordListByContractId(contractId);
        return success(buildRespList(list));
    }

    @PutMapping("/urge")
    @Operation(summary = "批量催办未签署合同（给员工发送待签署通知）")
    @PreAuthorize("@ss.hasPermission('hrm:contract:sign:urge')")
    public CommonResult<Integer> urgeSignRecords(@RequestBody List<Long> ids) {
        return success(signRecordService.urgeSignRecords(ids));
    }

    /**
     * 补齐员工姓名与工号，便于签署记录台账直接展示
     */
    private List<HrmContractSignRecordRespVO> buildRespList(List<HrmContractSignRecordDO> records) {
        List<HrmContractSignRecordRespVO> list = BeanUtils.toBean(records, HrmContractSignRecordRespVO.class);
        if (CollUtil.isEmpty(list)) {
            return list;
        }
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(
                list.stream().map(HrmContractSignRecordRespVO::getEmployeeId)
                        .filter(java.util.Objects::nonNull).toList());
        list.forEach(item -> {
            HrmEmployeeDO employee = employeeMap.get(item.getEmployeeId());
            if (employee != null) {
                item.setEmployeeName(employee.getName());
                item.setJobNumber(employee.getJobNumber());
            }
            if (CollUtil.isEmpty(item.getFileUrls())) {
                item.setFileUrls(List.of());
            }
        });
        return list;
    }

}
