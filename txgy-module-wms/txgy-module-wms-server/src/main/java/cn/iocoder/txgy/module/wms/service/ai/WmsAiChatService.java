package cn.iocoder.txgy.module.wms.service.ai;

/**
 * WMS AI 全域问答 Service 接口
 *
 * <p>回答用户关于 WMS 系统内任意数据的提问（商品档案、分类、品牌、往来企业、
 * 仓库、库存、出入库流水、汇总统计等），以及简短闲聊。
 * 实现策略：先用大模型把问题路由到数据域并抽取关键词，再用 Java 调用数据库
 * 真实查询，最后由后端拼装自然语言回复——数据绝不臆造。
 *
 * @author OPENLAB BS
 */
public interface WmsAiChatService {

    /**
     * 全域问答（无会话上下文，单轮）
     *
     * @param text 用户提问原文
     * @return 自然语言回复
     */
    String chat(String text);

    /**
     * 全域问答（带会话上下文，支持多轮追问与指代消解）
     *
     * @param text      用户提问原文
     * @param sessionId 会话标识；同一会话传相同值，为空时退化为单轮
     * @return 自然语言回复
     */
    String chat(String text, String sessionId);

}
