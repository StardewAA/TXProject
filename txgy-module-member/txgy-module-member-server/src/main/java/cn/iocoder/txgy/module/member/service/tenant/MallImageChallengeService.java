package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Registration images have their own protocol, Redis namespace and budgets; never use SMS credentials. */
@Service
public class MallImageChallengeService {
    public static final ErrorCode INVALID = new ErrorCode(1_004_008_200, "图片验证码无效或已过期，请刷新后重试");
    public static final ErrorCode LIMITED = new ErrorCode(1_004_008_201, "操作过于频繁，请稍后重试");
    public static final ErrorCode UNAVAILABLE = new ErrorCode(1_004_008_202, "图片验证暂不可用，请稍后重试");
    private static final String PREFIX = "mall:image:{mall-image}:";
    private static final String SECRET_KEY = PREFIX + "hmac-secret";
    private static final String PURPOSE = "REGISTER";
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TTL = 300;
    private static final int RETRY = 2;
    private static final DefaultRedisScript<Long> ISSUE = script("issue");
    private static final DefaultRedisScript<Long> CONSUME = script("consume");
    private static final DefaultRedisScript<Long> BUDGET = script("budget");
    private final StringRedisTemplate redis;

    public MallImageChallengeService(StringRedisTemplate redis) { this.redis = redis; }

    public record Challenge(long tenantId, String challengeId, String imageDataUrl, int expiresIn, int retryAfter) { }

    /** Tenant authorization is the caller's responsibility; this service binds the authorized target. */
    public Challenge issue(long tenantId, String loginName, String session, String previousId, String purpose, String peer) {
        String name = normalizedName(loginName);
        validateTarget(tenantId, session, peer);
        if (!PURPOSE.equals(purpose) || (previousId != null && !previousId.isBlank() && !isId(previousId))) {
            throw exception(INVALID);
        }
        try {
            SecretKeySpec secret = secret();
            String id = UUID.randomUUID().toString().replace("-", "");
            String code = randomCode();
            String binding = binding(secret, tenantId, name, session);
            String sessionKey = hmac(secret, "session:" + session);
            List<String> keys = List.of(PREFIX + "challenge:" + id, PREFIX + "active:" + sessionKey,
                    PREFIX + "cooldown:" + sessionKey, PREFIX + "issue-name:" + hmac(secret, "name:" + name),
                    PREFIX + "issue-session:" + sessionKey, PREFIX + "issue-peer:" + hmac(secret, "peer:" + peer));
            Long result = redis.execute(ISSUE, keys, id, binding, digest(secret, id, binding, code),
                    String.valueOf(TTL), String.valueOf(RETRY), "30", "30", "300", "900");
            if (Long.valueOf(-1).equals(result)) throw exception(LIMITED);
            if (!Long.valueOf(1).equals(result)) throw exception(UNAVAILABLE);
            // previousId never chooses a deletion target. The session's active ID supersedes every old image,
            // including an image issued in another tenant or an overlapping request from the same session.
            return new Challenge(tenantId, id, renderImage(code), TTL, RETRY);
        } catch (ServiceException error) {
            throw error;
        } catch (Exception | LinkageError error) {
            // No underlying message, request, key material or answer enters logs or public exceptions.
            throw exception(UNAVAILABLE);
        }
    }

    /** The endpoint fixes REGISTER: a client cannot choose another challenge purpose during consumption. */
    public void consume(long tenantId, String loginName, String session, String id, String code, String peer) {
        String name = normalizedName(loginName);
        validateTarget(tenantId, session, peer);
        if (!isId(id) || code == null || !code.trim().matches("[a-zA-Z0-9]{5}")) throw exception(INVALID);
        try {
            SecretKeySpec secret = secret();
            String binding = binding(secret, tenantId, name, session);
            String sessionKey = hmac(secret, "session:" + session);
            Long result = redis.execute(CONSUME, List.of(PREFIX + "challenge:" + id, PREFIX + "active:" + sessionKey,
                            PREFIX + "verify-name:" + hmac(secret, "name:" + name),
                            PREFIX + "verify-session:" + sessionKey, PREFIX + "verify-peer:" + hmac(secret, "peer:" + peer)),
                    id, binding, digest(secret, id, binding, code.trim().toUpperCase(Locale.ROOT)), "5", "30", "30", "300", "900");
            if (Long.valueOf(-2).equals(result)) throw exception(LIMITED);
            if (!Long.valueOf(1).equals(result)) throw exception(INVALID);
        } catch (ServiceException error) {
            throw error;
        } catch (Exception error) {
            throw exception(UNAVAILABLE);
        }
    }

