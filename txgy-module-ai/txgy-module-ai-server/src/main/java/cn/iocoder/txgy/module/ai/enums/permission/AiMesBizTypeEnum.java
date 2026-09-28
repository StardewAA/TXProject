package cn.iocoder.txgy.module.ai.enums.permission;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES 业务域枚举
 *
 * <p>作用：AI 权限控制的「最小授权单元」。每个业务域对应一类 MES 数据，
 * 管理员在【控制台 - 聊天角色】里按业务域给角色授权，用户提问时后端按此枚举
 * 识别意图、校验权限，越权直接拦截，不交给大模型判断。
 *
 * <p>三处关键设计：
 * 1. keywords：用于从用户提问中「识别意图」，命中关键词即认为本次提问涉及该业务域
 * 2. sensitiveKeywords：命中即视为「敏感数据」请求，需要角色额外开启 canSensitive
 * 3. toolName：该业务域对应的 Function Calling 工具名（Bean 名），
 *    只有授权的业务域，其工具才会被注入给大模型，从源头杜绝越权调用
 *
 * @author 天信管业
 */
@Getter
@AllArgsConstructor
public enum AiMesBizTypeEnum {

    // ==================== 生产制造域 ====================
    PRO_WORK_ORDER("PRO_WORK_ORDER", "生产工单", "mes_work_order_query",
            new String[]{"工单", "生产工单", "制造命令", "排产单", "订单进度", "生产计划"},
            new String[]{"成本", "报价", "利润", "单价"}),

    PRO_TASK("PRO_TASK", "生产任务", "mes_task_query",
            new String[]{"生产任务", "派工", "工序任务", "任务进度", "在制", "开工"},
            new String[]{"成本", "人工成本"}),

    PRO_FEEDBACK("PRO_FEEDBACK", "生产报工", "mes_feedback_query",
            new String[]{"报工", "产量", "完工", "计件", "工时", "良品数", "达成率"},
            new String[]{"工资", "计件工资", "薪酬"}),

    PRO_ANDON("PRO_ANDON", "安灯异常", "mes_andon_query",
            new String[]{"安灯", "异常呼叫", "停线求助", "安灯记录", "呼叫记录"},
            new String[]{}),

    PRO_PENDING("PRO_PENDING", "待检任务", "mes_pending_query",
            new String[]{"待检", "待检任务", "待生产", "待办任务", "未开工任务", "待执行任务"},
            new String[]{}),

    PRO_PROCESS("PRO_PROCESS", "工艺工序", "mes_process_query",
            new String[]{"工艺", "工序", "路线", "sop", "作业指导", "工艺参数"},
            // 工艺配方、参数属于企业核心资产，默认按敏感数据管控
            new String[]{"配方", "工艺参数", "保密", "技术参数", "核心工艺"}),

    // ==================== 主数据域 ====================
    MD_ITEM("MD_ITEM", "物料产品", "mes_item_query",
            new String[]{"物料", "产品", "bom", "物料编码", "规格型号", "产品档案"},
            new String[]{"采购价", "成本价", "进货价"}),

    MD_WORKSHOP("MD_WORKSHOP", "车间产线", "mes_workshop_query",
            new String[]{"车间", "产线", "生产线", "工作站", "工位"},
            new String[]{}),

    // ==================== 质量域 ====================
    QC_INSPECT("QC_INSPECT", "质量检验", "mes_qc_query",
            new String[]{"质检", "检验", "不良", "合格率", "缺陷", "iqc", "ipqc", "oqc", "返工", "报废"},
            new String[]{"客户投诉", "索赔", "质量成本"}),

    // ==================== 设备域 ====================
    DV_MACHINERY("DV_MACHINERY", "设备台账", "mes_machinery_query",
            new String[]{"设备", "机器", "机床", "保养", "点检", "故障", "维修", "oee", "稼动率"},
            new String[]{"设备价格", "采购价格"}),

    // ==================== 仓储域 ====================
    WM_STOCK("WM_STOCK", "仓储库存", "mes_stock_query",
            new String[]{"库存", "在库", "仓库", "入库", "出库", "盘点", "批次", "库位", "呆滞", "安全库存", "领料", "余量", "现存量", "可用量"},
            new String[]{"库存金额", "存货成本", "金额"}),

    // ==================== 排班域 ====================
    CAL_TEAM("CAL_TEAM", "排班班组", "mes_team_query",
            new String[]{"排班", "班组", "班次", "人员排班", "值班"},
            new String[]{}),

    // ==================== 客户/供应商（高敏感）====================
    MD_PARTNER("MD_PARTNER", "客户供应商", "mes_partner_query",
            new String[]{"客户", "供应商", "厂商", "往来单位"},
            // 客户与供应商信息本身即敏感，需要单独授权
            new String[]{"联系方式", "电话", "手机", "地址", "联系人"}),

