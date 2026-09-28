# WMS—ERP Master Data Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first real ERP-to-WMS remote integration for material, partner, warehouse, idempotency, and request-result lookup.

**Architecture:** A WMS integration facade validates tenant and routes requests to focused master-data services. A transactional request service persists a stable payload digest and response, while existing WMS mappers remain the only persistence boundary for master data. ERP warehouse operations call the established gateway and retain only ERP-specific configuration plus the WMS identifier/version.

**Tech Stack:** Java 25, Spring Boot, MyBatis Plus, JUnit 5, Mockito, H2/MySQL, Maven.

**Spec:** `docs/superpowers/specs/2026-09-08-wms-erp-master-data-design.md`

## Global Constraints

- Preserve the existing v0.2 `WmsIntegrationApi` paths and DTOs.
- Validate authenticated tenant against `tenantId` for every RPC operation.
- Never modify inventory from master-data synchronization.
- Preserve WMS-only extension fields during ERP updates.
- Use `<materialCode>-DEFAULT` as the sole automatically created default SKU.
- Do not return success for inventory, receipt, shipment, or reservation operations before they are implemented.
- Run tests with the unpacked JDK 25 at `../outputs/jdk25-runtime/PFiles64/Eclipse Adoptium/jdk-25.0.4.101-hotspot`.

---

### Task 1: Idempotent Request Ledger

**Files:**
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/dataobject/integration/WmsIntegrationRequestDO.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/integration/WmsIntegrationRequestMapper.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/service/integration/WmsIntegrationRequestService.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/service/integration/WmsIntegrationRequestServiceTest.java`
- Modify: `txgy-module-wms/txgy-module-wms-server/src/test/resources/sql/create_tables.sql`

**Interfaces:**
- Produces: `WmsIntegrationRequestRespDTO execute(String operationType, WmsIntegrationBaseReqDTO request, Supplier<WmsIntegrationRequestRespDTO> action)` and `WmsIntegrationRequestRespDTO getResult(String requestId)`.

- [ ] **Step 1: Write failing tests** for first execution, identical replay without a second action call, changed payload rejection, missing request lookup, and concurrent duplicate delivery. The serial replay and changed-payload cases are complete; missing lookup and concurrency remain. Use the real H2 mapper and a counter-backed action; assert returned business status and persisted response rather than mapper call counts.
- [x] **Step 2: Run the test and verify RED** with:
  `mvn -o -pl txgy-module-wms/txgy-module-wms-server -am -Dtest=WmsIntegrationRequestServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: compilation failure because the request service and record do not exist.
- [ ] **Step 3: Implement the minimal ledger.** Serial execution, hashing, persistence and replay are complete; concurrent duplicate-key recovery remains. Serialize requests with the configured Jackson `ObjectMapper`, hash UTF-8 JSON with SHA-256, insert a PROCESSING record, run the supplied action, then store SUCCESS plus serialized response. On duplicate key, reload by tenant/requestId and return the saved response only when the digest matches; otherwise throw `INTEGRATION_REQUEST_DUPLICATE_CONFLICT`.
- [ ] **Step 4: Run the focused test and verify GREEN**, then run existing WMS tests after concurrency coverage is complete.
- [x] **Step 5: Commit** with `feat(wms): add idempotent integration request ledger`.

### Task 2: Material Master Data Synchronization

**Files:**
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/service/integration/WmsMaterialIntegrationService.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/service/integration/WmsMaterialIntegrationServiceTest.java`
- Modify: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/md/item/WmsItemMapper.java`
- Modify: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/md/item/WmsItemSkuMapper.java`

**Interfaces:**
- Consumes: request DTO and existing WMS item/SKU mappers.
- Produces: `WmsIntegrationAcceptedRespDTO upsert(WmsMaterialUpsertReqDTO request)`.

- [x] **Step 1: Write failing database tests** proving a new material creates one default SKU, a higher version updates public fields while preserving category/brand/SKU selling price, an old version fails, and an ambiguous existing SKU mapping fails.
- [x] **Step 2: Run focused tests and verify RED** because the service is absent.
- [x] **Step 3: Implement minimal upsert logic** keyed by item code. Map ENABLED/DISABLED to `CommonStatusEnum`, validate RAW_MATERIAL/FINISHED_GOODS, use `dataVersion`, and create or find exactly one `<materialCode>-DEFAULT` SKU. Never update inventory or WMS extension fields.
- [x] **Step 4: Run focused and WMS regression tests; verify GREEN.**
- [x] **Step 5: Commit** with `feat(wms): synchronize ERP material master data`.

### Task 3: Partner and Warehouse Synchronization

**Files:**
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/service/integration/WmsPartnerIntegrationService.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/service/integration/WmsWarehouseIntegrationService.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/service/integration/WmsPartnerIntegrationServiceTest.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/service/integration/WmsWarehouseIntegrationServiceTest.java`
- Modify: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/md/merchant/WmsMerchantMapper.java`
- Modify: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/md/warehouse/WmsWarehouseMapper.java`

**Interfaces:**
- Produces: `upsert(WmsPartnerUpsertReqDTO)` and `upsert(WmsWarehouseUpsertReqDTO)`, both returning `WmsIntegrationAcceptedRespDTO`.

