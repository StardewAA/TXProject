package cn.iocoder.txgy.module.ai.controller.admin.chat;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessagePageReqVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageRespVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import cn.iocoder.txgy.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO;
import cn.iocoder.txgy.module.ai.dal.dataobject.chat.AiChatConversationDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.chat.AiChatMessageDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.knowledge.AiKnowledgeDocumentDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.knowledge.AiKnowledgeSegmentDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiChatRoleDO;
import cn.iocoder.txgy.module.ai.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.ai.service.chat.AiChatConversationService;
import cn.iocoder.txgy.module.ai.service.chat.AiChatMessageService;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeDocumentService;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeSegmentService;
import cn.iocoder.txgy.module.ai.service.model.AiChatRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.error;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 聊天消息")
@RestController
@RequestMapping("/ai/chat/message")
@Slf4j
public class AiChatMessageController {

    /** [SSE 修复] 流式未知异常统一提示话术：对外隐藏英文堆栈/系统细节 */
    private static final String SSE_UNKNOWN_ERROR_MESSAGE = "数据查询接口异常，请稍后重试";

    @Resource
    private AiChatMessageService chatMessageService;
    @Resource
    private AiChatConversationService chatConversationService;
    @Resource
    private AiChatRoleService chatRoleService;
    @Resource
    private AiKnowledgeSegmentService knowledgeSegmentService;
    @Resource
    private AiKnowledgeDocumentService knowledgeDocumentService;

    @Operation(summary = "发送消息（段式）", description = "一次性返回，响应较慢")
    @PostMapping("/send")
    public CommonResult<AiChatMessageSendRespVO> sendMessage(@Valid @RequestBody AiChatMessageSendReqVO sendReqVO) {
        return success(chatMessageService.sendMessage(sendReqVO, getLoginUserId()));
    }

