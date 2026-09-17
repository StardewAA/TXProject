package cn.iocoder.txgy.module.wms.controller.admin.ai;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiChatReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeFileReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiChatService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - WMS AI 智能助手")
@RestController
@RequestMapping("/wms/ai")
@Validated
public class WmsAiController {

    @Resource
    private WmsAiService aiService;
    @Resource
    private WmsAiChatService aiChatService;

    @PostMapping("/recognize")
    @Operation(summary = "AI 智能识别：把自然语言/清单图片解析为待确认的出入库/移库草稿",
            description = "文字指令走文本大模型；上传的清单图片（可打印/电子/手写）走视觉大模型逐行识别。"
                    + "结果做商品/仓库/供应商主数据匹配补全后返回，不写数据库，由前端确认后走既有建单接口"
                    + "（建单权限仍由 wms:receipt-order/shipment-order/movement-order:create 控制）")
    public CommonResult<WmsAiRecognizeRespVO> recognize(@RequestBody WmsAiRecognizeReqVO reqVO) {
        return success(aiService.recognize(reqVO.getText(), reqVO.getImages(), reqVO.getSessionId()));
    }

    @PostMapping("/recognize-file")
    @Operation(summary = "AI 智能识别：解析上传的 Excel/CSV/TXT/PDF 清单文件，生成待确认的出入库/移库草稿",
            description = "前端把文件按 base64 data URL 传入（name + url），后端解析为逐行文本后"
                    + "走与 /recognize 相同的抽取与主数据匹配链路；结果不落库，由前端确认后走既有建单接口")
    public CommonResult<WmsAiRecognizeRespVO> recognizeFile(@RequestBody WmsAiRecognizeFileReqVO reqVO) {
        return success(aiService.recognizeFile(reqVO.getText(), reqVO.getFiles(), reqVO.getSessionId()));
    }

    @PostMapping("/chat")
    @Operation(summary = "AI 全域问答：回答关于 WMS 系统内任意数据的问题",
            description = "支持商品档案/分类/品牌/往来企业/仓库/库存/低库存预警/出入库流水/汇总统计等。"
                    + "先由大模型路由意图，再由后端查询真实数据库拼装回复，数据不臆造。")
    public CommonResult<String> chat(@RequestBody @Valid WmsAiChatReqVO reqVO) {
        return success(aiChatService.chat(reqVO.getText(), reqVO.getSessionId()));
    }

}
