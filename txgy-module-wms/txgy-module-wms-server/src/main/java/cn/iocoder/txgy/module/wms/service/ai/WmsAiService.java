package cn.iocoder.txgy.module.wms.service.ai;

import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiApprovalAdviceReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiApprovalAdviceRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiCreateOrderReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiCreateOrderRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeFileReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO;

import java.util.List;

/**
 * WMS AI 智能识别 Service 接口
 *
 * <p>用于把用户的一段自然语言 / 一张或多张清单图片（例如“采购入库 200 米 预制直埋保温管 DN200
 * 到 金岩原辅材料库”或手写收货清单照片）解析成可确认的入库/出库/移库单据草稿。
 * 内部调用大模型做结构化抽取（文字用文本模型、图片用视觉模型并强制逐行全收清单），
 * 再基于 WMS 商品/SKU/仓库/供应商主数据进行匹配补全；结果不落库，
 * 由前端展示「确认预览」后走系统既有的入库/出库/移库 create 接口完成建单
 * （建单权限仍由各 create 接口原有 @PreAuthorize 控制，AI 不绕过任何权限校验）。
 *
 * @author OPENLAB BS
 */
public interface WmsAiService {

    /**
     * 智能识别一段自然语言指令与可选的清单图片
     *
     * @param text      用户指令文本（可空，空时必须提供图片）
     * @param images    清单图片 base64 data URL 列表（可空）
     * @param sessionId 会话标识（可空，用于多轮对话上下文隔离；未传按登录用户隔离）
     * @return 待确认单据草稿
     */
    WmsAiRecognizeRespVO recognize(String text, List<String> images, String sessionId);

    /**
     * 智能识别上传的清单文件（Excel/CSV/TXT/PDF）
     *
     * <p>后端把文件解析为逐行文本后，复用 {@link #recognize} 的抽取与主数据匹配链路。
     *
     * @param text      用户补充指令（可空）
     * @param files     清单文件列表（name + url(base64 data URL)）
     * @param sessionId 会话标识（可空，用于多轮对话上下文隔离；未传按登录用户隔离）
     * @return 待确认单据草稿
     */
    WmsAiRecognizeRespVO recognizeFile(String text, List<WmsAiRecognizeFileReqVO.FileDoc> files, String sessionId);

    /**
     * 确认建单：把用户在确认卡片上核对过的草稿落库为真实单据
     *
     * <p>权限与手工建单完全一致——调用方仍需具备对应 create 权限，AI 不绕过权限校验。
     *
     * @param reqVO 已确认的建单请求（动作、单据类型、仓库、往来企业、明细）
     * @return 单据编号与主键
     */
    WmsAiCreateOrderRespVO createOrder(WmsAiCreateOrderReqVO reqVO);

    /**
     * AI 辅助审批：为审批人给出单据摘要、风险点与建议结论
     *
     * <p>只读取单据与库存做分析，<b>不改变单据状态、不代替人工审批</b>。
     * 库存、验收数量、价格偏差等由后端按真实数据先算成事实，再交给大模型归纳，
     * 避免模型凭空推断。
     *
     * @param reqVO 单据编号与类型（RECEIPT 入库 / SHIPMENT 出库）
     * @return 建议结论、风险列表与理由
     */
    WmsAiApprovalAdviceRespVO approvalAdvice(WmsAiApprovalAdviceReqVO reqVO);

}