    @Operation(summary = "发送消息（流式）", description = "流式返回，响应较快")
    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CommonResult<AiChatMessageSendRespVO>> sendChatMessageStream(@Valid @RequestBody AiChatMessageSendReqVO sendReqVO,
            HttpServletResponse response) {
        // ===== [SSE 修复 1] 强制 SSE 标准响应头（在 Flux 写流前同步设置，流内异常也始终保持）=====
        // 保证前端 fetchEventSource 拿到的永远是 text/event-stream，
        // 而不是被代理/异常处理改写后的 application/json
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE); // Content-Type: text/event-stream
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache"); // Cache-Control: no-cache
        response.setHeader("X-Content-Type-Options", "nosniff"); // X-Content-Type-Options: nosniff
        Long userId = getLoginUserId();
        // ===== [SSE 修复 2] Flux.defer 延迟执行 + 异常转 SSE 错误帧，规避全局异常 JSON 拦截 =====
        // 原实现 sendChatMessageStream 中的对话/模型校验、权限检查、数据库预查询等"同步段"
        // 若抛异常，会被全局异常处理器包装成 JSON（Content-Type: application/json）返回，
        // 导致前端报 "Expected content-type to be text/event-stream, Actual: application/json"。
        // 此处延迟到订阅期执行，使同步异常变为 Flux 的 error signal，
        // 并由 onErrorResume 以 SSE 错误帧（data: {"code":..,"msg":".."}）推送给前端
        return Flux.defer(() -> chatMessageService.sendChatMessageStream(sendReqVO, userId))
                // 已知业务异常（越权/对话不存在/模型未配置等）：透出后端具体原因
                .onErrorResume(ServiceException.class, ex -> Flux.just(error(ex.getCode(),
                        StrUtil.blankToDefault(ex.getMessage(), "请求失败，请稍后重试"))))
                // 未知异常：隐藏内部细节，按约定话术提示，避免英文堆栈暴露给用户
                .onErrorResume(Exception.class, ex -> {
                    log.error("[sendChatMessageStream][userId({}) 发送 SSE 流异常]", userId, ex);
                    return Flux.just(error(ErrorCodeConstants.CHAT_STREAM_ERROR.getCode(), SSE_UNKNOWN_ERROR_MESSAGE));
                });
    }

    @Operation(summary = "获得指定对话的消息列表")
    @GetMapping("/list-by-conversation-id")
    @Parameter(name = "conversationId", required = true, description = "对话编号", example = "1024")
    public CommonResult<List<AiChatMessageRespVO>> getChatMessageListByConversationId(
            @RequestParam("conversationId") Long conversationId) {
        AiChatConversationDO conversation = chatConversationService.getChatConversation(conversationId);
        if (conversation == null || ObjUtil.notEqual(conversation.getUserId(), getLoginUserId())) {
            return success(Collections.emptyList());
        }
        // 1. 获取消息列表
        List<AiChatMessageDO> messageList = chatMessageService.getChatMessageListByConversationId(conversationId);
        if (CollUtil.isEmpty(messageList)) {
            return success(Collections.emptyList());
        }

        // 2. 拼接数据，主要是知识库段落信息
        Map<Long, AiKnowledgeSegmentDO> segmentMap = knowledgeSegmentService.getKnowledgeSegmentMap(convertListByFlatMap(messageList,
                message -> CollUtil.isEmpty(message.getSegmentIds()) ? null : message.getSegmentIds().stream()));
        Map<Long, AiKnowledgeDocumentDO> documentMap = knowledgeDocumentService.getKnowledgeDocumentMap(
                convertList(segmentMap.values(), AiKnowledgeSegmentDO::getDocumentId));
        List<AiChatMessageRespVO> messageVOList = BeanUtils.toBean(messageList, AiChatMessageRespVO.class);
        for (int i = 0; i < messageList.size(); i++) {
            AiChatMessageDO message = messageList.get(i);
            if (CollUtil.isEmpty(message.getSegmentIds())) {
                continue;
            }
            // 设置知识库段落信息
            messageVOList.get(i).setSegments(convertList(message.getSegmentIds(), segmentId -> {
                AiKnowledgeSegmentDO segment = segmentMap.get(segmentId);
                if (segment == null) {
                    return null;
                }
                AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                if (document == null) {
                    return null;
                }
                return new AiChatMessageRespVO.KnowledgeSegment().setId(segment.getId()).setContent(segment.getContent())
                        .setDocumentId(segment.getDocumentId()).setDocumentName(document.getName());
            }));
        }
        return success(messageVOList);
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/delete")
    @Parameter(name = "id", required = true, description = "消息编号", example = "1024")
    public CommonResult<Boolean> deleteChatMessage(@RequestParam("id") Long id) {
        chatMessageService.deleteChatMessage(id, getLoginUserId());
        return success(true);
    }

    @Operation(summary = "删除指定对话的消息")
    @DeleteMapping("/delete-by-conversation-id")
    @Parameter(name = "conversationId", required = true, description = "对话编号", example = "1024")
    public CommonResult<Boolean> deleteChatMessageByConversationId(@RequestParam("conversationId") Long conversationId) {
        chatMessageService.deleteChatMessageByConversationId(conversationId, getLoginUserId());
        return success(true);
    }

    // ========== 对话管理 ==========

    @GetMapping("/page")
    @Operation(summary = "获得消息分页", description = "用于【对话管理】菜单")
    @PreAuthorize("@ss.hasPermission('ai:chat-message:query')")
    public CommonResult<PageResult<AiChatMessageRespVO>> getChatMessagePage(AiChatMessagePageReqVO pageReqVO) {
        PageResult<AiChatMessageDO> pageResult = chatMessageService.getChatMessagePage(pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }
        // 拼接数据
        Map<Long, AiChatRoleDO> roleMap = chatRoleService.getChatRoleMap(
                convertSet(pageResult.getList(), AiChatMessageDO::getRoleId));
        return success(BeanUtils.toBean(pageResult, AiChatMessageRespVO.class,
                respVO -> MapUtils.findAndThen(roleMap, respVO.getRoleId(),
                        role -> respVO.setRoleName(role.getName()))));
    }

    @GetMapping("/get")
    @Operation(summary = "获得消息", description = "用于【对话管理】菜单")
    @Parameter(name = "id", required = true, description = "消息编号", example = "1024")
    @PreAuthorize("@ss.hasPermission('ai:chat-message:query')")
    public CommonResult<AiChatMessageRespVO> getChatMessage(@RequestParam("id") Long id) {
        AiChatMessageDO message = chatMessageService.getChatMessage(id);
        AiChatMessageRespVO respVO = BeanUtils.toBean(message, AiChatMessageRespVO.class);
        if (respVO != null && respVO.getRoleId() != null) {
            AiChatRoleDO role = chatRoleService.getChatRole(respVO.getRoleId());
            respVO.setRoleName(role != null ? role.getName() : null);
        }
        return success(respVO);
    }

    @Operation(summary = "管理员删除消息")
    @DeleteMapping("/delete-by-admin")
    @Parameter(name = "id", required = true, description = "消息编号", example = "1024")
    @PreAuthorize("@ss.hasPermission('ai:chat-message:delete')")
    public CommonResult<Boolean> deleteChatMessageByAdmin(@RequestParam("id") Long id) {
        chatMessageService.deleteChatMessageByAdmin(id);
        return success(true);
    }

}
