package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 规范依据：设计文档 v0.6 §三（批次/汇总）；《招投标模块开发规范》§4.2

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlJobPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlJobRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlJobMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 采集批次查询实现（管理面只读；批次创建/回写由调度与人工触发流程负责）。
 */
@Service
public class CrawlJobServiceImpl implements CrawlJobService {

    @Resource
    private CrawlJobMapper crawlJobMapper;

    @Override
    public PageResult<CrawlJobRespVO> getJobPage(CrawlJobPageReqVO reqVO) {
        LambdaQueryWrapper<CrawlJobDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(CrawlJobDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getTriggerType())) {
            wrapper.eq(CrawlJobDO::getTriggerType, reqVO.getTriggerType());
        }
        if (StringUtils.hasText(reqVO.getStatus())) {
            wrapper.eq(CrawlJobDO::getStatus, reqVO.getStatus());
        }
        wrapper.orderByDesc(CrawlJobDO::getId);
        IPage<CrawlJobDO> page = crawlJobMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public CrawlJobRespVO getJob(Long id) {
        CrawlJobDO job = crawlJobMapper.selectById(id);
        if (job == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_JOB_NOT_EXISTS);
        }
        return toRespVO(job);
    }

    private CrawlJobRespVO toRespVO(CrawlJobDO job) {
        CrawlJobRespVO resp = new CrawlJobRespVO();
        resp.setId(job.getId());
        resp.setSourceCode(job.getSourceCode());
        resp.setTriggerType(job.getTriggerType());
        resp.setScope(job.getScope());
        resp.setStatus(job.getStatus());
        resp.setSummaryJson(job.getSummaryJson());
        resp.setFinishedAt(job.getFinishedAt());
        resp.setCreateTime(job.getCreateTime());
        resp.setUpdateTime(job.getUpdateTime());
        return resp;
    }

}
