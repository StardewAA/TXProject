package cn.iocoder.txgy.module.crm.framework.ai.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.crm.framework.ai.config.CrmAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.CRM_AI_CLASSIFY_FAIL;

/**
 * CRM AI 意图识别客户端
 *
 * 把用户的自然语言问题交给大模型，让它输出结构化意图（查客户还是查待办等）与过滤条件，
 * 前端再据此调用真实的 CRM 接口取数。
 *
 * 密钥与提示词都放在后端，前端只调 {@code /crm/ai/classify-intent}。
 */
@Slf4j
@Component
public class CrmAiIntentClient {

    /**
     * 意图识别提示词。
     *
     * 注意：
     * 1. JSON 结构示例里不要嵌套引号（会干扰模型解析格式），描述尽量用短句；
     * 2. 「代词识别」是最容易出错的地方：用户说的「我 / 我的下属 / 我的团队」都是指代，
     *    绝不能被当成员工姓名去检索，所以这一段的规则要写得足够直白。
     */
    private static final String SYSTEM_PROMPT = """
            你是 CRM 系统内置的智能对话助手，面向公司内部的业务人员。
            业务范围：客户管理、联系人、线索管理、客户公海、商机管理、合同管理、回款管理、
            待办事项、售后管理（质保档案、回访计划、回访任务、回访记录、问题工单）、报价管理。
            读取当前登录账号对应的权限、数据范围与系统真实业务数据作答，禁止编造任何数据。
            你的任务是把用户的中文提问解析成结构化查询意图与筛选条件，供系统去查真实数据。
            只返回一个 JSON 对象，不要 markdown、不要解释文字，格式如下：
            {"intent":"customer","intents":null,"name":null,"ownerName":null,"scope":null,"customerStatus":null,"businessStage":null,"customerType":null,"timeRange":null,"month":false,"groupByOwner":false,"needMoreInfo":false,"askMessage":null,"summary":"一句话说明","date":null,"content":null,"cycle":null,"visitType":null}

            一、intent 只能取以下值之一
            customer=客户, pool=客户公海, clue=线索, business=商机, todo=待办,
            contact=联系人, contract=合同, receivable=回款, receivablePlan=回款计划, product=产品,
            quotation=报价单, afterSale=售后问题工单, visitPlan=售后回访计划, visitTask=售后回访任务,
            visitRecord=售后回访记录, warranty=质保档案, help=CRM业务规则或功能咨询, unknown=无法判断。
            intents 是数组：当用户一句话里同时要多个对象时使用（见第七节）。

            二、代词与指代识别（最重要，做错会把代词当成人名去检索）
            1. 用户说 我 本人 我的 当前用户 时，指当前登录账号本人，scope 填 mine，ownerName 必须为 null；
            2. 用户说 我的下属 所有下属 手下员工 团队成员 下级 时，scope 填 subordinate，ownerName 必须为 null；
            3. 用户说 我的团队 本组 我们组 我们部门 时，指本人加全部下属，scope 填 team，ownerName 必须为 null；
            4. 只有问题里出现明确的具体人名（例如李四、王五）时，ownerName 才填这个人名；
               我、下属、团队、大家、这些人 这类代词一律不许写进 ownerName；
            5. 用户要求看每个下属的客户、按人分组、分别列出时，groupByOwner 填 true；
            6. 用户没有说明范围时，默认按「我负责的 + 下属负责的」处理（scope 填 team），
               不要因为「没说是谁的」而反问，也不要把 scope 留空或缩小为 mine；
               归属范围的完整口径见第十二节，务必严格区分。

            三、筛选条件解析
            1. name：用户提到的具体业务名称原文（客户名、公司名、线索名、商机名等），泛问列表时为 null；
            2. customerStatus：客户状态类条件，例如成交、未成交、跟进中、已流失，取用户原话关键词，没有则为 null；
            3. businessStage：商机阶段类条件，取用户原话关键词，没有则为 null；
            4. customerType：客户类别，用户说下游、客户、下游客户时为 1，说上游、供应商、上游客户时为 2，未提及为 null；
            5. timeRange：用户提到的时间范围原话，例如本月、上月、今年、近7天、最近一周，未提及为 null；
            6. month：用户要求本月或这个月时为 true，否则 false。
            严格按解析出的条件筛选数据，不要直接返回系统全部数据。

            四、追问、反问与上下文
            1. 问题过于笼统、缺少关键筛选条件时，needMoreInfo 填 true，并在 askMessage 里用一句自然的业务口吻反问，
               例如：请问需要筛选什么时间段或者客户状态的数据？
               此时不要返回全量数据，也不要生硬地说没有数据；
            2. 若本次提供了对话历史，而本轮问题是对上一轮的追问或补充条件，要沿用上一轮的 intent 与已有条件，
               再叠加本轮的新条件，不要从头重新判断；
            3. askMessage 要口语化、友好，不要出现系统报错式的表述；
            4. 对话历史里已出现过的客户、商机、合同、报价、售后单据名称，本轮用 他、他们、这个客户、那张单、
               刚才那个 等指代时，一律沿用该对象继续处理，不要当成新对象，也不要因为没重复名称就反问；
            5. 同一句话可对应多个业务对象、无法确定用户指哪一个时，needMoreInfo 填 true，
               在 askMessage 里给出可选口径让用户直接挑，例如：您是想看今日需要联系的客户，还是这位客户的跟进记录？
               绝不许随便挑一个返回。

            五、summary 的写法
            1. summary 是一句中文，只概括本次要查询的内容，例如 您名下今日需要联系的客户明细；
            2. 严禁在 summary 里给出具体数量、客户名称、名单、日期或业务细节 ——
               你此刻并不知道真实查询结果，实际条数与明细由系统查询后展示，
               若写了具体内容必然与结果表格对不上，等同于编造数据；
            3. 业务化、简洁自然，避免机器感，不要出现字段名、表名等实现细节。

            六、判断规则
            1. 问题里出现待办、要联系、逾期等字眼时，intent 为 todo，即使同时出现客户二字；
               但若同时出现具体对象名（线索、商机、合同、报价、售后等），以该对象为准：
               例如「待跟进的线索」intent 为 clue、「需要跟进的商机」intent 为 business、
               「待回访的任务」intent 为 visitTask，不要一律当成待办；
            2. 涉及客户公海、公海客户时 intent 为 pool；
            3. 用户明确要求全部、所有、列表、罗列时，needMoreInfo 为 false，正常返回列表；
            4. 用户只是笼统地说查客户、看商机、找线索等，且未给出任何条件（无名称、无人员、无范围、无时间）时，
               needMoreInfo 为 true，并在 askMessage 中向用户追问，不要直接返回全量数据；
            5. 结合上下文能确定业务对象时，选择最贴切的一个值，不要轻易返回 unknown；
            6. 与 CRM 业务无关的问题（闲聊、写代码、问天气等），intent 填 unknown，
               needMoreInfo 必须为 false，并在 summary 里礼貌说明只处理 CRM 相关业务查询，
               不要反问用户需要什么类型的代码或闲聊内容；
            7. 「需要跟进、待跟进的线索」指 跟进状态为待跟进（未跟进）且转化状态为未转化的线索，
               intent 为 clue；不能返回已跟进的线索，不能返回已转化的线索，也不能返回全部线索；
               同理「需要跟进的客户」intent 为 customer 并限定待跟进口径；
            8. 用户提到 下属负责、我的下属、团队成员 时，scope 一律为 subordinate，不要当成由 name 指定的对象。

            七、售后与报价专项判定
            1. 提到售后工单、问题工单、售后问题、待处理售后时 intent 为 afterSale；
            2. 提到回访计划时 intent 为 visitPlan；提到回访任务、待回访时 intent 为 visitTask；
               提到回访记录、历史回访、回访情况时 intent 为 visitRecord；
            3. 提到质保、质保档案、质保期、质保到期时 intent 为 warranty；
            4. 提到报价单、报价、报价记录时 intent 为 quotation；
            5. 售后与报价优先级高于客户：用户问「XX客户的报价单」时 intent 为 quotation，客户名放进 name；
            6. 问「今天要回访哪些客户」「待回访任务」时 intent 为 visitTask，不要当成待办；
            7. 用户一句话同时查多个对象（例如 查询XX客户的报价单、售后工单和质保档案）时，
               intent 填最主要的一个，其余对象按出现顺序放进 intents 数组（最多 3 个），
               元素只能取 intent 的可选值，不要把说明文字放进 intents。

            八、业务规则与功能咨询
            1. 用户询问 CRM 业务规则或系统功能（公海回收机制、商机阶段流转、回款逾期规则、待办超时提醒、
               售后回访规则、质保档案管理、报价流程、某个字段的含义、你能做什么）时，intent 填 help；
            2. help 时 needMoreInfo 必须为 false，并在 summary 里用业务口吻概括用户想了解的规则点；
            3. 各类规则的具体时限数值以系统配置为准，你不许自行编造具体数字。

            九、写操作意图（创建 / 修改 / 完成，执行前系统会向用户二次确认）
            1. 用户要求 新建、创建、添加、提醒我 待办、跟进提醒、回访提醒时，intent 为 todoCreate；
               content 填待办内容，name 填客户名，date 填截止时间；
               日期一律结合今天日期换算成绝对日期 YYYY-MM-DD（如「3天后」「下周一」），只给日期不带时分秒；
            2. 用户要求 标记完成、办结、已联系 某个待办时，intent 为 todoComplete，content 填待办内容关键词；
            3. 用户要求 修改、改期、推迟 待办时间时，intent 为 todoReschedule，
               content 填待办内容关键词，date 填新的截止时间（绝对日期）；
            4. 用户要求 新建、创建、安排 回访计划时，intent 为 visitPlanCreate；
               name 填客户名或质保编号，cycle 填周期原话（每季度、每半年、每年、到期前），
               visitType 填回访方式原话（电话、上门、微信、邮件），date 填开始日期；
            5. 用户要求 新建、创建 回访任务时，intent 为 visitTaskCreate，
               name 填客户名或质保编号，date 填任务日期（绝对日期）；
            6. 用户要求 新建报价、报价单时，intent 为 quotationCreate，name 填客户名；
               若产品明细缺失，needMoreInfo 为 true，并在 askMessage 提示可以点击输入框右侧上传按钮传报价图片自动识别，
               或直接在对话里补充产品名称、数量、单价；
            7. 写操作缺少必要信息（客户、时间、内容）时 needMoreInfo 为 true 并友好反问，绝不编造；
            8. 查询优先于创建：用户明确说 查、看、列出 时不要判成创建；明确说 新建、创建、提醒我 时才判成创建。

            十、业务概念区分（混淆会直接答错，务必区分）
            1. 跟进记录：一次跟进的沟通记录，包含跟进方式、跟进内容、下次联系时间、关联商机与联系人；
            2. 下次联系时间：客户档案上的字段，代表约定下次联系客户的时间，会生成今日需联系待办，
               它既不是跟进记录本身，也不是上次联系时间；
            3. 客户公海：超过公海配置的未跟进时限后，客户自动回收进公海并清空归属人，公海客户全员可领取；
            4. 线索转客户：线索需转化后才成为正式客户，线索与客户是两类数据、编号体系不同，不能混为一谈；
            5. 商机阶段：商机在状态组下按阶段推进，赢单、输单属于结案状态；
            6. 回款计划与回款：回款计划是应收安排（含计划回款时间），回款是实际到账记录，逾期指计划已到期未回款；
            7. 报价与合同：报价单走审批流程，审批通过后可转合同，报价与合同是两张单据、金额不可互相替代；
            8. 售后：质保档案登记质保期，回访计划按周期生成回访任务，任务完成后登记回访记录，问题工单记录故障处理；
            9. 产品：CRM 产品数据由 WMS 同步且只读，不能在 CRM 手工新增或修改产品，WMS 是产品唯一数据源；
            10. 遇到用户使用上述术语时按其准确含义理解，并选择正确的查询对象，避免因术语混淆选错对象。

            十一、澄清优先于猜测
            1. 用户描述简略、有多种合理解释时先澄清再查询，needMoreInfo 填 true；
            2. 关键信息缺失（哪类数据、哪个人、哪个时间段）时同样先澄清，不要用默认值代替用户的选择；
            3. 澄清只问一个最关键的点，语气自然，不要一次抛出多个问题。

            十二、归属范围口径（管理者视角，严禁混淆两种范围）
            1. 用户说 列出所有 XX、全部 XX、所有 XX 时，范围 = 我负责的 + 下属负责的，scope 填 team；
            2. 用户说 我的 XX、我负责的 XX、我名下 XX、本人 时，范围 = 仅本人，scope 填 mine；
            3. 用户说 下属、下级、手下、我的下属、团队成员 时，范围 = 仅下属负责的，scope 填 subordinate；
            4. 用户没有指定归属范围时，一律按第 1 条处理（我负责的 + 下属负责的），scope 填 team，
               不要默认缩小到本人；
            5. 归属范围可与转化状态、跟进状态、时间等条件叠加，多个条件之间是「且」的关系，
               例如 下属负责的需要被跟进的线索 = 归属下属 + 待跟进 + 未转化。
            """;

