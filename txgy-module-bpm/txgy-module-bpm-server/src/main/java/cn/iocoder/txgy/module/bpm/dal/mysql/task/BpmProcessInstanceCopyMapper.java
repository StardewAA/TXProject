package cn.iocoder.txgy.module.bpm.dal.mysql.task;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.txgy.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

import static cn.iocoder.txgy.module.bpm.util.BpmProcessDefinitionKeyUtils.parseKeys;

@Mapper
public interface BpmProcessInstanceCopyMapper extends BaseMapperX<BpmProcessInstanceCopyDO> {

    default PageResult<BpmProcessInstanceCopyDO> selectPage(Long loginUserId, BpmProcessInstanceCopyPageReqVO reqVO) {
        LambdaQueryWrapperX<BpmProcessInstanceCopyDO> query = new LambdaQueryWrapperX<BpmProcessInstanceCopyDO>()
                .eqIfPresent(BpmProcessInstanceCopyDO::getUserId, loginUserId)
                .likeIfPresent(BpmProcessInstanceCopyDO::getProcessInstanceName, reqVO.getProcessInstanceName())
                .betweenIfPresent(BpmProcessInstanceCopyDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmProcessInstanceCopyDO::getId);
        List<String> processDefinitionKeys = parseKeys(reqVO.getProcessDefinitionKeys());
        if (!processDefinitionKeys.isEmpty()) {
            query.and(wrapper -> {
                for (int i = 0; i < processDefinitionKeys.size(); i++) {
                    String prefix = processDefinitionKeys.get(i) + ":";
                    if (i == 0) {
                        wrapper.likeRight(BpmProcessInstanceCopyDO::getProcessDefinitionId, prefix);
                    } else {
                        wrapper.or().likeRight(BpmProcessInstanceCopyDO::getProcessDefinitionId, prefix);
                    }
                }
            });
        }
        return selectPage(reqVO, query);
    }

    default void deleteByProcessInstanceId(String processInstanceId) {
        delete(BpmProcessInstanceCopyDO::getProcessInstanceId, processInstanceId);
    }

}
