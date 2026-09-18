package cn.iocoder.txgy.module.ai.service.chat;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessagePageReqVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageRespVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO;
import cn.iocoder.txgy.module.ai.dal.dataobject.chat.AiChatConversationDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.chat.AiChatMessageDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.knowledge.AiKnowledgeDocumentDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiChatRoleDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiModelDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiToolDO;
import cn.iocoder.txgy.module.ai.dal.mysql.chat.AiChatMessageMapper;
import cn.iocoder.txgy.module.ai.enums.ErrorCodeConstants;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.ai.enums.model.AiPlatformEnum;
import cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum;
import cn.iocoder.txgy.module.ai.framework.ai.core.webserch.AiWebSearchClient;
import cn.iocoder.txgy.module.ai.framework.ai.core.webserch.AiWebSearchRequest;
import cn.iocoder.txgy.module.ai.framework.ai.core.webserch.AiWebSearchResponse;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeDocumentService;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeSegmentService;
import cn.iocoder.txgy.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchReqBO;
import cn.iocoder.txgy.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchRespBO;
import cn.iocoder.txgy.module.ai.service.model.AiChatRoleService;
import cn.iocoder.txgy.module.ai.service.model.AiModelService;
import cn.iocoder.txgy.module.ai.service.model.AiToolService;
import cn.iocoder.txgy.module.ai.service.permission.AiPermissionGuard;
import cn.iocoder.txgy.module.ai.service.permission.bo.AiPermissionCheckResultBO;
import cn.iocoder.txgy.module.ai.service.permission.bo.AiPermissionContextBO;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.ai.util.FileTypeUtils;
import com.google.common.collect.Maps;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.error;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static cn.iocoder.txgy.module.ai.enums.ErrorCodeConstants.CHAT_MESSAGE_NOT_EXIST;

/**
 * AI 聊天消息 Service 实现类
 *
 * @author fansili
 */
@Service
@Slf4j
public class AiChatMessageServiceImpl implements AiChatMessageService {

    /**
     * 联网搜索的结束数
     */
    private static final Integer WEB_SEARCH_COUNT = 10;

    // TODO @芋艿：后续优化下对话的 Prompt 整体结构

    /**
     * 业务类问题的强制工具调用指令（SystemMessage）
     *
     * 命中 MES 业务域关键词时注入，要求模型必须调用 mes_*_query 工具查询数据库真实数据，
     * 严禁使用自身知识、估算或编造任何业务数值与单据号
     */
    private static final String BIZ_QUESTION_TOOL_TEMPLATE = "===== MES 业务数据查询强制规则 =====\n"
            + "用户本次提问涉及 MES 业务数据（工单/任务/报工/库存/设备/质检/物料/排班/客户供应商等）。\n"
            + "强制要求：\n"
            + "1. 你不能编造任何 MES 业务数据，所有工单编号、数量、库存、设备信息、报工记录、质检结果，全部以本次消息中「MES 数据库实时查询结果」段的后端真实数据为准；该段未提供或为空时才可调用 mes_*_query 系列工具查询。\n"
            + "2. 没有后端返回的数据，不允许自行猜测、估算、虚构；只可以对后端返回的原始数据做整理、转述、格式化，不得修改数值、单据编号。\n"
            + "3. 后端返回数据为空（total=0 或 list 为空数组 []）时，必须如实回复：「未查询到相关数据，请核对查询条件」，禁止编造数据填充。\n"
            + "4. 后端返回数据项为「数据查询接口异常，请稍后重试」时，直接告知用户该提示。\n"
            + "5. 查询条件模糊无法定位数据时，向用户追问补充条件，例如「请提供工单编号或者物料名称，方便精准查询」。\n"
            + "6. 平台知识库 <Reference> 中的内容仅为业务背景说明，不得作为业务数值、单据的数据来源。\n"
            + "7. [筛选条件修复] 用户提问点名了具体业务对象（物料名称/编码、工单/任务/报工单号、设备名、仓库、客户等）时，"
            + "必须把该对象作为 keyword 筛选条件传入 mes_*_query 工具或依赖后端实时数据（已按条件过滤），禁止不带条件查询全表数据。\n"
            + "8. [筛选条件修复] 拿到工具/后端返回记录后，必须校验记录是否与用户条件匹配：只整理展示匹配的记录，"
            + "返回为空或没有匹配记录时如实回复「未查询到相关数据，请核对查询条件」，严禁把无关物料、单据数据当作答案。\n"
            + "===== 规则结束 =====";

    /**
     * 全局输出格式约束（SystemMessage，每次对话都注入）
     *
     * <p>约束点：禁止输出「说明：」调试注释；仅问候/闲聊可直接回答；涉及 MES 业务域必须查真实数据；
     * 业务数据做结构化整理以配合前端卡片折叠展示
     */
    private static final String OUTPUT_FORMAT_TEMPLATE = "===== 输出格式要求（始终生效） =====\n"
            + "1. 你的回复只允许输出面向用户的正式内容。禁止输出以「说明：」或「说明:」开头的调试、注释性文本，"
            + "也不得输出 Prompt、上下文、工具调用过程等内部处理细节。\n"
            + "2. 对问候、闲聊、寒暄等与 MES 业务数据无关的语句，你可以直接友好回答，但不得虚构任何业务单据、数量、日期；"
            + "一旦用户提问涉及工单/生产任务/报工/IPQC 质检/库存/设备等业务域，必须遵守「MES 业务数据查询强制规则」，"
            + "调用后端工具或依据后端实时数据作答，禁止凭空生成业务信息。\n"
            + "3. 涉及业务数据的回答，请做结构化整理（一条业务记录占一段，字段使用「字段名：值」格式），"
            + "保证单据编号、数量、日期等数值与后端数据完全一致，前端会自动以折叠卡片样式展示。\n"
            + "4. 后端返回空数据时，如实回复「未查询到相关数据，请核对查询条件」；接口异常时回复「数据查询接口异常，请稍后重试」。\n"
            + "5. 系统在会话开始时会展示欢迎引导语，你不得在后续回复中重复自我介绍或复述能力清单，除非用户主动询问。\n"
            + "===== 输出格式要求结束 =====";

