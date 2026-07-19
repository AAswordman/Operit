const fs = require('fs');
const path = require('path');

function extractMetadataBanner(entryFilePath) {
    if (!entryFilePath) return '';
    if (!fs.existsSync(entryFilePath)) return '';

    const src = fs.readFileSync(entryFilePath, 'utf8');
    const match = src.match(/\/\*\s*METADATA[\s\S]*?\*\//);
    return match ? `${match[0]}\n` : '';
}

function resolveRepoRoot() {
    return path.resolve(__dirname, '..', '..');
}

function listTsSourceFiles(dir) {
    const result = [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });

    for (const ent of entries) {
        if (ent.name === 'node_modules') continue;
        if (ent.name.startsWith('.')) continue;

        const full = path.join(dir, ent.name);
        if (ent.isDirectory()) {
            result.push(...listTsSourceFiles(full));
            continue;
        }

        if (!ent.isFile()) continue;
        if (!ent.name.toLowerCase().endsWith('.ts')) continue;
        if (ent.name.toLowerCase().endsWith('.d.ts')) continue;

        result.push(full);
    }

    return result;
}

function toModulePath(githubDir, filePath) {
    const rel = path.relative(githubDir, filePath);
    const withoutExt = rel.replace(/\.ts$/i, '');
    const posix = withoutExt.split(path.sep).join('/');
    return posix.startsWith('.') ? posix : `./${posix}`;
}

async function main() {
    let esbuild;
    try {
        esbuild = require('esbuild');
    } catch (e) {
        throw new Error('Missing devDependency "esbuild". Install it first: npm i -D esbuild');
    }

    const repoRoot = resolveRepoRoot();
    const examplesDir = path.join(repoRoot, 'examples');
    const githubDir = path.join(examplesDir, 'github');

    const tsconfig = path.join(githubDir, 'tsconfig.json');
    const outfile = path.join(examplesDir, 'github.js');

    const preferredEntry = path.join(githubDir, 'src', 'index.ts');
    const legacyEntry = path.join(githubDir, 'index.ts');
    const tsFiles = listTsSourceFiles(githubDir);

    if (fs.existsSync(preferredEntry)) {
        const metadataBanner = extractMetadataBanner(preferredEntry);
        await esbuild.build({
            absWorkingDir: githubDir,
            entryPoints: [preferredEntry],
            bundle: true,
            format: 'cjs',
            platform: 'neutral',
            target: ['es2017'],
            outfile,
            banner: { js: metadataBanner },
            tsconfig,
            logLevel: 'info'
        });
        return;
    }

    if (fs.existsSync(legacyEntry)) {
        const metadataBanner = extractMetadataBanner(legacyEntry);
        await esbuild.build({
            absWorkingDir: githubDir,
            entryPoints: [legacyEntry],
            bundle: true,
            format: 'cjs',
            platform: 'neutral',
            target: ['es2017'],
            outfile,
            banner: { js: metadataBanner },
            tsconfig,
            logLevel: 'info'
        });
        return;
    }

    if (tsFiles.length === 0) {
        throw new Error('No .ts files found in examples/github. Create at least one TypeScript file.');
    }

    if (tsFiles.length === 1) {
        const metadataBanner = extractMetadataBanner(tsFiles[0]);
        await esbuild.build({
            absWorkingDir: githubDir,
            entryPoints: [tsFiles[0]],
            bundle: true,
            format: 'cjs',
            platform: 'neutral',
            target: ['es2017'],
            outfile,
            banner: { js: metadataBanner },
            tsconfig,
            logLevel: 'info'
        });
        return;
    }

    const contents = tsFiles
        .map((filePath) => {
            const modulePath = toModulePath(githubDir, filePath);
            return `Object.assign(exports, require(${JSON.stringify(modulePath)}));`;
        })
        .join('\n');

    const metadataBanner = extractMetadataBanner(tsFiles.find((p) => fs.readFileSync(p, 'utf8').includes('/* METADATA')) || tsFiles[0]);

    await esbuild.build({
        absWorkingDir: githubDir,
        stdin: {
            contents,
            resolveDir: githubDir,
            sourcefile: 'github-bundle-entry.ts',
            loader: 'ts'
        },
        bundle: true,
        format: 'cjs',
        platform: 'neutral',
        target: ['es2017'],
        outfile,
        banner: { js: metadataBanner },
        tsconfig,
        logLevel: 'info'
    });
}

main().catch((err) => {
    console.error(err && err.stack ? err.stack : String(err));
    process.exitCode = 1;
});
