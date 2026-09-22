package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MES 实时数据查询服务（对话链路专用）
 *
 * 作用：用户提出业务类问题时，后端【直接执行】与 Function Calling 工具完全相同的
 * 只读 SQL 查询，把数据库真实结果注入 Prompt（确定性保障）——
 * 大模型只能基于注入的真实数据整理转述，从机制上杜绝编造业务数据。
 *
 * 说明：与 mes_*_query 工具共用同一套查询实现（MesBaseQueryToolFunction.doQuery），
 * 保证「模型主动调用工具」与「后端预注入」两条通道的数据口径完全一致。
 *
 * [筛选条件修复] 用户提问若点到了具体对象（物料名称、物料/单据编码、设备名、仓库名等），
 * 本服务会从提问中提取出该筛选词并作为 keyword 传入 SQL，只查询与之匹配的记录，
 * 避免「查 PVC 颗粒库存却把全表物料返回给大模型」的答非所问。
 *
 * @author 天信管业
 */
@Slf4j
@Service
public class MesDataQueryService {

    /**
     * 业务域工具 Bean 名称 → 工具实例（启动时收集）
     */
    private final Map<String, MesBaseQueryToolFunction> toolMap;

    /**
     * 状态词表：从用户提问中提取状态过滤条件（各工具内部负责翻译为状态码）
     */
    private static final List<String> STATUS_WORDS = List.of(
            "已完成", "已确认", "已取消", "草稿", "审批中", "待检验", "停机", "生产中", "维护中",
            "未处置", "已处置", "待检");

    /**
     * [筛选条件修复] 停用/问法词表：keyword 提取时从提问中剥离的无过滤价值词。
     * 仅当提问中带具体业务对象（物料名、编号、设备名等）时，剥离后才会剩下候选关键词。
     */
    private static final List<String> STOP_WORDS;

    static {
        List<String> words = new ArrayList<>(List.of(
                // ---- 业务域泛化词（问"整体情况"时剥离，不应作为过滤关键词）----
                "生产工单", "制造命令", "排产单", "订单进度", "生产计划", "生产任务", "工序任务", "任务进度",
                "生产报工", "工艺工序", "作业指导", "工艺路线", "物料产品", "物料编码", "规格型号",
                "车间产线", "车间工位", "质量检验", "设备台账", "仓储库存", "排班班组", "客户供应商",
                "工单", "任务", "报工", "工艺", "工序", "物料", "产品", "车间", "产线", "工位",
                "库存", "仓库", "设备", "机器", "机床", "质检", "检验", "不良", "缺陷", "报废",
                "排班", "班组", "班次", "客户", "供应商", "厂商", "往来单位",
                "安灯", "安灯记录", "异常呼叫", "停线求助", "待检任务", "待检", "待生产", "待办",
                // ---- 泛化/整体概况词（问"整体/总量/汇总"时剥离，不应作为过滤关键词）----
                "总体情况", "整体情况", "库存总体", "库存总量", "总体", "整体", "整个", "全部库存",
                "概况", "概览", "总况", "总量", "总览", "大致", "大概", "情况如何",
                // ---- 状态词（状态走 status 参数，不作为 keyword）----
                "已完成", "已确认", "已取消", "草稿", "审批中", "待检验", "校验通过", "校验不通过",
                "停机", "生产中", "维护中", "未开工", "未完工", "待处理", "处理中",
                // ---- 时间词（时间范围暂不做 SQL 过滤，避免剥成时间词误过滤）----
                "今天", "今日", "昨天", "昨日", "明天", "明日", "本周", "上周", "本月", "上月",
                "今年", "去年", "最近", "近期", "目前", "当前", "现在", "当天", "当日",
                // ---- 疑问 / 问法词 ----
                "多少", "几个", "几台", "几条", "几", "怎么", "如何", "什么", "哪些", "哪个", "哪种",
                "有没有", "是否有", "是否", "在不在", "是不是", "还有", "还剩", "剩余", "现存", "现有",
                "查询", "查一下", "查下", "查看", "查查", "查", "看看", "看一下", "看下", "搜一下",
                "调取", "调一下", "调出", "提取", "读取",
                "班组", "负载", "负荷", "各",
                "了解", "了解下", "介绍一下", "统计", "汇总", "清点", "核对", "看看现在",
                "一下", "一下下",
                "帮", "帮我", "帮忙", "请", "请问", "给我", "我们", "咱们", "麻烦",
                // ---- 方位/介词词（切分"实体词+在+地点/仓库"类问法，如"PVC颗粒在原料仓的库存"）----
                // 必须按长词优先排序（长词先于"在/里"替换），避免把"放在/存放于"等剥成残余单字挂到实体词后
                "放在", "放到", "存放在", "存放于", "存储于", "储存于", "位于", "分布于", "在哪个",
                "哪个仓库", "哪个仓", "在哪", "那个仓", "哪个库", "从", "到", "于", "在", "里",
                // ---- 助词 / 连接词 / 量词 / 单位 ----
                "的", "了", "呢", "吗", "吧", "啊", "哦", "和", "与", "及", "或者", "或", "并", "等",
                "吨", "千克", "公斤", "kg", "KG", "桶", "袋", "包", "卷", "箱", "台", "套", "条", "个",
                "件", "瓶", "罐", "斤", "米", "米长", "状态", "情况", "信息", "数据", "记录", "结果",
                "清单", "明细", "一共", "总共", "全部", "所有", "截至", "数量", "金额", "进度",
                "关于", "相关", "有关", "以内", "以下", "这些", "那个", "这个"
        ));
        // 长词优先替换，避免子串误伤（如先剥"生产任务"再剥"任务"）
        words.sort((a, b) -> b.length() - a.length());
        STOP_WORDS = List.copyOf(words);
    }

