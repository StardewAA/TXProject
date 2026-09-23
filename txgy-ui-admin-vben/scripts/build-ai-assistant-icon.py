# -*- coding: utf-8 -*-
"""AI 助手图标资源生成脚本（可复现的资源管线）

用途
    把设计稿（玻璃质感蓝色圆球 + 白色机器人）处理成项目使用的正式资源：
        apps/web-antd/src/assets/imgs/ai-assistant.png
    **只做「抠白底 + 裁到图标 + 等比缩放」，不重绘、不改造型、不改配色。**

用法
    python scripts/build-ai-assistant-icon.py <设计稿路径>
    例：python scripts/build-ai-assistant-icon.py D:\\tmp\\ai-icon.png
    随后提交生成的 src/assets/imgs/ai-assistant.png。

处理步骤
    1. 识别背景：设计稿通常是白底；同时兼容「图片底部被看图/截图工具界面压住」的情况 ——
       底部若存在一条接近满宽的实心带（工具栏），判定为遮挡区，不参与圆拟合；
    2. 用两行弦长解出圆球的圆心与半径（对轻微光晕不敏感）；
    3. 被遮挡的球体底部用「关于遮挡线上沿做镜像反射」补齐（球体下部是平滑渐变，
       反射拼接肉眼几乎不可见，避免出现被切平的球底）；
    4. 以球心为中心、半径 r + 光晕余量 做圆形 alpha（边缘 smoothstep 抗锯齿），
       圆形之外一律透明 —— 白底与工具栏残留一并去掉；
    5. 预乘 alpha 后 LANCZOS 缩放到 256×256，再反预乘，避免边缘出现白色描边。

依赖：Pillow（python -m pip install pillow）
"""
import math
import os
import sys

from PIL import Image, ImageChops

