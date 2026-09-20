package cn.iocoder.txgy.module.hrm.controller.admin.policy;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicyPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicyRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicySaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.policy.HrmPolicyDO;
import cn.iocoder.txgy.module.hrm.service.policy.HrmPolicyService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 政策库")
@RestController
@RequestMapping("/hrm/policy")
@Validated
public class HrmPolicyController {

    @Resource
    private HrmPolicyService policyService;

    @PostMapping("/create")
    @Operation(summary = "新增政策")
    @PreAuthorize("@ss.hasPermission('hrm:policy:create')")
    public CommonResult<Long> createPolicy(@Valid @RequestBody HrmPolicySaveReqVO reqVO) {
        return success(policyService.createPolicy(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改政策")
    @PreAuthorize("@ss.hasPermission('hrm:policy:update')")
    public CommonResult<Boolean> updatePolicy(@Valid @RequestBody HrmPolicySaveReqVO reqVO) {
        policyService.updatePolicy(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除政策")
    @Parameter(name = "id", description = "政策编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:policy:delete')")
    public CommonResult<Boolean> deletePolicy(@RequestParam("id") Long id) {
        policyService.deletePolicy(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得政策")
    @Parameter(name = "id", description = "政策编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:policy:query')")
    public CommonResult<HrmPolicyRespVO> getPolicy(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(policyService.getPolicy(id), HrmPolicyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得政策分页")
    @PreAuthorize("@ss.hasPermission('hrm:policy:query')")
    public CommonResult<PageResult<HrmPolicyRespVO>> getPolicyPage(@Valid HrmPolicyPageReqVO reqVO) {
        PageResult<HrmPolicyDO> page = policyService.getPolicyPage(reqVO);
        return success(BeanUtils.toBean(page, HrmPolicyRespVO.class));
    }
}