    /**
     * 图片识别（OCR 字段提取）提示词
     *
     * 合同识别最容易出错的地方：把「对方主体」一律当成客户。
     * 本系统存在两类合同，我方身份相反：
     * - 采购合同：我方是甲方（采购人），对方是供应商（乙方）；
     * - 购销合同：我方是乙方（供货方），对方是客户（甲方）。
     * 因此提示词要求先判定合同类型与我方身份，再决定字段归属。
     */
    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是 CRM 系统的合同 / 表单识别助手。用户会提供一份合同、报价单或客户资料，
            可能是图片，也可能是 Word / PDF 文档的文本内容；
            请识别其中的内容，并把业务字段整理成结构化 JSON 返回。
            只返回一个 JSON 对象，不要 markdown、不要解释文字，格式如下：
            {"docType":"unknown","docTitle":null,"typeBasis":"unknown","ourSide":"unknown","partyAName":null,"partyBName":null,
            "counterpartyName":null,"contactName":null,"phone":null,"address":null,"remark":null,"products":null}
            products 是数组，元素格式：{"productName":null,"count":null,"unit":null,"price":null}
            docTitle 填图片上单据标题的原文（例如 采购合同、购销合同、报价单），
            标题只是「合同」或没有标题时也要如实填写所见内容，确实没有标题才填 null。

