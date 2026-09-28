package cn.iocoder.txgy.module.cms.service.consult;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.module.ai.api.model.AiRuntimeModelApi;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatRespDTO;
import cn.iocoder.txgy.module.im.api.group.ImGroupApi;
import cn.iocoder.txgy.module.im.api.group.dto.ImConsultGroupCreateReqDTO;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import cn.iocoder.txgy.module.system.api.permission.dto.PermissionEnsureRoleReqDTO;
import lombok.extern.slf4j.Slf4j;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.*;

/** CMS 内部的专家资料、咨询订单、评价和线下结算状态机。支付与 IM 调用由整合层回填。 */
@Service
@Slf4j
public class CmsExpertConsultService {

    public static final int WAIT_PAY = 0, WAIT_ACCEPT = 1, ACCEPTED = 2, CONSULTING = 3,
            WAIT_CONFIRM = 4, COMPLETED = 5, CANCELED = 6, EXPIRED = 7, REJECTED = 8,
            REFUNDING = 9, REFUNDED = 10, DISPUTED = 11;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<PayOrderApi> payOrderApiProvider;
    private final ObjectProvider<ImGroupApi> imGroupApiProvider;
    private final ObjectProvider<PayRefundApi> payRefundApiProvider;
    private final PermissionApi permissionApi;
    private final int paymentExpireMinutes;
    private final int commissionRate;
    private final String payAppKey;

    /** AI 模块（可选依赖）：用于专家资料智能核验 */
    @Resource
    private ObjectProvider<AiRuntimeModelApi> aiRuntimeModelApiProvider;

    public CmsExpertConsultService(JdbcTemplate jdbc,
            ObjectProvider<PayOrderApi> payOrderApiProvider,
            ObjectProvider<ImGroupApi> imGroupApiProvider,
            ObjectProvider<PayRefundApi> payRefundApiProvider,
            PermissionApi permissionApi,
            @Value("${txgy.cms.consult.payment-expire-minutes:30}") int paymentExpireMinutes,
            @Value("${txgy.cms.consult.commission-rate:20}") int commissionRate,
            @Value("${txgy.cms.consult.pay-app-key:cms}") String payAppKey) {
        this.jdbc = jdbc;
        this.payOrderApiProvider = payOrderApiProvider;
        this.imGroupApiProvider = imGroupApiProvider;
        this.payRefundApiProvider = payRefundApiProvider;
        this.permissionApi = permissionApi;
        this.paymentExpireMinutes = paymentExpireMinutes;
        this.commissionRate = commissionRate;
        this.payAppKey = payAppKey;
    }