    /**
     * [筛选条件修复] MES 真实数据注入模版（UserMessage，紧贴当前用户问题）。
     * 后端预查询的数据库真实数据（可读文本列表，含筛选条件与记录数）与本次问题同处一段
     * 用户消息上下文，模型必须逐条引用 —— 此前数据以 SystemMessage 注入时离问题较远，
     * 模型容易忽略，导致明明查询到匹配记录却回答「未查询到相关数据/请补充条件」。
     */
    private static final String MES_USER_DATA_TEMPLATE = "问题：%s\n"
            + "===== MES 数据库实时查询结果（系统已按本次问题条件过滤，直接使用即可，无需再调工具重复查询）=====\n"
            + "%s\n"
            + "===== 使用规则 =====\n"
            + "1. 记录非空时必须逐条引用真实记录（编码、名称、数量等一律不得改动），整理成折叠卡片回答用户，禁止回复「未查询到」。\n"
            + "2. 业务域记录为「共 0 条（无匹配数据）」或未出现时，如实回复：「未查询到相关数据，请核对查询条件」。\n"
            + "3. 只整理展示与本次问题条件匹配的记录；不得虚构，不得引用记录之外的物料、单据数据。\n"
            + "4. 数据项为「数据查询接口异常，请稍后重试」时，直接告知用户该提示。";

    /**
     * 知识库转 {@link UserMessage} 的内容模版
     *
     * 需求要求：优先依据知识库回答，并明确标注数据来源；知识库没有的数据，说明是联网搜索/通用知识
     */
    private static final String KNOWLEDGE_USER_MESSAGE_TEMPLATE = "下面 <Reference></Reference> 标记中的内容，是刚从平台知识库中检索到的参考内容:\n\n" +
            "%s\n\n" + // 多个 <Reference></Reference> 的拼接
            "回答要求：\n" +
            "- 优先、严格根据 <Reference></Reference> 中的内容回答用户问题，不要编造知识库中不存在的数据。\n" +
            "- 回答中实际使用了 <Reference></Reference> 的内容时，末尾用一行注明「数据来源：平台知识库」。\n" +
            "- 如果 <Reference></Reference> 中没有与问题相关的内容，直接忽略这些参考内容，基于你自己的知识简洁自然地回答即可；"
            + "不要声明「知识库未检索到数据」「本回答为通用知识」等机械表述，也不要提及检索过程或数据来源细节。";

    private static final String WEB_SEARCH_USER_MESSAGE_TEMPLATE = "使用 <WebSearch></WebSearch> 标记中的内容作为本次对话的参考:\n\n" +
            "%s\n\n" + // 多个 <WebSearch></WebSearch> 的拼接
            "回答要求：\n" +
            "- 使用 <WebSearch></WebSearch> 中的内容回答时，回答末尾用一行注明「数据来源：联网搜索」，并列出引用的网页标题。\n" +
            "- 避免编造 <WebSearch></WebSearch> 中不存在的信息。";

    /**
     * 附件转 ${@link UserMessage} 的内容模版
     */
    @SuppressWarnings("TextBlockMigration")
    private static final String Attachment_USER_MESSAGE_TEMPLATE = "使用 <Attachment></Attachment> 标记用户对话上传的附件内容:\n\n" +
            "%s\n\n" + // 多个 <Attachment></Attachment> 的拼接
            "回答要求：\n- 避免提及 <Attachment></Attachment> 附件的编码格式。";

    @Resource
    private AiChatMessageMapper chatMessageMapper;

    @Resource
    private AiChatConversationService chatConversationService;
    @Resource
    private AiChatRoleService chatRoleService;
    @Resource
    private AiModelService modalService;
    @Resource
    private AiKnowledgeSegmentService knowledgeSegmentService;
    @Resource
    private AiKnowledgeDocumentService knowledgeDocumentService;
    @Resource
    private AiToolService toolService;

    /**
     * MES 实时数据查询服务：业务类问题由后端直接查询数据库真实数据注入 Prompt
     */
    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesDataQueryService mesDataQueryService;

    /**
     * AI 权限校验（规则层）：负责提问越权拦截、权限 SystemMessage 注入、工具白名单过滤
     */
    @Resource
    private AiPermissionGuard permissionGuard;

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired(required = false) // 由于 txgy.ai.web-search.enable 配置项，可以关闭 AiWebSearchClient 的功能，所以这里只能不强制注入
    private AiWebSearchClient webSearchClient;

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired(required = false) // 由于 txgy.ai.mcp.client.enable 配置项，可以关闭 McpSyncClient 的功能，所以这里只能不强制注入
    private List<McpSyncClient> mcpClients;

    @Value("${spring.ai.mcp.client.name:mcp}")
    private String mcpClientName;

    @Resource
    private ToolCallbackResolver toolCallbackResolver;

