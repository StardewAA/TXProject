package cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail;

import cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.QuotaDetail.CmsQuotaDetailDO;
import cn.iocoder.txgy.module.cms.service.QuotaDetail.CmsQuotaDetailService;
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

@Tag(name = "管理后台 - 定额明细表")
@RestController
@RequestMapping("/cms/quota-detail")
public class CmsQuotaDetailController {

    @Resource
    private CmsQuotaDetailService quotadetailService;

    @PostMapping("/create")
    @Operation(summary = "创建定额明细表")
    @CmsOperateLog(type = "定额明细表", subType = "新增")
    public CommonResult<Long> create(@Valid @RequestBody CmsQuotaDetailSaveReqVO createReqVO) {
        return success(quotadetailService.createQuotaDetail(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新定额明细表")
    @CmsOperateLog(type = "定额明细表", subType = "修改")
    public CommonResult<Boolean> update(@Valid @RequestBody CmsQuotaDetailSaveReqVO updateReqVO) {
        quotadetailService.updateQuotaDetail(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除定额明细表")
    @CmsOperateLog(type = "定额明细表", subType = "删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        quotadetailService.deleteQuotaDetail(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得定额明细表")
    public CommonResult<CmsQuotaDetailRespVO> get(@RequestParam("id") Long id) {
        CmsQuotaDetailDO record = quotadetailService.getQuotaDetail(id);
        return success(BeanUtils.toBean(record, CmsQuotaDetailRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得定额明细表分页")
    public CommonResult<PageResult<CmsQuotaDetailRespVO>> page(CmsQuotaDetailPageReqVO pageReqVO) {
        PageResult<CmsQuotaDetailDO> pageResult = quotadetailService.getQuotaDetailPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CmsQuotaDetailRespVO.class));
    }

    @GetMapping("/list-all")
    @Operation(summary = "获得定额明细表全量列表（下拉选择用）")
    public CommonResult<List<CmsQuotaDetailRespVO>> listAll() {
        List<CmsQuotaDetailDO> list = quotadetailService.getQuotaDetailList();
        return success(BeanUtils.toBean(list, CmsQuotaDetailRespVO.class));
    }
}
