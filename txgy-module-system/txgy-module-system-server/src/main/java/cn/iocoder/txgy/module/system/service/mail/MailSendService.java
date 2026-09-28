package cn.iocoder.txgy.module.system.service.mail;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.module.system.mq.message.mail.MailSendMessage;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 邮件发送 Service 接口
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
public interface MailSendService {

    /**
     * 发送单条邮件给管理后台的用户
     *
     * @param userId 用户编码
     * @param toMails 收件邮箱
     * @param ccMails 抄送邮箱
     * @param bccMails 密送邮箱
     * @param templateCode 邮件模版编码
     * @param templateParams 邮件模版参数
     * @param attachments 附件
     * @return 发送日志编号
     */
    default Long sendSingleMailToAdmin(Long userId,
                                       Collection<String> toMails, Collection<String> ccMails, Collection<String> bccMails,
                                       String templateCode, Map<String, Object> templateParams,
                                       File... attachments) {
        return sendSingleMail(toMails, ccMails, bccMails, userId, UserTypeEnum.ADMIN.getValue(),
                templateCode, templateParams, attachments);
    }

    /**
     * 发送单条邮件给用户 APP 的用户
     *
     * @param userId 用户编码
     * @param toMails 收件邮箱
     * @param ccMails 抄送邮箱
     * @param bccMails 密送邮箱
     * @param templateCode 邮件模版编码
     * @param templateParams 邮件模版参数
     * @param attachments 附件
     * @return 发送日志编号
     */
    default Long sendSingleMailToMember(Long userId,
                                        Collection<String> toMails, Collection<String> ccMails, Collection<String> bccMails,
                                        String templateCode, Map<String, Object> templateParams,
                                        File... attachments) {
        return sendSingleMail(toMails, ccMails, bccMails, userId, UserTypeEnum.MEMBER.getValue(),
                templateCode, templateParams, attachments);
    }

    /**
     * 发送单条邮件到「显式指定的邮箱」，不关联任何用户。
     *
     * <p>与 {@link #sendSingleMailToAdmin} 的区别：后者会把 userId 对应用户的邮箱也加入收件人
     * （语义是「发给我」），因此不适合后台的「测试发送」场景，
     * 否则测试时管理员的邮箱会被额外塞进收件人，出现「只填了一个收件人却发给了两个人」。</p>
     *
     * @param toMails 收件邮箱
     * @param ccMails 抄送邮箱
     * @param bccMails 密送邮箱
     * @param templateCode 邮件模版编码
     * @param templateParams 邮件模版参数
     * @param attachments 附件
     * @return 发送日志编号
     */
    default Long sendSingleMailToAddresses(Collection<String> toMails, Collection<String> ccMails,
                                           Collection<String> bccMails, String templateCode,
                                           Map<String, Object> templateParams, File... attachments) {
        return sendSingleMail(toMails, ccMails, bccMails, null, null, templateCode, templateParams, attachments);
    }

    /**
     * 发送单条邮件到「显式指定的邮箱」，附件以「文件服务地址」传入。
     *
     * <p>跨服务调用无法传递 {@link File} 对象，因此 HRM 等模块先把附件上传到文件服务，
     * 再把地址传进来，由邮件服务侧下载为临时文件后随信发送。</p>
     *
     * @param toMails 收件邮箱
     * @param ccMails 抄送邮箱
     * @param bccMails 密送邮箱
     * @param templateCode 邮件模版编码
     * @param templateParams 邮件模版参数
     * @param attachmentUrls 附件地址列表（文件服务 URL）
     * @return 发送日志编号
     */
    Long sendSingleMailToAddresses(Collection<String> toMails, Collection<String> ccMails,
                                   Collection<String> bccMails, String templateCode,
                                   Map<String, Object> templateParams, List<String> attachmentUrls);

    /**
     * 发送单条邮件
     *
     * @param toMails 收件邮箱
     * @param ccMails 抄送邮箱
     * @param bccMails 密送邮箱
     * @param userId 用户编号
     * @param userType 用户类型
     * @param templateCode 邮件模版编码
     * @param templateParams 邮件模版参数
     * @param attachments 附件
     * @return 发送日志编号
     */
    Long sendSingleMail(Collection<String> toMails, Collection<String> ccMails, Collection<String> bccMails,
                        Long userId, Integer userType,
                        String templateCode, Map<String, Object> templateParams,
                        File... attachments);

    /**
     * 执行真正的邮件发送
     * 注意，该方法仅仅提供给 MQ Consumer 使用
     *
     * @param message 邮件
     */
    void doSendMail(MailSendMessage message);

}
