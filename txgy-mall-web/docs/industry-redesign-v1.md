# PC 商城行业视觉第一版

本版范围：PC 前端 `txgy-mall-web`。保留现有会员、支付预览及交易流程改动，后端接口与数据库未修改。

## 已完成

- 深蓝绿色工业首页：材料目录、3 张工业轮播图、后台分类缩略图、工程选材入口、企业店铺、4 类商品专区、采购优惠及客服入口。
- 分类沿用 `/product/category/list`，按父子关系选择分类图片；楼层使用分类编号查询，价格、商品和店铺名称均来自接口。
- 头部支持切换商品/店铺搜索。店铺搜索匹配现有店铺名称与简介；暂不提供尚无数据支持的地区、经营模式和认证筛选。
- `/shops`：企业店铺目录、搜索、分页、每店代表商品及在售数量。每页只请求当前 6 家店铺的商品预览。
- `/shops/:id`：店铺信息、联系方式、店内分类/关键词/价格排序/分页，以及全站搜索入口。
- 商品详情的所属店铺可跳转到店铺主页。
- 店铺标识字段采用后端 `logoUrl`；没有 Logo 时显示店铺简称；没有联系人或电话时显示“暂未公开”。
- 页面区分请求失败和无数据；店内请求使用序号避免快速切换时旧响应覆盖新结果。

## 素材

三张横幅使用内置 imagegen 工具生成，属于通用行业场景示意，不代表任何供应商的实景或具体商品。商品卡片继续使用既有商品图片。

- `public/banners/industry-hero-v2.png`
- `public/banners/industry-insulation-v2.png`
- `public/banners/industry-pipes-v2.png`

生成提示词：

1. Use case: ads-marketing. Generate a polished photorealistic panoramic banner for a Chinese industrial corrosion-protection and thermal-insulation materials marketplace. 2048x1152 landscape image. Scene: clean contemporary industrial pipe installation, brushed aluminium insulated pipes with large sweeping elbows, neat silver cylindrical storage tanks in background, subtle teal painted steel structural components, natural daylight, crisp realistic material textures. Composition: hero for website with text on left, reserve left 45 percent as quiet pale blue grey sky and softly blurred industrial background, prominent pipes and storage tanks on right and lower right. Premium restrained architectural photography, blue green palette with silver metals, elegant clean realistic depth, no people, no logos, NO text, NO letters, NO watermark. This is a generic industry illustration, not a depiction of any named business.
2. Use case: ads-marketing. Create a photorealistic premium wide landscape industrial thermal insulation materials website banner. No text or logos anywhere. Off-white ceramic fibre blanket rolls, stacked white insulation panels, a section of yellow mineral wool pipe insulation with foil cladding arranged elegantly on a pale grey industrial worktop on the RIGHT half. A light-filled clean factory interior softly blurred in background. Left 45 percent clean pale grey green negative space for website title, natural soft side lighting, realistic fibrous materials and clean edges, restrained blue-green and white palette. No people, no certification badges, no watermark, no company logos. Generic material scene illustration, not a specific product claim. Landscape 2048x1152.
3. Use case: ads-marketing. Photorealistic polished wide landscape hero banner for industrial pipe supply marketplace, no text or logos. On RIGHT half: neatly stacked dark coated steel pipes with visible circular openings, one large brushed stainless steel elbow and an industrial flange with realistic bolt holes arranged in a clean industrial warehouse. Cool blue grey environment, side lighting, restrained dark teal metal structure, credible physical pipe geometry, real metal texture. LEFT 45 percent largely empty softly lit pale grey blue warehouse wall for website heading overlay. No humans, no words, no watermark, no branding, generic industrial scene. Landscape 2048x1152.

## 验收入口

1. 开发预览：`http://localhost:5175/`，后端使用当前 Docker 网关 `48080`。
2. 首页选择“企业店铺”，搜索“科盾”，进入店铺查看商品。
3. 输入不存在的商品名称，确认显示空结果；清空后恢复商品。
4. 点击商品进入详情，再点击所属店铺回到店铺。
5. 切换材料分类和价格排序，确认请求包含当前 `shopId`。
6. 检查 1366、1440、1920 三种屏幕宽度；确认首屏及店铺内容不横向溢出。
7. 正式构建：`pnpm build`（包含类型检查）。Docker 15175 的部署独立于本地预览，需构建新版 PC 镜像后才会更新。

## 后续可完善

企业地区、经营模式、资质认证及企业级搜索，需要公开接口提供相应字段后再展示。目前不生成虚构企业认证、联系方式或销量。

本版仅回归新增浏览入口及布局，不代表真实短信、支付、WMS 已联调完成。