            一、第一步先判断文档类型 docType（判错会把供应商当成客户，必须仔细）
            1. 标题或正文出现 采购合同、采购订单、采购方、需方、订货方、我方为采购人 等 → docType 填 purchase；
            2. 标题或正文出现 购销合同、销售合同、供货合同、供方、我方为供应商 等 → docType 填 sales；
            3. 是报价单、询价单、报价明细 → docType 填 quotation；
            4. 其它资料或是客户/联系人信息 → docType 填 other；
            5. 判断不出属于哪一类 → docType 填 unknown，禁止猜测；
            6. typeBasis 必须如实说明你得出 docType 的依据，取值只有三种：
               title = 标题或正文中明确写出了合同类型名称（如 采购合同、购销合同、供货合同）；
               inferred = 标题没有写明类型，是根据我方公司在甲/乙方位置推断的；
               unknown = 无法判断（含 docType 为 unknown 的情况）。
               注意：typeBasis 填 inferred 时，系统会再向用户二次确认类型，所以你只管如实标注，不要为了省事填 title。

            二、第二步判断我方身份 ourSide
            1. docType 为 purchase 时，我方是采购人，即甲方，ourSide 填 jia；
            2. docType 为 sales 时，我方是供货方，即乙方，ourSide 填 yi；
            3. 其它情况 ourSide 填 unknown。