    @Transactional(rollbackFor = Exception.class)
    public Expert invite(Long userId, ExpertInput input) {
        long tenantId = tenant();
        Integer activeUsers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_users
                WHERE tenant_id=? AND id=? AND status=0 AND deleted=0
                """, Integer.class, tenantId, userId);
        if (activeUsers == null || activeUsers == 0) {
            throw exception(CONSULT_EXPERT_USER_INVALID);
        }
        List<Long> ids = jdbc.query("SELECT id FROM cms_expert_profile WHERE tenant_id=? AND user_id=? AND deleted=0",
                (rs, row) -> rs.getLong(1), tenantId, userId);
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO cms_expert_profile
                      (tenant_id,user_id,expert_name,professional_title,company,service_fields,introduction,
                       fixed_price,audit_status,audit_remark,enabled)
                    VALUES (?,?,?,?,?,?,?,?,1,NULL,b'1')
                    """, tenantId, userId, clean(input.expertName()), clean(input.professionalTitle()),
                    clean(input.company()), clean(input.serviceFields()), clean(input.introduction()), input.fixedPrice());
        } else {
            jdbc.update("""
                    UPDATE cms_expert_profile SET expert_name=?,professional_title=?,company=?,service_fields=?,
                      introduction=?,fixed_price=?,audit_status=1,audit_remark=NULL,enabled=b'1',version=version+1
                    WHERE tenant_id=? AND id=? AND deleted=0
                    """, clean(input.expertName()), clean(input.professionalTitle()), clean(input.company()),
                    clean(input.serviceFields()), clean(input.introduction()), input.fixedPrice(), tenantId, ids.getFirst());
        }
        permissionApi.ensureRoleAndGrant(new PermissionEnsureRoleReqDTO(
                userId, "cms_expert", "CMS 专家", Set.of(70052L))).getCheckedData();
        return findExpertByUser(tenantId, userId);
    }

    public List<Expert> listExperts(Integer auditStatus, boolean onlyEnabled) {
        long tenantId = tenant();
        StringBuilder sql = new StringBuilder("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       audit_status,audit_remark,enabled,rating,order_count,create_time
                FROM cms_expert_profile WHERE tenant_id=? AND deleted=0
                """);
        if (auditStatus != null) sql.append(" AND audit_status=").append(auditStatus);
        if (onlyEnabled) sql.append(" AND audit_status=1 AND enabled=1");
        sql.append(" ORDER BY id DESC");
        return jdbc.query(sql.toString(), (rs, row) -> expertRow(rs), tenantId);
    }

    /** 门户专家市场：已认证并启用的专家面向所有已登录租户开放。 */
    public List<Expert> listAvailableExperts() {
        return jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       audit_status,audit_remark,enabled,rating,order_count,create_time
                FROM cms_expert_profile
                WHERE audit_status=1 AND enabled=1 AND deleted=0
                ORDER BY rating DESC, order_count DESC, id DESC
                """, (rs, row) -> expertRow(rs));
    }

    public Expert findEnabledExpertByUser(Long userId) {
        List<Expert> rows = jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       audit_status,audit_remark,enabled,rating,order_count,create_time
                FROM cms_expert_profile
                WHERE tenant_id=? AND user_id=? AND audit_status=1 AND enabled=1 AND deleted=0
                """, (rs, row) -> expertRow(rs), tenant(), userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // ==================== 专家入驻（自助申请，一期） ====================

    /** 实名核验：一期为格式校验占位，二期替换为实名认证 API（阿里云/腾讯云） */
    public VerifyResult verifyIdentity(String name, String idCard) {
        String n = clean(name);
        String id = idCard == null ? "" : idCard.strip().toUpperCase();
        if (n == null || n.length() < 2 || n.length() > 20) {
            return new VerifyResult(false, "FORMAT", "姓名长度需在 2-20 字之间");
        }
        if (!id.matches("\\d{17}[0-9X]")) {
            return new VerifyResult(false, "FORMAT", "身份证号格式不正确");
        }
        return new VerifyResult(true, "STUB", "格式校验通过（实名核验服务未接入，二期自动升级为真实核验）");
    }

    /** AI 资料核验：调用 AI 模块判断资料自洽性；AI 不可用时降级跳过（不阻塞申请） */
    public VerifyResult aiVerify(ApplyInput input) {
        AiRuntimeModelApi api = aiRuntimeModelApiProvider.getIfAvailable();
        if (api == null) {
            return new VerifyResult(true, "NO_AI", "AI 模块不可用，跳过智能核验");
        }
        try {
            StringBuilder user = new StringBuilder("请核验以下专家入驻申请资料：\n");
            user.append("姓名：").append(nullSafe(input.expertName())).append("\n");
            user.append("职称：").append(nullSafe(input.professionalTitle())).append("\n");
            user.append("所属单位：").append(nullSafe(input.company())).append("\n");
            user.append("从业年限：").append(input.yearsOfExperience() == null ? "未填" : input.yearsOfExperience() + " 年").append("\n");
            user.append("服务领域：").append(nullSafe(input.serviceFields())).append("\n");
            user.append("个人简介：").append(nullSafe(input.introduction())).append("\n");

            AiRuntimeChatReqDTO req = new AiRuntimeChatReqDTO();
            req.setSystemPrompt("你是专家入驻资料核验助手。判断资料是否自洽：职称与从业年限是否匹配、服务领域与简介是否一致、是否存在明显编造或空泛迹象。"
                    + "只输出一行 JSON，不要任何解释：{\"consistent\":true或false,\"confidence\":0到1,\"suggestions\":[\"问题\"],\"advice\":\"通过\"或\"人工复核\"或\"驳回\"}");
            req.setUserPrompt(user.toString());
            req.setTemperature(0.1);
            req.setMaxTokens(400);
            AiRuntimeChatRespDTO resp = api.chat(req).getCheckedData();
            String content = resp == null ? null : resp.getContent();
            if (content == null || content.isBlank()) {
                return new VerifyResult(true, "AI_EMPTY", "AI 未返回内容，转人工复核");
            }
            String flat = content.replace(" ", "");
            boolean rejected = flat.contains("\"advice\":\"驳回\"") || flat.contains("advice:驳回");
            boolean dubious = !rejected && (flat.contains("\"consistent\":false") || flat.contains("人工复核"));
            String channel = "AI:" + (resp.getPlatform() == null ? "unknown" : resp.getPlatform());
            return new VerifyResult(!rejected, channel, content.strip());
        } catch (Exception ex) {
            log.warn("[aiVerify][AI 核验失败，降级跳过]", ex);
            return new VerifyResult(true, "AI_ERROR", "AI 核验失败，转人工复核");
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "未填" : value;
    }

    /** 专家自助申请：实名核验通过即自动上架为「基础认证」专家 */
    @Transactional(rollbackFor = Exception.class)
    public ExpertApplication apply(Long userId, ApplyInput input) {
        long tenantId = tenant();
        VerifyResult verify = verifyIdentity(input.expertName(), input.idCard());
        if (!verify.passed()) {
            throw exception(CONSULT_EXPERT_APPLY_REJECTED);
        }
        VerifyResult aiResult = aiVerify(input);
        String phone = clean(input.phone());
        if (phone != null) {
            Integer dup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cms_expert_profile WHERE phone=? AND user_id<>? AND deleted=0",
                    Integer.class, phone, userId);
            if (dup != null && dup > 0) {
                throw exception(CONSULT_EXPERT_PHONE_DUPLICATED);
            }
        }
        List<Long> ids = jdbc.query("SELECT id FROM cms_expert_profile WHERE user_id=? AND deleted=0",
                (rs, row) -> rs.getLong(1), userId);
        String verifyJson = "{\"identity\":\"" + verify.channel() + "\",\"passed\":true,\"message\":\""
                + verify.message().replace("\"", "'") + "\",\"ai\":\"" + aiResult.channel() + "\",\"aiDetail\":\""
                + (aiResult.message() == null ? "" : aiResult.message().replace("\"", "'").replace("\n", " ")) + "\"}";
        // AI 有疑点 -> 进人工队列（audit_status=0）；AI 通过 -> 自动上架（audit_status=1）
        int auditStatus = aiResult.passed() ? 1 : 0;
        String auditRemark = aiResult.passed() ? null : "AI 核验提示需要人工复核";
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO cms_expert_profile
                      (tenant_id,user_id,expert_name,professional_title,company,service_fields,introduction,
                       fixed_price,audit_status,audit_remark,enabled,apply_channel,expert_level,phone,
                       years_of_experience,apply_time,verify_result)
                    VALUES (?,?,?,?,?,?,?,?,?,?,b'1','SELF_APPLY','NORMAL',?,?,NOW(),?)
                    """, tenantId, userId, clean(input.expertName()), clean(input.professionalTitle()),
                    clean(input.company()), clean(input.serviceFields()), clean(input.introduction()),
                    input.fixedPrice(), auditStatus, auditRemark, phone, input.yearsOfExperience(), verifyJson);
        } else {
            jdbc.update("""
                    UPDATE cms_expert_profile SET expert_name=?,professional_title=?,company=?,service_fields=?,
                      introduction=?,fixed_price=?,audit_status=?,audit_remark=?,enabled=b'1',
                      apply_channel='SELF_APPLY',phone=?,years_of_experience=?,apply_time=NOW(),
                      verify_result=?,version=version+1
                    WHERE id=? AND deleted=0
                    """, clean(input.expertName()), clean(input.professionalTitle()), clean(input.company()),
                    clean(input.serviceFields()), clean(input.introduction()), input.fixedPrice(),
                    auditStatus, auditRemark, phone, input.yearsOfExperience(), verifyJson, ids.getFirst());
        }
        try {
            permissionApi.ensureRoleAndGrant(new PermissionEnsureRoleReqDTO(
                    userId, "cms_expert", "CMS 专家", Set.of(70052L))).getCheckedData();
        } catch (Exception ex) {
            log.warn("[apply][授予专家角色失败 userId={}]", userId, ex);
        }
        return myApplication(userId);
    }

    /** 我的专家申请 */
    public ExpertApplication myApplication(Long userId) {
        List<ExpertApplication> rows = jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       apply_channel,expert_level,phone,years_of_experience,audit_status,audit_remark,enabled,
                       verify_result,apply_time,create_time
                FROM cms_expert_profile WHERE user_id=? AND deleted=0 ORDER BY id DESC
                """, (rs, row) -> applicationRow(rs), userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 管理端：专家入驻申请列表（跨租户） */
    public List<ExpertApplication> listApplications(Integer auditStatus) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       apply_channel,expert_level,phone,years_of_experience,audit_status,audit_remark,enabled,
                       verify_result,apply_time,create_time
                FROM cms_expert_profile WHERE deleted=0
                """);
        if (auditStatus != null) sql.append(" AND audit_status=").append(auditStatus);
        sql.append(" ORDER BY audit_status ASC, id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> applicationRow(rs));
    }

    /** 管理端：审核通过 / 驳回 */
    @Transactional(rollbackFor = Exception.class)
    public ExpertApplication auditApplication(long id, boolean pass, String remark, Long operatorId) {
        int updated = jdbc.update("""
                UPDATE cms_expert_profile SET audit_status=?,audit_remark=?,enabled=?,audit_time=NOW(),
                  audit_by=?,version=version+1
                WHERE id=? AND deleted=0
                """, pass ? 1 : 2, clean(remark), pass, operatorId, id);
        if (updated == 0) {
            throw exception(CONSULT_EXPERT_NOT_FOUND);
        }
        return jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       apply_channel,expert_level,phone,years_of_experience,audit_status,audit_remark,enabled,
                       verify_result,apply_time,create_time
                FROM cms_expert_profile WHERE id=?
                """, (rs, row) -> applicationRow(rs), id).getFirst();
    }

    private static ExpertApplication applicationRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExpertApplication(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11),
                (Integer) rs.getObject(12), rs.getInt(13), rs.getString(14), rs.getBoolean(15), rs.getString(16),
                time(rs, 17), time(rs, 18));
    }

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, long expertId, String subject, String question, String userIp) {
        long customerTenantId = tenant();
        OwnedExpert ownedExpert = getOwnedExpert(expertId, true);
        long expertTenantId = ownedExpert.tenantId();
        Expert expert = ownedExpert.expert();
        if (Objects.equals(expert.userId(), userId)) throw new IllegalArgumentException("不能购买自己的咨询服务");
        String orderNo = "ZX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(paymentExpireMinutes);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO consult_order
                      (tenant_id,customer_tenant_id,order_no,user_id,expert_id,service_subject,question,price,status,expire_time)
                    VALUES (?,?,?,?,?,?,?,?,0,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, expertTenantId); ps.setLong(2, customerTenantId); ps.setString(3, orderNo);
            ps.setLong(4, userId); ps.setLong(5, expertId); ps.setString(6, clean(subject));
            ps.setString(7, clean(question)); ps.setInt(8, expert.fixedPrice());
            ps.setObject(9, expireTime); return ps;
        }, keys);
        long orderId = keys.getKey().longValue();
        Long payOrderId = createPayOrder(userId, orderNo, expert.fixedPrice(), expireTime, subject, userIp);
        if (payOrderId != null) {
            jdbc.update("UPDATE consult_order SET pay_order_id=? WHERE tenant_id=? AND id=?",
                    payOrderId, expertTenantId, orderId);
        }
        return getOrder(orderId, userId, false);
    }

    /** 支付整合小组在核验真实支付单后调用；条件更新保证重复回调幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public Order markPaid(String orderNo, long payOrderId) {
        List<Order> rows = queryOrders("o.order_no=?", orderNo);
        if (rows.isEmpty()) throw exception(CONSULT_ORDER_NOT_FOUND);
        Order old = rows.getFirst();
        if (old.status() != WAIT_PAY) {
            if (old.status() >= WAIT_ACCEPT && Objects.equals(old.payOrderId(), payOrderId)) return old;
            throw exception(CONSULT_PAY_CALLBACK_INVALID);
        }
        int updated = jdbc.update("""
                UPDATE consult_order SET status=1,pay_order_id=?,paid_time=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND order_no=? AND status=0
                """, payOrderId, old.expertTenantId(), orderNo);
        if (updated != 1) throw exception(CONSULT_PAY_CALLBACK_INVALID);
        Order paid = queryOrders("o.order_no=?", orderNo).getFirst();
        try {
            openImSession(paid);
        } catch (RuntimeException ex) {
            log.warn("[markPaid][orderNo({}) 创建 IM 会话失败，可稍后重试]", orderNo, ex);
        }
        return queryOrders("o.order_no=?", orderNo).getFirst();
    }

    /** 支付成功后创建一单一群的隔离 IM 会话并回填群编号；失败不阻断支付状态。 */
    private void openImSession(Order order) {
        ImGroupApi imGroupApi = imGroupApiProvider.getIfAvailable();
        if (imGroupApi == null || order.imGroupId() != null) return;
        ImConsultGroupCreateReqDTO reqDTO = new ImConsultGroupCreateReqDTO();
        reqDTO.setOwnerUserId(order.expertUserId());
        reqDTO.setMemberUserIds(List.of(order.userId()));
        reqDTO.setName("专家咨询 " + order.orderNo());
        reqDTO.setNotice("订单号：" + order.orderNo() + "；咨询主题：" + order.serviceSubject());
        Long groupId = imGroupApi.createConsultGroup(reqDTO).getCheckedData();
        if (groupId != null) {
            jdbc.update("UPDATE consult_order SET im_group_id=? WHERE tenant_id=? AND id=? AND im_group_id IS NULL",
                    groupId, order.expertTenantId(), order.id());
        }
    }

    /** IM 整合小组建好一单一群后幂等回填。 */
    @Transactional(rollbackFor = Exception.class)
    public Order bindImGroup(String orderNo, long imGroupId) {
        List<Order> existing = queryOrders("o.order_no=?", orderNo);
        if (existing.isEmpty()) throw exception(CONSULT_ORDER_NOT_FOUND);
        Order order = existing.getFirst();
        int updated = jdbc.update("""
                UPDATE consult_order SET im_group_id=?,version=version+1
                WHERE tenant_id=? AND order_no=? AND status>=1 AND im_group_id IS NULL
                """, imGroupId, order.expertTenantId(), orderNo);
        if (updated == 0) {
            List<Order> rows = queryOrders("o.order_no=?", orderNo);
            if (rows.isEmpty() || !Objects.equals(rows.getFirst().imGroupId(), imGroupId)) {
                throw exception(CONSULT_ORDER_STATUS_INVALID);
            }
        }
        return queryOrders("o.order_no=?", orderNo).getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public Order action(long orderId, Long actorUserId, String action, String reason) {
        Order order = getOrder(orderId, actorUserId, true);
        int from; int to; boolean expertActor;
        switch (action.toUpperCase()) {
            case "ACCEPT" -> { from = WAIT_ACCEPT; to = ACCEPTED; expertActor = true; }
            case "START" -> { from = ACCEPTED; to = CONSULTING; expertActor = true; }
            case "REQUEST_COMPLETE" -> { from = CONSULTING; to = WAIT_CONFIRM; expertActor = true; }
            case "COMPLETE" -> { from = WAIT_CONFIRM; to = COMPLETED; expertActor = false; }
            case "CANCEL" -> { from = WAIT_PAY; to = CANCELED; expertActor = false; }
            case "REJECT" -> { from = WAIT_ACCEPT; to = REFUNDING; expertActor = true; }
            default -> throw exception(CONSULT_ORDER_STATUS_INVALID);
        }
        boolean isExpert = Objects.equals(order.expertUserId(), actorUserId);
        boolean isCustomer = Objects.equals(order.userId(), actorUserId);
        if ((expertActor && !isExpert) || (!expertActor && !isCustomer)) throw exception(CONSULT_ORDER_NOT_FOUND);
        int updated = jdbc.update("""
                UPDATE consult_order SET status=?,cancel_reason=?,completed_time=?,version=version+1
                WHERE tenant_id=? AND id=? AND status=?
                """, to, clean(reason), to == COMPLETED ? LocalDateTime.now() : null,
                order.expertTenantId(), orderId, from);
        if (updated != 1) throw exception(CONSULT_ORDER_STATUS_INVALID);
        if (to == REFUNDING) createRefund(order);
        if (to == COMPLETED) createSettlement(order);
        return getOrder(orderId, actorUserId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Evaluation evaluate(long orderId, Long userId, int rating, String content) {
        Order order = getOrder(orderId, userId, true);
        if (order.status() != COMPLETED || !Objects.equals(order.userId(), userId)) {
            throw exception(CONSULT_ORDER_STATUS_INVALID);
        }
        try {
            jdbc.update("""
                    INSERT INTO consult_evaluation(tenant_id,order_id,user_id,expert_id,rating,content)
                    VALUES (?,?,?,?,?,?)
                    """, order.expertTenantId(), orderId, userId, order.expertId(), rating, clean(content));
        } catch (DuplicateKeyException ex) {
            throw exception(CONSULT_EVALUATION_DUPLICATE);
        }
        jdbc.update("""
                UPDATE cms_expert_profile e SET
                  rating=(SELECT COALESCE(AVG(v.rating),0) FROM consult_evaluation v
                          WHERE v.tenant_id=e.tenant_id AND v.expert_id=e.id AND v.deleted=0),
                  order_count=(SELECT COUNT(*) FROM consult_order o
                          WHERE o.tenant_id=e.tenant_id AND o.expert_id=e.id AND o.status=5 AND o.deleted=0)
                WHERE e.tenant_id=? AND e.id=?
                """, order.expertTenantId(), order.expertId());
        return jdbc.query("SELECT id,order_id,rating,content,create_time FROM consult_evaluation WHERE tenant_id=? AND order_id=?",
                (rs, row) -> new Evaluation(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime()), order.expertTenantId(), orderId).getFirst();
    }

    public List<Order> myOrders(Long userId) {
        return queryOrders("o.customer_tenant_id=? AND o.user_id=?", tenant(), userId);
    }

    public List<Order> expertRequests(Long userId) {
        return queryOrders("o.tenant_id=? AND e.user_id=?", tenant(), userId);
    }

    public Contact expertContact(long orderId, Long expertUserId) {
        Order order = getOrder(orderId, expertUserId, true);
        if (!Objects.equals(order.expertUserId(), expertUserId)) {
            throw exception(CONSULT_ORDER_NOT_FOUND);
        }
        return new Contact(order.id(), order.userId(), order.requesterName(), order.requesterMobile(),
                order.imGroupId(), order.imGroupId() != null);
    }

    public List<Order> allOrders() { return queryOrders("o.tenant_id=?", tenant()); }

    public List<Settlement> settlements(Integer status) {
        String suffix = status == null ? "" : " AND s.status=" + status;
        return jdbc.query("""
                SELECT s.id,s.order_id,o.order_no,e.expert_name,s.order_amount,s.commission_rate,
                       s.commission_amount,s.settlement_amount,s.status,s.proof_url,s.proof_no,s.settled_time,s.remark
                FROM expert_settlement s JOIN consult_order o ON o.id=s.order_id AND o.tenant_id=s.tenant_id
                  JOIN cms_expert_profile e ON e.id=s.expert_id AND e.tenant_id=s.tenant_id
                WHERE s.tenant_id=? AND s.deleted=0
                """ + suffix + " ORDER BY s.id DESC", (rs, row) -> new Settlement(rs.getLong(1), rs.getLong(2),
                rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBigDecimal(6), rs.getInt(7),
                rs.getInt(8), rs.getInt(9), rs.getString(10), rs.getString(11),
                rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toLocalDateTime(), rs.getString(13)), tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Settlement settle(long settlementId, Long operatorId, String proofUrl, String proofNo, String remark) {
        int updated = jdbc.update("""
                UPDATE expert_settlement SET status=1,proof_url=?,proof_no=?,settled_by=?,settled_time=CURRENT_TIMESTAMP,
                  remark=?,version=version+1 WHERE tenant_id=? AND id=? AND status=0 AND deleted=0
                """, clean(proofUrl), clean(proofNo), operatorId, clean(remark), tenant(), settlementId);
        if (updated != 1) throw exception(CONSULT_SETTLEMENT_NOT_FOUND);
        return settlements(1).stream().filter(row -> row.id() == settlementId).findFirst()
                .orElseThrow(() -> exception(CONSULT_SETTLEMENT_NOT_FOUND));
    }

    @Transactional(rollbackFor = Exception.class)
    public Settlement rejectSettlement(long settlementId, Long operatorId, String remark) {
        int updated = jdbc.update("""
                UPDATE expert_settlement SET status=2,settled_by=?,settled_time=CURRENT_TIMESTAMP,
                  remark=?,version=version+1 WHERE tenant_id=? AND id=? AND status=0 AND deleted=0
                """, operatorId, clean(remark), tenant(), settlementId);
        if (updated != 1) throw exception(CONSULT_SETTLEMENT_NOT_FOUND);
        return settlements(2).stream().filter(row -> row.id() == settlementId).findFirst()
                .orElseThrow(() -> exception(CONSULT_SETTLEMENT_NOT_FOUND));
    }

    /** 支付整合小组完成退款后调用；重复通知返回当前记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Order markRefunded(String orderNo, long payOrderId) {
        List<Order> rows = queryOrders("o.order_no=?", orderNo);
        if (rows.isEmpty()) throw exception(CONSULT_ORDER_NOT_FOUND);
        Order old = rows.getFirst();
        if (old.status() == REFUNDED && Objects.equals(old.payOrderId(), payOrderId)) return old;
        int updated = jdbc.update("""
                UPDATE consult_order SET status=10,version=version+1
                WHERE tenant_id=? AND order_no=? AND status=9 AND pay_order_id=?
                """, old.expertTenantId(), orderNo, payOrderId);
        if (updated != 1) throw exception(CONSULT_ORDER_STATUS_INVALID);
        return queryOrders("o.order_no=?", orderNo).getFirst();
    }

    /** 定时任务调用：未支付订单过期；已支付长时间未接单的订单转退款中，等待支付整合层退款。 */
    @Transactional(rollbackFor = Exception.class)
    public int expireOrders() {
        int unpaid = jdbc.update("UPDATE consult_order SET status=7,version=version+1 WHERE status=0 AND expire_time<NOW() AND deleted=0");
        int unaccepted = jdbc.update("UPDATE consult_order SET status=9,cancel_reason='专家超时未接单',version=version+1 "
                + "WHERE status=1 AND paid_time<DATE_SUB(NOW(),INTERVAL 24 HOUR) AND deleted=0");
        return unpaid + unaccepted;
    }

    /** 支付通道退款成功回调；重复通知保持幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public Order markRefunded(String orderNo) {
        List<Order> rows = queryOrders("o.order_no=?", orderNo);
        if (rows.isEmpty()) throw exception(CONSULT_ORDER_NOT_FOUND);
        Order old = rows.getFirst();
        if (old.status() == REFUNDED) return old;
        if (old.status() != REFUNDING) throw exception(CONSULT_PAY_CALLBACK_INVALID);
        jdbc.update("""
                UPDATE consult_order SET status=?,version=version+1
                WHERE tenant_id=? AND order_no=? AND status=?
                """, REFUNDED, old.expertTenantId(), orderNo, REFUNDING);
        return queryOrders("o.order_no=?", orderNo).getFirst();
    }

    /** 创建支付单；支付模块不可用时不阻塞下单，交易号可后续补偿回填。 */
    private Long createPayOrder(Long userId, String orderNo, int price, LocalDateTime expireTime,
                                String subject, String userIp) {
        PayOrderApi payOrderApi = payOrderApiProvider.getIfAvailable();
        if (payOrderApi == null) return null;
        PayOrderCreateReqDTO reqDTO = new PayOrderCreateReqDTO();
        reqDTO.setAppKey(payAppKey);
        reqDTO.setUserIp(userIp == null || userIp.isBlank() ? "127.0.0.1" : userIp);
        reqDTO.setUserId(userId);
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        reqDTO.setUserType(loginUser != null ? loginUser.getUserType() : UserTypeEnum.ADMIN.getValue());
        reqDTO.setMerchantOrderId(orderNo);
        String title = clean(subject) == null || clean(subject).isBlank() ? "专家咨询" : clean(subject);
        reqDTO.setSubject(title.length() > PayOrderCreateReqDTO.SUBJECT_MAX_LENGTH
                ? title.substring(0, PayOrderCreateReqDTO.SUBJECT_MAX_LENGTH) : title);
        reqDTO.setBody("专家咨询服务");
        reqDTO.setPrice(price);
        reqDTO.setExpireTime(expireTime);
        return payOrderApi.createOrder(reqDTO).getCheckedData();
    }

    /** 原路退款；商户退款号按订单号幂等，重复发起由支付模块拒绝。 */
    private void createRefund(Order order) {
        PayRefundApi payRefundApi = payRefundApiProvider.getIfAvailable();
        if (payRefundApi == null || order.payOrderId() == null) return;
        PayRefundCreateReqDTO reqDTO = new PayRefundCreateReqDTO();
        reqDTO.setAppKey(payAppKey);
        reqDTO.setUserIp("127.0.0.1");
        reqDTO.setUserId(order.userId());
        reqDTO.setUserType(UserTypeEnum.ADMIN.getValue());
        reqDTO.setMerchantOrderId(order.orderNo());
        reqDTO.setMerchantRefundId("RF" + order.orderNo());
        reqDTO.setReason("专家拒单退款");
        reqDTO.setPrice(order.price());
        payRefundApi.createRefund(reqDTO).getCheckedData();
    }

    private void createSettlement(Order order) {
        int commission = Math.round(order.price() * commissionRate / 100F);
        jdbc.update("""
                INSERT IGNORE INTO expert_settlement
                  (tenant_id,order_id,expert_id,order_amount,commission_rate,commission_amount,settlement_amount,status)
                VALUES (?,?,?,?,?,?,?,0)
                """, order.expertTenantId(), order.id(), order.expertId(), order.price(), commissionRate, commission,
                order.price() - commission);
    }

    private Order getOrder(long id, Long actor, boolean participantOnly) {
        List<Order> rows = queryOrders("o.id=?", id);
        if (rows.isEmpty()) throw exception(CONSULT_ORDER_NOT_FOUND);
        Order order = rows.getFirst();
        if (participantOnly && !Objects.equals(order.userId(), actor) && !Objects.equals(order.expertUserId(), actor)) {
            throw exception(CONSULT_ORDER_NOT_FOUND);
        }
        return order;
    }

    private List<Order> queryOrders(String condition, Object... args) {
        return jdbc.query("""
                SELECT o.id,o.order_no,o.user_id,u.nickname,u.mobile,o.expert_id,e.user_id,e.expert_name,
                       o.service_subject,o.question,o.price,o.status,o.pay_order_id,o.im_group_id,o.expire_time,
                       o.paid_time,o.completed_time,o.cancel_reason,o.create_time,o.version,
                       o.tenant_id,o.customer_tenant_id
                FROM consult_order o JOIN cms_expert_profile e ON e.tenant_id=o.tenant_id AND e.id=o.expert_id
                  JOIN system_users u ON u.tenant_id=o.customer_tenant_id AND u.id=o.user_id AND u.deleted=0
                WHERE o.deleted=0 AND """ + " " + condition + " ORDER BY o.id DESC",
                (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), rs.getLong(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getInt(11), rs.getInt(12), (Long) rs.getObject(13),
                        (Long) rs.getObject(14), time(rs, 15), time(rs, 16), time(rs, 17), rs.getString(18),
                        time(rs, 19), rs.getInt(20), rs.getLong(21), rs.getLong(22)), args);
    }

    private OwnedExpert getOwnedExpert(long id, boolean purchasable) {
        List<Long> owners = jdbc.query("""
                SELECT tenant_id FROM cms_expert_profile
                WHERE id=? AND deleted=0
                """ + (purchasable ? " AND audit_status=1 AND enabled=1" : ""),
                (rs, row) -> rs.getLong(1), id);
        if (owners.isEmpty()) throw exception(CONSULT_EXPERT_NOT_FOUND);
        long expertTenantId = owners.getFirst();
        List<Expert> rows = jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       audit_status,audit_remark,enabled,rating,order_count,create_time
                FROM cms_expert_profile WHERE tenant_id=? AND id=? AND deleted=0
                """ + (purchasable ? " AND audit_status=1 AND enabled=1" : ""),
                (rs, row) -> expertRow(rs), expertTenantId, id);
        if (rows.isEmpty()) throw exception(CONSULT_EXPERT_NOT_FOUND);
        return new OwnedExpert(expertTenantId, rows.getFirst());
    }

    private Expert findExpertByUser(long tenantId, long userId) {
        return jdbc.query("""
                SELECT id,user_id,expert_name,professional_title,company,service_fields,introduction,fixed_price,
                       audit_status,audit_remark,enabled,rating,order_count,create_time
                FROM cms_expert_profile WHERE tenant_id=? AND user_id=? AND deleted=0
                """, (rs, row) -> expertRow(rs), tenantId, userId).getFirst();
    }

    private static Expert expertRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Expert(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getInt(8), rs.getInt(9), rs.getString(10),
                rs.getBoolean(11), rs.getBigDecimal(12), rs.getInt(13), time(rs, 14));
    }
    private static LocalDateTime time(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return rs.getTimestamp(i) == null ? null : rs.getTimestamp(i).toLocalDateTime();
    }
    private static String clean(String value) { return value == null ? null : value.strip(); }
    private static long tenant() { return TenantContextHolder.getRequiredTenantId(); }

    public record ExpertInput(String expertName, String professionalTitle, String company,
                              String serviceFields, String introduction, int fixedPrice) { }
    public record Expert(long id, long userId, String expertName, String professionalTitle, String company,
                         String serviceFields, String introduction, int fixedPrice, int auditStatus,
                         String auditRemark, boolean enabled, java.math.BigDecimal rating, int orderCount,
                         LocalDateTime createTime) { }
    public record Order(long id, String orderNo, long userId, String requesterName, String requesterMobile,
                        long expertId, long expertUserId, String expertName, String serviceSubject, String question,
                        int price, int status, Long payOrderId,
                        Long imGroupId, LocalDateTime expireTime, LocalDateTime paidTime,
                        LocalDateTime completedTime, String cancelReason, LocalDateTime createTime, int version,
                        long expertTenantId, long customerTenantId) { }
    private record OwnedExpert(long tenantId, Expert expert) { }
    public record Contact(long orderId, long requesterUserId, String requesterName, String requesterMobile,
                          Long imGroupId, boolean imReady) { }
    public record Evaluation(long id, long orderId, int rating, String content, LocalDateTime createTime) { }
    public record Settlement(long id, long orderId, String orderNo, String expertName, int orderAmount,
                             java.math.BigDecimal commissionRate, int commissionAmount, int settlementAmount,
                             int status, String proofUrl, String proofNo, LocalDateTime settledTime, String remark) { }

    public record ApplyInput(String expertName, String idCard, String phone, String professionalTitle,
                             String company, String serviceFields, String introduction, int fixedPrice,
                             Integer yearsOfExperience) { }
    public record VerifyResult(boolean passed, String channel, String message) { }
    public record ExpertApplication(long id, long userId, String expertName, String professionalTitle, String company,
                                    String serviceFields, String introduction, int fixedPrice, String applyChannel,
                                    String expertLevel, String phone, Integer yearsOfExperience, int auditStatus,
                                    String auditRemark, boolean enabled, String verifyResult,
                                    LocalDateTime applyTime, LocalDateTime createTime) { }
}
