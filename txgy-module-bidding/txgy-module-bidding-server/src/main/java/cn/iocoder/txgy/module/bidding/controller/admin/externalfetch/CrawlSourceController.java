package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch;
// 规范依据：设计文档 v0.6 §六（源配置/审批：仅授权管理员）；《招投标模块开发规范》§4.4/4.5

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlRunService;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlSourceService;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.CrawlSourceRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

/**
 * 管理后台 - 外部公告采集源配置（白名单源：URL/频控/通道/策略）。
 *
 * <p>启用源 = 外链 /go 白名单成员（ExternalRedirectService）；停用/删除有引用保护（见 CrawlSourceService 注释）。
 * 采集侧真实下载（GENERIC 通道执行）未接通前，源配置为“管理面可维护 + 后续执行器启用”状态（联调清单/知会）。
 */
@Tag(name = "管理后台 - bidding 外部公告采集源")
@RestController
@RequestMapping("/bidding/crawl-source")
@Validated
public class CrawlSourceController {

    @Resource
    private CrawlSourceService crawlSourceService;
    @Resource
    private CrawlRunService crawlRunService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:query')")
    @Operation(summary = "采集源分页")
    public CommonResult<PageResult<CrawlSourceRespVO>> page(@Valid CrawlSourcePageReqVO reqVO) {
        return success(crawlSourceService.getSourcePage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:query')")
    @Operation(summary = "采集源详情")
    public CommonResult<CrawlSourceRespVO> get(@RequestParam("id") Long id) {
        return success(crawlSourceService.getSource(id));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:create')")
    @Idempotent
    @Operation(summary = "新建采集源（默认不启用）")
    public CommonResult<Long> create(@Valid @RequestBody CrawlSourceSaveReqVO reqVO) {
        return success(crawlSourceService.createSource(reqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:update')")
    @Idempotent
    @Operation(summary = "修改采集源（sourceCode 不可改）")
    public CommonResult<Boolean> update(@Valid @RequestBody CrawlSourceSaveReqVO reqVO) {
        crawlSourceService.updateSource(reqVO);
        return success(Boolean.TRUE);
    }

    @PostMapping("/enabled")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:update')")
    @Idempotent
    @Operation(summary = "启停采集源（启用=进入外链 /go 白名单；停用须先下线其 ACTIVE 公告）")
    public CommonResult<Boolean> enabled(@RequestParam("id") Long id,
                                         @RequestParam("enabled") Boolean enabled) {
        crawlSourceService.setSourceEnabled(id, enabled);
        return success(Boolean.TRUE);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:delete')")
    @Idempotent
    @Operation(summary = "删除采集源（须无未终态任务且无 ACTIVE 公告引用）")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        crawlSourceService.deleteSource(id);
        return success(Boolean.TRUE);
    }

    @PostMapping("/run")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:update')")
    @Idempotent
    @Operation(summary = "立即采集一轮（下载器人工触发；同步执行并返回汇总）")
    public CommonResult<CrawlRunRespVO> run(@RequestParam("sourceCode") @NotEmpty(message = "sourceCode 不能为空") String sourceCode) {
        return success(crawlRunService.runNow(sourceCode));
    }

}