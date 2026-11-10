import { spawnSync } from 'node:child_process';
import { readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateSync } from 'node:zlib';
import sharp from 'sharp';

export const maxIconDimension = 150;
export const maxResourceSize = 24 * 1024;
const oxipngArgs = ['-o', '2', '--strip', 'safe', '--nc', '--nb', '--stdout', '-'];
const oxipngTimeout = 30 * 1000;
const fail = (message) => { throw new Error(message); };

const crcTable = Array.from({ length: 256 }, (_, value) => {
    let result = value;
    for (let bit = 0; bit < 8; bit++) result = result & 1 ? 0xEDB88320 ^ (result >>> 1) : result >>> 1;
    return result >>> 0;
});

const crc32 = (bytes) => {
    let crc = 0xFFFFFFFF;
    for (const byte of bytes) crc = crcTable[(crc ^ byte) & 0xFF] ^ (crc >>> 8);
    return (crc ^ 0xFFFFFFFF) >>> 0;
};
const readUint32 = (bytes, offset) => bytes.readUInt32BE(offset);
const paeth = (left, above, upperLeft) => {
    const prediction = left + above - upperLeft;
    const leftDistance = Math.abs(prediction - left);
    const aboveDistance = Math.abs(prediction - above);
    const upperLeftDistance = Math.abs(prediction - upperLeft);
    if (leftDistance <= aboveDistance && leftDistance <= upperLeftDistance) return left;
    return aboveDistance <= upperLeftDistance ? above : upperLeft;
};

/** Validate PNG structure and monochrome pixels, returning decoded RGBA data. */
export const validatePng = (bytes) => {
    const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
    if (bytes.length < 33 || !bytes.subarray(0, 8).equals(signature)) fail('PNG signature is invalid.');
    const chunks = [];
    let offset = 8;
    let reachedEnd = false;
    while (offset + 12 <= bytes.length) {
        const length = readUint32(bytes, offset);
        const typeOffset = offset + 4;
        const dataOffset = typeOffset + 4;
        const crcOffset = dataOffset + length;
        if (crcOffset + 4 > bytes.length) fail('PNG chunk length is invalid.');
        const typeBytes = bytes.subarray(typeOffset, dataOffset);
        const type = typeBytes.toString('ascii');
        const data = bytes.subarray(dataOffset, crcOffset);
        const expectedCrc = readUint32(bytes, crcOffset);
        if (crc32(Buffer.concat([typeBytes, data])) !== expectedCrc) fail(`PNG ${type} chunk CRC is invalid.`);
        chunks.push({ data, type });
        offset = crcOffset + 4;
        if (type === 'IEND') {
            reachedEnd = true;
            break;
        }
    }
    if (!reachedEnd || offset !== bytes.length || chunks[0]?.type !== 'IHDR' || chunks[0].data.length !== 13)
        fail('PNG chunk structure is invalid.');
    const header = chunks[0].data;
    const width = readUint32(header, 0);
    const height = readUint32(header, 4);
    const bitDepth = header[8];
    const colorType = header[9];
    if (!width || !height) fail('PNG dimensions are invalid.');
    if (width > maxIconDimension || height > maxIconDimension)
        fail(`Icon size exceeds ${maxIconDimension} px.`);
    if (bitDepth !== 8) fail('Only 8-bit PNG resources are supported.');
    if (![0, 2, 3, 4, 6].includes(colorType)) fail('PNG color type is unsupported.');
    if (header[10] !== 0 || header[11] !== 0 || header[12] !== 0) fail('PNG compression, filtering, or interlace mode is unsupported.');
    const channelCounts = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };
    const channels = channelCounts[colorType];
    const rowBytes = width * channels;
    const compressed = Buffer.concat(chunks.filter(({ type }) => type === 'IDAT').map(({ data }) => data));
    if (!compressed.length) fail('PNG has no image data.');
    let inflated;
    try {
        inflated = inflateSync(compressed, { maxOutputLength: (rowBytes + 1) * height });
    } catch {
        fail('PNG image data cannot be decompressed.');
    }
    if (inflated.length !== (rowBytes + 1) * height) fail('PNG decompressed data length is invalid.');
    const pixels = Buffer.alloc(rowBytes * height);
    for (let row = 0; row < height; row++) {
        const sourceOffset = row * (rowBytes + 1);
        const destinationOffset = row * rowBytes;
        const filter = inflated[sourceOffset];
        if (filter > 4) fail('PNG uses an invalid scanline filter.');
        for (let column = 0; column < rowBytes; column++) {
            const raw = inflated[sourceOffset + 1 + column];
            const left = column >= channels ? pixels[destinationOffset + column - channels] : 0;
            const above = row ? pixels[destinationOffset - rowBytes + column] : 0;
            const upperLeft = row && column >= channels ? pixels[destinationOffset - rowBytes + column - channels] : 0;
            const reconstructed = filter === 0 ? raw
                : filter === 1 ? raw + left
                    : filter === 2 ? raw + above
                        : filter === 3 ? raw + Math.floor((left + above) / 2)
                            : raw + paeth(left, above, upperLeft);
            pixels[destinationOffset + column] = reconstructed & 0xFF;
        }
    }
    const palette = chunks.find(({ type }) => type === 'PLTE')?.data;
    const transparency = chunks.find(({ type }) => type === 'tRNS')?.data;
    if (colorType === 3 && (!palette || palette.length % 3 || palette.length > 768))
        fail('PNG palette is invalid.');
    const data = Buffer.alloc(width * height * 4);
    let visible = false;
    let transparent = false;
    for (let pixel = 0; pixel < width * height; pixel++) {
        const index = pixel * channels;
        let red;
        let green;
        let blue;
        let alpha = 255;
        if (colorType === 0) {
            red = green = blue = pixels[index];
            if (transparency?.length === 2 && transparency.readUInt16BE(0) === pixels[index]) alpha = 0;
        } else if (colorType === 2) {
            [red, green, blue] = pixels.subarray(index, index + 3);
            if (transparency?.length === 6 && transparency.readUInt16BE(0) === red &&
                transparency.readUInt16BE(2) === green && transparency.readUInt16BE(4) === blue) alpha = 0;
        } else if (colorType === 3) {
            const paletteIndex = pixels[index];
            if (paletteIndex * 3 + 2 >= palette.length) fail('PNG palette index is invalid.');
            red = palette[paletteIndex * 3];
            green = palette[paletteIndex * 3 + 1];
            blue = palette[paletteIndex * 3 + 2];
            alpha = transparency?.[paletteIndex] ?? 255;
        } else if (colorType === 4) {
            red = green = blue = pixels[index];
            alpha = pixels[index + 1];
        } else {
            [red, green, blue, alpha] = pixels.subarray(index, index + 4);
        }
        data.set([red, green, blue, alpha], pixel * 4);
        if (alpha < 255) transparent = true;
        if (!alpha) continue;
        visible = true;
        if (red !== green || green !== blue) fail('PNG visible pixels must be grayscale.');
    }
    if (!visible) fail('PNG has no visible pixels.');
    if (!transparent) fail('PNG must contain transparency.');
    return { data, width, height };
};