            三、第三步按类型确定字段归属（严禁混用）
            1. purchase 采购合同：counterpartyName 填「乙方（供应商）」的名称，绝不能填甲方或我方名称；
            2. sales 购销合同：counterpartyName 填「甲方（客户）」的名称，绝不能填乙方或我方名称；
            3. 同时把合同上出现的甲乙方原文名称分别填进 partyAName、partyBName，没有就填 null；
            4. contactName、phone、address 一律填「对方主体」的联系人、联系电话、地址
               （purchase 取乙方信息，sales 取甲方信息）；
            5. products 填合同或报价中的产品明细：count 为数量（整数），
               price 为单价（数字，去掉货币符号与千分位逗号），unit 填原文单位（如 米、平方米、台、个）；
            6. 只提取图片上真实出现的内容，看不清或不存在的一律填 null，禁止推测或编造；
            7. docType 为 quotation 或 other 时，counterpartyName 填客户名称（如能识别）。
            """;

    private static final int MAX_QUERY_LENGTH = 500;

    /**
     * 文档文本送入大模型的上限（字符）。
     * 与文档抽取侧的上限保持一致，避免上下文过大拖慢响应。
     */
    private static final int MAX_DOCUMENT_TEXT_LENGTH = 20000;

    /**
     * 对话历史上限（字符）。
     * 带上历史是为了让模型理解「那他们呢」这类追问，但不能无限长，
     * 否则会挤占上下文并明显拖慢响应。
     */
    private static final int MAX_HISTORY_LENGTH = 1500;

    @Resource
    private CrmAiProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 是否已正确配置（未配置时前端会退化到关键词匹配）
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StrUtil.isNotBlank(properties.getBaseUrl())
                && StrUtil.isNotBlank(properties.getApiKey());
    }

    /**
     * 调用大模型做意图识别，返回其输出的 JSON 原文
     *
     * @param query   用户本轮问题
     * @param history 最近几轮对话记录（可为空），用于让模型理解「那他们呢」这类追问
     * @return 模型输出的 JSON 字符串
     */
    public String classify(String query, String history) {
        if (!isAvailable()) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "AI 未配置，请检查 txgy.crm.ai 配置项");
        }
        // 注入今天日期，让模型能把「3天后」「下周一」换算成绝对日期
        String content = "【今天是 " + LocalDate.now() + "】\n" + StrUtil.sub(query, 0, MAX_QUERY_LENGTH);
        String historyText = StrUtil.sub(StrUtil.blankToDefault(history, ""), 0, MAX_HISTORY_LENGTH);
        if (StrUtil.isNotBlank(historyText)) {
            content = "【对话历史】\n" + historyText + "\n【本轮问题】\n" + content;
        }
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", content)));
        return callChat(body, properties.getTimeout(), "意图识别");
    }

    /**
     * 图片识别：把用户上传的图片（base64 Data URL）交给多模态模型做 OCR 字段提取
     *
     * @param imageDataUrl 图片 Data URL（data:image/png;base64,...）
     * @param hint         用户补充说明（可空）
     * @return 模型输出的 JSON 字符串
     */
    public String extractImage(String imageDataUrl, String hint) {
        if (!isAvailable()) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "AI 未配置，请检查 txgy.crm.ai 配置项");
        }
        String text = StrUtil.blankToDefault(hint, "请识别图片中的业务字段");
        Map<String, Object> body = Map.of(
                "model", properties.getVisionModel(),
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", EXTRACT_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", text),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))))));
        return callChat(body, properties.getExtractTimeout(), "图片识别");
    }

    /**
     * 文档识别：把 Word / PDF 抽取出的文本交给大模型做字段提取
     *
     * 与图片识别共用同一套提示词与输出结构，前端也复用同一套确认卡与建单逻辑。
     *
     * @param fileName 文件名（仅用于提示模型文档用途，可为空）
     * @param text     文档纯文本
     * @return 模型输出的 JSON 字符串
     */
    public String extractDocument(String fileName, String text) {
        if (!isAvailable()) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "AI 未配置，请检查 txgy.crm.ai 配置项");
        }
        String content = "【文档名称】" + StrUtil.blankToDefault(fileName, "未命名文档")
                + "\n【文档内容】\n" + StrUtil.sub(StrUtil.blankToDefault(text, ""), 0, MAX_DOCUMENT_TEXT_LENGTH);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", EXTRACT_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", content)));
        return callChat(body, properties.getExtractTimeout(), "文档识别");
    }

    /**
     * 调用 OpenAI 兼容的 chat/completions 接口并返回首条回复内容
     */
    private String callChat(Map<String, Object> bodyMap, Integer timeoutMs, String action) {
        String url = StrUtil.removeSuffix(properties.getBaseUrl(), "/") + "/chat/completions";
        String body;
        try {
            body = objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw exception(CRM_AI_CLASSIFY_FAIL, action + "请求体构造失败");
        }
        if (log.isDebugEnabled()) {
            log.debug("[callChat][{} 调用大模型 url={}]", action, url);
        }
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[callChat][{} 调用大模型失败]", action, e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "无法连接大模型服务");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[callChat][{} 大模型返回异常状态码 {}]", action, response.statusCode());
            throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回状态码 " + response.statusCode());
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String answer = node.path("choices").path(0).path("message").path("content").asText("");
            if (StrUtil.isBlank(answer)) {
                throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回内容为空");
            }
            return answer;
        } catch (Exception e) {
            if (e instanceof cn.iocoder.txgy.framework.common.exception.ServiceException) {
                throw (cn.iocoder.txgy.framework.common.exception.ServiceException) e;
            }
            log.warn("[callChat][{} 解析大模型返回失败]", action, e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回格式异常");
        }
    }

}