    // ==================== WMS 仓储域（txgy-module-wms）====================
    // 说明：本枚举是 AI 权限体系的唯一数据源（意图识别 / 工具过滤 / 控制台清单），
    // WMS 业务域同样在此登记，即可被权限配置页面识别并授权。
    //
    // 【已知局限：与 MES 的关键词天然冲突，无法靠"精选关键词"规避】
    // extractBizTypes 用子串匹配，AiPermissionGuard 的策略是「命中的业务域必须全部有权限，
    // 任一未授权即整体拒绝」。MES 的 WM_STOCK / MD_ITEM / MD_PARTNER 已占用
    // 「库存、在库、仓库、入库、出库、盘点、现存量、可用量、客户、供应商、联系人、地址」
    // 等基础词，任何中文 WMS 关键词都会连带命中 MES 域。
    //
    // 因此配套要求（部署时必须满足）：
    //   给使用 WMS AI 的角色同时授予 MES 的 WM_STOCK / MD_ITEM / MD_PARTNER 查询权限，
    //   否则含上述基础词的提问会被 Guard 以「越权访问 MES 业务域」拒绝。

    WMS_STOCK("WMS_STOCK", "WMS 库存余额", "wms_stock_query",
            new String[]{"wms库存", "仓库库存", "库存余额", "在库数量", "各仓库存",
                    "哪个仓有", "仓库还有多少", "仓库库存分布"},
            new String[]{"wms库存金额", "wms存货成本"}),

    WMS_STOCK_ALERT("WMS_STOCK_ALERT", "WMS 库存预警", "wms_stock_alert_query",
            new String[]{"wms库存预警", "wms低库存", "低库存", "wms缺货预警", "wms补货提醒"},
            new String[]{}),

    WMS_ITEM("WMS_ITEM", "WMS 商品档案", "wms_item_query",
            new String[]{"wms商品", "商品档案", "商品资料", "商品明细", "有没有这个商品",
                    "商品规格", "商品条码", "商品分类", "商品品牌"},
            new String[]{"wms采购价", "wms成本价"}),

    WMS_WAREHOUSE("WMS_WAREHOUSE", "WMS 仓库信息", "wms_warehouse_query",
            new String[]{"wms仓库", "仓库列表", "有哪些仓库", "仓库信息", "仓库负责",
                    "仓库地址", "仓库所属组织"},
            new String[]{}),

    WMS_MERCHANT("WMS_MERCHANT", "WMS 往来企业", "wms_merchant_query",
            new String[]{"wms往来", "wms企业", "往来企业", "企业档案", "供货单位", "收货单位"},
            new String[]{"wms联系人", "wms联系电话", "wms税号"}),

    WMS_ORDER("WMS_ORDER", "WMS 出入库单据", "wms_order_query",
            new String[]{"wms单据", "wms入库单", "wms出库单", "单据查询", "出入库记录",
                    "单据状态", "wms审批状态", "待审批单据"},
            new String[]{"wms单据金额", "wms单据总价"}),
    ;

    /**
     * 业务域编码，与 ai_chat_role_permission.biz_type 对应
     */
    private final String type;
    /**
     * 业务域名称，用于前端展示与 Prompt 描述
     */
    private final String name;
    /**
     * 该业务域对应的 AI 工具（Function Calling Bean 名），未实现工具时为空
     */
    private final String toolName;
    /**
     * 意图识别关键词：提问命中任意一个即认为涉及该业务域
     */
    private final String[] keywords;
    /**
     * 敏感关键词：命中即需要角色开启「允许访问敏感生产数据」
     */
    private final String[] sensitiveKeywords;

    /**
     * 根据编码获得枚举
     *
     * @param type 业务域编码
     * @return 枚举，不存在返回 null
     */
    public static AiMesBizTypeEnum getByType(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.getType().equalsIgnoreCase(type))
                .findFirst().orElse(null);
    }

    /**
     * 【意图识别】从用户提问中解析出涉及的业务域编码集合
     *
     * <p>实现说明：采用关键词匹配（简单、可控、零成本）。命中多个业务域时全部返回，
     * 由后续权限校验层逐个校验，只要有一个越权就整体拒绝，保证安全兜底。
     *
     * @param question 用户提问内容
     * @return 涉及的业务域编码集合，无命中返回空集合
     */
    public static Set<String> extractBizTypes(String question) {
        if (StrUtil.isBlank(question)) {
            return Set.of();
        }
        String text = question.toLowerCase();
        return Arrays.stream(values())
                .filter(biz -> ArrayUtil.isNotEmpty(biz.getKeywords())
                        && Arrays.stream(biz.getKeywords()).anyMatch(text::contains))
                .map(AiMesBizTypeEnum::getType)
                .collect(Collectors.toSet());
    }

    /**
     * 【敏感度识别】判断提问是否触及敏感数据
     *
     * @param question 用户提问内容
     * @return true 表示命中敏感关键词
     */
    public static boolean isSensitiveQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        String text = question.toLowerCase();
        return Arrays.stream(values())
                .filter(biz -> ArrayUtil.isNotEmpty(biz.getSensitiveKeywords()))
                .anyMatch(biz -> Arrays.stream(biz.getSensitiveKeywords()).anyMatch(text::contains));
    }

    /**
     * 获得所有业务域（供控制台权限配置页面渲染用）
     *
     * @return 全部枚举
     */
    public static AiMesBizTypeEnum[] values_all() {
        return values();
    }
}
