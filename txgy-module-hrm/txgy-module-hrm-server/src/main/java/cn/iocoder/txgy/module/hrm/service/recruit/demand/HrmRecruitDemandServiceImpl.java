package cn.iocoder.txgy.module.hrm.service.recruit.demand;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.demand.HrmRecruitDemandMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandUrgencyEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.*;

/**
 * HRM 用工需求单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmRecruitDemandServiceImpl implements HrmRecruitDemandService {

    private static final String DEMAND_NO_PREFIX = "REQ-";

    @Resource
    private HrmRecruitDemandMapper recruitDemandMapper;

    @Override
    public Long createRecruitDemand(HrmRecruitDemandSaveReqVO createReqVO) {
        HrmRecruitDemandDO demand = BeanUtils.toBean(createReqVO, HrmRecruitDemandDO.class);
        // 需求单号：REQ-yyyyMMdd-序号
        demand.setDemandNo(generateDemandNo());
        demand.setOnboardCount(0);
        if (demand.getStatus() == null) {
            demand.setStatus(HrmRecruitDemandStatusEnum.PENDING.getStatus());
        }
        if (demand.getUrgency() == null) {
            demand.setUrgency(HrmRecruitDemandUrgencyEnum.NORMAL.getUrgency());
        }
        demand.setId(null);
        recruitDemandMapper.insert(demand);
        return demand.getId();
    }

    @Override
    public void updateRecruitDemand(HrmRecruitDemandSaveReqVO updateReqVO) {
        validateRecruitDemandExists(updateReqVO.getId());
        HrmRecruitDemandDO updateObj = BeanUtils.toBean(updateReqVO, HrmRecruitDemandDO.class);
        // 需求单号不允许修改
        updateObj.setDemandNo(null);
        recruitDemandMapper.updateById(updateObj);
    }

    @Override
    public void updateRecruitDemandStatus(Long id, Integer status) {
        HrmRecruitDemandDO demand = validateRecruitDemandExists(id);
        HrmRecruitDemandStatusEnum target = HrmRecruitDemandStatusEnum.valueOf(status);
        if (target == null) {
            throw exception(RECRUIT_DEMAND_STATUS_INVALID);
        }
        HrmRecruitDemandStatusEnum current = HrmRecruitDemandStatusEnum.valueOf(demand.getStatus());
        if (Objects.equals(demand.getStatus(), status) || current == null) {
            return;
        }
        // Q-009 / I-009：补状态流转矩阵，非法迁移直接拒绝并给出「当前状态 → 目标状态」提示，
        // 避免「已取消的需求单被重新置为招聘中」这类不可解释的跳变。
        if (!current.canTransferTo(target)) {
            throw exception(RECRUIT_DEMAND_STATUS_TRANSITION_INVALID, current.getName(), target.getName());
        }
        recruitDemandMapper.updateById(new HrmRecruitDemandDO().setId(id).setStatus(status));
    }

    @Override
    public void deleteRecruitDemand(Long id) {
        validateRecruitDemandExists(id);
        recruitDemandMapper.deleteById(id);
    }

    @Override
    public HrmRecruitDemandDO getRecruitDemand(Long id) {
        return recruitDemandMapper.selectById(id);
    }

    @Override
    public HrmRecruitDemandDO validateRecruitDemandExists(Long id) {
        HrmRecruitDemandDO demand = recruitDemandMapper.selectById(id);
        if (demand == null) {
            throw exception(RECRUIT_DEMAND_NOT_EXISTS);
        }
        return demand;
    }

    @Override
    public PageResult<HrmRecruitDemandDO> getRecruitDemandPage(HrmRecruitDemandPageReqVO pageReqVO) {
        return recruitDemandMapper.selectPage(pageReqVO);
    }

    @Override
    public List<HrmRecruitDemandDO> getRecruitDemandList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return recruitDemandMapper.selectList(HrmRecruitDemandDO::getId, ids);
    }

    @Override
    public List<HrmRecruitDemandDO> getRecruitingDemandList() {
        List<HrmRecruitDemandDO> list = recruitDemandMapper.selectListByStatus(null);
        return list.stream()
                .filter(item -> !HrmRecruitDemandStatusEnum.FINISHED.getStatus().equals(item.getStatus())
                        && !HrmRecruitDemandStatusEnum.CANCELED.getStatus().equals(item.getStatus()))
                .toList();
    }

    @Override
    public void increaseOnboardCount(Long demandId) {
        if (demandId == null) {
            return;
        }
        // 原子自增，避免并发入职同一需求单时「读-改-写」丢失更新（P1）
        int updated = recruitDemandMapper.increaseOnboardCount(demandId);
        if (updated == 0) {
            log.warn("[increaseOnboardCount][需求单({})不存在，跳过回写]", demandId);
            return;
        }
        // 自增完成后回读最新值，据以决定状态流转（到齐完成 / 未到齐进入招聘中）
        HrmRecruitDemandDO demand = recruitDemandMapper.selectById(demandId);
        if (demand == null) {
            log.warn("[increaseOnboardCount][需求单({})不存在，跳过回写]", demandId);
            return;
        }
        int onboardCount = demand.getOnboardCount() == null ? 0 : demand.getOnboardCount();
        HrmRecruitDemandDO updateObj = new HrmRecruitDemandDO().setId(demandId);
        // 到齐自动完成；未到齐且仍处于「待招聘」则自动进入「招聘中」
        int headcount = demand.getHeadcount() == null ? 0 : demand.getHeadcount();
        if (headcount > 0 && onboardCount >= headcount) {
            updateObj.setStatus(HrmRecruitDemandStatusEnum.FINISHED.getStatus());
        } else if (HrmRecruitDemandStatusEnum.PENDING.getStatus().equals(demand.getStatus())) {
            updateObj.setStatus(HrmRecruitDemandStatusEnum.RECRUITING.getStatus());
        }
        if (updateObj.getStatus() != null) {
            recruitDemandMapper.updateById(updateObj);
        }
    }

    @Override
    public boolean reserveOnboardCount(Long demandId) {
        if (demandId == null) {
            return true;
        }
        // 原子占位：条件自增，编制已满时受影响行数为 0（并发安全，见 HrmRecruitDemandMapper#reserveOnboardCount）
        if (recruitDemandMapper.reserveOnboardCount(demandId) > 0) {
            return true;
        }
        // 占位失败：区分「需求单不存在」与「编制已满」，给出可执行的业务提示
        HrmRecruitDemandDO demand = recruitDemandMapper.selectById(demandId);
        if (demand == null) {
            log.warn("[reserveOnboardCount][需求单({})不存在，跳过编制占位]", demandId);
            return true;
        }
        int headcount = demand.getHeadcount() == null ? 0 : demand.getHeadcount();
        if (headcount > 0) {
            throw exception(RECRUIT_DEMAND_HEADCOUNT_REACHED, demand.getDemandName(), headcount);
        }
        return true;
    }

    @Override
    public void refreshOnboardStatus(Long demandId) {
        if (demandId == null) {
            return;
        }
        HrmRecruitDemandDO demand = recruitDemandMapper.selectById(demandId);
        if (demand == null) {
            return;
        }
        HrmRecruitDemandDO updateObj = new HrmRecruitDemandDO().setId(demandId);
        int onboardCount = demand.getOnboardCount() == null ? 0 : demand.getOnboardCount();
        int headcount = demand.getHeadcount() == null ? 0 : demand.getHeadcount();
        // 已取消的需求单不因到岗人数回写被"复活"成已完成
        if (HrmRecruitDemandStatusEnum.CANCELED.getStatus().equals(demand.getStatus())) {
            return;
        }
        if (headcount > 0 && onboardCount >= headcount) {
            updateObj.setStatus(HrmRecruitDemandStatusEnum.FINISHED.getStatus());
        } else if (HrmRecruitDemandStatusEnum.PENDING.getStatus().equals(demand.getStatus())) {
            updateObj.setStatus(HrmRecruitDemandStatusEnum.RECRUITING.getStatus());
        }
        if (updateObj.getStatus() != null) {
            recruitDemandMapper.updateById(updateObj);
        }
    }

    @Override
    public void validateRecruitDemandCanRecruit(Long demandId) {
        if (demandId == null) {
            return;
        }
        HrmRecruitDemandDO demand = validateRecruitDemandExists(demandId);
        if (HrmRecruitDemandStatusEnum.FINISHED.getStatus().equals(demand.getStatus())) {
            throw exception(RECRUIT_DEMAND_FINISHED_CANNOT_UPDATE, demand.getDemandName());
        }
        if (HrmRecruitDemandStatusEnum.CANCELED.getStatus().equals(demand.getStatus())) {
            throw exception(RECRUIT_DEMAND_STATUS_INVALID);
        }
        int headcount = demand.getHeadcount() == null ? 0 : demand.getHeadcount();
        int onboardCount = demand.getOnboardCount() == null ? 0 : demand.getOnboardCount();
        if (headcount > 0 && onboardCount >= headcount) {
            throw exception(RECRUIT_DEMAND_HEADCOUNT_REACHED, demand.getDemandName(), headcount);
        }
    }

    /**
     * 生成需求单号：REQ-yyyyMMdd-三位序号（同租户内自增，冲突时最多重试 5 次）
     */
    private String generateDemandNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = DEMAND_NO_PREFIX + datePart + "-";
        Long count = recruitDemandMapper.selectCount(
                new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<HrmRecruitDemandDO>()
                        .likeRight(HrmRecruitDemandDO::getDemandNo, prefix));
        for (int i = 0; i < 5; i++) {
            String demandNo = prefix + StrUtil.padPre(String.valueOf(count + 1 + i), 3, '0');
            if (recruitDemandMapper.selectByDemandNo(demandNo) == null) {
                return demandNo;
            }
        }
        // 兜底：时间戳后缀，保证唯一
        return prefix + System.currentTimeMillis() % 100_000;
    }

}
