package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 求职助理对话 Request VO")
@Data
public class HrmPortalRecruitAgentChatReqVO {

    /**
     * 长度上限按「一句话 + 一段附件正文」定。
     *
     * <p>从 500 放开到 20000 是有意的：对话里可以直接上传简历，前端会把 PDF/DOCX
     * 提取出来的正文拼在这条消息里一起发过来。原来卡在 500 是防「调用方随便塞全文」，
     * 现在塞全文恰恰是需求本身 —— 前端提取时已经截断过一道。</p>
     */
    @Schema(description = "求职者说的话，可能包含附件的正文", example = "帮我看看防腐保温方向该怎么准备")
    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "内容不能超过 20000 个字符")
    private String message;

    /**
     * 随消息一起发的图片，data URL 形式。
     *
     * <p>模型是多模态的（当前配的 deepseek-flash 能读图），所以图片直接给模型看，
     * 而不是在前端先 OCR 成文字 —— 简历照片、截图这类内容被 OCR 掉的信息太多了。
     * 前端已压到长边 1600px 的 JPEG，这里的上限只是兜底。</p>
     */
    @Schema(description = "随消息一起发的图片（data URL）；不传表示纯文字")
    @Size(max = 4000000, message = "图片过大")
    private String imageDataUrl;
}