    @Transactional(rollbackFor = Exception.class)
    public AiChatMessageSendRespVO sendMessage(AiChatMessageSendReqVO sendReqVO, Long userId) {
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService
                .validateChatConversationExists(sendReqVO.getConversationId());
        if (ObjUtil.notEqual(conversation.getUserId(), userId)) {
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        }
        List<AiChatMessageDO> historyMessages = chatMessageMapper.selectListByConversationId(conversation.getId());
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        ChatModel chatModel = modalService.getChatModel(model.getId());

        // 2.0 业务类问题识别：命中 MES 业务域关键词时跳过知识库，后端直接查库注入真实数据
        boolean bizQuestion = CollUtil.isNotEmpty(AiMesBizTypeEnum.extractBizTypes(sendReqVO.getContent()));
        // 2.0.1 业务类问题：后端预查询数据库真实数据（与 mes_*_query 工具同一套查询实现）
        String realtimeData = bizQuestion
                ? mesDataQueryService.queryRealtimeData(sendReqVO.getContent(), TenantContextHolder.getTenantId())
                : null;

        // 2.1 知识库召回（业务类问题不召回，避免知识库旧数据抢先回答）
        List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments = bizQuestion
                ? Collections.emptyList()
                : recallKnowledgeSegment(sendReqVO.getContent(), conversation);

        // 2.2 联网搜索
        AiWebSearchResponse webSearchResponse = Boolean.TRUE.equals(sendReqVO.getUseSearch()) && webSearchClient != null ?
                doWebSearch(sendReqVO.getContent()) : null;

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model,
                userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(),
                null, sendReqVO.getAttachmentUrls(), null);

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model,
                userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(),
                knowledgeSegments, null, webSearchResponse);

        // 4.2 权限校验（后端规则强校验，第二层防护）：越权 / Prompt 注入直接拒绝，不调用大模型
        AiPermissionCheckResultBO checkResult = permissionGuard.check(userId, sendReqVO.getContent());
        if (!Boolean.TRUE.equals(checkResult.getAllowed())) {
            String denyMessage = checkResult.getDenyMessage();
            // 把拒绝话术作为助手回复落库，保证对话留痕、可审计
            chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(denyMessage));
            return new AiChatMessageSendRespVO()
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                    .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class).setContent(denyMessage));
        }

        // 4.3 创建 chat 需要的 Prompt（内部会注入权限约束 SystemMessage）
        Prompt prompt = buildPrompt(userId, conversation, historyMessages, knowledgeSegments, webSearchResponse, model, sendReqVO, bizQuestion, realtimeData);
        ChatResponse chatResponse = chatModel.call(prompt);

        // 4.3 更新响应内容
        String newContent = AiUtils.getChatResponseContent(chatResponse);
        String newReasoningContent = AiUtils.getChatResponseReasoningContent(chatResponse);
        chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                .setContent(newContent).setReasoningContent(newReasoningContent));
        // 4.4 响应结果
        Map<Long, AiKnowledgeDocumentDO> documentMap = knowledgeDocumentService.getKnowledgeDocumentMap(
                convertSet(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getDocumentId));
        List<AiChatMessageRespVO.KnowledgeSegment> segments = BeanUtils.toBean(knowledgeSegments,
                AiChatMessageRespVO.KnowledgeSegment.class, segment -> {
                    AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                    segment.setDocumentName(document != null ? document.getName() : null);
                });
        return new AiChatMessageSendRespVO()
                .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                        .setContent(newContent).setSegments(segments)
                        .setWebSearchPages(webSearchResponse != null ? webSearchResponse.getLists() : null));
    }

    @Override
    public Flux<CommonResult<AiChatMessageSendRespVO>> sendChatMessageStream(AiChatMessageSendReqVO sendReqVO,
            Long userId) {
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService
                .validateChatConversationExists(sendReqVO.getConversationId());
        if (ObjUtil.notEqual(conversation.getUserId(), userId)) {
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        }
        List<AiChatMessageDO> historyMessages = chatMessageMapper.selectListByConversationId(conversation.getId());
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        StreamingChatModel chatModel = modalService.getChatModel(model.getId());

        // 2.0 业务类问题识别：命中 MES 业务域关键词（工单/库存/设备/质检/报工等）时，
        //     跳过知识库召回（知识库数据可能过期），由后端直接查询数据库真实数据注入 Prompt，
        //     从机制上保证「业务数据必须来自后端接口，严禁大模型编造」
        //     [录入意图分流] 用户要求创建/录入工单/报工/IPQC 时属于写意图：跳过预查询
        //     （否则会把业务名当查询词注入「共 0 条」，带偏模型不去调用录入工具而口头编造结果）
        //     [多轮补字段延续] 用户补齐缺失字段的轮次往往不含业务名词（如"合格数量48，录入吧"），
        //     检查最近几轮历史：若出现过录入意图则本轮延续录入模式
        boolean createIntent = isMesCreateIntent(sendReqVO.getContent())
                || hasRecentCreateIntent(historyMessages);
        boolean bizQuestion = !createIntent
                && CollUtil.isNotEmpty(AiMesBizTypeEnum.extractBizTypes(sendReqVO.getContent()));
        // 2.0.1 业务类问题：后端预查询数据库真实数据（与 mes_*_query 工具同一套查询实现）
        String realtimeData = bizQuestion
                ? mesDataQueryService.queryRealtimeData(sendReqVO.getContent(), TenantContextHolder.getTenantId())
                : null;

        // 2.1 知识库找回（业务类问题不召回，避免知识库旧数据抢先回答）
        List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments = bizQuestion
                ? Collections.emptyList()
                : recallKnowledgeSegment(sendReqVO.getContent(), conversation);

        // 2.2 联网搜索
        AiWebSearchResponse webSearchResponse = Boolean.TRUE.equals(sendReqVO.getUseSearch()) && webSearchClient != null ?
                doWebSearch(sendReqVO.getContent()) : null;

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model,
                userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(),
                null, sendReqVO.getAttachmentUrls(), null);

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model,
                userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(),
                knowledgeSegments, null, webSearchResponse);

        // 4.2 权限校验（后端规则强校验，第二层防护）：越权 / Prompt 注入直接拒绝，不调用大模型
        AiPermissionCheckResultBO checkResult = permissionGuard.check(userId, sendReqVO.getContent());
        if (!Boolean.TRUE.equals(checkResult.getAllowed())) {
            String denyMessage = checkResult.getDenyMessage();
            // 把拒绝话术作为助手回复落库，保证对话留痕、可审计
            chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(denyMessage));
            return Flux.just(success(new AiChatMessageSendRespVO()
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                    .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class).setContent(denyMessage))));
        }

        // 4.3 构建 Prompt（内部会注入权限约束 SystemMessage），并进行调用
        Prompt prompt = buildPrompt(userId, conversation, historyMessages, knowledgeSegments, webSearchResponse, model, sendReqVO, bizQuestion, realtimeData);

        // [数据准确性修复] 业务类问题且后端已命中真实记录时，改用非流式 ChatModel 生成完整答案，
        // 再按字符切分模拟流式输出。deepseek-chat 的流式生成对编码/数量等长串容易错抄，
        // 非流式调用准确率显著更高；此分支仍保持 text/event-stream 响应形态与落盘逻辑。
        log.info("[sendChatMessageStream][数据准确性分支判断] bizQuestion={} realtimeDataLength={} hasZero={}",
                bizQuestion, StrUtil.length(realtimeData), realtimeData != null && realtimeData.contains("共 0 条（无匹配数据）"));
        boolean hasRealtimeData = bizQuestion && StrUtil.isNotBlank(realtimeData)
                && !realtimeData.contains("共 0 条（无匹配数据）");
        // 知识库命中时同样走非流式生成：deepseek-chat 流式输出存在丢字/语序错乱，
        // SOP 等知识回答对原文保真要求高（参数、步骤顺序不能错）
        boolean knowledgeHit = CollUtil.isNotEmpty(knowledgeSegments);
        // 【统一非流式】所有对话统一走非流式 ChatModel.call：
        // ① Spring AI 2.0 DeepSeek 流式模式下模型发出的 Function Calling 工具调用不会被执行（工具永不触发）
        // ② 流式生成对编码/数量/日期等长串存在丢字乱序
        // 非流式生成完整答案后由 streamStaticAnswer 按字符切成 SSE 增量帧，前端体验与流式一致
        boolean useBlocking = true;
        if (useBlocking) {
            ChatModel blockingChatModel = modalService.getChatModel(model.getId());
            ChatResponse chatResponse = blockingChatModel.call(prompt);
            // [修复] Spring AI 2.0 在 DeepSeek 平台下内部工具执行未生效（模型返回 tool_calls 后直接结束），
            // 这里手动执行工具并把结果回填提示词、再次调用模型，得到真实的工具执行结论
            org.springframework.ai.model.tool.ToolCallingManager toolCallingManager =
                    org.springframework.ai.model.tool.ToolCallingManager.builder().build();
            int toolRounds = 0;
            while (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null
                    && chatResponse.getResult().getOutput().hasToolCalls() && toolRounds < 3) {
                toolRounds++;
                log.info("[sendChatMessageStream][手动工具执行] 第 {} 轮 toolCalls={}", toolRounds,
                        chatResponse.getResult().getOutput().getToolCalls().size());
                // Spring AI 2.0：executeToolCalls 返回含工具结果的完整对话历史，直接作为下一轮提示词
                org.springframework.ai.model.tool.ToolExecutionResult toolResult =
                        toolCallingManager.executeToolCalls(prompt, chatResponse);
                prompt = new Prompt(toolResult.conversationHistory(), prompt.getOptions());
                chatResponse = blockingChatModel.call(prompt);
            }
            String fullContent = AiUtils.getChatResponseContent(chatResponse);
            log.info("[sendChatMessageStream][blocking完成] toolRounds={} textLength={}", toolRounds, StrUtil.length(fullContent));
            return streamStaticAnswer(fullContent, userMessage, assistantMessage, knowledgeSegments, webSearchResponse);
        }

        Flux<ChatResponse> streamResponse = chatModel.stream(prompt);

        // 4.3 流式返回
        StringBuffer contentBuffer = new StringBuffer();
        StringBuffer reasoningContentBuffer = new StringBuffer();

        // 防止执行多次知识库和联网搜索
        AtomicBoolean firstExecuteFlag = new AtomicBoolean(true);
        AtomicReference<List<AiChatMessageRespVO.KnowledgeSegment>> cacheSegments = new AtomicReference<>();
        AtomicReference<List<AiWebSearchResponse.WebPage>> cacheWebSearchPages = new AtomicReference<>();
        return streamResponse.map(chunk -> {
            // 仅首次：返回知识库、联网搜索
            if (StrUtil.isEmpty(contentBuffer)) {
                if (firstExecuteFlag.compareAndSet(true, false)) { // CAS 操作，确保仅执行一次
                    Map<Long, AiKnowledgeDocumentDO> documentMap = TenantUtils.executeIgnore(() -> knowledgeDocumentService.getKnowledgeDocumentMap(
                            convertSet(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getDocumentId)));
                    cacheSegments.set(BeanUtils.toBean(knowledgeSegments, AiChatMessageRespVO.KnowledgeSegment.class, segment -> {
                        AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                        segment.setDocumentName(document != null ? document.getName() : null);
                    }));
                    if (webSearchResponse != null) {
                        cacheWebSearchPages.set(webSearchResponse.getLists());
                    }
                }
            }
            // 响应结果
            String newContent = AiUtils.getChatResponseContent(chunk);
            String newReasoningContent = AiUtils.getChatResponseReasoningContent(chunk);
            if (StrUtil.isNotEmpty(newContent)) {
                contentBuffer.append(newContent);
            }
            if (StrUtil.isNotEmpty(newReasoningContent)) {
                reasoningContentBuffer.append(newReasoningContent);
            }
            return success(new AiChatMessageSendRespVO()
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                    .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                            .setContent(StrUtil.nullToDefault(newContent, "")) // 避免 null 的 情况
                            .setReasoningContent(StrUtil.nullToDefault(newReasoningContent, "")) // 避免 null 的 情况
                            .setSegments(cacheSegments.get()).setWebSearchPages(cacheWebSearchPages.get()))); // 知识库 + 联网搜索
        }).doOnComplete(() -> {
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> chatMessageMapper.updateById(
                    new AiChatMessageDO().setId(assistantMessage.getId()).setContent(contentBuffer.toString())
                            .setReasoningContent(reasoningContentBuffer.toString())));
        }).doOnError(throwable -> {
            log.error("[sendChatMessageStream][userId({}) sendReqVO({}) 发生异常]", userId, sendReqVO, throwable);
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> {
                // 如果有内容，则更新内容
                if (StrUtil.isNotEmpty(contentBuffer)) {
                    chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                            .setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
                } else {
                    // 否则，则进行删除
                    chatMessageMapper.deleteById(assistantMessage.getId());
                }
            });
        }).doOnCancel(() -> {
            log.info("[sendChatMessageStream][userId({}) sendReqVO({}) 取消请求]", userId, sendReqVO);
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> {
                // 如果有内容，则更新内容
                if (StrUtil.isNotEmpty(contentBuffer)) {
                    chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                            .setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
                } else {
                    // 否则，则进行删除
                    chatMessageMapper.deleteById(assistantMessage.getId());
                }
            });
        }).onErrorResume(error -> Flux.just(error(ErrorCodeConstants.CHAT_STREAM_ERROR)));
    }

    /**
     * 将阻塞生成的完整答案按字符切分为 SSE 事件序列，保持流式接口形态并确保数据准确。
     *
     * <p>每帧只发<strong>增量</strong>（即当前字符），与原生 {@code StreamingChatModel} 的事件语义保持一致：
     * 前端按事件顺序拼接得到最终回复，不会出现累计内容重复展示的问题。
     */
    private Flux<CommonResult<AiChatMessageSendRespVO>> streamStaticAnswer(
            String fullContent,
            AiChatMessageDO userMessage, AiChatMessageDO assistantMessage,
            List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments,
            AiWebSearchResponse webSearchResponse) {
        // 初始化知识库/联网搜索缓存（与原生流式路径保持一致）
        Map<Long, AiKnowledgeDocumentDO> documentMap = TenantUtils.executeIgnore(() ->
                knowledgeDocumentService.getKnowledgeDocumentMap(
                        convertSet(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getDocumentId)));
        List<AiChatMessageRespVO.KnowledgeSegment> segments = BeanUtils.toBean(knowledgeSegments,
                AiChatMessageRespVO.KnowledgeSegment.class, segment -> {
                    AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                    segment.setDocumentName(document != null ? document.getName() : null);
                });
        List<AiWebSearchResponse.WebPage> webSearchPages = webSearchResponse != null ? webSearchResponse.getLists() : null;

        List<String> deltaChunks = new ArrayList<>(fullContent.length());
        for (int i = 0; i < fullContent.length(); i++) {
            deltaChunks.add(fullContent.substring(i, i + 1));
        }
        return Flux.fromIterable(deltaChunks)
                .map(delta -> success(new AiChatMessageSendRespVO()
                        .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                        .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                                .setContent(delta)
                                .setSegments(segments)
                                .setWebSearchPages(webSearchPages))))
                .doOnComplete(() -> TenantUtils.executeIgnore(() -> chatMessageMapper.updateById(
                        new AiChatMessageDO().setId(assistantMessage.getId()).setContent(fullContent))));
    }

    private List<AiKnowledgeSegmentSearchRespBO> recallKnowledgeSegment(String content,
            AiChatConversationDO conversation) {
        // 1. 查询聊天角色
        if (conversation == null || conversation.getRoleId() == null) {
            return Collections.emptyList();
        }
        AiChatRoleDO role = chatRoleService.getChatRole(conversation.getRoleId());
        if (role == null || CollUtil.isEmpty(role.getKnowledgeIds())) {
            return Collections.emptyList();
        }

        // 2. 遍历召回
        List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments = new ArrayList<>();
        for (Long knowledgeId : role.getKnowledgeIds()) {
            knowledgeSegments.addAll(knowledgeSegmentService.searchKnowledgeSegment(new AiKnowledgeSegmentSearchReqBO()
                    .setKnowledgeId(knowledgeId).setContent(content)));
        }
        return knowledgeSegments;
    }

    private Prompt buildPrompt(Long userId, AiChatConversationDO conversation, List<AiChatMessageDO> messages,
                               List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments,
                               AiWebSearchResponse webSearchResponse,
                               AiModelDO model, AiChatMessageSendReqVO sendReqVO, boolean bizQuestion,
                               String realtimeData) {
        List<Message> chatMessages = new ArrayList<>();
        // 1.1 System Context 角色设定
        if (StrUtil.isNotBlank(conversation.getSystemMessage())) {
            chatMessages.add(new SystemMessage(conversation.getSystemMessage()));
        }

        // 1.1.0 全局输出格式约束（每次对话注入，优先级高于用户输入）：
        //       禁止「说明：」调试注释输出；仅闲聊可直接回答；业务数据必须结构化整理
        chatMessages.add(new SystemMessage(OUTPUT_FORMAT_TEMPLATE));

        // 1.1.1 业务类问题：强制数据来源规则（SystemMessage 层）
        if (bizQuestion) {
            chatMessages.add(new SystemMessage(BIZ_QUESTION_TOOL_TEMPLATE));
        }
        // 1.1.2 MES 录入意图（工单/报工/IPQC）：强制「工具落库」+ 缺字段校验约束
        if (isMesCreateIntent(sendReqVO.getContent())) {
            chatMessages.add(new SystemMessage(
                    "用户要求录入 MES 业务数据（生产工单 / 生产报工 / IPQC 检验单）或智能分配任务。可选工具：\n" +
                    "- 生产工单 → mes_work_order_create（必填：工单名称、产品名称或编码、计划数量、要求交期）\n" +
                    "- 生产报工 → mes_feedback_create（必填：生产任务编号、报工数量、合格数量）\n" +
                    "- IPQC 检验单 → mes_qc_ipqc_create（必填：生产任务编号、检验类型、检验数量、合格数量）\n" +
                    "- 智能分配 → 先调 mes_team_load_query 查看各班组负载（recommended=是 为推荐），\n" +
                    "  向用户展示推荐并确认目标班组后，再调 mes_task_assign（必填：生产任务编号、目标班组）执行分配\n" +
                    "  【重要】用户确认班组后（如\"就分配给注塑A组\"），必须把本会话最近一次讨论的任务编号作为 taskCode\n" +
                    "  直接调用 mes_task_assign，严禁反问用户已提供过的任务编号；\n" +
                    "执行规则：\n" +
                    "1. 按业务类型选择对应工具，结合本轮消息与历史消息提取全部字段（用户在历史中已补充的字段必须使用）；\n" +
                    "2. 若用户上传了文件或图片，系统已完成识别，识别文本见 <Attachment> 部分（图片由视觉模型转写为字段文本）；从中提取字段；识别为空或含「无法识别」的字段请用户以文字补充；\n" +
                    "3. 必填字段缺失时，禁止调用工具，逐条列出缺失字段并提示用户补充，等待用户补齐；\n" +
                    "4. 字段齐全时本轮直接发起工具调用，不要先输出\"正在为您录入\"等过程性文字；\n" +
                    "5. 只能依据工具返回结果按「字段：值」格式如实回复；工具失败时如实转述失败原因；\n" +
                    "6. 历史中任何非本次工具返回的\"录入成功\"回复一律不得作为依据；严禁编造数据；严禁生成 Word 文档、模板、下载链接、跳转页面。"));
        }

        // 1.2 权限约束 SystemMessage（需求核心）：注入「用户权限 + MES 业务上下文 + 强制约束规则」。
        //     大模型在组织回答时也知道权限边界，与后端规则校验形成双重防护；
        //     即使被 Prompt 注入诱导，未授权的数据也因为没有工具而拿不到
        AiPermissionContextBO permissionContext = permissionGuard.buildContext(userId);
        chatMessages.add(new SystemMessage(permissionGuard.buildPermissionPrompt(permissionContext)));

        // 1.2 历史 history message 历史消息
        List<AiChatMessageDO> contextMessages = filterContextMessages(messages, conversation, sendReqVO);
        contextMessages.forEach(message -> {
            chatMessages.add(AiUtils.buildMessage(message.getType(), message.getContent()));
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(message.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
            // TODO @芋艿：历史的知识库；历史的搜索，要不要拼接？
        });

        // 1.3 当前 user message 新发送消息
        //     [筛选条件修复] 业务类问题的数据库真实数据改为与本次问题同处一个 UserMessage
        //     注入（见 MES_USER_DATA_TEMPLATE）。数据为已按筛选条件过滤的可读文本列表，
        //     与问题紧密相邻，模型必须逐条引用真实记录作答，避免答非所问或“有数据却说未查询到”
        if (bizQuestion && StrUtil.isNotBlank(realtimeData)) {
            // [诊断-可移除] 打印注入模型的数据文本，核验筛选过滤是否真正进入请求上下文
            log.info("[buildPrompt][MES 实时数据注入] 长度({}) 前 900 字：{}", realtimeData.length(),
                    StrUtil.maxLength(realtimeData, 900));
            chatMessages.add(new UserMessage(String.format(MES_USER_DATA_TEMPLATE,
                    sendReqVO.getContent(), realtimeData)));
        } else if (bizQuestion) {
            log.info("[buildPrompt][MES 实时数据未注入：realtimeData 为空] 问题={}", sendReqVO.getContent());
            chatMessages.add(new UserMessage(sendReqVO.getContent()));
        } else {
            chatMessages.add(new UserMessage(sendReqVO.getContent()));
        }

        // 1.4 知识库，通过 UserMessage 实现
        if (CollUtil.isNotEmpty(knowledgeSegments)) {
            String reference = knowledgeSegments.stream()
                    .map(segment -> "<Reference>" + segment.getContent() + "</Reference>")
                    .collect(Collectors.joining("\n\n"));
            chatMessages.add(new UserMessage(String.format(KNOWLEDGE_USER_MESSAGE_TEMPLATE, reference)));
        } else if (!bizQuestion && hasKnowledge(conversation.getRoleId())) {
            // 角色绑定了知识库，但本次未检索到相关内容：明确告知 AI 数据来源口径
            chatMessages.add(new UserMessage("本次对话未从平台知识库中检索到与问题相关的内容。回答要求：\n" +
                    "- 如果基于联网搜索或通用知识回答，必须明确告知用户：「该问题在平台知识库中未检索到数据，本回答为联网搜索/通用知识，非平台库内数据」。\n" +
                    "- 不要编造平台知识库中不存在的业务数据。"));
        }

        // 1.5 联网搜索，通过 UserMessage 实现
        if (webSearchResponse != null && CollUtil.isNotEmpty(webSearchResponse.getLists())) {
            String webSearch = webSearchResponse.getLists().stream()
                    .map(page -> {
                        String summary = StrUtil.isNotEmpty(page.getSummary()) ?
                                "\nSummary: " + page.getSummary() : "";
                        return "<WebSearch title=\"" + page.getTitle() + "\" url=\"" + page.getUrl() + "\">"
                                + StrUtil.blankToDefault(page.getSummary(), page.getSnippet()) + "</WebSearch>";
                    })
                    .collect(Collectors.joining("\n\n"));
            chatMessages.add(new UserMessage(String.format(WEB_SEARCH_USER_MESSAGE_TEMPLATE, webSearch)));
        }

        // 1.6 附件，通过 UserMessage 实现
        if (CollUtil.isNotEmpty(sendReqVO.getAttachmentUrls())) {
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(sendReqVO.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
        }

        // 2.1 查询 tool 工具（第三层防护：按权限过滤，只注入已授权业务域对应的工具）
        List<ToolCallback> toolCallbacks = getToolCallbackListByRoleId(conversation.getRoleId(), permissionContext);
        Map<String,Object> toolContext = CollUtil.isNotEmpty(toolCallbacks) ? AiUtils.buildCommonToolContext()
                : Map.of();
        // 2.2 构建 ChatOptions 对象
        AiPlatformEnum platform = AiPlatformEnum.validatePlatform(model.getPlatform());
        ChatOptions chatOptions = AiUtils.buildChatOptions(platform, model.getModel(),
                conversation.getTemperature(), conversation.getMaxTokens(),
                toolCallbacks, toolContext);
        return new Prompt(chatMessages, chatOptions);
    }

    private List<ToolCallback> getToolCallbackListByRoleId(Long roleId, AiPermissionContextBO permissionContext) {
        if (roleId == null) {
            return null;
        }
        AiChatRoleDO chatRole = chatRoleService.getChatRole(roleId);
        if (chatRole == null) {
            return null;
        }
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        // 1. 通过 toolIds（先按权限白名单过滤：模型拿不到未授权业务域的工具，从能力上杜绝越权调用）
        if (CollUtil.isNotEmpty(chatRole.getToolIds())) {
            Set<String> toolNames = convertSet(toolService.getToolList(chatRole.getToolIds()), AiToolDO::getName);
            toolNames = permissionGuard.filterAllowedTools(permissionContext, toolNames);
            List<String> resolved = new ArrayList<>();
            List<String> unresolved = new ArrayList<>();
            toolNames.forEach(toolName -> {
                ToolCallback toolCallback = resolveMesToolCallback(toolName);
                if (toolCallback != null) {
                    toolCallbacks.add(toolCallback);
                    resolved.add(toolName);
                } else {
                    unresolved.add(toolName);
                }
            });
            log.info("[getToolCallbackListByRoleId][工具解析完成 resolved({}) unresolved({})]",
                    resolved, unresolved);
        }
        // 2. 通过 mcpClients
        if (CollUtil.isNotEmpty(mcpClients) && CollUtil.isNotEmpty(chatRole.getMcpClientNames())) {
            chatRole.getMcpClientNames().forEach(mcpClientName -> {
                // 2.1 标准化名字，参考 McpClientAutoConfiguration 的 connectedClientName 方法
                String finalMcpClientName = this.mcpClientName + " - " + mcpClientName;
                // 2.2 匹配对应的 McpSyncClient
                mcpClients.forEach(mcpClient -> {
                    if (ObjUtil.notEqual(mcpClient.getClientInfo().name(), finalMcpClientName)) {
                        return;
                    }
                    ToolCallback[] mcpToolCallBacks = SyncMcpToolCallbackProvider.builder()
                            .mcpClients(mcpClient)
                            .build()
                            .getToolCallbacks();
                    CollUtil.addAll(toolCallbacks, mcpToolCallBacks);
                });
            });
        }
        return toolCallbacks;
    }

    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesWorkOrderCreateToolFunction workOrderCreateToolFunction;
    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesFeedbackCreateToolFunction feedbackCreateToolFunction;
    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesQcIpqcCreateToolFunction ipqcCreateToolFunction;
    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesTeamLoadQueryToolFunction teamLoadQueryToolFunction;
    @Resource
    private cn.iocoder.txgy.module.ai.tool.function.mes.MesTaskAssignToolFunction taskAssignToolFunction;
    @Resource
    private AiVisionService visionService;

    /**
     * Spring AI 2.0 不再自动把 Function Bean 注册为工具（resolver 对全部 Bean 返回 null），
     * 这里对 Function Calling 工具做显式兜底：mes_work_order_create 用 FunctionToolCallback 包装。
     */
    private volatile ToolCallback workOrderCreateToolCallback;
    private volatile ToolCallback feedbackCreateToolCallback;
    private volatile ToolCallback ipqcCreateToolCallback;
    private volatile ToolCallback teamLoadQueryToolCallback;
    private volatile ToolCallback taskAssignToolCallback;

    private ToolCallback resolveMesToolCallback(String toolName) {
        ToolCallback callback = toolCallbackResolver.resolve(toolName);
        if (callback == null) {
            switch (toolName) {
                case "mes_work_order_create" -> {
                    if (workOrderCreateToolCallback == null) {
                        synchronized (this) {
                            if (workOrderCreateToolCallback == null) {
                                workOrderCreateToolCallback = org.springframework.ai.tool.function.FunctionToolCallback
                                        .builder("mes_work_order_create", workOrderCreateToolFunction)
                                        .inputType(cn.iocoder.txgy.module.ai.tool.function.mes.MesWorkOrderCreateToolFunction.Request.class)
                                        .description("MES 生产工单创建：把用户提供的工单字段（名称/产品/数量/交期/客户等）直接创建为 MES 系统内的真实生产工单（写入数据库）")
                                        .build();
                            }
                        }
                    }
                    callback = workOrderCreateToolCallback;
                }
                case "mes_feedback_create" -> {
                    if (feedbackCreateToolCallback == null) {
                        synchronized (this) {
                            if (feedbackCreateToolCallback == null) {
                                feedbackCreateToolCallback = org.springframework.ai.tool.function.FunctionToolCallback
                                        .builder("mes_feedback_create", feedbackCreateToolFunction)
                                        .inputType(cn.iocoder.txgy.module.ai.tool.function.mes.MesFeedbackCreateToolFunction.Request.class)
                                        .description("MES 生产报工录入：把用户提供的报工字段（任务编号/报工数量/合格数量等）直接录入 MES 系统真实报工记录（写入数据库）")
                                        .build();
                            }
                        }
                    }
                    callback = feedbackCreateToolCallback;
                }
                case "mes_qc_ipqc_create" -> {
                    if (ipqcCreateToolCallback == null) {
                        synchronized (this) {
                            if (ipqcCreateToolCallback == null) {
                                ipqcCreateToolCallback = org.springframework.ai.tool.function.FunctionToolCallback
                                        .builder("mes_qc_ipqc_create", ipqcCreateToolFunction)
                                        .inputType(cn.iocoder.txgy.module.ai.tool.function.mes.MesQcIpqcCreateToolFunction.Request.class)
                                        .description("MES 过程检验单（IPQC）录入：把用户提供的检验字段（任务编号/检验类型/检验数量/合格数量等）直接录入 MES 系统真实检验单（写入数据库）")
                                        .build();
                            }
                        }
                    }
                    callback = ipqcCreateToolCallback;
                }
                case "mes_team_load_query" -> {
                    if (teamLoadQueryToolCallback == null) {
                        synchronized (this) {
                            if (teamLoadQueryToolCallback == null) {
                                teamLoadQueryToolCallback = org.springframework.ai.tool.function.FunctionToolCallback
                                        .builder("mes_team_load_query", teamLoadQueryToolFunction)
                                        .inputType(cn.iocoder.txgy.module.ai.tool.function.mes.MesTeamLoadQueryToolFunction.Request.class)
                                        .description("MES 班组负载查询（智能分配推荐）：查看各班组当前在产任务数/未完工量/成员数，负载最低的班组标记为推荐")
                                        .build();
                            }
                        }
                    }
                    callback = teamLoadQueryToolCallback;
                }
                case "mes_task_assign" -> {
                    if (taskAssignToolCallback == null) {
                        synchronized (this) {
                            if (taskAssignToolCallback == null) {
                                taskAssignToolCallback = org.springframework.ai.tool.function.FunctionToolCallback
                                        .builder("mes_task_assign", taskAssignToolFunction)
                                        .inputType(cn.iocoder.txgy.module.ai.tool.function.mes.MesTaskAssignToolFunction.Request.class)
                                        .description("MES 生产任务分配：把任务分配到指定班组（写入数据库）。先用 mes_team_load_query 获取推荐，用户确认班组后再调用本工具")
                                        .build();
                            }
                        }
                    }
                    callback = taskAssignToolCallback;
                }
                default -> { /* 其他工具保持 resolver 结果 */ }
            }
        }
        return callback;
    }

    /**
     * MES 录入意图识别：用户要求创建/录入生产工单、报工、IPQC 检验单（写意图，与查询意图分流）
     */
    private boolean isMesCreateIntent(String content) {
        if (content == null) {
            return false;
        }
        return content.matches("(?s).*(创建|新增|添加|生成|录入|登记|填报|补录|开一?个|建一?个|加一?条|录一?条|写一?条|下一?单).{0,10}(生产工单|工单|报工|报工单|过程检验|检验单|IPQC|ipqc|质检单).*")
                || content.matches("(?s).*(报工|报检|检验单|IPQC|ipqc).{0,8}(创建|新增|添加|录入|登记|填报|提交).*")
                // 【智能分配】任务分配/派工意图
                || content.matches("(?s).*(智能分配|分配|派工).{0,8}(任务|班组).*")
                || content.matches("(?s).*任务.{0,6}(智能分配|分配|派工).*");
    }

    /**
     * 多轮补字段延续：最近几轮历史中出现过录入意图，则本轮延续录入模式
     * （补字段轮次往往不含业务名词，如"合格数量48，录入吧"）
     */
    private boolean hasRecentCreateIntent(List<AiChatMessageDO> historyMessages) {
        if (CollUtil.isEmpty(historyMessages)) {
            return false;
        }
        int start = Math.max(0, historyMessages.size() - 6);
        for (int i = historyMessages.size() - 1; i >= start; i--) {
            if (isMesCreateIntent(historyMessages.get(i).getContent())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断角色是否绑定了知识库
     */
    private boolean hasKnowledge(Long roleId) {
        if (roleId == null) {
            return false;
        }
        AiChatRoleDO role = chatRoleService.getChatRole(roleId);
        return role != null && CollUtil.isNotEmpty(role.getKnowledgeIds());
    }

    /**
     * 执行联网搜索；失败时降级为不搜索，避免网络/密钥问题阻塞对话
     */
    private AiWebSearchResponse doWebSearch(String query) {
        try {
            return webSearchClient.search(new AiWebSearchRequest().setQuery(query)
                    .setSummary(true).setCount(WEB_SEARCH_COUNT));
        } catch (Exception e) {
            log.warn("[doWebSearch][联网搜索({}) 失败，降级为不搜索]", query, e);
            return null;
        }
    }

    /**
     * 从历史消息中，获得倒序的 n 组消息作为消息上下文
     * <p>
     * n 组：指的是 user + assistant 形成一组
     *
     * @param messages     消息列表
     * @param conversation 对话
     * @param sendReqVO    发送请求
     * @return 消息上下文
     */
    private List<AiChatMessageDO> filterContextMessages(List<AiChatMessageDO> messages,
            AiChatConversationDO conversation,
            AiChatMessageSendReqVO sendReqVO) {
        if (conversation.getMaxContexts() == null || ObjUtil.notEqual(sendReqVO.getUseContext(), Boolean.TRUE)) {
            return Collections.emptyList();
        }
        List<AiChatMessageDO> contextMessages = new ArrayList<>(conversation.getMaxContexts() * 2);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessageDO assistantMessage = CollUtil.get(messages, i);
            if (assistantMessage == null || assistantMessage.getReplyId() == null) {
                continue;
            }
            AiChatMessageDO userMessage = CollUtil.get(messages, i - 1);
            if (userMessage == null
                    || ObjUtil.notEqual(assistantMessage.getReplyId(), userMessage.getId())
                    || StrUtil.isEmpty(assistantMessage.getContent())) {
                continue;
            }
            // 由于后续要 reverse 反转，所以先添加 assistantMessage
            contextMessages.add(assistantMessage);
            contextMessages.add(userMessage);
            // 超过最大上下文，结束
            if (contextMessages.size() >= conversation.getMaxContexts() * 2) {
                break;
            }
        }
        Collections.reverse(contextMessages);
        return contextMessages;
    }

    private UserMessage buildAttachmentUserMessage(List<String> attachmentUrls) {
        if (CollUtil.isEmpty(attachmentUrls)) {
            return null;
        }
        // 读取文件内容
        Map<String, String> attachmentContents = Maps.newLinkedHashMapWithExpectedSize(attachmentUrls.size());
        for (String attachmentUrl : attachmentUrls) {
            try {
                String name = FileNameUtil.getName(attachmentUrl);
                String mineType = FileTypeUtils.getMineType(name);
                String content;
                if (FileTypeUtils.isImage(mineType)) {
                    // 特殊：图片调用视觉模型识别为「字段:值」文本（Base64 直接塞给文本模型无法理解）；
                    // 识别失败时降级为提示文字，不阻断对话
                    String imageText = visionService.extractImageText(attachmentUrl);
                    content = StrUtil.isNotBlank(imageText)
                            ? imageText
                            : "（图片内容识别失败，请用户以文字补充图片中的字段信息）";
                } else {
                    content = knowledgeDocumentService.readUrl(attachmentUrl);
                }
                if (StrUtil.isNotEmpty(content)) {
                    attachmentContents.put(name, content);
                }
            } catch (Exception e) {
                log.error("[buildAttachmentUserMessage][读取附件({}) 发生异常]", attachmentUrl, e);
            }
        }
        if (CollUtil.isEmpty(attachmentContents)) {
            return null;
        }

        // 拼接 UserMessage 消息
        String attachment = attachmentContents.entrySet().stream()
                .map(entry -> "<Attachment name=\"" + entry.getKey() + "\">" + entry.getValue() + "</Attachment>")
                .collect(Collectors.joining("\n\n"));
        return new UserMessage(String.format(Attachment_USER_MESSAGE_TEMPLATE, attachment));
    }

    private AiChatMessageDO createChatMessage(Long conversationId, Long replyId,
                                              AiModelDO model, Long userId, Long roleId,
                                              MessageType messageType, String content, Boolean useContext,
                                              List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments,
                                              List<String> attachmentUrls,
                                              AiWebSearchResponse webSearchResponse) {
        AiChatMessageDO message = new AiChatMessageDO().setConversationId(conversationId).setReplyId(replyId)
                .setModel(model.getModel()).setModelId(model.getId()).setUserId(userId).setRoleId(roleId)
                .setType(messageType.getValue()).setContent(content).setUseContext(useContext)
                .setSegmentIds(convertList(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getId))
                .setAttachmentUrls(attachmentUrls);
        if (webSearchResponse != null) {
            message.setWebSearchPages(webSearchResponse.getLists());
        }
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<AiChatMessageDO> getChatMessageListByConversationId(Long conversationId) {
        return chatMessageMapper.selectListByConversationId(conversationId);
    }

    @Override
    public AiChatMessageDO getChatMessage(Long id) {
        return chatMessageMapper.selectById(id);
    }

    @Override
    public void deleteChatMessage(Long id, Long userId) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null || ObjUtil.notEqual(message.getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public void deleteChatMessageByConversationId(Long conversationId, Long userId) {
        // 1. 校验消息存在
        List<AiChatMessageDO> messages = chatMessageMapper.selectListByConversationId(conversationId);
        if (CollUtil.isEmpty(messages) || ObjUtil.notEqual(messages.getFirst().getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteByIds(convertList(messages, AiChatMessageDO::getId));
    }

    @Override
    public void deleteChatMessageByAdmin(Long id) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public Integer getChatMessageCount(Long conversationId) {
        return chatMessageMapper.selectCount(AiChatMessageDO::getConversationId, conversationId).intValue();
    }

    @Override
    public Map<Long, Integer> getChatMessageCountMap(Collection<Long> conversationIds) {
        return chatMessageMapper.selectCountMapByConversationId(conversationIds);
    }

    @Override
    public PageResult<AiChatMessageDO> getChatMessagePage(AiChatMessagePageReqVO pageReqVO) {
        return chatMessageMapper.selectPage(pageReqVO);
    }

}
