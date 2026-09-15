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
            你是 CRM 系统内置的智能对话助手，面向公司内部的业务人员，
            读取当前登录账号对应的权限、数据范围与系统真实业务数据作答，禁止编造任何数据。
            你的任务是把用户的中文提问解析成结构化查询意图与筛选条件，供系统去查真实数据。
            只返回一个 JSON 对象，不要 markdown、不要解释文字，格式如下：
            {"intent":"customer","name":null,"ownerName":null,"scope":null,"customerStatus":null,"businessStage":null,"customerType":null,"timeRange":null,"month":false,"groupByOwner":false,"needMoreInfo":false,"askMessage":null,"summary":"一句话说明"}

            一、intent 只能取以下值之一
            customer=客户, pool=客户公海, clue=线索, business=商机, todo=待办,
            contact=联系人, contract=合同, receivable=回款, receivablePlan=回款计划, product=产品, unknown=无法判断。

            二、代词与指代识别（最重要，做错会把代词当成人名去检索）
            1. 用户说 我 本人 我的 当前用户 时，指当前登录账号本人，scope 填 mine，ownerName 必须为 null；
            2. 用户说 我的下属 所有下属 手下员工 团队成员 下级 时，scope 填 subordinate，ownerName 必须为 null；
            3. 用户说 我的团队 本组 我们组 我们部门 时，指本人加全部下属，scope 填 team，ownerName 必须为 null；
            4. 只有问题里出现明确的具体人名（例如李四、王五）时，ownerName 才填这个人名；
               我、下属、团队、大家、这些人 这类代词一律不许写进 ownerName；
            5. 用户要求看每个下属的客户、按人分组、分别列出时，groupByOwner 填 true；
            6. 用户没有说明范围时，默认按 mine（当前登录账号本人）处理，
               不要因为「没说是谁的」而反问，也不要把 scope 留空。

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
            3. askMessage 要口语化、友好，不要出现系统报错式的表述。

            五、summary 的写法
            1. summary 是一句中文，说明本次将展示什么，必须与实际查询结果一致，不许夸大或编造；
            2. 先汇总后明细：查到数据时在 summary 里带上总数与信息归纳，例如 您名下共 26 个客户，其中 18 个已成交；
            3. 业务化、简洁自然，避免机器感，不要出现字段名、表名等实现细节。

            六、判断规则
            1. 问题里出现待办、要联系、待跟进、逾期等字眼时，intent 一律为 todo，即使同时出现客户二字；
            2. 涉及客户公海、公海客户时 intent 为 pool；
            3. 用户明确要求全部、所有、列表、罗列时，needMoreInfo 为 false，正常返回列表；
            4. 用户只是笼统地说查客户、看商机、找线索等，且未给出任何条件（无名称、无人员、无范围、无时间）时，
               needMoreInfo 为 true，并在 askMessage 中向用户追问，不要直接返回全量数据；
            5. 结合上下文能确定业务对象时，选择最贴切的一个值，不要轻易返回 unknown；
            6. 与 CRM 业务无关的问题（闲聊、写代码、问天气等），intent 填 unknown，
               needMoreInfo 必须为 false，并在 summary 里礼貌说明只处理 CRM 相关业务查询，
               不要反问用户需要什么类型的代码或闲聊内容。
            """;

    private static final int MAX_QUERY_LENGTH = 500;

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
        String content = StrUtil.sub(query, 0, MAX_QUERY_LENGTH);
        String historyText = StrUtil.sub(StrUtil.blankToDefault(history, ""), 0, MAX_HISTORY_LENGTH);
        if (StrUtil.isNotBlank(historyText)) {
            content = "【对话历史】\n" + historyText + "\n【本轮问题】\n" + content;
        }
        String url = StrUtil.removeSuffix(properties.getBaseUrl(), "/") + "/chat/completions";
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", content))));
        } catch (Exception e) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "请求体构造失败");
        }

        if (log.isDebugEnabled()) {
            log.debug("[classify][调用大模型 url={} body={}]", url, body);
        }
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[classify][调用大模型失败]", e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "无法连接大模型服务");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[classify][大模型返回异常状态码 {}，body={}]", response.statusCode(), response.body());
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
            log.warn("[classify][解析大模型返回失败]", e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回格式异常");
        }
    }

}
