package cn.iocoder.txgy.module.crm.controller.admin.ai;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiClassifyReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiClassifyRespVO;
import cn.iocoder.txgy.module.crm.framework.ai.core.CrmAiIntentClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * CRM AI 助手 Controller
 *
 * 只对外暴露「意图识别」这一个接口：把用户问题转给大模型，返回结构化意图。
 * 密钥保存在后端配置中，前端无需也无法接触。
 */
@Tag(name = "管理后台 - CRM AI 助手")
@RestController
@RequestMapping("/crm/ai")
@Validated
public class CrmAiController {

    @Resource
    private CrmAiIntentClient crmAiIntentClient;

    @PostMapping("/classify-intent")
    @Operation(summary = "意图识别（后端转发大模型）")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<CrmAiClassifyRespVO> classifyIntent(@Valid @RequestBody CrmAiClassifyReqVO reqVO) {
        return success(CrmAiClassifyRespVO.builder()
                .content(crmAiIntentClient.classify(reqVO.getQuery()))
                .build());
    }

}
