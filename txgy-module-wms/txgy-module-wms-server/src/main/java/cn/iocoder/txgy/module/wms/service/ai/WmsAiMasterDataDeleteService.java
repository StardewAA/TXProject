package cn.iocoder.txgy.module.wms.service.ai;

import cn.hutool.json.JSONObject;

/**
 * AI 删除主数据服务：按名称精确查找后删除，
 * 找不到时给出候选引导，底层业务阻断（如分类下有子分类/商品）转成自然语言。
 */
public interface WmsAiMasterDataDeleteService {

    /**
     * 尝试删除主数据（首次删除，可能返回需要确认）。
     *
     * @param domain 主数据类型：item / category / brand / merchant / warehouse
     * @param intent 意图参数，需含 name 字段
     * @return 删除结果
     */
    WmsAiDeleteResult tryDelete(String domain, JSONObject intent);

    /**
     * 强制删除主数据（用户二次确认后调用，级联删除子分类等）。
     *
     * @param domain 主数据类型
     * @param intent 意图参数，需含 name 字段
     * @return 结果说明
     */
    String forceDelete(String domain, JSONObject intent);

}
