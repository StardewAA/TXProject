/**
 * 极简指标注册表（B10）：以 Prometheus 文本格式暴露，零第三方依赖。
 * 只做进程内累计，进程重启归零——对单副本部署足够，多副本应换成集中式指标。
 */

type Labels = Record<string, string>;

interface MetricEntry {
  name: string;
  labels: Labels;
  value: number;
  help: string;
  type: 'counter' | 'gauge';
}

function escapeLabelValue(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n');
}

export class Metrics {
  private readonly values = new Map<string, MetricEntry>();

  constructor(private readonly prefix = 'bid_agent') {}

  private static keyOf(name: string, labels: Labels): string {
    const parts = Object.keys(labels)
      .sort()
      .map((key) => `${key}=${labels[key] ?? ''}`);
    return `${name}|${parts.join('|')}`;
  }

  /** 计数器自增 */
  increment(name: string, labels: Labels = {}, delta = 1, help = ''): void {
    const key = Metrics.keyOf(name, labels);
    const existing = this.values.get(key);
    if (existing) {
      existing.value += delta;
      return;
    }
    this.values.set(key, { name, labels, value: delta, help, type: 'counter' });
  }

  /** 瞬时值（并发数、队列长度等） */
  set(name: string, value: number, labels: Labels = {}, help = ''): void {
    const key = Metrics.keyOf(name, labels);
    this.values.set(key, { name, labels, value, help, type: 'gauge' });
  }

  snapshot(): Array<{ name: string; labels: Labels; value: number }> {
    return [...this.values.values()].map((entry) => ({
      name: `${this.prefix}_${entry.name}`,
      labels: entry.labels,
      value: entry.value,
    }));
  }

  render(): string {
    const lines: string[] = [];
    const declared = new Set<string>();
    for (const entry of this.values.values()) {
      const full = `${this.prefix}_${entry.name}`;
      if (!declared.has(full)) {
        declared.add(full);
        if (entry.help) lines.push(`# HELP ${full} ${entry.help}`);
        lines.push(`# TYPE ${full} ${entry.type}`);
      }
      const labels = Object.keys(entry.labels)
        .sort()
        .map((key) => `${key}="${escapeLabelValue(entry.labels[key] ?? '')}"`)
        .join(',');
      lines.push(labels ? `${full}{${labels}} ${entry.value}` : `${full} ${entry.value}`);
    }
    return `${lines.join('\n')}\n`;
  }
}
