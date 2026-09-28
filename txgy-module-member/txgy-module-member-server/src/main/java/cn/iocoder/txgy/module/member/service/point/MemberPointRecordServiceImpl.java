package cn.iocoder.txgy.module.member.service.point;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO;
import cn.iocoder.txgy.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;


/**
 * 积分记录 Service 实现类
 *
 * @author QingX
 */
@Slf4j
@Service
@Validated
public class MemberPointRecordServiceImpl implements MemberPointRecordService {

    @Resource
    private MemberPointRecordMapper memberPointRecordMapper;

    @Resource
    private MemberUserService memberUserService;

    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    public PageResult<MemberPointRecordDO> getPointRecordPage(MemberPointRecordPageReqVO pageReqVO) {
        // 根据用户昵称查询出用户 ids
        Set<Long> userIds = null;
        if (StringUtils.isNotBlank(pageReqVO.getNickname())) {
            List<MemberUserDO> users = memberUserService.getUserListByNickname(pageReqVO.getNickname());
            // 如果查询用户结果为空直接返回无需继续查询
            if (CollectionUtils.isEmpty(users)) {
                return PageResult.empty();
            }
            userIds = convertSet(users, MemberUserDO::getId);
        }
        // 执行查询
        return memberPointRecordMapper.selectPage(pageReqVO, userIds);
    }

    @Override
    public PageResult<MemberPointRecordDO> getPointRecordPage(Long userId, AppMemberPointRecordPageReqVO pageReqVO) {
        return memberPointRecordMapper.selectPage(userId, pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPointRecord(Long userId, Integer point, MemberPointBizTypeEnum bizType, String bizId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || userId == null || userId <= 0 || point == null) {
            throw exception(POINT_RECORD_REQUEST_INVALID);
        }
        if (bizType == null) {
            throw exception(POINT_RECORD_BIZ_NOT_SUPPORT);
        }
        boolean orderBusiness = isOrderBusiness(bizType);
        if (orderBusiness) {
            validateOrderBusiness(point, bizType, bizId);
        }
        if (point == 0) {
            return;
        }

        // 1. 所有积分来源共用会员行锁。锁、余额更新、流水插入必须处在同一个数据库事务内。
        // ADMIN/SIGN 不按订单业务键去重，但同样串行化，确保余额和 totalPoint 没有并发丢失。
        MemberUserDO user = memberUserMapper.selectPointUserForUpdate(tenantId, userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (orderBusiness && isBusinessAlreadyApplied(tenantId, userId, point, bizType, bizId)) {
            return;
        }

        // 2. 校验余额。扣减失败必须向调用方报错，不能让订单在未扣积分时继续推进。
        Integer userPoint = ObjectUtil.defaultIfNull(user.getPoint(), 0);
        long totalPoint = (long) userPoint + point;
        if (totalPoint < 0) {
            throw exception(USER_POINT_NOT_ENOUGH);
        }
        if (totalPoint > Integer.MAX_VALUE) {
            throw exception(POINT_RECORD_BALANCE_OVERFLOW);
        }

        // 3. 显式租户条件，余额与流水一起成功/回滚，不依赖 JVM 内锁或 RPC 调用方事务。
        if (memberUserMapper.updatePointForTenant(tenantId, userId, point) != 1) {
            throw exception(USER_POINT_NOT_ENOUGH);
        }

        // 4. 持久化业务事实。只有整笔事务提交后，后续重试才会被视为已经处理。
        MemberPointRecordDO record = new MemberPointRecordDO()
                .setUserId(userId).setBizId(bizId).setBizType(bizType.getType())
                .setTitle(bizType.getName()).setDescription(StrUtil.format(bizType.getDescription(), point))
                .setPoint(point).setTotalPoint((int) totalPoint);
        record.setTenantId(tenantId);
        if (memberPointRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("积分流水写入失败");
        }
    }

    private boolean isBusinessAlreadyApplied(Long tenantId, Long userId, Integer point,
            MemberPointBizTypeEnum bizType, String bizId) {
        List<MemberPointRecordDO> records = memberPointRecordMapper.selectBusinessRecordsForUpdate(
                tenantId, userId, bizType.getType(), bizId);
        if (records.isEmpty()) {
            return false;
        }
        // 旧单可能已有多笔相同返还：不再次返还、不删除/合并历史。每笔金额都必须相同；不能只取第一笔。
        if (records.stream().anyMatch(record -> !Objects.equals(record.getPoint(), point))) {
            throw exception(POINT_RECORD_BIZ_CONFLICT);
        }
        if (records.size() > 1) {
            log.warn("[createPointRecord][historical duplicate points: tenantId({}) userId({}) bizType({}) bizId({}) count({})]",
                    tenantId, userId, bizType.getType(), bizId, records.size());
        }
        return true;
    }

    private static boolean isOrderBusiness(MemberPointBizTypeEnum bizType) {
        return switch (bizType) {
            case ORDER_USE, ORDER_USE_CANCEL, ORDER_USE_CANCEL_ITEM,
                 ORDER_GIVE, ORDER_GIVE_CANCEL, ORDER_GIVE_CANCEL_ITEM -> true;
            default -> false;
        };
    }

    private static void validateOrderBusiness(Integer point, MemberPointBizTypeEnum bizType, String bizId) {
        // 11/12/21/22 的编号是订单 ID，13/23 是订单项 ID；业务类型本身是幂等键的一部分。
        // 只接受正 Long 的标准十进制写法，拒绝空串、负号、前导零等可绕过业务键的别名。
        if (bizId == null || !bizId.matches("[1-9][0-9]{0,18}")
                || point == 0 || (point > 0) != bizType.isAdd()) {
            throw exception(POINT_RECORD_REQUEST_INVALID);
        }
        try {
            Long.parseLong(bizId);
        } catch (NumberFormatException exception) {
            throw exception(POINT_RECORD_REQUEST_INVALID);
        }
    }

}
