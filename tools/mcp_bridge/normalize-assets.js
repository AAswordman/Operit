#!/usr/bin/env node
/** 清理桥接资源的行尾空白并统一末尾换行，保持生成资源的差异稳定。 */
const fs = require('fs');
const path = require('path');

const assets = [
    path.resolve(__dirname, '../../app/src/main/assets/bridge/index.js'),
    path.resolve(__dirname, '../../app/src/main/assets/bridge/spawn-helper.js')
];

for (const asset of assets) {
    const normalized = fs
        .readFileSync(asset, 'utf8')
        .split(/\r?\n/)
        .map((line) => line.replace(/[ \t]+$/, ''))
        .join('\n')
        .replace(/\n*$/, '\n');
    fs.writeFileSync(asset, normalized);
    console.log(`Normalized ${asset}`);
}
