import zlib from 'node:zlib';

/**
 * 极简 ZIP 打包器（仅 Store 无压缩，method=0），
 * 用于零依赖生成 docx（OOXML 包）。utf8 文件名按通用做法以 ascii 部件名组织。
 * 同时提供 unzip 以读取外部 docx（招标方/标书模板），支持 method=0（Store）与 method=8（Deflate）。
 */
export interface ZipEntry {
  name: string;
  data: Buffer;
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

export function crc32(buf: Buffer): number {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc = CRC_TABLE[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

export function zipStore(entries: ZipEntry[]): Buffer {
  const chunks: Buffer[] = [];
  const central: Buffer[] = [];
  let offset = 0;
  const DOS_EPOCH = 0x21; // 1980-01-01 时间戳精简
  for (const entry of entries) {
    const name = Buffer.from(entry.name, 'utf8');
    const crc = crc32(entry.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); // PK\x03\x04
    local.writeUInt16LE(20, 4); // version needed
    local.writeUInt16LE(0x0800, 6); // flags: UTF-8
    local.writeUInt16LE(0, 8); // method store
    local.writeUInt16LE(DOS_EPOCH, 10); // time
    local.writeUInt16LE(DOS_EPOCH, 12); // date
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(entry.data.length, 18);
    local.writeUInt32LE(entry.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    chunks.push(local, name, entry.data);

    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0); // PK\x01\x02
    cen.writeUInt16LE(20, 4); // version made by
    cen.writeUInt16LE(20, 6); // version needed
    cen.writeUInt16LE(0x0800, 8);
    cen.writeUInt16LE(0, 10);
    cen.writeUInt16LE(DOS_EPOCH, 12);
    cen.writeUInt16LE(DOS_EPOCH, 14);
    cen.writeUInt32LE(crc, 16);
    cen.writeUInt32LE(entry.data.length, 20);
    cen.writeUInt32LE(entry.data.length, 24);
    cen.writeUInt16LE(name.length, 28);
    cen.writeUInt32LE(offset, 42);
    central.push(cen, name);
    offset += local.length + name.length + entry.data.length;
  }
  const centralStart = offset;
  const centralData = Buffer.concat(central);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); // PK\x05\x06
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralData.length, 12);
  eocd.writeUInt32LE(centralStart, 16);
  return Buffer.concat([...chunks, centralData, eocd]);
}

/**
 * 读取外部 .docx（ZIP 包）的全部条目。
 * 仅支持 method=0（Store，直接透传）与 method=8（Deflate，zlib.inflateRawSync）；
 * 其它压缩方式（method=12 bzip2 等）在 docx 中不常见，直接报错避免静默产出损坏文件。
 */
export function unzip(buffer: Buffer): ZipEntry[] {
  const EOCD_SIG = 0x06054b50;
  let eocdOffset = -1;
  const searchFloor = Math.max(0, buffer.length - 22 - 65_535);
  for (let i = buffer.length - 22; i >= searchFloor; i -= 1) {
    if (buffer.readUInt32LE(i) === EOCD_SIG) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) throw new Error('不是有效的 zip/docx 包（缺少 EOCD）');
  const entryCount = buffer.readUInt16LE(eocdOffset + 10);
  let cursor = buffer.readUInt32LE(eocdOffset + 16);

  const entries: ZipEntry[] = [];
  for (let i = 0; i < entryCount; i += 1) {
    if (buffer.readUInt32LE(cursor) !== 0x02014b50) throw new Error('zip 中央目录损坏');
    const method = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const nameLen = buffer.readUInt16LE(cursor + 28);
    const extraLen = buffer.readUInt16LE(cursor + 30);
    const commentLen = buffer.readUInt16LE(cursor + 32);
    const localOffset = buffer.readUInt32LE(cursor + 42);
    const name = buffer.toString('utf8', cursor + 46, cursor + 46 + nameLen);
    cursor += 46 + nameLen + extraLen + commentLen;

    if (buffer.readUInt32LE(localOffset) !== 0x04034b50) throw new Error(`zip 本地头损坏：${name}`);
    const lNameLen = buffer.readUInt16LE(localOffset + 26);
    const lExtraLen = buffer.readUInt16LE(localOffset + 28);
    const dataStart = localOffset + 30 + lNameLen + lExtraLen;
    const raw = buffer.subarray(dataStart, dataStart + compressedSize);
    const data =
      method === 0
        ? Buffer.from(raw)
        : method === 8
          ? zlib.inflateRawSync(raw)
          : (() => {
              throw new Error(`不支持的 zip 压缩方式 method=${method}（条目 ${name}）`);
            })();
    entries.push({ name, data });
  }
  return entries;
}