const glyphBounds = ({ data, width, height }) => {
    let left = width;
    let top = height;
    let right = 0;
    let bottom = 0;
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            if (!data[(y * width + x) * 4 + 3]) continue;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + 1);
            bottom = Math.max(bottom, y + 1);
        }
    }
    if (right <= left || bottom <= top) fail('PNG has no visible pixels after resizing.');
    return { left, top, width: right - left, height: bottom - top };
};

const fittedSize = (glyph, width, height) => {
    const limitWidth = Math.max(1, Math.round(width * 8 / 9));
    const limitHeight = Math.max(1, Math.round(height * 8 / 9));
    return glyph.width * limitHeight >= glyph.height * limitWidth
        ? { width: limitWidth, height: Math.max(1, Math.round(glyph.height * limitWidth / glyph.width)) }
        : { width: Math.max(1, Math.round(glyph.width * limitHeight / glyph.height)), height: limitHeight };
};

const normalizeGlyph = async (bytes, image) => {
    const { width, height } = image;
    const before = glyphBounds(image);
    const target = fittedSize(before, width, height);
    const resized = before.width !== target.width || before.height !== target.height;
    const centered = before.left === Math.floor((width - before.width) / 2) &&
        before.top === Math.floor((height - before.height) / 2);
    if (!resized && centered) return { bytes, note: 'Glyph margins already normalized.' };
    let pipeline = sharp(image.data, { raw: { width, height, channels: 4 } }).extract(before);
    // Target dimensions already preserve the glyph's proportions to pixel precision.
    if (resized) pipeline = pipeline.resize(target.width, target.height, { fit: 'fill', kernel: sharp.kernel.lanczos3 });
    let { data, info } = await pipeline.raw().toBuffer({ resolveWithObject: true });
    let after = glyphBounds({ data, width: info.width, height: info.height });
    let fitted = fittedSize(after, width, height);
    // Alpha rounding can remove a faint outer row/column. Refit the visible crop
    // once so these icons reach the target size and remain stable on later runs.
    if (after.width !== fitted.width || after.height !== fitted.height) {
        ({ data, info } = await sharp(data, { raw: { width: info.width, height: info.height, channels: 4 } })
            .extract(after).resize(target.width, target.height, { fit: 'fill', kernel: sharp.kernel.lanczos3 })
            .raw().toBuffer({ resolveWithObject: true }));
        after = glyphBounds({ data, width: info.width, height: info.height });
        fitted = fittedSize(after, width, height);
    }
    if (after.width !== fitted.width || after.height !== fitted.height)
        fail('PNG glyph does not fill the target bounds after resizing.');
    const left = Math.floor((width - after.width) / 2);
    const top = Math.floor((height - after.height) / 2);
    const canvas = Buffer.alloc(width * height * 4);
    for (let row = 0; row < after.height; row++) {
        const sourceOffset = ((after.top + row) * info.width + after.left) * 4;
        data.copy(canvas, ((top + row) * width + left) * 4, sourceOffset, sourceOffset + after.width * 4);
    }
    const normalized = await sharp(canvas, { raw: { width, height, channels: 4 } })
        .png({ palette: false, compressionLevel: 9 }).toBuffer();
    return {
        bytes: normalized,
        note: `Glyph ${before.width}×${before.height} → ${after.width}×${after.height}, centered on ${width}×${height} canvas.`
    };
};

