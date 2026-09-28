package cn.iocoder.txgy.module.member.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.*;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.enums.file.FileAccessTypeEnum;
import cn.iocoder.txgy.module.infra.enums.file.FileBizTypeEnum;
import cn.iocoder.txgy.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.txgy.module.member.controller.admin.user.vo.MemberUserUpdateReqVO;
import cn.iocoder.txgy.module.member.controller.app.user.vo.*;
import cn.iocoder.txgy.module.member.convert.auth.AuthConvert;
import cn.iocoder.txgy.module.member.convert.user.MemberUserConvert;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.mq.producer.user.MemberUserProducer;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.txgy.module.system.enums.sms.SmsSceneEnum;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

/**
 * 会员 User Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Valid
@Slf4j
public class MemberUserServiceImpl implements MemberUserService {

    @Resource
    private MemberUserMapper memberUserMapper;

    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private SocialClientApi socialClientApi;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private MemberUserProducer memberUserProducer;

    @Resource
    private MemberPlatformAccountService platformAccountService;

    @Resource
    private FileApi fileApi;

    @Override
    public MemberUserDO getUserByMobile(String mobile) {
        return memberUserMapper.selectByMobile(mobile);
    }

    @Override
    public List<MemberUserDO> getUserListByNickname(String nickname) {
        return memberUserMapper.selectListByNicknameLike(nickname);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO createUserIfAbsent(String mobile, String registerIp, Integer terminal) {
        // 用户已经存在
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user != null) {
            return user;
        }
        // 用户不存在，则进行创建
        return createUser(mobile, null, null, registerIp, terminal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO createUser(String nickname, String avtar, String registerIp, Integer terminal) {
        return createUser(null, nickname, avtar, registerIp, terminal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO registerUser(String mobile, String password, String nickname,
                                     String registerIp, Integer terminal) {
        validateMobileUnique(null, mobile);
        MemberUserDO user = new MemberUserDO();
        user.setMobile(mobile);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setPassword(encodePassword(password));
        user.setRegisterIp(registerIp).setRegisterTerminal(terminal);
        user.setNickname(StrUtil.isBlank(nickname) ? "用户" + RandomUtil.randomNumbers(6) : nickname);
        user.setAvatar("");
        memberUserMapper.insert(user);
        registerUserCreateMessage(user);
        return user;
    }

    private MemberUserDO createUser(String mobile, String nickname, String avtar,
                                    String registerIp, Integer terminal) {
        // 生成密码
        String password = IdUtil.fastSimpleUUID();
        // 插入用户
        MemberUserDO user = new MemberUserDO();
        user.setMobile(mobile);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
        user.setPassword(encodePassword(password)); // 加密密码
        user.setRegisterIp(registerIp).setRegisterTerminal(terminal);
        user.setNickname(nickname).setAvatar(avtar); // 基础信息
        if (StrUtil.isEmpty(nickname)) {
            // 昵称为空时，随机一个名字，避免一些依赖 nickname 的逻辑报错，或者有点丑。例如说，短信发送有昵称时~
            user.setNickname("用户" + RandomUtil.randomNumbers(6));
        }
        memberUserMapper.insert(user);

        // 发送 MQ 消息：用户创建
        registerUserCreateMessage(user);
        return user;
    }

    private void registerUserCreateMessage(MemberUserDO user) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                memberUserProducer.sendUserCreateMessage(user.getId());
            }

        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO registerAccountUser(String loginName, String password, String nickname,
                                           String contactMobile, String memberIdentity, String registerIp, Integer terminal) {
        String normalized = cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy.normalize(loginName);
        MemberUserDO user = new MemberUserDO().setLoginName(normalized)
                .setContactMobile(StrUtil.isBlank(contactMobile) ? null : contactMobile)
                .setMemberIdentity(memberIdentity).setMobile(null)
                .setPassword(encodePassword(password)).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setNickname(StrUtil.isBlank(nickname) ? normalized : nickname).setAvatar("")
                .setRegisterIp(registerIp).setRegisterTerminal(terminal);
        memberUserMapper.insert(user); // Tenant + login_name unique index also protects concurrent registration.
        registerUserCreateMessage(user);
        return user;
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        memberUserMapper.updateById(new MemberUserDO().setId(id)
                .setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    public MemberUserDO getUser(Long id) {
        return memberUserMapper.selectById(id);
    }

    @Override
    public List<MemberUserDO> getUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return memberUserMapper.selectByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, AppMemberUserUpdateReqVO reqVO) {
        // 1.1 检测用户是否存在
        validateUserExists(userId);
        // 1.2 校验手机是否已经被绑定
        validateEmailUnique(userId, reqVO.getEmail());

        // 2. 更新用户
        MemberUserDO updateObj = BeanUtils.toBean(reqVO, MemberUserDO.class).setId(userId);
        memberUserMapper.updateById(updateObj);
        // 3. 头像走的是公开面（商品评价 / 后台客服会话都会展示），落库后需由服务端确认归属
        bindMemberAvatar(userId, reqVO.getAvatar());
    }

    /**
     * 会员头像属于**公开面**资源：它会出现在商品评价（{@code product_comment.user_avatar}）与后台客服会话列表里，
     * 由其他用户直接通过 {@code <img>} 访问，无法携带登录态。
     *
     * 若不在这里确认归属，头像会一直停留在「待绑定」状态，超期后连本人也读不到。
     * 与 {@code 009-file-public-recompute.sql} 的历史口径保持一致。
     */
    private void bindMemberAvatar(Long userId, String avatar) {
        if (userId == null || StrUtil.isBlank(avatar)) {
            return;
        }
        FileBindReqDTO reqDTO = new FileBindReqDTO()
                .setUrls(List.of(avatar.trim()))
                .setBizType(FileBizTypeEnum.MEMBER_AVATAR.name())
                .setBizId(String.valueOf(userId))
                .setAccessType(FileAccessTypeEnum.PUBLIC.getAccessType())
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                // 归属声明：infra 侧会拿它与文件记录的上传者比对，**不一致直接拒绝**。
                // 头像 URL 由会员本人上传，所以声明的上传者就是当前会员；
                // 这条同时堵住「把他人尚未绑定的私有图片地址填成自己的头像，从而被抬成公开」。
                .setOwnerUserType(UserTypeEnum.MEMBER.getValue())
                .setOwnerUserId(userId);
        // getCheckedData：绑定失败必须让资料保存回滚，否则用户以为改成功了，实际是一张读不到的头像
        fileApi.bindFile(reqDTO).getCheckedData();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserMobile(Long userId, AppMemberUserUpdateMobileReqVO reqVO) {
        // 1.1 检测用户是否存在
        MemberUserDO user = validateUserExists(userId);
        // 1.2 校验新手机是否已经被绑定
        validateMobileUnique(null, reqVO.getMobile());

        // 2.1 校验旧手机和旧验证码
        // 补充说明：从安全性来说，老手机也校验 oldCode 验证码会更安全。但是由于 uni-app 商城界面暂时没做，所以这里不强制校验
        if (StrUtil.isNotEmpty(reqVO.getOldCode())) {
            smsCodeApi.useSmsCode(new SmsCodeUseReqDTO().setMobile(user.getMobile()).setCode(reqVO.getOldCode())
                    .setScene(SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene()).setUsedIp(getClientIP())).checkError();
        }
        // 2.2 使用新验证码
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO().setMobile(reqVO.getMobile()).setCode(reqVO.getCode())
                .setScene(SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene()).setUsedIp(getClientIP())).checkError();

        // 3. 更新用户手机
        memberUserMapper.updateById(MemberUserDO.builder().id(userId).mobile(reqVO.getMobile()).build());
        platformAccountService.synchronizeMobileAlias(user, reqVO.getMobile());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserMobileByWeixin(Long userId, AppMemberUserUpdateMobileByWeixinReqVO reqVO) {
        // 1.1 获得对应的手机号信息
        SocialWxPhoneNumberInfoRespDTO phoneNumberInfo = socialClientApi.getWxMaPhoneNumberInfo(
                UserTypeEnum.MEMBER.getValue(), reqVO.getCode()).getCheckedData();
        Assert.notNull(phoneNumberInfo, "获得手机信息失败，结果为空");
        // 1.2 校验新手机是否已经被绑定
        MemberUserDO user = validateUserExists(userId);
        validateMobileUnique(userId, phoneNumberInfo.getPhoneNumber());

        // 2. 更新用户手机
        memberUserMapper.updateById(MemberUserDO.builder().id(userId).mobile(phoneNumberInfo.getPhoneNumber()).build());
        platformAccountService.synchronizeMobileAlias(user, phoneNumberInfo.getPhoneNumber());
    }

    @Override
    public void updateUserPassword(Long userId, AppMemberUserUpdatePasswordReqVO reqVO) {
        // 检测用户是否存在
        MemberUserDO user = validateUserExists(userId);
        // 校验验证码
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO().setMobile(user.getMobile()).setCode(reqVO.getCode())
                .setScene(SmsSceneEnum.MEMBER_UPDATE_PASSWORD.getScene()).setUsedIp(getClientIP())).checkError();

        // 更新用户密码
        memberUserMapper.updateById(MemberUserDO.builder().id(userId)
                .password(passwordEncoder.encode(reqVO.getPassword())).build());
    }

    @Override
    public void resetUserPassword(AppMemberUserResetPasswordReqVO reqVO) {
        // 检验用户是否存在
        MemberUserDO user = validateUserExists(reqVO.getMobile());

        // 使用验证码
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO, SmsSceneEnum.MEMBER_RESET_PASSWORD,
                getClientIP())).checkError();

        // 更新密码
        memberUserMapper.updateById(MemberUserDO.builder().id(user.getId())
                .password(passwordEncoder.encode(reqVO.getPassword())).build());
    }

    private MemberUserDO validateUserExists(String mobile) {
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }
        return user;
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 对密码进行加密
     *
     * @param password 密码
     * @return 加密后的密码
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(MemberUserUpdateReqVO updateReqVO) {
        // 校验存在
        MemberUserDO existing = validateUserExists(updateReqVO.getId());
        // Account-name users bind an identity phone only via a verified binding flow, not profile editing.
        if (existing.getLoginName() != null && StrUtil.isNotBlank(updateReqVO.getMobile())
                && !java.util.Objects.equals(existing.getMobile(), updateReqVO.getMobile())) {
            throw exception(new cn.iocoder.txgy.framework.common.exception.ErrorCode(1_004_008_194, "身份手机号须由会员验证绑定，不可通过资料编辑设置"));
        }
        if (StrUtil.isBlank(updateReqVO.getMobile())) updateReqVO.setMobile(null);
        // 校验手机唯一
        validateMobileUnique(updateReqVO.getId(), updateReqVO.getMobile());
        // 校验邮箱唯一
        validateEmailUnique(updateReqVO.getId(), updateReqVO.getEmail());

        // 更新
        MemberUserDO updateObj = MemberUserConvert.INSTANCE.convert(updateReqVO);
        memberUserMapper.updateById(updateObj);
        // 头像属于公开面资源，后台改头像同样要确认归属
        bindMemberAvatar(updateReqVO.getId(), updateReqVO.getAvatar());
        // 平台账号的身份手机号与会员手机号必须在同一事务内保持一致。
        // 即使后台传入空值也必须进入同步边界：已关联平台账号会明确拒绝清空，
        // 避免 member_user 已更新而平台登录别名仍残留，导致账号永久无法换绑。
        if (!java.util.Objects.equals(existing.getMobile(), updateReqVO.getMobile())) {
            platformAccountService.synchronizeMobileAlias(existing, updateReqVO.getMobile());
        }

        // 如果是禁用用户，则删除其 Token 信息
        if (CommonStatusEnum.isDisable(updateObj.getStatus())) {
            oauth2TokenApi.removeAccessToken(updateObj.getId(), UserTypeEnum.MEMBER.getValue()).checkError();
        }
    }

    @VisibleForTesting
    MemberUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        MemberUserDO user = memberUserMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @VisibleForTesting
    void validateMobileUnique(Long id, String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return;
        }
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_MOBILE_USED, mobile);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_MOBILE_USED, mobile);
        }
    }

    @VisibleForTesting
    void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        MemberUserDO user = memberUserMapper.selectByEmail(email);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_EMAIL_USED, email);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_EMAIL_USED, email);
        }
    }

    @Override
    public PageResult<MemberUserDO> getUserPage(MemberUserPageReqVO pageReqVO) {
        return memberUserMapper.selectPage(pageReqVO);
    }

    @Override
    public void updateUserLevel(Long id, Long levelId, Integer experience) {
        // 0 代表无等级：防止UpdateById时，会被过滤掉的问题
        levelId = ObjectUtil.defaultIfNull(levelId, 0L);
        memberUserMapper.updateById(new MemberUserDO()
                .setId(id)
                .setLevelId(levelId).setExperience(experience)
        );
    }

    @Override
    public Long getUserCountByGroupId(Long groupId) {
        return memberUserMapper.selectCountByGroupId(groupId);
    }

    @Override
    public Long getUserCountByLevelId(Long levelId) {
        return memberUserMapper.selectCountByLevelId(levelId);
    }

    @Override
    public Long getUserCountByTagId(Long tagId) {
        return memberUserMapper.selectCountByTagId(tagId);
    }

    @Override
    public boolean updateUserPoint(Long id, Integer point) {
        if (point > 0) {
            memberUserMapper.updatePointIncr(id, point);
        } else if (point < 0) {
            return memberUserMapper.updatePointDecr(id, point) > 0;
        }
        return true;
    }

}
