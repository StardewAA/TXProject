package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.EnterpriseMaterialCompletenessRespVO;
import cn.iocoder.txgy.module.bidding.service.qualification.EnterpriseMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业材料完备性自查接口（身份类 / 资质类 / 实力类）。
 *
 * <p>规范出处：用户业务口径"按《资质管理规范文件》三分类覆盖企业身份证明、企业资质证明、企业实力证明"；
 * 《电子招标投标办法》（20号令）第42条（应如实提交资质证书、人员资格等材料）；
 * 《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》（国务院令第397号）。
 * <p>业务目标：投标/申报前一次自查即知缺哪份材料、哪份临近到期，避免资格审查被否决。
 */
@Tag(name = "管理后台 - 企业材料完备性自查")
@RestController
@RequestMapping("/bidding/enterprise-material")
@Validated
public class EnterpriseMaterialController {

    @Resource
    private EnterpriseMaterialService enterpriseMaterialService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;

    @GetMapping("/completeness")
    @Operation(summary = "企业材料完备性自查（身份类/资质类/实力类三档结论 + 缺失与到期清单）")
    @Parameter(name = "entId", description = "企业主体 id（可空：不传=本租户主体档案）", example = "1")
    @Parameter(name = "asOf", description = "核验基准日（yyyy-MM-dd，可空=今天）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:material:query')")
    public CommonResult<EnterpriseMaterialCompletenessRespVO> completeness(
            @RequestParam(value = "entId", required = false) Long entId,
            @RequestParam(value = "asOf", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate asOf) {
        // 规范出处：《后端变更通知-给前端》§12（"我是谁"类 id 由后端推导）。
        // 为什么这样写：entId 是"本企业主体"，按业务口径（一个租户只有一家公司）后端能从登录上下文推出来，
        // 不该让调用方传。缺省取本租户主体档案 id —— 与证照建档的 org_id 用同一个入口，口径一致。
        Long subjectId = entId != null ? entId : subjectProfileService.getOwnSubjectId();
        return CommonResult.success(enterpriseMaterialService.checkCompleteness(subjectId, asOf));
    }
}
