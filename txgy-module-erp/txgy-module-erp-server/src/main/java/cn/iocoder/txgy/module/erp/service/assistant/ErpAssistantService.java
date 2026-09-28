package cn.iocoder.txgy.module.erp.service.assistant;

import cn.iocoder.txgy.module.erp.controller.admin.assistant.vo.ErpAssistantChatRespVO;

/**
 * ERP 智能助手 Service
 *
 * <p>面向系统首页的 AI 对话：把用户的自然语言映射成对<b>真实业务数据</b>的查询，
 * 返回回答文案 + 可跳转的页面入口。不依赖外部的 AI 大模型服务。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpAssistantService {

    /**
     * 处理一次对话
     *
     * @param message 用户输入
     * @return 回答 + 可跳转入口
     */
    ErpAssistantChatRespVO chat(String message);

}
