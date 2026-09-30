package cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRecordReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc.TenderDocDownloadDO;
import cn.iocoder.txgy.module.bidding.service.tenderdoc.TenderDocDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 招标文件下载记录（下载名单）。
 *
 * <p>记录投标方下载招标文件的事实，供招标方按标段聚合查询下载名单，
 * 作为澄清/修改定向通知（条例第21条）与异议期限计算的事实基础。
 */
@Tag(name = "管理后台 - 招标文件下载名单")
@RestController
@RequestMapping("/bidding/tender-doc")
@Validated
public class TenderDocDownloadController {

    @Resource
    private TenderDocDownloadService tenderDocDownloadService;

    @PostMapping("/record")
    @PreAuthorize("@ss.hasPermission('bidding:tender-doc:download')")
    @Operation(summary = "记录下载（投标方下载招标文件时调用，幂等）")
    @Idempotent
    public CommonResult<Long> record(@Valid @RequestBody TenderDocDownloadRecordReqVO reqVO) {
        return success(tenderDocDownloadService.record(reqVO).getId());
    }

    @GetMapping("/downloaders")
    @PreAuthorize("@ss.hasPermission('bidding:tender-doc:query')")
    @Operation(summary = "下载名单（招标方查询，按招标项目/标段聚合）")
    public CommonResult<List<TenderDocDownloadRespVO>> downloaders(
            @RequestParam("tenderProjectCode") String tenderProjectCode,
            @RequestParam(value = "bidSectionCode", required = false) String bidSectionCode) {
        List<TenderDocDownloadDO> list = tenderDocDownloadService.listDownloaders(tenderProjectCode, bidSectionCode);
        return success(BeanUtils.toBean(list, TenderDocDownloadRespVO.class));
    }

    @GetMapping("/count")
    @PreAuthorize("@ss.hasPermission('bidding:tender-doc:query')")
    @Operation(summary = "下载人数统计（按标段）")
    public CommonResult<Long> count(@RequestParam("bidSectionCode") String bidSectionCode) {
        return success(tenderDocDownloadService.countDownloaders(bidSectionCode));
    }
}
