package cn.iocoder.txgy.module.crm.controller.admin.ai;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiClassifyReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiClassifyRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiExtractFileReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.ai.vo.CrmAiExtractReqVO;
import cn.iocoder.txgy.module.crm.framework.ai.core.CrmAiDocumentExtractor;
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

import java.util.Base64;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.CRM_AI_EXTRACT_FAIL;

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

    @Resource
    private CrmAiDocumentExtractor crmAiDocumentExtractor;

    @PostMapping("/classify-intent")
    @Operation(summary = "意图识别（后端转发大模型）")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<CrmAiClassifyRespVO> classifyIntent(@Valid @RequestBody CrmAiClassifyReqVO reqVO) {
        return success(CrmAiClassifyRespVO.builder()
                .content(crmAiIntentClient.classify(reqVO.getQuery(), reqVO.getHistory()))
                .build());
    }

    @PostMapping("/extract-image")
    @Operation(summary = "图片识别（OCR 提取业务字段，后端转发多模态大模型）")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<CrmAiClassifyRespVO> extractImage(@Valid @RequestBody CrmAiExtractReqVO reqVO) {
        return success(CrmAiClassifyRespVO.builder()
                .content(crmAiIntentClient.extractImage(reqVO.getImage(), reqVO.getHint()))
                .build());
    }

    @PostMapping("/extract-file")
    @Operation(summary = "文档识别（Word/PDF 抽取文本后提取业务字段，输出结构与图片识别一致）")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<CrmAiClassifyRespVO> extractFile(@Valid @RequestBody CrmAiExtractFileReqVO reqVO) {
        String content = reqVO.getContent();
        // 兼容前端传入的 data URL 前缀：data:application/pdf;base64,xxxx
        int base64Idx = content.indexOf("base64,");
        if (base64Idx >= 0) {
            content = content.substring(base64Idx + "base64,".length());
        }
        String text;
        try {
            byte[] bytes = Base64.getDecoder().decode(content.trim());
            text = crmAiDocumentExtractor.extractText(reqVO.getFileName(), bytes);
        } catch (IllegalArgumentException e) {
            // 格式不支持、文档无文字层等属于「可预期的使用问题」，
            // 用业务错误码透出友好提示（否则会被全局异常处理成“系统异常”）
            throw exception(CRM_AI_EXTRACT_FAIL, e.getMessage());
        }
        return success(CrmAiClassifyRespVO.builder()
                .content(crmAiIntentClient.extractDocument(reqVO.getFileName(), text))
                .build());
    }

}