    /**
     * 单据/物料编码特征：字母开头后跟数字（如 IF2022082432、WO2026001、MO-2026-001）
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z]{2,}[-]?[0-9]{2,}");

    /**
     * 单次对话最多查询的业务域数量（防止多域命中导致 Prompt 过长）
     */
    private static final int MAX_BIZ_DOMAINS = 3;

    public MesDataQueryService(ApplicationContext applicationContext) {
        Map<String, MesBaseQueryToolFunction> tools = new LinkedHashMap<>();
        applicationContext.getBeansOfType(MesBaseQueryToolFunction.class)
                .forEach((beanName, tool) -> tools.put(beanName, tool));
        this.toolMap = tools;
    }

    /**
     * 按用户提问识别业务域并查询真实数据
     *
     * @param question 用户提问
     * @param tenantId 租户编号（来自当前登录上下文）
     * @return 真实数据 JSON 文本（注入 Prompt 用）；非业务类问题返回 null
     */
    public String queryRealtimeData(String question, Long tenantId) {
        // 1. 意图识别：命中 MES 业务域关键词才算业务类问题
        Set<String> bizTypes = AiMesBizTypeEnum.extractBizTypes(question);
        if (CollUtil.isEmpty(bizTypes)) {
            return null;
        }
        // 1.1 [负载语境优先] "查询各班组的任务负载"这类问法同时命中任务域与排班域，
        //     但用户真正想看的是班组负载而非任务清单：此时只保留排班域，
        //     并把"班组/负载"从关键词中剥离，避免按班组名过滤任务表导致恒 0 行
        if (bizTypes.contains("CAL_TEAM") && question.matches("(?s).*(负载|负荷).{0,6}(情况|如何|怎么样)?")
                && bizTypes.size() > 1) {
            bizTypes = Set.of("CAL_TEAM");
        }
        // 2. 提取状态过滤词（如"已完成"）
        String status = extractStatus(question);
        // 2.1 [筛选条件修复] 从用户提问提取"实体筛选词"（物料名称/编码、工单号等），
        //     作为 keyword 传给查询 SQL 过滤，只返回与问题匹配的记录，杜绝把全表数据交给大模型
        String keyword = extractKeyword(question);
        log.info("[queryRealtimeData][提问({}) tenantId({}) 筛选关键词({}) 状态({})]",
                question, tenantId, keyword, status);
        // 3. 逐业务域查询真实数据（最多 3 个域）
        Map<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (String bizType : bizTypes) {
            if (count >= MAX_BIZ_DOMAINS) {
                break;
            }
            AiMesBizTypeEnum biz = AiMesBizTypeEnum.getByType(bizType);
            if (biz == null || biz.getToolName() == null) {
                continue;
            }
            MesBaseQueryToolFunction tool = toolMap.get(biz.getToolName());
            if (tool == null) {
                continue;
            }
            count++;
            try {
                // 复用工具的查询实现（protected 同包可见）：带用户筛选词 + 状态过滤条件查询
                // 【修复】50 条与工具默认值对齐：原 10 条会截断结果（如设备台账 11 台只注入 10 台）
                result.put(biz.getName(), tool.doQuery(tenantId, keyword, status, 50));
            } catch (Exception e) {
                log.error("[queryRealtimeData][业务域({}) 查询异常 tenantId({})]", bizType, tenantId, e);
                result.put(biz.getName(), "数据查询接口异常，请稍后重试");
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        // [筛选条件修复] 渲染为人类可读的文本列表（含筛选条件与记录数）：
        // 原 JSON 文本经 SystemMessage 注入时模型容易忽略/误读，导致明明查到匹配数据
        // 却回复「未查询到相关数据/请补充条件」；可读文本列表更利于模型逐条引用真实记录
        return renderResult(result, keyword, status);
    }

    /**
     * [筛选条件修复] 把各业务域查询结果渲染为可读文本（域 → 分组 → 记录行），
     * 供 buildPrompt 注入 <RealtimeData> 使用。
     *
     * <pre>
     * 查询条件：按关键词[PVC颗粒]过滤
     * 【仓储库存】：共 4 条
     *   1. 物料编码:IF2022082432  物料名称:PVC颗粒  仓库:原料仓  批次:xxx  库存数量:500  入库时间:2026-06-01
     *   ...
     * 【质量检验】：...
     * </pre>
     */
    private String renderResult(Map<String, Object> result, String keyword, String status) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("查询条件：");
        if (StrUtil.isBlank(keyword) && StrUtil.isBlank(status)) {
            sb.append("未限定（返回该业务最新若干条）");
        } else {
            if (StrUtil.isNotBlank(keyword)) {
                sb.append("按关键词[").append(keyword).append("]过滤");
            }
            if (StrUtil.isNotBlank(status)) {
                if (StrUtil.isNotBlank(keyword)) {
                    sb.append("，");
                }
                sb.append("状态[").append(status).append("]");
            }
        }
        sb.append("\n请直接引用下方数据作答，禁止编造、改写、合并任何字段值；无记录时按系统提示回复。\n");
        result.forEach((domain, data) -> appendBlock(sb, domain, data, 0));
        return sb.toString();
    }

    /**
     * 递归渲染一个业务块：title 后接 List（记录/分组）或 Map（子分组）
     */
    private void appendBlock(StringBuilder sb, String title, Object data, int depth) {
        String indent = repeat("  ", depth);
        sb.append(indent).append("【").append(title).append("】");
        if (data instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("：共 0 条（无匹配数据）\n");
                return;
            }
            sb.append("：共 ").append(list.size()).append(" 条\n");
            // 平铺记录优先用 Markdown 表格输出，降低模型错抄/改写概率
            if (list.get(0) instanceof Map<?, ?> firstRow) {
                @SuppressWarnings("unchecked")
                Map<String, Object> first = (Map<String, Object>) firstRow;
                boolean flat = first.values().stream()
                        .noneMatch(v -> v instanceof Map<?, ?> || v instanceof List<?>);
                if (flat) {
                    List<String> headers = new ArrayList<>(first.keySet());
                    sb.append(indent).append("  | ").append(String.join(" | ", headers)).append(" |\n");
                    sb.append(indent).append("  | ")
                            .append(headers.stream().map(h -> "---").collect(Collectors.joining(" | ")))
                            .append(" |\n");
                    for (Object row : list) {
                        if (row instanceof Map<?, ?> rawRow) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) rawRow;
                            sb.append(indent).append("  | ")
                                    .append(headers.stream()
                                            .map(h -> String.valueOf(m.getOrDefault(h, "")).replace("|", "\\|"))
                                            .collect(Collectors.joining(" | ")))
                                    .append(" |\n");
                        }
                    }
                    return;
                }
            }
            int no = 0;
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    boolean hasNested = map.values().stream().anyMatch(v -> v instanceof Map || v instanceof List);
                    if (hasNested) {
                        // 分组数据（如车间工位"车间列表/工位列表"、质检三类检验单、客户/供应商）：递归展开
                        map.forEach((k, v) -> appendBlock(sb, String.valueOf(k), v, depth + 2));
                    } else {
                        sb.append(indent).append("  ").append(++no).append(". ").append(joinRow(map)).append('\n');
                    }
                } else {
                    sb.append(indent).append("  ").append(++no).append(". ").append(row).append('\n');
                }
            }
        } else if (data instanceof Map<?, ?> map) {
            sb.append("：\n");
            map.forEach((k, v) -> {
                if (v instanceof Map || v instanceof List) {
                    appendBlock(sb, String.valueOf(k), v, depth + 1);
                } else {
                    sb.append(indent).append("  ").append(k).append(":").append(v).append('\n');
                }
            });
        } else {
            // 异常占位文本（如"数据查询接口异常，请稍后重试"）由模型直接转述
            sb.append("：").append(data).append('\n');
        }
    }

    /**
     * 单条记录 k:v 合并为一行（跳过嵌套容器值）
     */
    private String joinRow(Map<?, ?> row) {
        StringBuilder line = new StringBuilder();
        row.forEach((k, v) -> {
            if (v != null && !(v instanceof Map) && !(v instanceof List)) {
                line.append(k).append(':').append(v).append("  ");
            }
        });
        return line.toString().trim();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * 从提问中提取状态过滤词
     */
    private String extractStatus(String question) {
        if (question == null) {
            return null;
        }
        for (String word : STATUS_WORDS) {
            if (question.contains(word)) {
                return word;
            }
        }
        return null;
    }

    /**
     * [筛选条件修复] 从用户提问中提取"实体筛选关键词"（物料名称/编码、工单号、设备名、仓库名等）。
     *
     * <p>示例：
     * <pre>
     *   "PVC颗粒库存多少"            → PVC颗粒   （剥掉"库存/多少"）
     *   "查一下IF2022082432的库存"    → IF2022082432（编码优先正则命中）
     *   "WO2026001这个工单什么状态"   → WO2026001
     *   "看看A车间注塑机的设备情况"    → 注塑机    （剥掉问法与业务域词后取实体段）
     *   "库存总体情况怎么样"          → null     （无具体对象 → 不传条件，返回最新若干条）
     * </pre>
     *
     * @param question 用户提问原文
     * @return 筛选关键词；无具体对象返回 null
     */
    /**
     * [取值规则] “字段 + 是/为/叫 + 值”结构：如“工单编码是111”“编号为MO123”“名称是1122”
     * 值后面必须跟“的/空白/标点”或结尾，避免误匹配
     */
    private static final Pattern FIELD_VALUE_PATTERN = Pattern.compile(
            "(?:编码|编号|单号|代码|工单号|名称)(?:是|为|叫|[:：])\\s*([0-9A-Za-z\\u4e00-\\u9fa5_-]{1,32}?)(?=的|\\s|，|,|。|；|;|？|\\?|$)");

    /** 疑问值词：后接这些词时不视为字段取值（如“编码是什么”） */
    private static final Set<String> QUESTION_VALUE_WORDS =
            Set.of("什么", "啥", "多少", "几", "哪些", "怎么", "如何", "啥样");

    private String extractKeyword(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        String text = question.trim();
        // 1. 优先提取单据/物料编码（字母+数字连续串，能精确命中 code LIKE）
        Matcher codeMatcher = CODE_PATTERN.matcher(text);
        if (codeMatcher.find()) {
            return codeMatcher.group();
        }
        // 1.5 [取值规则修复] “字段 + 是/为/叫 + 值”结构（如“工单编码是111”“编号为MO123”“名称是1122”）
        //     直接取值作为关键词，避免把“编码是”这类字段词混进 LIKE 条件导致查不到
        Matcher fieldValuePair = FIELD_VALUE_PATTERN.matcher(text);
        if (fieldValuePair.find()) {
            String value = fieldValuePair.group(1);
            // 疑问值词（“编码是什么”）不视为取值，继续走停用词流程
            if (!QUESTION_VALUE_WORDS.contains(value)) {
                return MesBaseQueryToolFunction.normalizeKeyword(value);
            }
        }
        // 2. 停用词替换为空格，剥离问法/业务域/状态/量词后，剩余连续片段即为候选实体词
        String reduced = text;
        for (String stop : STOP_WORDS) {
            reduced = reduced.replace(stop, " ");
        }
        // 3. 从候选片段中挑出"最像业务对象"的一段
        String best = null;
        int bestScore = 0;
        for (String part : reduced.split("[\\s,.，。、:：;；!！?？/\\\\|()（）\\-\\-]+")) {
            String p = part.trim();
            if (p.isEmpty() || p.length() < 2 || p.length() > 24) {
                continue;
            }
            // 纯数字片段（如重量"100"）不能作为名称/编码 LIKE 条件，丢弃
            if (p.codePoints().allMatch(Character::isDigit)) {
                continue;
            }
            boolean hasLatin = p.matches(".*[A-Za-z].*");
            boolean hasChinese = p.matches(".*[\\u4e00-\\u9fa5].*");
            if (!hasLatin && !hasChinese) {
                continue;
            }
            int score = 0;
            if (hasLatin) { // 字母/数字组合更可能是编码或型号（如 PVC颗粒、HDPE7000）
                score += 10;
            }
            if (hasChinese) {
                score += 4;
            }
            if (p.contains("-")) {
                score += 2;
            }
            score += Math.min(p.length(), 12);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        // 4. 无实体词（只剩问法）→ null：SQL 不加条件，返回该业务域最新若干条
        return MesBaseQueryToolFunction.normalizeKeyword(best);
    }

    /**
     * 获取已注册的工具数量（自检用）
     */
    public int getToolCount() {
        return toolMap.size();
    }

    /**
     * 供日志/调试输出业务域清单
     */
    public List<String> getToolNames() {
        return new ArrayList<>(toolMap.keySet());
    }

}
