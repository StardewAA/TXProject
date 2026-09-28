package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewAiProbeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigUpdateReqVO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 智慧标书审查 · 管理端 AI 配置与自检接口。
 *
 * <p>方案依据：§5.2、§6.4（密钥只回掩码；提供 ai-probe 主动自检）。
 */
@Tag(name = "管理后台 - 智慧标书审查 AI 配置")
@RestController
@RequestMapping("/bidding/bid-review/config")
@Validated
public class BidReviewConfigController {

    @Resource
    private BidReviewConfigService configService;

    @GetMapping("/get")
    @Operation(summary = "读取 AI 配置（密钥只回掩码）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewConfigRespVO> get() {
        return success(configService.getConfig());
    }

    @PutMapping("/update")
    @Operation(summary = "修改 AI 配置（不含密钥；密钥只走环境变量）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:config:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody BidReviewConfigUpdateReqVO reqVO) {
        configService.updateConfig(reqVO);
        return success(true);
    }

    @PostMapping("/ai-probe")
    @Operation(summary = "AI 密钥自检（最小 prompt 真实调用一次）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:config:update')")
    @Idempotent
    public CommonResult<BidReviewAiProbeRespVO> probe() {
        return success(configService.probe());
    }
}
