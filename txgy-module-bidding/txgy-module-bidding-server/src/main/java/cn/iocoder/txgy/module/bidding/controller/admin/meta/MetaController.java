package cn.iocoder.txgy.module.bidding.controller.admin.meta;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.DictItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.MetaConfigsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.MetaDictService;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 元数据接口（字典与规则配置）——前端契约 §1 的实现。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1（GET /bidding/meta/dicts）、§1.2（GET /bidding/meta/configs）；
 * 用户硬约束"状态码、枚举值、阈值必须支持动态配置，严禁硬编码"。
 * <p>为什么做成独立接口而不是把枚举写进 OpenAPI 文档：写进文档的枚举仍需前端发版才能更新；
 * 做成接口后，运营改字典/阈值即可让前端（包括 AI 生成的前端）自动跟随。
 * <p>业务目标：前端"零硬编码"落地——下拉、标签、阈值、校验范围全部运行时获取。
 */
@Tag(name = "管理后台 - 电子证照元数据（字典/配置）")
@RestController
@RequestMapping("/bidding/meta")
@Validated
public class MetaController {

    @Resource
    private MetaDictService metaDictService;
    @Resource
    private RuleConfigService ruleConfigService;

    @GetMapping("/dicts")
    @Operation(summary = "获取字典（枚举值对照表；前端禁止硬编码枚举）")
    @Parameter(name = "types", description = "字典类型，逗号分隔（空=全部）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<Map<String, List<DictItemRespVO>>> getDicts(
            @RequestParam(value = "types", required = false) String types) {
        List<String> typeList = (types == null || types.isBlank())
                ? null : Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return CommonResult.success(metaDictService.getDicts(typeList));
    }

    @GetMapping("/configs")
    @Operation(summary = "获取业务阈值与规则配置（前端禁止硬编码阈值）")
    @Parameter(name = "group", description = "分组 code：CERT/ALERT/PERSONNEL（空=全部）")
    @Parameter(name = "keys", description = "精确取某些键，逗号分隔（空=分组全部）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<MetaConfigsRespVO> getConfigs(
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "keys", required = false) String keys) {
        MetaConfigsRespVO resp = new MetaConfigsRespVO();
        if (keys != null && !keys.isBlank()) {
            List<RuleConfigItemRespVO> items = Arrays.stream(keys.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(ruleConfigService::getItem).toList();
            resp.setItems(items);
            return CommonResult.success(resp);
        }
        resp.setItems(ruleConfigService.listByGroup(group));
        return CommonResult.success(resp);
    }
}
