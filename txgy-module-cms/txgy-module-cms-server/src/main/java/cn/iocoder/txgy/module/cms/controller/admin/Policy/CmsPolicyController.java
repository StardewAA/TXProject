package cn.iocoder.txgy.module.cms.controller.admin.Policy;

import cn.iocoder.txgy.module.cms.controller.admin.Policy.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Policy.CmsPolicyDO;
import cn.iocoder.txgy.module.cms.service.Policy.CmsPolicyService;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.cms.framework.operatelog.CmsOperateLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 政策资讯")
@RestController
@RequestMapping("/cms/policy")
public class CmsPolicyController {

    @Resource
    private CmsPolicyService policyService;

    @PostMapping("/create")
    @Operation(summary = "创建政策资讯")
    @CmsOperateLog(type = "政策资讯", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsPolicySaveReqVO createReqVO) {
        return success(policyService.createPolicy(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新政策资讯")
    @CmsOperateLog(type = "政策资讯", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsPolicySaveReqVO updateReqVO) {
        policyService.updatePolicy(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除政策资讯")
    @CmsOperateLog(type = "政策资讯", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        policyService.deletePolicy(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得政策资讯")
    public CommonResult<CmsPolicyRespVO> get(@RequestParam("id") Long id) {
        CmsPolicyDO record = policyService.getPolicy(id);
        return success(BeanUtils.toBean(record, CmsPolicyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得政策资讯分页")
    public CommonResult<PageResult<CmsPolicyRespVO>> page(CmsPolicyPageReqVO pageReqVO) {
        PageResult<CmsPolicyDO> pageResult = policyService.getPolicyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsPolicyRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得政策资讯全量列表（下拉选择用）")
    public CommonResult<List<CmsPolicyRespVO>> listAll() {
        List<CmsPolicyDO> list = policyService.getPolicyList();
        return success(BeanUtils.toBean(list, CmsPolicyRespVO.class));
    }
}
