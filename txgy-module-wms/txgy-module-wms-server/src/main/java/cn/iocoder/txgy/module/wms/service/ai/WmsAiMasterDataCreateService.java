package cn.iocoder.txgy.module.wms.service.ai;

import cn.hutool.json.JSONObject;

/**
 * WMS AI 主数据建档 Service 接口
 *
 * <p>把一段"建档意图 + 用户提供的主数据字段"翻译为一次真实的建档操作：
 * 自动生成编码、查重（同名已存在则直接提示/复用）、必要时自动补建缺失的分类/品牌，
 * 最终调用各主数据模块既有 createXxx Service 落库，返回自然语言结果。
 *
 * <p>字段不全时返回"还缺什么"的引导文本，等待用户补充后带着完整上下文再次调用（可收敛）。
 * 绝不使用大模型编造的编码/名称落库。
 *
 * @author OPENLAB BS
 */
public interface WmsAiMasterDataCreateService {

    /**
     * 按领域建档
     *
     * @param domain category / brand / merchant / warehouse / item
     * @param fields 已抽取的字段 JSON（各领域字段见实现注释）
     * @return 自然语言结果（成功说明 或 缺项引导）
     */
    String create(String domain, JSONObject fields);

}