- [ ] **Step 1: Write failing tests** for customer creation, customer-plus-supplier identity merge, stale partner version, generated warehouse code, matching expectedVersion update, mismatched expectedVersion rejection, and extension-field preservation.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement partner mapping** for CUSTOMER, SUPPLIER, and BOTH using the existing merchant enum; update only ERP-owned fields and `sourceVersion`.
- [ ] **Step 4: Implement warehouse mapping** for RAW_MATERIAL and FINISHED_GOODS. Generate a stable unique code when absent, return it, and use one conditional database update on `dataVersion = expectedVersion` before incrementing.
- [ ] **Step 5: Run focused and WMS regression tests; verify GREEN.**
- [ ] **Step 6: Commit** with `feat(wms): synchronize ERP partners and warehouses`.

### Task 4: WMS RPC Facade and Tenant Enforcement

**Files:**
- Create: `txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/api/integration/WmsIntegrationApiImpl.java`
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/api/integration/WmsIntegrationApiImplTest.java`
- Modify: `txgy-module-wms/txgy-module-wms-api/src/main/java/cn/iocoder/txgy/module/wms/enums/ErrorCodeConstants.java`

**Interfaces:**
- Consumes: ledger and three master-data services.
- Produces: implementation of all existing `WmsIntegrationApi` methods.

- [ ] **Step 1: Write failing tests** that the four enabled endpoints route through the ledger, reject mismatched tenant IDs, isolate result lookup by tenant, and reject all seven deferred business endpoints with a dedicated unavailable error.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement the facade.** Read the required tenant from `TenantContextHolder`, compare it to request tenant, wrap each enabled operation with a distinct operation type, and return `CommonResult.success`. Deferred methods throw the unavailable business exception.
- [ ] **Step 4: Run focused and module tests; verify GREEN.**
- [ ] **Step 5: Commit** with `feat(wms): expose ERP master data integration API`.

### Task 5: Database Migration and ERP Warehouse Proxy

**Files:**
- Create: `deploy/mysql/upgrade/20260908-wms-erp-master-data.sql`
- Modify: `txgy-module-erp/txgy-module-erp-server/src/main/java/cn/iocoder/txgy/module/erp/service/stock/ErpWarehouseServiceImpl.java`
- Modify: `txgy-module-erp/txgy-module-erp-server/src/main/java/cn/iocoder/txgy/module/erp/dal/dataobject/stock/ErpWarehouseDO.java`
- Modify: `txgy-module-erp/txgy-module-erp-server/src/main/java/cn/iocoder/txgy/module/erp/controller/admin/stock/vo/warehouse/ErpWarehouseRespVO.java`
- Modify: `txgy-module-erp/txgy-module-erp-server/src/main/java/cn/iocoder/txgy/module/erp/controller/admin/stock/vo/warehouse/ErpWarehouseSaveReqVO.java`
- Create: `txgy-module-erp/txgy-module-erp-server/src/test/java/cn/iocoder/txgy/module/erp/service/stock/ErpWarehouseServiceImplTest.java`
- Modify: `docs/wms-pre-erp-checkpoint-20260908.md` with configuration and migration order.

**Interfaces:**
- Consumes: `ErpWmsIntegrationGateway.upsertWarehouse`.
- Produces: ERP warehouse create/update flow that persists WMS authority code/version and exposes sync failure state.

- [ ] **Step 1: Write failing ERP tests** for create/update request payloads, returned WMS code/version persistence, ERP extension preservation, and remote failure recording without allowing an unsynchronized warehouse into cross-module use.
- [ ] **Step 2: Run focused ERP tests and verify RED.**
- [ ] **Step 3: Implement the smallest proxy integration** using ERP’s existing outbound record and after-commit pattern. Keep local ERP-specific pricing/default fields and persist WMS response metadata.
- [ ] **Step 4: Add the migration** for the WMS request ledger, indexes, ERP sync metadata, and read-only preflight queries that expose duplicate/ambiguous codes before unique constraints are added.
- [ ] **Step 5: Run ERP and WMS focused suites, then compile both modules; verify GREEN.**
- [ ] **Step 6: Commit** with `feat(erp-wms): connect warehouse proxy and migrations`.

### Task 6: Remote-Mode Contract Verification and Delivery Notes

**Files:**
- Create: `txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/api/integration/WmsIntegrationContractTest.java`
- Create: `docs/erp-wms-master-data-delivery.md`

**Interfaces:**
- Verifies the v0.2 request/response contract across ERP gateway and WMS facade.

- [ ] **Step 1: Write an integration contract test** exercising material, partner, warehouse, replay, stale version, tenant mismatch, and request-result lookup through the public API classes with literal DTO values.
- [ ] **Step 2: Run it and verify RED** for any remaining wiring or contract gap.
- [ ] **Step 3: Make only the wiring/configuration changes required for GREEN.**
- [ ] **Step 4: Run all affected ERP/WMS tests and package with tests enabled.** Record exact commands and outcomes in the delivery document.
- [ ] **Step 5: Document remote-mode configuration, migration order, supported endpoints, deferred endpoints, known limitations, and the entry criteria for inventory phase two.**
- [ ] **Step 6: Commit** with `test(erp-wms): verify master data remote contract`.
