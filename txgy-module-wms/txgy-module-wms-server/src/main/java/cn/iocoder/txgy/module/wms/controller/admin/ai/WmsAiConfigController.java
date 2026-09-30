package cn.iocoder.txgy.module.wms.controller.admin.ai;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigSaveReqVO;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiConfigService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * WMS AI 客服配置 Controller
 *
 * <p>决定「仓储 AI 助手用哪个模型」。模型与 API Key 仍在 AI 模块统一维护
 * （模型配置 / API 密钥菜单），这里只保存「选了哪个模型」并展示实际生效来源。
 */
@Tag(name = "管理后台 - WMS AI 客服配置")
@RestController
@RequestMapping("/wms/ai-config")
@Validated
public class WmsAiConfigController {

    @Resource
    private WmsAiConfigService aiConfigService;

    @GetMapping("/get")
    @Operation(summary = "获取 AI 客服配置", description = "同时返回实际生效来源（CONFIG 界面配置 / ENV 环境变量 / NONE 未配置）")
    @PreAuthorize("@ss.hasPermission('wms:ai-config:query') or @ss.hasPermission('wms:warehouse:query')")
    public CommonResult<WmsAiConfigRespVO> getConfig() {
        return success(aiConfigService.getConfig());
    }

    @PutMapping("/update")
    @Operation(summary = "保存 AI 客服配置", description = "保存前会校验所选模型是否已关联可用的 API 密钥")
    @PreAuthorize("@ss.hasPermission('wms:ai-config:update') or @ss.hasPermission('wms:warehouse:update')")
    public CommonResult<Boolean> saveConfig(@RequestBody @Valid WmsAiConfigSaveReqVO reqVO) {
        aiConfigService.saveConfig(reqVO);
        return success(true);
    }

    @PostMapping("/test")
    @Operation(summary = "测试模型连通性",
            description = "用指定模型发一句最短对话，验证「调用地址 + API Key + 模型名」三者可用；"
                    + "不传 modelId 时测试当前生效配置。失败时返回可直接展示的原因。")
    @PreAuthorize("@ss.hasPermission('wms:ai-config:query') or @ss.hasPermission('wms:warehouse:query')")
    public CommonResult<Long> testModel(@RequestParam(value = "modelId", required = false) Long modelId) {
        return success(aiConfigService.testModel(modelId));
    }

}
