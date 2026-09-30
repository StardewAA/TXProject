package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.Purpose;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import cn.iocoder.txgy.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

/** Own OTP namespace: does NOT read, create or consume system_sms_code rows. */
@Service
@RequiredArgsConstructor
public class MallSmsChallengeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "mall:auth:{mall-auth}:";
    private static final DefaultRedisScript<Long> RESERVE = script("reserve");
    private static final DefaultRedisScript<Long> READY = script("ready");
    private static final DefaultRedisScript<Long> CONSUME = script("consume");
    private static final DefaultRedisScript<Long> CANCEL = script("cancel");
    private static final DefaultRedisScript<Long> BUDGET = script("budget");
    private final MallTenantAuthProperties properties;
    private final MallAuthPolicy policy;
    private final StringRedisTemplate redis;
    private final SmsSendApi smsSendApi;

    public record Sent(String challengeId, long tenantId, int expiresIn, int retryAfter, String deliveryStatus) { }

    public Sent send(Long target, String mobile, Purpose purpose, String peerAddress) {
        long tenant = policy.resolve(target, purpose == Purpose.REGISTER);
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String binding = binding(tenant, mobile, purpose);
        List<String> keys = List.of(challengeKey(id), activeKey(binding), PREFIX + "cooldown:" + phoneKey(mobile),
                PREFIX + "daily:" + phoneKey(mobile), PREFIX + "send-peer:" + hmac("peer:" + peerAddress));
        Long result = redis.execute(RESERVE, keys, id, binding, codeDigest(id, binding, code),
                value(properties.getCodeTtlSeconds()), value(properties.getResendSeconds()),
                value(properties.getMobileDailyLimit()), value(properties.getIpHourlyLimit()));
        if (!Long.valueOf(1).equals(result)) throw exception(MALL_AUTH_RATE_LIMITED);
        try {
            Long logId = TenantUtils.execute(tenant, () -> smsSendApi.sendSingleSmsToMember(new SmsSendSingleToUserReqDTO()
                    .setMobile(mobile).setTemplateCode(properties.getSmsTemplateCode())
                    .setTemplateParams(Map.of("code", code, "minutes", properties.getCodeTtlSeconds() / 60))).getCheckedData());
            if (logId == null || logId <= 0 || !Long.valueOf(1).equals(redis.execute(READY, keys.subList(0, 2), id))) {
                throw exception(MALL_AUTH_SEND_FAILED);
            }
        } catch (RuntimeException error) {
            // Including ambiguous RPC timeout: invalidate rather than accepting an uncertain challenge.
            redis.execute(CANCEL, keys.subList(0, 2), id);
            throw exception(MALL_AUTH_SEND_FAILED);
        }
        // Existing SMS RPC queues work / may only record a disabled template. Never claim delivered.
        return new Sent(id, tenant, properties.getCodeTtlSeconds(), properties.getResendSeconds(), "SUBMITTED_UNCONFIRMED");
    }

    public long consume(Long target, String mobile, Purpose purpose, String id, String code, String peerAddress) {
        long tenant = policy.resolve(target, purpose == Purpose.REGISTER);
        if (id == null || !id.matches("[a-f0-9]{32}") || code == null || !code.matches("[0-9]{6}")) {
            throw exception(MALL_AUTH_CODE_INVALID);
        }
        String binding = binding(tenant, mobile, purpose);
        Long result = redis.execute(CONSUME, List.of(challengeKey(id), activeKey(binding),
                        PREFIX + "verify-phone:" + phoneKey(mobile), PREFIX + "verify-peer:" + hmac("peer:" + peerAddress)),
                id, binding, codeDigest(id, binding, code), value(properties.getMaxAttempts()),
                value(properties.getMobileVerifyLimit()), value(properties.getIpVerifyLimit()));
        if (Long.valueOf(-2).equals(result)) throw exception(MALL_AUTH_RATE_LIMITED);
        if (!Long.valueOf(1).equals(result)) throw exception(MALL_AUTH_CODE_INVALID);
        return tenant;
    }

    public void limitPasswordAttempts(String mobile, String peerAddress) {
        policy.requireConfigured();
        Long result = redis.execute(BUDGET, List.of(PREFIX + "password-phone:" + phoneKey(mobile),
                        PREFIX + "password-peer:" + hmac("peer:" + peerAddress)),
                value(properties.getMobileVerifyLimit()), value(properties.getIpVerifyLimit()));
        if (!Long.valueOf(1).equals(result)) throw exception(MALL_AUTH_RATE_LIMITED);
    }

    private String binding(long tenant, String mobile, Purpose purpose) {
        return hmac("binding:" + tenant + ":" + mobile + ":" + purpose.name());
    }
    private String codeDigest(String id, String binding, String code) { return hmac("code:" + id + ":" + binding + ":" + code); }
    private String phoneKey(String mobile) { return hmac("mobile:" + mobile); }
    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("商城验证码摘要初始化失败"); }
    }
    private static String challengeKey(String id) { return PREFIX + "challenge:" + id; }
    private static String activeKey(String binding) { return PREFIX + "active:" + binding; }
    private static String value(int value) { return Integer.toString(value); }
    private static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("lua/mall-auth-" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }
}
