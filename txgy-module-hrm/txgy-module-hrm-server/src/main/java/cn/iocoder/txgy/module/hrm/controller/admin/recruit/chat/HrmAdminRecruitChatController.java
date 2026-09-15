package cn.iocoder.txgy.module.hrm.controller.admin.recruit.chat;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatEndReqVO;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/hrm/recruit/chat")
@PreAuthorize("@ss.hasPermission('hrm:recruit:chat:query')")
public class HrmAdminRecruitChatController {
    @Resource private HrmRecruitChatService chatService;
    @GetMapping("/conversation/list")
    public CommonResult<List<HrmPortalRecruitChatConversationRespVO>> list(@RequestParam(required=false) Long candidateId, @RequestParam(required=false) Long postId, @RequestParam(required=false) Integer status, @RequestParam(required=false) String startTime, @RequestParam(required=false) String endTime) {
        return success(chatService.getConversationList(getLoginUserId()).stream()
                .filter(x -> x.getLastTime() != null)
                .filter(x -> candidateId == null || candidateId.equals(x.getCandidateId()))
                .filter(x -> postId == null || postId.equals(x.getPostId()))
                .filter(x -> status == null || status.equals(x.getStatus()))
                .filter(x -> startTime == null || x.getLastTime() == null || x.getLastTime().toString().compareTo(startTime) >= 0)
                .filter(x -> endTime == null || x.getLastTime() == null || x.getLastTime().toString().compareTo(endTime) <= 0)
                .toList()); }
    @PostMapping("/message/send")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:chat:reply')")
    public CommonResult<HrmPortalRecruitChatMessageRespVO> send(@Valid @RequestBody HrmPortalRecruitChatSendReqVO req) { return success(chatService.sendMessage(getLoginUserId(), req)); }
    @GetMapping("/message/list")
    public CommonResult<List<HrmPortalRecruitChatMessageRespVO>> messages(@RequestParam Long conversationId, @RequestParam(required=false) Long minId, @RequestParam(required=false) Integer limit) { return success(chatService.getMessageList(getLoginUserId(), conversationId, minId, limit)); }
    @PutMapping("/conversation/read")
    public CommonResult<Boolean> read(@RequestParam Long conversationId) { chatService.markRead(getLoginUserId(), conversationId); return success(true); }

    @PostMapping("/conversation/convert-candidate")
    @Operation(summary = "将招聘沟通转为候选人")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:chat:convert-candidate')")
    public CommonResult<Long> convertCandidate(@RequestParam Long conversationId) {
        return success(chatService.convertConversationToCandidate(getLoginUserId(), conversationId));
    }

    @PutMapping("/conversation/end")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:chat:end')")
    public CommonResult<Boolean> end(@Valid @RequestBody HrmPortalRecruitChatEndReqVO req) { chatService.endConversation(getLoginUserId(), req.getConversationId(), req.getReason()); return success(true); }

    @DeleteMapping("/conversation/delete")
    @Operation(summary = "删除招聘沟通会话")
    @Parameter(name = "id", description = "沟通会话编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:chat:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        chatService.deleteConversation(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/conversation/delete-list")
    @Operation(summary = "批量删除招聘沟通会话")
    @Parameter(name = "ids", description = "沟通会话编号数组", required = true, example = "1024,1025")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:chat:delete')")
    public CommonResult<Boolean> deleteList(@RequestParam("ids") @NotEmpty List<Long> ids) {
        chatService.deleteConversationList(getLoginUserId(), ids);
        return success(true);
    }
}
