package cn.iocoder.txgy.module.product.service.inquiry;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryExcelController;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class InquiryExcelTest {
 private InquiryExcelController.ExcelRow row(String code,String quantity){
  var r=new InquiryExcelController.ExcelRow();r.setName("保温板");r.setCode(code);r.setQuantity(quantity);r.setCategory("保温材料");r.setUnit("块");return r;
 }
 private byte[] workbook(List<InquiryExcelController.ExcelRow> rows){
  var bytes=new ByteArrayOutputStream();FastExcelFactory.write(bytes,InquiryExcelController.ExcelRow.class).sheet("采购商品").doWrite(rows);return bytes.toByteArray();
 }
 private List<InquiryExcelController.Preview> preview(String name,byte[] bytes)throws Exception{
  try(var security=Mockito.mockStatic(SecurityFrameworkUtils.class)){
   security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser().setId(7L).setUserType(1));
   return controller.preview(new MockMultipartFile("file",name,"application/octet-stream",bytes)).getCheckedData();
  }
 }
 @Test void rejectsWrongExtensionEmptyOversizedAndCorruptFiles(){
  for(var name:List.of("test.xls","test.xlsm","test.txt"))assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,()->preview(name,new byte[]{1}));
  for(var bytes:List.of(new byte[0],new byte[2097153],new byte[]{1,2,3}))assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,()->preview("test.xlsx",bytes));
 }
 @Test void rejectsEmptyAndOver200Rows(){
  assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,()->preview("empty.xlsx",workbook(List.of())));
  assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,()->preview("large.xlsx",workbook(java.util.Collections.nCopies(201,row(null,"1")))));
 }
 @Test void acceptsExactly200RowsAndReportsActualLineNumbers()throws Exception{
  var result=preview("valid.xlsx",workbook(java.util.Collections.nCopies(200,row(null,"1"))));
  assertEquals(200,result.size());assertEquals(2,result.getFirst().rowNumber());assertEquals(201,result.getLast().rowNumber());
 }
 @Test void rejectsMissingDuplicateAndUnknownHeaders(){
  for(var headers:List.of(List.of("商品名称","采购数量"),List.of("商品名称","商品编码","分类","规格型号","采购数量","单位","商品名称"),List.of("商品名称","商品编码","分类","规格型号","采购数量","单位","额外列"))){
   var bytes=new ByteArrayOutputStream();FastExcelFactory.write(bytes).head(headers.stream().map(List::of).toList()).sheet().doWrite(List.of(java.util.Collections.nCopies(headers.size(),"测试")));
   var error=assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,()->preview("bad-header.xlsx",bytes.toByteArray()));assertTrue(error.getMessage().contains("模板表头"));
  }
 }
 @Test void reportsEveryOversizedFieldBeforeMatching()throws Exception{
  var r=row("1".repeat(101),"1");r.setName("名".repeat(201));r.setCategory("类".repeat(101));r.setSpecification("规".repeat(501));r.setUnit("米".repeat(21));r.setRemark("备".repeat(501));
  assertEquals(6,preview("length.xlsx",workbook(List.of(r))).getFirst().errors().size());verifyNoInteractions(skuMapper,spuMapper);
 }
 @Test void rejectsInvalidQuantitiesAndAcceptsBoundary()throws Exception{
  var rows=List.of("0","-1","0.0001","1000000000","not-number","1".repeat(65),"999999999.999"," 1.125 ").stream().map(q->row(null,q)).toList();
  var result=preview("quantity.xlsx",workbook(rows));for(int i=0;i<6;i++)assertFalse(result.get(i).errors().isEmpty());assertTrue(result.get(6).errors().isEmpty());assertTrue(result.get(7).errors().isEmpty());
 }
 @Test void duplicatesUseCategoryAndTrimWithoutDelimiterCollision()throws Exception{
  var first=row(null,"1");var same=row(null,"2");same.setName(" 保温板 ");var other=row(null,"1");other.setCategory("其他");
  var result=preview("duplicates.xlsx",workbook(List.of(first,same,other)));assertTrue(result.get(1).warnings().stream().anyMatch(s->s.contains("重复")));assertTrue(result.get(2).warnings().isEmpty());
 }
 @Test void duplicateSkuBarcodeDoesNotBind()throws Exception{
  var a=new cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO().setId(1L).setSpuId(1L);
  var b=new cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO().setId(2L).setSpuId(1L);
  when(skuMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(a,b));
  when(spuMapper.selectById(1L)).thenReturn(new cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO().setStatus(1));
  var result=preview("ambiguous.xlsx",workbook(List.of(row("000001","1")))).getFirst();assertNull(result.skuId());assertTrue(result.warnings().stream().anyMatch(s->s.contains("多个SKU")));
 }
 @Mock EnterpriseMembershipService membership;
 @Mock ProductSkuMapper skuMapper;
 @Mock ProductSpuMapper spuMapper;
 @InjectMocks InquiryExcelController controller;
 @Test void importPreservesTextCodesAndReportsInvalidQuantity() throws Exception {
  // 浏览器专项复用真实工作簿；仅生成到构建产物目录，不写源文件。
  var fixtureDir=java.nio.file.Path.of("target","inquiry-qa");java.nio.file.Files.createDirectories(fixtureDir);
  java.nio.file.Files.write(fixtureDir.resolve("valid.xlsx"),workbook(List.of(row("0000099999998765","1.125"),row("0000099999998765","2"))));
  java.nio.file.Files.write(fixtureDir.resolve("invalid-quantity.xlsx"),workbook(List.of(row(null,"-1"))));
  var wrongHead=new ByteArrayOutputStream();FastExcelFactory.write(wrongHead).head(List.of(List.of("错误表头"))).sheet().doWrite(List.of(List.of("内容")));
  java.nio.file.Files.write(fixtureDir.resolve("invalid-header.xlsx"),wrongHead.toByteArray());
  var good=new InquiryExcelController.ExcelRow();good.setName("测试保温材料");good.setCode("000001");good.setCategory("保温材料");good.setUnit("米");good.setQuantity("1.125");
  var bad=new InquiryExcelController.ExcelRow();bad.setName("测试无效材料");bad.setCategory("保温材料");bad.setUnit("米");bad.setQuantity("-1");
  var bytes=new ByteArrayOutputStream();
  FastExcelFactory.write(bytes,InquiryExcelController.ExcelRow.class).sheet("采购商品").doWrite(List.of(good,bad));
  try(var security=Mockito.mockStatic(SecurityFrameworkUtils.class)) {
   security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser().setId(7L).setUserType(1));
   var result=controller.preview(new MockMultipartFile("file","test.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray())).getCheckedData();
   assertEquals(2,result.size());assertEquals("000001",result.get(0).item().getCode());
   assertTrue(result.get(0).errors().isEmpty());assertFalse(result.get(1).errors().isEmpty());
  }
 }
}
