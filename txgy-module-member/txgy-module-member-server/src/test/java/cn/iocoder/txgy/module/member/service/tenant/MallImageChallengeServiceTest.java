package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static cn.iocoder.txgy.module.member.service.tenant.MallImageChallengeService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MallImageChallengeServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private MallImageChallengeService service;
    private final List<List<String>> keys = new ArrayList<>();
    private final List<List<String>> args = new ArrayList<>();
    private static final String SESSION = "a".repeat(32);
    private static final String ID = "b".repeat(32);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("12".repeat(32));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            keys.add(List.copyOf(call.getArgument(1)));
            List<String> arguments = new ArrayList<>();
            for (int i = 2; i < call.getArguments().length; i++) arguments.add(String.valueOf(call.getArguments()[i]));
            args.add(arguments);
            return 1L;
        });
        service = new MallImageChallengeService(redis);
    }

    @Test void issueReturnsDecodablePngAndOnlyHmacDataReachesRedis() throws Exception {
        Challenge challenge = service.issue(9001, " Account_1 ", SESSION, null, "REGISTER", "test-peer");
        assertEquals(9001, challenge.tenantId());
        assertEquals(300, challenge.expiresIn());
        assertEquals(2, challenge.retryAfter());
        assertTrue(challenge.challengeId().matches("[a-f0-9]{32}"));
        assertTrue(challenge.imageDataUrl().startsWith("data:image/png;base64,"));
        var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(challenge.imageDataUrl().substring(22))));
        assertEquals(180, image.getWidth()); assertEquals(60, image.getHeight());
        assertTrue(args.getFirst().get(1).matches("[a-f0-9]{64}"));
        assertTrue(args.getFirst().get(2).matches("[a-f0-9]{64}"));
        assertTrue(keys.getFirst().stream().allMatch(key -> key.startsWith("mall:image:{mall-image}:")));
        assertFalse(keys.getFirst().toString().contains("account_1"));
        assertFalse(keys.getFirst().toString().contains("test-peer"));
        assertFalse(args.getFirst().toString().contains("Account_1"));
        assertEquals(5, Challenge.class.getRecordComponents().length);
    }

    @Test void tenantChangesKeepSessionActiveKeyAndGlobalBudgetsButChangeBinding() {
        service.issue(9001, "account_1", SESSION, null, "REGISTER", "test-peer");
        service.issue(9002, "ACCOUNT_1", SESSION, ID, "REGISTER", "test-peer");
        assertEquals(keys.get(0).subList(1, 6), keys.get(1).subList(1, 6));
        assertNotEquals(args.get(0).get(1), args.get(1).get(1));
        assertFalse(keys.get(1).toString().contains(ID));
    }

    @Test void anotherSessionOrNameCannotShareChallengeBinding() {
        service.consume(9001, "account_1", SESSION, ID, "abc23", "test-peer");
        service.consume(9001, "account_1", "c".repeat(32), ID, "abc23", "test-peer");
        service.consume(9001, "account_2", SESSION, ID, "abc23", "test-peer");
        assertNotEquals(args.get(0).get(1), args.get(1).get(1));
        assertNotEquals(args.get(0).get(1), args.get(2).get(1));
        assertNotEquals(keys.get(0).get(1), keys.get(1).get(1));
        assertEquals(keys.get(0).get(1), keys.get(2).get(1));
        assertEquals(keys.get(0).get(4), keys.get(1).get(4));
    }

    @Test void namesAndAnswersHaveConsistentCaseNormalization() {
        service.consume(9001, " Account_1 ", SESSION, ID, " abC23 ", "test-peer");
        service.consume(9001, "account_1", SESSION, ID, "ABC23", "test-peer");
        assertEquals(keys.get(0), keys.get(1));
        assertEquals(args.get(0), args.get(1));
    }

    @Test void unavailableRedisAndMissingSharedSecretFailClosed() {
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString())).thenReturn(false);
        assertEquals(UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.issue(9001, "account_1", SESSION, null, "REGISTER", "peer")).getCode());
        assertTrue(keys.isEmpty());
        when(values.get(anyString())).thenThrow(new IllegalStateException("private transport details"));
        var error = assertThrows(ServiceException.class, () -> service.consume(9001, "account_1", SESSION, ID, "ABC23", "peer"));
        assertEquals(UNAVAILABLE.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("private"));
    }

    @Test void initializationUsesRedisWinnerNotLosingLocallyGeneratedSecret() {
        when(values.get(anyString())).thenReturn(null, "34".repeat(32), "34".repeat(32));
        when(values.setIfAbsent(anyString(), anyString())).thenReturn(false);
        service.consume(9001, "account_1", SESSION, ID, "ABC23", "peer");
        service.consume(9001, "account_1", SESSION, ID, "ABC23", "peer");
        verify(values).setIfAbsent(anyString(), matches("[a-f0-9]{64}"));
        assertEquals(args.get(0), args.get(1));
    }

    @Test void malformedScopeOrUnsupportedPurposeNeverTouchesRedis() {
        assertThrows(ServiceException.class, () -> service.issue(9001, "account_1", SESSION, null, "SMS_LOGIN", "peer"));
        assertThrows(ServiceException.class, () -> service.issue(0, "account_1", SESSION, null, "REGISTER", "peer"));
        assertThrows(ServiceException.class, () -> service.issue(9001, "account_1", SESSION, "../other", "REGISTER", "peer"));
        assertThrows(ServiceException.class, () -> service.consume(9001, "account_1", "wrong", ID, "ABC23", "peer"));
        assertThrows(ServiceException.class, () -> service.consume(9001, "13900000000", SESSION, ID, "ABC23", "peer"));
        assertThrows(ServiceException.class, () -> service.consume(9001, "account-name", SESSION, ID, "ABC23", "peer"));
        verifyNoInteractions(values);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test void limitsAndInvalidConsumptionHaveSeparatePublicErrors() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1L);
        assertEquals(LIMITED.getCode(), assertThrows(ServiceException.class,
                () -> service.issue(9001, "account_1", SESSION, null, "REGISTER", "peer")).getCode());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-2L);
        assertEquals(LIMITED.getCode(), assertThrows(ServiceException.class,
                () -> service.consume(9001, "account_1", SESSION, ID, "ABC23", "peer")).getCode());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertEquals(INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.consume(9001, "account_1", SESSION, ID, "ABC23", "peer")).getCode());
    }

    @Test void passwordBudgetAcceptsLegacyPhoneAndNormalizesLoginName() {
        service.limitPasswordAttempts("13900000000", "peer");
        service.limitPasswordAttempts(" Account_1 ", "peer");
        service.limitPasswordAttempts("account_1", "peer");
        assertEquals(keys.get(0).get(1), keys.get(1).get(1));
        assertEquals(keys.get(1), keys.get(2));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertEquals(LIMITED.getCode(), assertThrows(ServiceException.class,
                () -> service.limitPasswordAttempts("account_1", "peer")).getCode());
    }
}
