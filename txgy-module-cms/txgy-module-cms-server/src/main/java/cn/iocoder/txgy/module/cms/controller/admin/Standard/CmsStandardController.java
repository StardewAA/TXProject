package cn.iocoder.txgy.module.cms.controller.admin.Standard;

import cn.iocoder.txgy.module.cms.controller.admin.Standard.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Standard.CmsStandardDO;
import cn.iocoder.txgy.module.cms.service.Standard.CmsStandardService;
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

@Tag(name = "管理后台 - 行业标准规范库")
@RestController
@RequestMapping("/cms/standard")
public class CmsStandardController {

    @Resource
    private CmsStandardService standardService;

    @PostMapping("/create")
    @Operation(summary = "创建行业标准规范库")
    @CmsOperateLog(type = "行业标准规范库", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsStandardSaveReqVO createReqVO) {
        return success(standardService.createStandard(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新行业标准规范库")
    @CmsOperateLog(type = "行业标准规范库", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsStandardSaveReqVO updateReqVO) {
        standardService.updateStandard(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除行业标准规范库")
    @CmsOperateLog(type = "行业标准规范库", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        standardService.deleteStandard(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得行业标准规范库")
    public CommonResult<CmsStandardRespVO> get(@RequestParam("id") Long id) {
        CmsStandardDO record = standardService.getStandard(id);
        return success(BeanUtils.toBean(record, CmsStandardRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得行业标准规范库分页")
    public CommonResult<PageResult<CmsStandardRespVO>> page(CmsStandardPageReqVO pageReqVO) {
        PageResult<CmsStandardDO> pageResult = standardService.getStandardPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsStandardRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得行业标准规范库全量列表（下拉选择用）")
    public CommonResult<List<CmsStandardRespVO>> listAll() {
        List<CmsStandardDO> list = standardService.getStandardList();
        return success(BeanUtils.toBean(list, CmsStandardRespVO.class));
    }
}
