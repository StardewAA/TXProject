-- WMS 出库审批：仓库主管与上层决策节点均使用“多人或签（任意一人处理）”。
START TRANSACTION;

SET @warehouse_old = '</extensionElements>
    </userTask>
    <userTask id="DecisionApproverApprove"';
SET @warehouse_new = '</extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${coll_userList}">
        <completionCondition xsi:type="tFormalExpression"><![CDATA[${ nrOfCompletedInstances > 0 }]]></completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <userTask id="DecisionApproverApprove"';
SET @decision_old = '</extensionElements>
    </userTask>
    <endEvent id="EndEvent"';
SET @decision_new = '</extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${coll_userList}">
        <completionCondition xsi:type="tFormalExpression"><![CDATA[${ nrOfCompletedInstances > 0 }]]></completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <endEvent id="EndEvent"';

-- 同时更新模型主资源和最新已发布定义，保证再次发布和立即新建流程都正确。
UPDATE `ACT_GE_BYTEARRAY` resource
LEFT JOIN `ACT_RE_MODEL` model ON model.EDITOR_SOURCE_VALUE_ID_ = resource.ID_
LEFT JOIN `ACT_RE_PROCDEF` definition ON definition.DEPLOYMENT_ID_ = resource.DEPLOYMENT_ID_
LEFT JOIN (
    SELECT KEY_, TENANT_ID_, MAX(VERSION_) AS VERSION_
    FROM `ACT_RE_PROCDEF`
    WHERE KEY_ = 'wms_shipment_approval' AND TENANT_ID_ = '2005'
    GROUP BY KEY_, TENANT_ID_
) latest ON latest.KEY_ = definition.KEY_
    AND latest.TENANT_ID_ = definition.TENANT_ID_
    AND latest.VERSION_ = definition.VERSION_
SET resource.BYTES_ = CONVERT(
    REPLACE(
      REPLACE(
       REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(
              REPLACE(CONVERT(resource.BYTES_ USING utf8mb4),
                '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60096]]></flowable:candidateParam>',
                '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60096,60084]]></flowable:candidateParam>'),
              '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60085]]></flowable:candidateParam>',
              '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60084]]></flowable:candidateParam>'),
            '<flowable:approveMethod xmlns:flowable="http://flowable.org/bpmn"><![CDATA[1]]></flowable:approveMethod>',
            '<flowable:approveMethod xmlns:flowable="http://flowable.org/bpmn"><![CDATA[3]]></flowable:approveMethod>'),
          @warehouse_old, @warehouse_new),
        @decision_old, @decision_new),
      '<multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${coll_userList}">
        <completionCondition xsi:type="tFormalExpression"><![CDATA[${ nrOfCompletedInstances > 0 }]]></completionCondition>
      </multiInstanceLoopCharacteristics>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${coll_userList}">',
      '<multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${coll_userList}">'),
      '<flowable:assignStartUserHandlerType xmlns:flowable="http://flowable.org/bpmn"><![CDATA[2]]></flowable:assignStartUserHandlerType>',
      '<flowable:assignStartUserHandlerType xmlns:flowable="http://flowable.org/bpmn"><![CDATA[1]]></flowable:assignStartUserHandlerType>')
    USING binary)
WHERE (model.KEY_ = 'wms_shipment_approval' AND model.TENANT_ID_ = '2005')
   OR (latest.VERSION_ IS NOT NULL AND resource.NAME_ = 'wms_shipment_approval.bpmn');

COMMIT;
