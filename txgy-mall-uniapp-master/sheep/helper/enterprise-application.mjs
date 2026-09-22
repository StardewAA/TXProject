export const emptyEnterpriseApplication = () => ({ name: '', creditCode: '', licenseData: '', contactName: '', contactMobile: '', address: '', introduction: '' });
export function enterpriseApplicationPayload(form) {
  const data = emptyEnterpriseApplication();
  const limits = { name: 100, creditCode: 18, contactName: 32, contactMobile: 11, address: 255, introduction: 1024 };
  for (const key of Object.keys(limits)) {
    if (typeof form[key] !== 'string' || form[key].trim().length > limits[key]) throw new Error('企业资料格式或长度不正确');
    data[key] = form[key].trim();
  }
  if (!data.name || !/^[0-9A-Z]{18}$/.test(data.creditCode) || !data.contactName || !/^1\d{10}$/.test(data.contactMobile) || !data.address) throw new Error('请完整填写企业名称、信用代码、联系人、手机号和地址');
  if (typeof form.licenseData !== 'string' || !/^data:image\/(png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(form.licenseData) || form.licenseData.length > 1400000) throw new Error('请选择1MB以内PNG或JPEG营业执照');
  data.licenseData = form.licenseData;
  return data; // Identity, tenant, status and enterprise binding are exclusively server-controlled.
}
