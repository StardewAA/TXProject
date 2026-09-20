package cn.iocoder.txgy.module.wms.service.ai;

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

}