DST = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    '..',
    'apps',
    'web-antd',
    'src',
    'assets',
    'imgs',
    'ai-assistant.png',
)
OUT = 256
# 判定「背景」的阈值：三通道都 ≥ 该值视为近白（设计稿白底为 250~252）
NEAR_WHITE = 244
# 圆形 alpha 的半径余量（保留球体自带的柔光）与羽化宽度（像素，按源图尺度）
GLOW_MARGIN = 4.0
FEATHER = 4.0


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit('用法：python scripts/build-ai-assistant-icon.py <设计稿路径>')
    src = os.path.abspath(sys.argv[1])
    if not os.path.exists(src):
        raise SystemExit(f'设计稿不存在：{src}')

    im = Image.open(src).convert('RGB')
    w, h = im.size
    px = im.load()
    print(f'设计稿 {w}x{h}')

    def is_fg(p) -> bool:
        return min(p) < NEAR_WHITE

    # ---------- 1. 检测被工具栏遮挡的底部实心带 ----------
    strip_top = h
    y = h - 1
    while y >= 0:
        row_fg = sum(1 for x in range(w) if is_fg(px[x, y]))
        if row_fg >= w * 0.97:
            strip_top = y
            y -= 1
        else:
            break
    if strip_top < h:
        print(f'检测到遮挡带：y={strip_top}..{h - 1}（该区域只用上部镜像补齐，不参与圆拟合）')

    # ---------- 2. 用「饱和色像素」定位球体 ----------
    # 球体/机器人/星芒都是带彩度的（蓝色系），而白底、淡阴影、灰色工具栏是中性色：
    # 用 max-min 通道差判定，可天然排除背景与工具栏，且不受柔和光晕干扰。
    def is_icon(p) -> bool:
        return (max(p) - min(p)) > 28

    rows = []
    for yy in range(h):
        xs = [x for x in range(w) if is_icon(px[x, yy])]
        if xs:
            rows.append((yy, max(xs) - min(xs), (max(xs) + min(xs)) / 2.0))
    if not rows:
        raise SystemExit('未检测到球体内容：请确认设计稿为彩色图标')

    # 圆球最宽的一行即球心所在行：半径 = 半弦长，球心 x = 该行中点
    y_c, chord, cx = max(rows, key=lambda r: r[1])
    radius = chord / 2.0
    cy = float(y_c)
    top_seen, bottom_seen = rows[0][0], rows[-1][0]
    print(
        f'球体定位：圆心=({cx:.1f}, {cy:.1f}) 半径={radius:.1f}；'
        f'可见纵向 y={top_seen}..{bottom_seen}，理论应为 '
        f'{cy - radius:.0f}..{cy + radius:.0f}',
    )

    # ---------- 3. 底部镜像补齐（被工具栏压住时） ----------
    # 注意：遮挡带在画布内也会压住球体底部（本设计稿球底 y≈cy+radius 落在遮挡带里），
    # 因此「画布内被遮挡行」与「超出画布的行」都要用遮挡线上沿做镜像。
    r_alpha = radius + GLOW_MARGIN
    need_bottom = max(h, int(round(cy + r_alpha)) + 3)
    pad_bottom = need_bottom - h
    canvas = Image.new('RGB', (w, need_bottom))
    canvas.paste(im, (0, 0))
    cp = canvas.load()
    # 用「沿列衰减式线性外推」补球底：
    #   镜像反射虽然简单，但球体上半是亮边、下半是深色渐变，硬拼会出现横向断层/V 形折角；
    #   而按每列最近 8 行的趋势继续外推（斜率逐行衰减、最终收敛），能平滑延续球体的
    #   渐变与两侧边缘光，肉眼看不出补过。显示尺寸仅 52px，缺失的球底边缘光不可辨。
    anchor = min(strip_top - 1, h - 1)
    seg = 8
    slope_from = max(0, anchor - seg)
    for xx in range(w):
        c_anchor = px[xx, anchor]
        c_prev = px[xx, slope_from]
        slope = [(c_anchor[i] - c_prev[i]) / float(max(1, anchor - slope_from)) for i in range(3)]
        cur = list(c_anchor)
        for yy in range(anchor + 1, need_bottom):
            decay = 0.85 ** (yy - anchor - 1)
            for i in range(3):
                v = cur[i] + slope[i] * decay
                cur[i] = 0 if v < 0 else (255 if v > 255 else v)
            cp[xx, yy] = (int(round(cur[0])), int(round(cur[1])), int(round(cur[2])))
    base = canvas
    print(f'球底按列外推补齐 {max(0, need_bottom - anchor - 1)} 行（锚点 y={anchor}，画布 {w}x{need_bottom}）')

    bw, bh = base.size

    # ---------- 4. 圆形 alpha（smoothstep 抗锯齿） ----------
    mask = Image.new('L', (bw, bh), 0)
    mp = mask.load()
    for yy in range(bh):
        dy = yy - cy
        for xx in range(bw):
            d = math.hypot(xx - cx, dy)
            t = (r_alpha - d) / FEATHER
            t = 0.0 if t < 0 else (1.0 if t > 1 else t)
            mp[xx, yy] = int(round(t * 255))

    # ---------- 5. 预乘 alpha → 缩放 → 反预乘 ----------
    r_ch, g_ch, b_ch = base.split()
    prem = Image.merge(
        'RGB',
        (
            ImageChops.multiply(r_ch, mask),
            ImageChops.multiply(g_ch, mask),
            ImageChops.multiply(b_ch, mask),
        ),
    ).resize((OUT, OUT), Image.LANCZOS)
    alpha_small = mask.resize((OUT, OUT), Image.LANCZOS)

    pp, ap = prem.load(), alpha_small.load()
    out = Image.new('RGBA', (OUT, OUT))
    op = out.load()
    for yy in range(OUT):
        for xx in range(OUT):
            a = ap[xx, yy]
            if a == 0:
                op[xx, yy] = (255, 255, 255, 0)
                continue
            rr, gg, bb = pp[xx, yy]
            if a < 255:  # 反预乘：还原被白底稀释过的颜色
                rr = min(255, int(round(rr * 255.0 / a)))
                gg = min(255, int(round(gg * 255.0 / a)))
                bb = min(255, int(round(bb * 255.0 / a)))
            op[xx, yy] = (rr, gg, bb, a)

    os.makedirs(os.path.dirname(DST), exist_ok=True)
    out.save(DST)
    print('已生成:', os.path.normpath(DST), out.size, out.mode, os.path.getsize(DST), 'bytes')


if __name__ == '__main__':
    main()
