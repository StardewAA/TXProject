package cn.iocoder.txgy.module.cms.controller.admin.Quota;

import cn.iocoder.txgy.module.cms.controller.admin.Quota.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Quota.CmsQuotaDO;
import cn.iocoder.txgy.module.cms.service.Quota.CmsQuotaService;
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

@Tag(name = "管理后台 - 资质定额资料库")
@RestController
@RequestMapping("/cms/quota")
public class CmsQuotaController {

    @Resource
    private CmsQuotaService quotaService;

    @PostMapping("/create")
    @Operation(summary = "创建资质定额资料库")
    @CmsOperateLog(type = "资质定额资料库", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsQuotaSaveReqVO createReqVO) {
        return success(quotaService.createQuota(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新资质定额资料库")
    @CmsOperateLog(type = "资质定额资料库", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsQuotaSaveReqVO updateReqVO) {
        quotaService.updateQuota(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除资质定额资料库")
    @CmsOperateLog(type = "资质定额资料库", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        quotaService.deleteQuota(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得资质定额资料库")
    public CommonResult<CmsQuotaRespVO> get(@RequestParam("id") Long id) {
        CmsQuotaDO record = quotaService.getQuota(id);
        return success(BeanUtils.toBean(record, CmsQuotaRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得资质定额资料库分页")
    public CommonResult<PageResult<CmsQuotaRespVO>> page(CmsQuotaPageReqVO pageReqVO) {
        PageResult<CmsQuotaDO> pageResult = quotaService.getQuotaPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsQuotaRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得资质定额资料库全量列表（下拉选择用）")
    public CommonResult<List<CmsQuotaRespVO>> listAll() {
        List<CmsQuotaDO> list = quotaService.getQuotaList();
        return success(BeanUtils.toBean(list, CmsQuotaRespVO.class));
    }
}