    /** The same account and peer budget applies to every tenant, including legacy mobile password login. */
    public void limitPasswordAttempts(String account, String peer) {
        if (account == null || account.isBlank() || account.length() > 100 || peer == null || peer.isBlank() || peer.length() > 128) {
            throw exception(INVALID);
        }
        try {
            SecretKeySpec secret = secret();
            Long result = redis.execute(BUDGET, List.of(PREFIX + "password-name:" + hmac(secret, "name:" + account.trim().toLowerCase(Locale.ROOT)),
                    PREFIX + "password-peer:" + hmac(secret, "peer:" + peer)), "30", "300", "900");
            if (!Long.valueOf(1).equals(result)) throw exception(LIMITED);
        } catch (ServiceException error) {
            throw error;
        } catch (Exception error) {
            throw exception(UNAVAILABLE);
        }
    }

    private SecretKeySpec secret() {
        // Read the shared value on each operation: Redis restoration/flush must not leave JVMs using different cached keys.
        String hex = redis.opsForValue().get(SECRET_KEY);
        if (hex == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            redis.opsForValue().setIfAbsent(SECRET_KEY, HexFormat.of().formatHex(bytes));
            hex = redis.opsForValue().get(SECRET_KEY);
        }
        if (hex == null || !hex.matches("[a-f0-9]{64}")) throw exception(UNAVAILABLE);
        return new SecretKeySpec(HexFormat.of().parseHex(hex), "HmacSHA256");
    }

    private static String normalizedName(String name) {
        if (name == null || name.length() > 100) throw exception(INVALID);
        return MallAccountPolicy.normalize(name);
    }

    private static void validateTarget(long tenant, String session, String peer) {
        if (tenant <= 0 || !isId(session) || peer == null || peer.isBlank() || peer.length() > 128) throw exception(INVALID);
    }
    private static boolean isId(String value) { return value != null && value.matches("[a-f0-9]{32}"); }
    private static String binding(SecretKeySpec key, long tenant, String name, String session) throws Exception {
        return hmac(key, "binding:" + tenant + ":" + PURPOSE + ":" + name + ":" + session);
    }
    private static String digest(SecretKeySpec key, String id, String binding, String code) throws Exception {
        return hmac(key, "answer:" + id + ":" + binding + ":" + code);
    }
    private static String hmac(SecretKeySpec key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static String randomCode() {
        StringBuilder code = new StringBuilder(5);
        for (int i = 0; i < 5; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    static String renderImage(String code) throws Exception {
        BufferedImage image = new BufferedImage(180, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 248, 252));
            graphics.fillRect(0, 0, 180, 60);
            graphics.setStroke(new BasicStroke(1.3f));
            for (int i = 0; i < 7; i++) {
                graphics.setColor(new Color(170 + RANDOM.nextInt(55), 170 + RANDOM.nextInt(55), 170 + RANDOM.nextInt(55)));
                graphics.drawLine(RANDOM.nextInt(180), RANDOM.nextInt(60), RANDOM.nextInt(180), RANDOM.nextInt(60));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            for (int i = 0; i < code.length(); i++) {
                int x = 15 + i * 31;
                int y = 42 + RANDOM.nextInt(7) - 3;
                AffineTransform transform = graphics.getTransform();
                graphics.rotate((RANDOM.nextDouble() - 0.5) * 0.36, x + 12, y - 15);
                graphics.setColor(new Color(25 + RANDOM.nextInt(70), 25 + RANDOM.nextInt(70), 25 + RANDOM.nextInt(70)));
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
                graphics.setTransform(transform);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", bytes)) throw exception(UNAVAILABLE);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }
    private static DefaultRedisScript<Long> script(String name) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/mall-image-" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }
}
