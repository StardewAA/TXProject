-- 为租户 2005 的 WMS 出库审批模型补齐 SIMPLE 流程资源。
-- 审批链：仓库管理员（仓库主管） -> 决策审批人（更高层级）。

START TRANSACTION;

SET @model_id = (
    SELECT `ID_` FROM `ACT_RE_MODEL`
    WHERE `KEY_` = 'wms_shipment_approval' AND `TENANT_ID_` = '2005'
    LIMIT 1
);
SET @source_id = UUID();
SET @source_extra_id = UUID();

SET @bpmn_xml = '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC" xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="wms_shipment_approval" name="WMS出库审批" isExecutable="true">
    <startEvent id="StartEvent" name="开始"/>
    <userTask id="StartUserNode" name="发起人">
      <extensionElements>
        <flowable:approveType><![CDATA[1]]></flowable:approveType>
        <flowable:candidateStrategy><![CDATA[36]]></flowable:candidateStrategy>
        <flowable:assignStartUserHandlerType><![CDATA[2]]></flowable:assignStartUserHandlerType>
        <flowable:nodeType><![CDATA[10]]></flowable:nodeType>
      </extensionElements>
    </userTask>
    <userTask id="WarehouseSupervisorApprove" name="仓库主管审批">
      <extensionElements>
        <flowable:approveType><![CDATA[1]]></flowable:approveType>
        <flowable:candidateStrategy><![CDATA[10]]></flowable:candidateStrategy>
        <flowable:candidateParam><![CDATA[60096]]></flowable:candidateParam>
        <flowable:approveMethod><![CDATA[1]]></flowable:approveMethod>
        <flowable:rejectHandlerType><![CDATA[1]]></flowable:rejectHandlerType>
        <flowable:assignStartUserHandlerType><![CDATA[2]]></flowable:assignStartUserHandlerType>
        <flowable:assignEmptyHandlerType><![CDATA[4]]></flowable:assignEmptyHandlerType>
        <flowable:signEnable><![CDATA[false]]></flowable:signEnable>
        <flowable:reasonRequire><![CDATA[true]]></flowable:reasonRequire>
        <flowable:nodeType><![CDATA[11]]></flowable:nodeType>
      </extensionElements>
    </userTask>
    <userTask id="DecisionApproverApprove" name="上级决策审批">
      <extensionElements>
        <flowable:approveType><![CDATA[1]]></flowable:approveType>
        <flowable:candidateStrategy><![CDATA[10]]></flowable:candidateStrategy>
        <flowable:candidateParam><![CDATA[60085]]></flowable:candidateParam>
        <flowable:approveMethod><![CDATA[1]]></flowable:approveMethod>
        <flowable:rejectHandlerType><![CDATA[1]]></flowable:rejectHandlerType>
        <flowable:assignStartUserHandlerType><![CDATA[2]]></flowable:assignStartUserHandlerType>
        <flowable:assignEmptyHandlerType><![CDATA[4]]></flowable:assignEmptyHandlerType>
        <flowable:signEnable><![CDATA[false]]></flowable:signEnable>
        <flowable:reasonRequire><![CDATA[true]]></flowable:reasonRequire>
        <flowable:nodeType><![CDATA[11]]></flowable:nodeType>
      </extensionElements>
    </userTask>
    <endEvent id="EndEvent" name="结束"/>
    <sequenceFlow id="Flow_Start" sourceRef="StartEvent" targetRef="StartUserNode"/>
    <sequenceFlow id="Flow_Supervisor" sourceRef="StartUserNode" targetRef="WarehouseSupervisorApprove"/>
    <sequenceFlow id="Flow_Decision" sourceRef="WarehouseSupervisorApprove" targetRef="DecisionApproverApprove"/>
    <sequenceFlow id="Flow_End" sourceRef="DecisionApproverApprove" targetRef="EndEvent"/>
  </process>
</definitions>';

SET @simple_json = '{"id":"StartUserNode","type":10,"name":"发起人","showText":"发起人本人","childNode":{"id":"WarehouseSupervisorApprove","type":11,"name":"仓库主管审批","showText":"指定角色：仓库管理员","candidateStrategy":10,"candidateParam":"60096","approveType":1,"approveMethod":1,"rejectHandler":{"type":1},"assignStartUserHandlerType":2,"assignEmptyHandler":{"type":4},"signEnable":false,"reasonRequire":true,"childNode":{"id":"DecisionApproverApprove","type":11,"name":"上级决策审批","showText":"指定角色：决策审批人","candidateStrategy":10,"candidateParam":"60085","approveType":1,"approveMethod":1,"rejectHandler":{"type":1},"assignStartUserHandlerType":2,"assignEmptyHandler":{"type":4},"signEnable":false,"reasonRequire":true,"childNode":{"id":"EndEvent","type":1,"name":"结束"}}}}';

INSERT INTO `ACT_GE_BYTEARRAY` (`ID_`, `REV_`, `NAME_`, `BYTES_`)
SELECT @source_id, 1, 'source', CONVERT(@bpmn_xml USING binary)
WHERE @model_id IS NOT NULL
  AND (SELECT `EDITOR_SOURCE_VALUE_ID_` FROM `ACT_RE_MODEL` WHERE `ID_` = @model_id) IS NULL;

INSERT INTO `ACT_GE_BYTEARRAY` (`ID_`, `REV_`, `NAME_`, `BYTES_`)
SELECT @source_extra_id, 1, 'source-extra', CONVERT(@simple_json USING binary)
WHERE @model_id IS NOT NULL
  AND (SELECT `EDITOR_SOURCE_EXTRA_VALUE_ID_` FROM `ACT_RE_MODEL` WHERE `ID_` = @model_id) IS NULL;

UPDATE `ACT_RE_MODEL`
SET `EDITOR_SOURCE_VALUE_ID_` = COALESCE(`EDITOR_SOURCE_VALUE_ID_`, @source_id),
    `EDITOR_SOURCE_EXTRA_VALUE_ID_` = COALESCE(`EDITOR_SOURCE_EXTRA_VALUE_ID_`, @source_extra_id),
    `META_INFO_` = JSON_SET(
        `META_INFO_`,
        '$.formCustomCreatePath', '/wms/order/shipment/modules/form.vue',
        '$.formCustomViewPath', '/wms/order/shipment/modules/detail.vue'
    ),
    `REV_` = `REV_` + 1,
    `LAST_UPDATE_TIME_` = CURRENT_TIMESTAMP(3)
WHERE `ID_` = @model_id;

COMMIT;
