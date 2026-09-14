package cn.iocoder.txgy.module.cms.controller.admin.Material;

import cn.iocoder.txgy.module.cms.controller.admin.Material.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Material.CmsMaterialDO;
import cn.iocoder.txgy.module.cms.service.material.CmsMaterialService;
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

@Tag(name = "管理后台 - 材料参数数据库")
@RestController
@RequestMapping("/cms/material")
public class CmsMaterialController {

    @Resource
    private CmsMaterialService materialService;

    @PostMapping("/create")
    @Operation(summary = "创建材料参数数据库")
    @CmsOperateLog(type = "材料参数数据库", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsMaterialSaveReqVO createReqVO) {
        return success(materialService.createMaterial(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新材料参数数据库")
    @CmsOperateLog(type = "材料参数数据库", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsMaterialSaveReqVO updateReqVO) {
        materialService.updateMaterial(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除材料参数数据库")
    @CmsOperateLog(type = "材料参数数据库", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        materialService.deleteMaterial(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得材料参数数据库")
    public CommonResult<CmsMaterialRespVO> get(@RequestParam("id") Long id) {
        CmsMaterialDO record = materialService.getMaterial(id);
        return success(BeanUtils.toBean(record, CmsMaterialRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得材料参数数据库分页")
    public CommonResult<PageResult<CmsMaterialRespVO>> page(CmsMaterialPageReqVO pageReqVO) {
        PageResult<CmsMaterialDO> pageResult = materialService.getMaterialPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsMaterialRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得材料参数数据库全量列表（下拉选择用）")
    public CommonResult<List<CmsMaterialRespVO>> listAll() {
        List<CmsMaterialDO> list = materialService.getMaterialList();
        return success(BeanUtils.toBean(list, CmsMaterialRespVO.class));
    }
}
