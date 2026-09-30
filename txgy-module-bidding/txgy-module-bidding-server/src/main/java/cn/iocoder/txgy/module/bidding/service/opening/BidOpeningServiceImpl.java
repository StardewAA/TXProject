package cn.iocoder.txgy.module.bidding.service.opening;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningChantReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningEnterReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingBidOpeningDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingOpeningMemberDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.opening.BiddingBidOpeningMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.opening.BiddingOpeningMemberMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.processtrack.BidOpeningStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.processtrack.BidRoomStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.processtrack.OpeningMemberRoleEnum;
import cn.iocoder.txgy.module.bidding.infra.ca.CaClientFacade;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开标房间实现（房间状态机 + 成员认证留痕）。
 * CA 认证/加密/解密均走 {@link CaClientFacade}（外部 CA SDK 适配层，当前占位）；
 * 房间成员表 bidding_opening_member 记录每名进房成员的认证结果（PENDING→AUTHED/FAILED）。
 */
@Service
@Slf4j
public class BidOpeningServiceImpl implements BidOpeningService {

    @Resource
    private BiddingBidOpeningMapper openingMapper;
    @Resource
    private BiddingOpeningMemberMapper memberMapper;
    @Resource
    private CaClientFacade caClientFacade;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingBidOpeningDO createOpening(OpeningCreateReqVO reqVO) {
        BiddingBidOpeningDO exist = openingMapper.selectByTenderProjectCode(reqVO.getTenderProjectCode());
        if (exist != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_ALREADY_EXISTS);
        }
        BiddingBidOpeningDO opening = new BiddingBidOpeningDO();
        opening.setTenderProjectCode(reqVO.getTenderProjectCode());
        opening.setBidSectionCode(reqVO.getBidSectionCode());
        opening.setOpeningTime(reqVO.getOpeningTime() != null ? reqVO.getOpeningTime() : LocalDateTime.now());
        opening.setOpeningStatus(BidOpeningStatusEnum.PENDING.name());
        opening.setRoomStatus(BidRoomStatusEnum.WAITING.name());
        opening.setOperatorName(reqVO.getOperatorName());
        openingMapper.insert(opening);
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingBidOpeningDO openRoom(Long openingId, String operatorName) {
        BiddingBidOpeningDO opening = requireOpening(openingId);
        if (!BidRoomStatusEnum.WAITING.name().equals(opening.getRoomStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_STATUS_INVALID, opening.getRoomStatus());
        }
        if (StrUtil.isNotBlank(operatorName)) {
            opening.setOperatorName(operatorName);
        }
        opening.setRoomStatus(BidRoomStatusEnum.DECRYPTING.name());
        opening.setOpeningStatus(BidOpeningStatusEnum.DECRYPTING.name());
        opening.setRoomOpenedTime(LocalDateTime.now());
        openingMapper.updateById(opening);
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingOpeningMemberDO enterRoom(OpeningEnterReqVO reqVO) {
        BiddingBidOpeningDO opening = requireOpening(reqVO.getOpeningId());
        if (!BidRoomStatusEnum.DECRYPTING.name().equals(opening.getRoomStatus())
                && !BidRoomStatusEnum.OPENING.name().equals(opening.getRoomStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_STATUS_INVALID, opening.getRoomStatus());
        }
        // 身份角色校验
        if (OpeningMemberRoleEnum.of(reqVO.getRole()) == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_MEMBER_ROLE_INVALID, reqVO.getRole());
        }
        // 同一用户同一房间唯一在线
        BiddingOpeningMemberDO exist = memberMapper.selectByOpeningIdAndUser(reqVO.getOpeningId(), reqVO.getUserId());
        if (exist != null && Boolean.TRUE.equals(exist.getOnline())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_MEMBER_ALREADY_IN);
        }
        // 进房身份认证（外部 CA SDK 占位：identityType=CA_CERT 时按证书号核验；SDK 到位后调 CaClientFacade）
        String authResult = "AUTHED";
        String authMessage = null;
        try {
            boolean ok = caClientFacade.authenticate(reqVO.getCaCertNo(), reqVO.getSubjectCode());
            if (!ok) {
                authResult = "FAILED";
                authMessage = "CA 证书无效或身份不符";
            }
        } catch (Exception e) {
            log.warn("CA 认证占位失败，放行测试: {}", e.getMessage());
        }
        BiddingOpeningMemberDO member = new BiddingOpeningMemberDO();
        member.setOpeningId(reqVO.getOpeningId());
        member.setTenderProjectCode(opening.getTenderProjectCode());
        member.setUserId(reqVO.getUserId());
        member.setUserName(reqVO.getUserName());
        member.setSubjectName(reqVO.getSubjectName());
        member.setSubjectCode(reqVO.getSubjectCode());
        member.setRole(reqVO.getRole());
        member.setIdentityType(reqVO.getIdentityType());
        member.setCaCertNo(reqVO.getCaCertNo());
        member.setAuthResult(authResult);
        member.setAuthMessage(authMessage);
        if ("AUTHED".equals(authResult)) {
            member.setAuthTime(LocalDateTime.now());
            member.setEnterTime(LocalDateTime.now());
            member.setOnline(true);
        }
        memberMapper.insert(member);
        return member;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(Long openingId, Long userId) {
        BiddingOpeningMemberDO member = memberMapper.selectByOpeningIdAndUser(openingId, userId);
        if (member == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_MEMBER_NOT_EXISTS);
        }
        member.setLeaveTime(LocalDateTime.now());
        member.setOnline(false);
        memberMapper.updateById(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingBidOpeningDO chant(OpeningChantReqVO reqVO) {
        BiddingBidOpeningDO opening = requireOpening(reqVO.getOpeningId());
        if (!BidRoomStatusEnum.DECRYPTING.name().equals(opening.getRoomStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_STATUS_INVALID, opening.getRoomStatus());
        }
        if (StrUtil.isBlank(reqVO.getOpenList())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_OPEN_LIST_REQUIRED);
        }
        opening.setOpenList(reqVO.getOpenList());
        opening.setRoomStatus(BidRoomStatusEnum.OPENING.name());
        opening.setOpeningStatus(BidOpeningStatusEnum.DONE.name());
        opening.setBidderCount(reqVO.getBidderCount() != null ? reqVO.getBidderCount() : 0);
        openingMapper.updateById(opening);
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingBidOpeningDO finishOpening(Long openingId) {
        BiddingBidOpeningDO opening = requireOpening(openingId);
        if (!BidRoomStatusEnum.OPENING.name().equals(opening.getRoomStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_STATUS_INVALID, opening.getRoomStatus());
        }
        opening.setRoomStatus(BidRoomStatusEnum.DONE.name());
        opening.setOpeningStatus(BidOpeningStatusEnum.DONE.name());
        opening.setRoomClosedTime(LocalDateTime.now());
        openingMapper.updateById(opening);
        return opening;
    }

    @Override
    public List<BiddingOpeningMemberDO> listMembers(Long openingId) {
        return memberMapper.selectListByOpeningId(openingId);
    }

    @Override
    public BiddingBidOpeningDO getByTenderProject(String tenderProjectCode) {
        return openingMapper.selectByTenderProjectCode(tenderProjectCode);
    }

    @Override
    public List<BiddingBidOpeningDO> listBySection(String bidSectionCode) {
        if (StrUtil.isBlank(bidSectionCode)) {
            return openingMapper.selectList(null);
        }
        return openingMapper.selectList(new LambdaQueryWrapper<BiddingBidOpeningDO>()
                .eq(BiddingBidOpeningDO::getBidSectionCode, bidSectionCode.trim())
                .or()
                .isNull(BiddingBidOpeningDO::getBidSectionCode)); // 覆盖招标项目全部标段的开标记录
    }

    @Override
    public BiddingBidOpeningDO getOpening(Long openingId) {
        return requireOpening(openingId);
    }

    private BiddingBidOpeningDO requireOpening(Long openingId) {
        BiddingBidOpeningDO opening = openingMapper.selectById(openingId);
        if (opening == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OPENING_ROOM_NOT_EXISTS);
        }
        return opening;
    }
}
