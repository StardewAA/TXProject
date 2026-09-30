package cn.iocoder.txgy.module.cms.controller.admin.finance;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.finance.CmsFinancePolicyService;
import cn.iocoder.txgy.module.cms.service.interpretation.FinancePolicyInterpretationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 企业金融政策")
@RestController
@RequestMapping("/cms/finance-policy")
@Validated
public class CmsFinancePolicyController {

    private final CmsFinancePolicyService service;
    private final FinancePolicyInterpretationService interpretationService;

    public CmsFinancePolicyController(CmsFinancePolicyService service,
                                      FinancePolicyInterpretationService interpretationService) {
        this.service = service;
        this.interpretationService = interpretationService;
    }

    @GetMapping("/get")
    @Operation(summary = "读取金融政策结构化信息")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<CmsFinancePolicyService.FinancePolicyDetail> get(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(service.get(reviewId));
    }

    @PutMapping("/save")
    @Operation(summary = "保存金融政策结构化信息并同步已发布索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsFinancePolicyService.FinancePolicyDetail> save(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId,
            @Valid @RequestBody SaveRequest request) {
        return success(service.save(reviewId, new CmsFinancePolicyService.FinancePolicyInput(
                request.applicableRegions(), request.applicableIndustries(), request.applicableEnterpriseTypes(),
                request.supportType(), request.amountMin(), request.amountMax(), request.amountUnit(),
                request.supportRate(), request.supportDescription(), request.applyStartTime(), request.applyEndTime(),
                request.policyStatus(), request.version())));
    }

    @PostMapping("/interpretation/generate")
    @Operation(summary = "生成经过 JSON Schema 与引用防伪校验的 AI 政策解读")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<FinancePolicyInterpretationService.InterpretationView> generateInterpretation(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(interpretationService.generate(reviewId));
    }

    @GetMapping("/interpretation/get")
    @Operation(summary = "读取金融政策结构化解读")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<FinancePolicyInterpretationService.InterpretationView> getInterpretation(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(interpretationService.get(reviewId));
    }

    @PostMapping("/interpretation/review")
    @Operation(summary = "人工审核金融政策 AI 解读")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<FinancePolicyInterpretationService.InterpretationView> reviewInterpretation(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId,
            @RequestParam boolean approved) {
        return success(interpretationService.review(reviewId, approved));
    }

    public record SaveRequest(
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> applicableRegions,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> applicableIndustries,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> applicableEnterpriseTypes,
            @Size(max = 64) String supportType,
            @DecimalMin("0") BigDecimal amountMin,
            @DecimalMin("0") BigDecimal amountMax,
            @Size(max = 16) String amountUnit,
            @DecimalMin("0") @DecimalMax("100") BigDecimal supportRate,
            @Size(max = 4000) String supportDescription,
            LocalDateTime applyStartTime,
            LocalDateTime applyEndTime,
            @Min(1) @Max(3) int policyStatus,
            @Min(0) int version) { }
}
