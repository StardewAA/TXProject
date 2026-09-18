package cn.iocoder.txgy.module.wms.service.ai;

import cn.hutool.json.JSONObject;

/**
 * AI 切换主数据状态服务（目前仅支持商品分类的启用/停用）。
 */
public interface WmsAiMasterDataStatusService {

    /**
     * 切换分类状态。
     *
     * @param type   主数据类型（category）
     * @param intent 意图参数，需含 name 字段
     * @param enable true=启用，false=停用
     * @return 结果说明
     */
    String changeStatus(String type, JSONObject intent, boolean enable);

}
