package cn.iocoder.txgy.module.cms.controller.admin.ProcessResource;

import cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource.CmsProcessResourceDO;
import cn.iocoder.txgy.module.cms.service.ProcessResource.CmsProcessResourceService;
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

@Tag(name = "管理后台 - 工艺工法与案例库")
@RestController
@RequestMapping("/cms/process")
public class CmsProcessResourceController {

    @Resource
    private CmsProcessResourceService processService;

    @PostMapping("/create")
    @Operation(summary = "创建工艺工法与案例库")
    @CmsOperateLog(type = "工艺工法与案例库", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsProcessResourceSaveReqVO createReqVO) {
        return success(processService.createProcessResource(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工艺工法与案例库")
    @CmsOperateLog(type = "工艺工法与案例库", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsProcessResourceSaveReqVO updateReqVO) {
        processService.updateProcessResource(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工艺工法与案例库")
    @CmsOperateLog(type = "工艺工法与案例库", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        processService.deleteProcessResource(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工艺工法与案例库")
    public CommonResult<CmsProcessResourceRespVO> get(@RequestParam("id") Long id) {
        CmsProcessResourceDO record = processService.getProcessResource(id);
        return success(BeanUtils.toBean(record, CmsProcessResourceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工艺工法与案例库分页")
    public CommonResult<PageResult<CmsProcessResourceRespVO>> page(CmsProcessResourcePageReqVO pageReqVO) {
        PageResult<CmsProcessResourceDO> pageResult = processService.getProcessResourcePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsProcessResourceRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得工艺工法与案例库全量列表（下拉选择用）")
    public CommonResult<List<CmsProcessResourceRespVO>> listAll() {
        List<CmsProcessResourceDO> list = processService.getProcessResourceList();
        return success(BeanUtils.toBean(list, CmsProcessResourceRespVO.class));
    }
}
