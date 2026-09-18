package cn.iocoder.txgy.module.product.service.inquiry;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryDO;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PurchaseInquiryServiceTest {
 private cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO imageRequest(String url) {
  var row=new InquiryDO();row.setId(12L);row.setStatus(0);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setId(12L);req.setDeadline(System.currentTimeMillis()+86400000);
  var item=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO.Item();item.setName("保温板");item.setPicUrl(url);
  req.setItems(java.util.List.of(item));return req;
 }
 @Test void imageIsSavedOnInquiryItem() {
  service.save(7L,imageRequest("https://example.com/material.png"));
  verify(itemMapper).insert(argThat((cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO item)->"https://example.com/material.png".equals(item.getPicUrl())&&item.getInquiryId()==12L));
 }
 @Test void unsafeImageUrlRejected() {
  assertThrows(ServiceException.class,()->service.save(7L,imageRequest("javascript:alert(1)")));
  verify(itemMapper,never()).insert(any(cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO.class));
 }
 @Test void emptyImageSupportsRemoval() {
  service.save(7L,imageRequest(""));
  verify(itemMapper).insert(argThat((cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO item)->"".equals(item.getPicUrl())));
 }
 @Mock InquiryMapper mapper;
 @Mock InquiryItemMapper itemMapper;
 @Mock EnterpriseMembershipService membership;
 @InjectMocks PurchaseInquiryService service;
 @Test void expiredSubmittedInquiryIsClosedForQuotes() {
  var row=new InquiryDO();row.setStatus(1);row.setDeadline(System.currentTimeMillis()-100);
  assertEquals(2,PurchaseInquiryService.effectiveStatus(row));
  row.setStatus(3);assertEquals(3,PurchaseInquiryService.effectiveStatus(row));
 }
 @Test void missingOrForeignInquiryCannotBeRead() {
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
  assertThrows(ServiceException.class,()->service.detail(7L,99L));
  verifyNoInteractions(itemMapper);
 }
 @Test void repeatSubmitDoesNotGenerateAnotherNumber() {
  var row=new InquiryDO();row.setStatus(1);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  service.submit(7L,1L);verify(mapper,never()).updateById(any(InquiryDO.class));
 }
 @Test void expiredDraftCannotSubmit() {
  var row=new InquiryDO();row.setStatus(0);row.setDeadline(System.currentTimeMillis()-100);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.submit(7L,1L));
  verify(mapper,never()).updateById(any(InquiryDO.class));
 }
}
