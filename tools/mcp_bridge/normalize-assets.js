#!/usr/bin/env node
/**
 * Normalizes the generated bridge bundles copied into Android assets.
 *
 * ncc copies dependency sources verbatim, and some of them contain trailing spaces. A
 * regenerated bundle would otherwise introduce whitespace errors in `git diff --check` and
 * differ from the surrounding repository style. This also guarantees a final newline.
 */
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
