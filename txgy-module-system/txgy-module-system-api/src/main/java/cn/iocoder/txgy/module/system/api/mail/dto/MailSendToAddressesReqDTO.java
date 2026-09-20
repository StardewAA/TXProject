package cn.iocoder.txgy.module.system.api.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 邮件发送（指定邮箱 + 附件 URL）Request DTO
 *
 * <p>与 {@link MailSendSingleToUserReqDTO} 的区别：本 DTO 只面向「显式指定的邮箱」，不关联任何后台/会员用户，
 * 且附件以「文件服务地址」传递 —— 跨服务（Feign）无法传递 {@code File} 对象，
 * 由邮件服务侧按地址取回文件内容后再作为附件发送。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 邮件发送（指定邮箱 + 附件 URL）Request DTO")
@Data
public class MailSendToAddressesReqDTO {

    @Schema(description = "收件邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "收件邮箱不能为空")
    private List<@Email String> toMails;

    @Schema(description = "抄送邮箱")
    private List<@Email String> ccMails;

    @Schema(description = "密送邮箱")
    private List<@Email String> bccMails;

    @Schema(description = "邮件模板编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "hrm_offer_notice")
    @NotNull(message = "邮件模板编号不能为空")
    private String templateCode;

    @Schema(description = "邮件模板参数")
    private Map<String, Object> templateParams;

    @Schema(description = "附件地址列表（文件服务 URL，邮件服务侧按地址下载后作为附件发送）")
    private List<String> attachmentUrls;

}
