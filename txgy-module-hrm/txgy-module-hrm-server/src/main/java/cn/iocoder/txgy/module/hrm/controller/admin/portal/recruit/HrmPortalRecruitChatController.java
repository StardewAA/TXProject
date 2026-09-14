package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatEndReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatOpenReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "员工端 - HRM 招聘沟通")
@RestController
@RequestMapping("/hrm/portal/recruit/chat")
@PreAuthorize("isAuthenticated()")
@Validated
public class HrmPortalRecruitChatController {

    @Resource
    private HrmRecruitChatService chatService;

    @PostMapping("/conversation/open")
    @Operation(summary = "打开与岗位 HR 的沟通会话")
    public CommonResult<HrmPortalRecruitChatConversationRespVO> openConversation(
            @Valid @RequestBody HrmPortalRecruitChatOpenReqVO reqVO) {
        return success(chatService.openConversation(getLoginUserId(), reqVO.getPostId()));
    }

    @GetMapping("/conversation/list")
    @Operation(summary = "查询我的招聘沟通会话")
    public CommonResult<List<HrmPortalRecruitChatConversationRespVO>> getConversationList() {
        return success(chatService.getConversationList(getLoginUserId()));
    }

    @GetMapping("/message/list")
    @Operation(summary = "查询招聘沟通消息")
    public CommonResult<List<HrmPortalRecruitChatMessageRespVO>> getMessageList(
            @RequestParam("conversationId") Long conversationId,
            @RequestParam(value = "minId", required = false) Long minId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(chatService.getMessageList(getLoginUserId(), conversationId, minId, limit));
    }

    @PostMapping("/message/send")
    @Operation(summary = "发送招聘沟通消息")
    public CommonResult<HrmPortalRecruitChatMessageRespVO> sendMessage(
            @Valid @RequestBody HrmPortalRecruitChatSendReqVO reqVO) {
        return success(chatService.sendMessage(getLoginUserId(), reqVO));
    }

    @PutMapping("/conversation/read")
    @Operation(summary = "标记招聘沟通会话已读")
    @Parameter(name = "conversationId", description = "会话编号", required = true)
    public CommonResult<Boolean> markRead(@RequestParam("conversationId") Long conversationId) {
        chatService.markRead(getLoginUserId(), conversationId);
        return success(true);
    }

    @PutMapping("/conversation/end")
    @Operation(summary = "结束招聘沟通")
    public CommonResult<Boolean> endConversation(@Valid @RequestBody HrmPortalRecruitChatEndReqVO reqVO) {
        chatService.endConversation(getLoginUserId(), reqVO.getConversationId(), reqVO.getReason());
        return success(true);
    }
}
