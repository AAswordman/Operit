import fs from "node:fs";
import path from "node:path";

const rootDir = process.cwd();
const outputPath = path.join(rootDir, "dist", "toolpkg_wasm_demo.toolpkg");
const entries = [
  { source: "manifest.json", name: "manifest.json" },
  { source: "build/main.js", name: "main.js" },
  { source: "modules/core.wasm", name: "modules/core.wasm" }
];

const crcTable = new Uint32Array(256);
for (let index = 0; index < crcTable.length; index += 1) {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) {
    value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  crcTable[index] = value >>> 0;
}

function crc32(data) {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function u16(value) {
  const buffer = Buffer.allocUnsafe(2);
  buffer.writeUInt16LE(value);
  return buffer;
}

function u32(value) {
  const buffer = Buffer.allocUnsafe(4);
  buffer.writeUInt32LE(value);
  return buffer;
}

function checkedEntryData(entry) {
  const entryPath = path.join(rootDir, ...entry.source.split("/"));
  if (!fs.existsSync(entryPath)) {
    throw new Error(`Missing package source: ${entry.source}`);
  }
  const stat = fs.statSync(entryPath);
  if (!stat.isFile()) {
    throw new Error(`Package source is not a file: ${entry.source}`);
  }
  return fs.readFileSync(entryPath);
}

function localFileHeader(fileName, data) {
  const name = Buffer.from(fileName, "utf8");
  const checksum = crc32(data);
  return Buffer.concat([
    u32(0x04034b50),
    u16(20),
    u16(0),
    u16(0),
    u16(0),
    u16(0),
    u32(checksum),
    u32(data.length),
    u32(data.length),
    u16(name.length),
    u16(0),
    name
  ]);
}

function centralDirectoryHeader(fileName, data, localHeaderOffset) {
  const name = Buffer.from(fileName, "utf8");
  const checksum = crc32(data);
  return Buffer.concat([
    u32(0x02014b50),
    u16(20),
    u16(20),
    u16(0),
    u16(0),
    u16(0),
    u16(0),
    u32(checksum),
    u32(data.length),
    u32(data.length),
    u16(name.length),
    u16(0),
    u16(0),
    u16(0),
    u16(0),
    u32(0),
    u32(localHeaderOffset),
    name
  ]);
}

function endOfCentralDirectory(entryCount, centralDirectorySize, centralDirectoryOffset) {
  return Buffer.concat([
    u32(0x06054b50),
    u16(0),
    u16(0),
    u16(entryCount),
    u16(entryCount),
    u32(centralDirectorySize),
    u32(centralDirectoryOffset),
    u16(0)
  ]);
}

function buildToolPkg() {
  const fileRecords = [];
  const localParts = [];
  let offset = 0;

  for (const entry of entries) {
    const data = checkedEntryData(entry);
    const header = localFileHeader(entry.name, data);
    fileRecords.push({ entryName: entry.name, data, offset });
    localParts.push(header, data);
    offset += header.length + data.length;
  }

  const centralParts = fileRecords.map((record) =>
    centralDirectoryHeader(record.entryName, record.data, record.offset)
  );
  const centralDirectory = Buffer.concat(centralParts);
  const end = endOfCentralDirectory(fileRecords.length, centralDirectory.length, offset);

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, Buffer.concat([...localParts, centralDirectory, end]));
  console.log(`Packed ${path.relative(rootDir, outputPath).replace(/\\/g, "/")}`);
}

buildToolPkg();