const formatKiB = (bytes) => `${(bytes / 1024).toFixed(1)} KiB`;
const singleLine = (value) => value.replace(/\s+/g, ' ').trim();
const compressPng = (bytes) => {
    const skipped = (reason) => ({
        bytes,
        note: 'PNG compression skipped; kept the normalized image.',
        warning: `PNG compression skipped: ${singleLine(reason)}`
    });
    const executable = process.env.OXIPNG || 'oxipng';
    let result;
    try {
        result = spawnSync(executable, oxipngArgs, {
            input: bytes,
            maxBuffer: maxResourceSize * 8,
            timeout: oxipngTimeout,
            windowsHide: true
        });
    } catch (error) {
        return skipped(`cannot run ${executable}: ${error.message}`);
    }
    if (result.error) return skipped(`cannot run ${executable}: ${result.error.message}`);
    if (result.status !== 0)
        return skipped(result.stderr?.toString().trim() || `${executable} exited with code ${result.status}`);
    const compressed = result.stdout;
    if (!compressed?.length) return skipped(`${executable} produced no output`);
    try {
        validatePng(compressed);
    } catch (error) {
        return skipped(`compressed output failed validation: ${error.message}`);
    }
    if (compressed.length >= bytes.length)
        return { bytes, note: `PNG compression did not reduce size (kept ${formatKiB(bytes.length)} normalized image).` };
    return {
        bytes: compressed,
        note: `PNG compressed: ${formatKiB(bytes.length)} → ${formatKiB(compressed.length)}.`
    };
};

/** Normalize glyph margins, compress with oxipng, and validate the final resource. */
export const optimizePng = async (bytes) => {
    const original = validatePng(bytes);
    const normalized = await normalizeGlyph(bytes, original);
    const compressed = compressPng(normalized.bytes);
    const final = validatePng(compressed.bytes);
    const bounds = glyphBounds(final);
    const target = fittedSize(bounds, original.width, original.height);
    if (final.width !== original.width || final.height !== original.height ||
        bounds.width !== target.width || bounds.height !== target.height ||
        bounds.left !== Math.floor((final.width - bounds.width) / 2) ||
        bounds.top !== Math.floor((final.height - bounds.height) / 2))
        fail('Optimized PNG canvas or glyph margins are invalid.');
    if (compressed.bytes.length > maxResourceSize)
        fail(`Optimized PNG exceeds ${formatKiB(maxResourceSize)}.`);
    return { bytes: compressed.bytes, note: `${normalized.note}\n${compressed.note}`, warning: compressed.warning };
};

const optimizeRepositoryIcons = async (args) => {
    if (args.includes('--help')) {
        console.log('Install dependencies: npm ci --prefix .github/scripts');
        console.log('Usage: npm --prefix .github/scripts run optimize -- [--write] [--verbose]');
        console.log('Without --write, reports changes without modifying files.');
        return;
    }
    if (args.some((argument) => !['--write', '--verbose'].includes(argument)))
        throw new Error('Unknown argument. Use --help for usage.');
    const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
    const paths = (await readdir(join(root, 'icons'), { recursive: true, withFileTypes: true }))
        .filter((entry) => entry.isFile() && entry.name.endsWith('.png') && basename(entry.parentPath) === 'res')
        .map((entry) => join(entry.parentPath, entry.name)).sort();
    if (!paths.length) throw new Error('No PNG icon resources found.');
    const counts = { unchanged: 0, optimized: 0, failed: 0 };
    const warnings = new Set();
    for (const path of paths) {
        try {
            const original = await readFile(path);
            const result = await optimizePng(original);
            if (result.warning) warnings.add(result.warning);
            const status = original.equals(result.bytes) ? 'unchanged' : 'optimized';
            if (status === 'optimized' && args.includes('--write')) {
                const temporary = `${path}.tmp`;
                try {
                    await writeFile(temporary, result.bytes);
                    await rename(temporary, path);
                } finally {
                    await rm(temporary, { force: true });
                }
            }
            counts[status]++;
            if (args.includes('--verbose'))
                console.log(`${relative(root, path)}: ${status}; ${result.note.replaceAll('\n', ' ')}`);
        } catch (error) {
            counts.failed++;
            console.error(`${relative(root, path)}: ${error.message}`);
        }
    }
    for (const warning of warnings) console.warn(warning);
    console.log(`icons: ${paths.length} total, ${counts.unchanged} unchanged, ${counts.optimized} optimized, ${counts.failed} failed`);
    process.exitCode = counts.failed ? 1 : 0;
};

// Submission processing imports the optimizer without running the batch command.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url))
    await optimizeRepositoryIcons(process.argv.slice(2));