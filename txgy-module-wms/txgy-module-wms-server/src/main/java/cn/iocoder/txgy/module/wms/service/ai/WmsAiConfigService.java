package cn.iocoder.txgy.module.wms.service.ai;

import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigSaveReqVO;

/**
 * WMS AI 客服配置 Service
 *
 * <p>决定「仓储 AI 助手用哪个模型」：模型与 Key 仍由 AI 模块统一维护（ai_model / ai_api_key），
 * 本服务只保存「选了哪个模型」并把它解析成运行期可用的地址与密钥；未配置时回落容器环境变量，
 * 保证升级过程不中断。
 */
public interface WmsAiConfigService {

    /**
     * 取当前配置（含实际生效来源），供配置页展示；无配置时返回默认对象而非 null
     */
    WmsAiConfigRespVO getConfig();

    /**
     * 保存配置（单租户单条，已存在则更新）
     *
     * @param reqVO 模型编号与运行参数
     */
    void saveConfig(WmsAiConfigSaveReqVO reqVO);

    /**
     * 解析运行期生效的模型配置（供 WmsAiServiceImpl 调用）
     */
    WmsAiRuntimeConfig resolveRuntimeConfig();

    /**
     * 用指定模型做一次真实连通性测试（保存前自检）。
     *
     * <p>只发一句最短的对话请求，验证「地址 + Key + 模型名」三者可用，
     * 让配置错误在保存时暴露，而不是等用户聊天才发现。
     *
     * @param modelId 要测试的模型编号；为空则测试当前已生效配置
     * @return 耗时（毫秒）
     * @throws IllegalArgumentException 配置缺失或调用失败（消息可直接展示给用户）
     */
    long testModel(Long modelId);

}
