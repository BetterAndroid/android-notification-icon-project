import { appendFileSync, existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import { createHash } from 'node:crypto';
import { maxIconDimension, maxResourceSize, optimizePng, validatePng } from './optimize-png.mjs';

const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const packageNamePattern = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
// Preserve the existing Android framework resource while applying applicationId rules to new entries.
const legacyPackageNames = new Set(['android']);
const localeCodePattern = /^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$/;
const colorPattern = /^#[0-9A-F]{6}$/;
const resourcePrefix = 'ANIP_RESOURCE:';
const categoryPaths = ['app', 'game', 'system/common', 'system/mios', 'system/coloros'];
const fieldNames = {
    type: 'Type / 类型',
    category: 'Category / 类别',
    packageName: 'App Package Name / 应用包名',
    target: 'Target App Package Name / 目标应用包名',
    label: 'App Label / 应用名称',
    color: 'Color / 颜色',
    format: 'Icon Type / 图标类型',
    overlay: 'Overlay / 覆盖',
    optimization: 'Optimization / 优化',
    contributors: 'Contributors / 贡献者',
    iconResource: 'Icon Resource / 图标资源',
    remark: 'Remarks / 备注'
};
const expectedFields = Object.values(fieldNames);

class SubmissionError extends Error {
    constructor(errors) {
        super(errors.join('\n'));
        this.errors = errors;
    }
}

const fail = (...errors) => {
    throw new SubmissionError(errors);
};
const normalizeOptional = (value) => value === '_No response_' ? '' : value.trim();
const writeOutput = (name, value) => {
    if (!process.env.GITHUB_OUTPUT) return;
    const delimiter = `ANIP_${name.toLocaleUpperCase()}_EOF`;
    appendFileSync(process.env.GITHUB_OUTPUT, `${name}<<${delimiter}\n${value}\n${delimiter}\n`);
};

const parseIssueFields = (body) => {
    if (!body.startsWith(`### ${expectedFields[0]}\n\n`))
        fail('The issue body does not start with the standard submit_adaption.yml Issue Form.');
    const fields = new Map();
    const fieldPattern = /^### ([^\n]+)\n\n([\s\S]*?)(?=\n\n### [^\n]+\n\n|(?![\s\S]))/gm;
    for (const match of body.matchAll(fieldPattern)) {
        if (fields.has(match[1])) fail(`Duplicate Issue Form field: ${match[1]}`);
        fields.set(match[1], match[2].trim());
    }
    const names = [...fields.keys()];
    if (names.length !== expectedFields.length || !expectedFields.every((name, index) => names[index] === name))
        fail('The issue body does not match the submit_adaption.yml Issue Form.');
    return Object.fromEntries(fields);
};

const normalizeIconResource = (value) => {
    const normalized = normalizeOptional(value);
    const codeBlockPatterns = [
        /^<code(?:\s[^>]*)?>([\s\S]*?)<\/code>$/i,
        /^<pre>\s*<code(?:\s[^>]*)?>([\s\S]*?)<\/code>\s*<\/pre>$/i,
        /^<details>\s*<summary>[^<]*<\/summary>\s*<pre>\s*<code(?:\s[^>]*)?>([\s\S]*?)<\/code>\s*<\/pre>\s*<\/details>$/i
    ];
    for (const pattern of codeBlockPatterns) {
        const match = normalized.match(pattern);
        if (match) return match[1].trim();
    }
    return normalized;
};

const formatIconResourceBody = (body) => {
    const heading = `### ${fieldNames.iconResource}\n\n`;
    const headingStart = body.indexOf(heading);
    if (headingStart < 0) return body;
    const contentStart = headingStart + heading.length;
    const nextHeading = body.indexOf('\n\n### ', contentStart);
    const contentEnd = nextHeading < 0 ? body.length : nextHeading;
    const iconResource = normalizeIconResource(body.slice(contentStart, contentEnd));
    if (!iconResource.startsWith(resourcePrefix)) return body;
    const collapsed = `<details><summary>Expand to view</summary><pre><code>${iconResource}</code></pre></details>`;
    return `${body.slice(0, contentStart)}${collapsed}${body.slice(contentEnd)}`;
};

const decodeIconPayload = (value) => {
    if (!value.startsWith(resourcePrefix) || value.includes('\n'))
        fail('Icon Resource must contain one complete ANIP_RESOURCE payload.');
    const encoded = value.slice(resourcePrefix.length);
    if (!/^[A-Za-z0-9_-]+$/.test(encoded)) fail('Icon Resource is not valid Base64URL data.');
    let parsed;
    try {
        const decoded = Buffer.from(encoded, 'base64url');
        if (decoded.toString('base64url') !== encoded) fail('Icon Resource uses a non-canonical Base64URL encoding.');
        parsed = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(decoded));
    } catch (error) {
        if (error instanceof SubmissionError) throw error;
        fail('Icon Resource payload cannot be decoded.');
    }
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || parsed.schemaVersion !== 1)
        fail('Icon Resource payload has an unsupported schema.');
    return parsed;
};

const hasExactKeys = (value, keys) => {
    const actualKeys = Object.keys(value).sort();
    const expectedKeys = [...keys].sort();
    return actualKeys.length === expectedKeys.length && actualKeys.every((key, index) => key === expectedKeys[index]);
};

const parseLabel = (value) => {
    const trimmed = value.trim();
    if (!trimmed) fail('App Label is required.');
    if (!trimmed.startsWith('{')) return trimmed;
    let label;
    try {
        label = JSON.parse(trimmed);
    } catch {
        fail('Localized App Label must be valid JSON.');
    }
    if (!label || Array.isArray(label) || typeof label !== 'object')
        fail('Localized App Label must be a locale-keyed JSON object.');
    const entries = Object.entries(label);
    const normalizedCodes = entries.map(([code]) => code.toLocaleLowerCase());
    if (!entries.length || entries.some(([code, text]) =>
        !localeCodePattern.test(code) || typeof text !== 'string' || !text.trim()
    ) || new Set(normalizedCodes).size !== normalizedCodes.length)
        fail('Localized App Label requires at least one unique locale code and non-empty value.');
    return Object.fromEntries(entries.map(([code, text]) => [code, text.trim()]));
};

const decodeResourceData = (payload, format) => {
    const expectedMimeType = format === 'png' ? 'image/png' : 'image/svg+xml';
    if (payload.format !== format || payload.mimeType !== expectedMimeType ||
        !Number.isInteger(payload.size) || payload.size < 1 || payload.size > maxResourceSize ||
        typeof payload.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(payload.sha256) ||
        typeof payload.data !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(payload.data))
        fail('Icon Resource metadata is invalid or does not match the Issue Form.');
    const bytes = Buffer.from(payload.data, 'base64');
    if (bytes.toString('base64') !== payload.data || bytes.length !== payload.size)
        fail('Icon Resource size or Base64 data is invalid.');
    const digest = createHash('sha256').update(bytes).digest('hex');
    if (digest !== payload.sha256) fail('Icon Resource SHA-256 verification failed.');
    return bytes;
};

const validateSvgColor = (value) => {
    const normalized = value.trim().toLocaleLowerCase();
    if (!normalized || ['none', 'transparent', 'currentcolor', 'inherit'].includes(normalized) || /^url\(\s*#[^)]+\)$/.test(normalized)) return;
    const hex = normalized.match(/^#([0-9a-f]{3,8})$/);
    if (hex) {
        const digits = hex[1];
        const colors = digits.length === 3 || digits.length === 4
            ? digits.slice(0, 3).split('').map((digit) => parseInt(`${digit}${digit}`, 16))
            : [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 6)].map((part) => parseInt(part, 16));
        if (colors[0] === colors[1] && colors[1] === colors[2]) return;
        fail(`SVG color is not grayscale: ${value}`);
    }
    const rgb = normalized.match(/^rgba?\(\s*([\d.]+)%?[, ]+([\d.]+)%?[, ]+([\d.]+)%?(?:\s*[,/]\s*[\d.]+%?)?\s*\)$/);
    if (rgb && rgb[1] === rgb[2] && rgb[2] === rgb[3]) return;
    const hsl = normalized.match(/^hsla?\(\s*[\d.]+(?:deg)?[, ]+0%[, ]+[\d.]+%(?:\s*[,/]\s*[\d.]+%?)?\s*\)$/);
    if (hsl || ['black', 'white', 'gray', 'grey'].includes(normalized)) return;
    fail(`SVG color syntax is unsupported or not grayscale: ${value}`);
};

const readSvgAttribute = (attributes, name) =>
    attributes.match(new RegExp(`(?:^|\\s)${name}\\s*=\\s*(['"])([\\s\\S]*?)\\1`, 'i'))?.[2];
const parseSvgLength = (value) => {
    const match = value?.trim().match(/^(\d+(?:\.\d+)?)(?:px)?$/i);
    if (!match) return;
    const length = Number(match[1]);
    return length > 0 ? length : undefined;
};
const validateSvgDimensions = (source) => {
    const attributes = source.match(/<svg\b([^>]*)>/i)?.[1];
    if (attributes === undefined) fail('SVG document is invalid.');
    const viewBox = readSvgAttribute(attributes, 'viewBox')?.trim().split(/[\s,]+/).map(Number);
    const validViewBox = viewBox?.length === 4 && viewBox.every((value) => Number.isFinite(value)) &&
        viewBox[2] > 0 && viewBox[3] > 0;
    const viewBoxWidth = validViewBox ? viewBox[2] : undefined;
    const viewBoxHeight = validViewBox ? viewBox[3] : undefined;
    let width = parseSvgLength(readSvgAttribute(attributes, 'width'));
    let height = parseSvgLength(readSvgAttribute(attributes, 'height'));
    if (width && !height && viewBoxWidth && viewBoxHeight) height = width * viewBoxHeight / viewBoxWidth;
    if (height && !width && viewBoxWidth && viewBoxHeight) width = height * viewBoxWidth / viewBoxHeight;
    width ??= viewBoxWidth;
    height ??= viewBoxHeight;
    if (!width || !height) fail('SVG dimensions are invalid.');
    if (width > maxIconDimension || height > maxIconDimension)
        fail(`Icon size exceeds ${maxIconDimension} px.`);
};

const validateSvg = (bytes) => {
    let source;
    try {
        source = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    } catch {
        fail('SVG must use UTF-8 encoding.');
    }
    if (!/<svg\b/i.test(source) || !/<\/(?:svg)>\s*$/i.test(source.trim())) fail('SVG document is invalid.');
    validateSvgDimensions(source);
    if (/<!ENTITY|<\s*(?:script|foreignObject|iframe|object|embed|audio|video)\b/i.test(source))
        fail('SVG contains unsupported active or embedded content.');
    if (/\son[a-z]+\s*=/i.test(source) || /(?:href|xlink:href)\s*=\s*['"](?!#)/i.test(source) ||
        /@import\b|url\(\s*['"]?(?:https?:|data:|\/\/)/i.test(source))
        fail('SVG external references and event attributes are not allowed.');
    if (!/<(?:path|rect|circle|ellipse|line|polyline|polygon|text|use)\b/i.test(source))
        fail('SVG contains no supported visible geometry.');
    const colorAttributePattern = /\b(?:fill|stroke|color|stop-color|flood-color|lighting-color)\s*=\s*(['"])(.*?)\1/gi;
    for (const match of source.matchAll(colorAttributePattern)) validateSvgColor(match[2]);
    const stylePattern = /\bstyle\s*=\s*(['"])(.*?)\1/gi;
    for (const match of source.matchAll(stylePattern)) {
        for (const declaration of match[2].split(';')) {
            const separator = declaration.indexOf(':');
            if (separator < 0) continue;
            const property = declaration.slice(0, separator).trim().toLocaleLowerCase();
            if (['fill', 'stroke', 'color', 'stop-color', 'flood-color', 'lighting-color'].includes(property))
                validateSvgColor(declaration.slice(separator + 1));
        }
    }
    const styleBlockPattern = /<style\b[^>]*>([\s\S]*?)<\/style>/gi;
    const styleColorPattern = /\b(?:fill|stroke|color|stop-color|flood-color|lighting-color)\s*:\s*([^;{}]+)/gi;
    for (const match of source.matchAll(styleBlockPattern)) {
        for (const color of match[1].matchAll(styleColorPattern)) validateSvgColor(color[1]);
    }
};

const resolveTarget = (manifest, target) => {
    const rule = manifest[target];
    if (!rule || rule.target) fail(`Target does not exist in the selected category: ${target}`);
    const format = rule.format;
    if (!format || !['png', 'svg'].includes(format.toLocaleLowerCase())) fail('Target does not resolve to a supported icon resource.');
    return rule;
};

const validateAndApply = async (event, applyChanges) => {
    const issue = event.issue;
    if (!issue || typeof issue.body !== 'string') fail('GitHub issue event data is missing.');
    const fields = parseIssueFields(issue.body);
    const typeValue = normalizeOptional(fields[fieldNames.type]);
    if (!['0', '1'].includes(typeValue)) fail('Type must be 0 or 1.');
    const modification = typeValue === '1';
    const categoryValue = normalizeOptional(fields[fieldNames.category]);
    const categoryIndex = /^\d+$/.test(categoryValue) ? Number(categoryValue) : -1;
    const categoryPath = categoryPaths[categoryIndex];
    if (!categoryPath) fail('Category is invalid.');
    const packageName = normalizeOptional(fields[fieldNames.packageName]);
    if (!packageNamePattern.test(packageName) && !legacyPackageNames.has(packageName))
        fail('App Package Name is invalid.');
    const target = normalizeOptional(fields[fieldNames.target]);
    if (target && !packageNamePattern.test(target) && !legacyPackageNames.has(target))
        fail('Target App Package Name is invalid.');
    if (target === packageName) fail('Target App Package Name and App Package Name cannot be the same.');
    const label = parseLabel(fields[fieldNames.label]);
    const color = normalizeOptional(fields[fieldNames.color]).toLocaleUpperCase();
    if (color && !colorPattern.test(color)) fail('Icon Color must use uppercase #RRGGBB format.');
    const format = normalizeOptional(fields[fieldNames.format]).toLocaleLowerCase();
    if (!['png', 'svg'].includes(format)) fail('Icon Type must be PNG or SVG.');
    const overlayText = normalizeOptional(fields[fieldNames.overlay]).toLocaleLowerCase();
    if (!['true', 'false'].includes(overlayText)) fail('Overlay must be true or false.');
    const overlay = overlayText === 'true';
    const optimizationText = normalizeOptional(fields[fieldNames.optimization]).toLocaleLowerCase();
    if (format === 'png' && !target && !['true', 'false'].includes(optimizationText))
        fail('Optimization must be true or false.');
    const optimizationEnabled = format === 'png' && !target && optimizationText === 'true';
    const contributorsText = normalizeOptional(fields[fieldNames.contributors]);
    const contributors = contributorsText.split(',').map((value) => value.trim());
    if (!contributorsText || contributors.some((value) => !value) || contributorsText !== contributors.join(', '))
        fail('Contributors must be non-empty and separated by a comma and a space.');
    // GitHub user names are case-insensitive.
    if (new Set(contributors.map((value) => value.toLocaleLowerCase())).size !== contributors.length)
        fail('Contributors must not be duplicated.');
    if (!normalizeOptional(fields[fieldNames.remark])) fail('Remarks are required. Explain why the app needs adaptation or why the resource needs modification.');
    const manifestPath = join(repositoryRoot, 'icons', categoryPath, 'manifest.json');
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
    const resourceDirectory = join(repositoryRoot, 'icons', categoryPath, 'res');
    const existingRule = modification ? manifest[packageName] : undefined;
    if (modification) {
        if (!Object.hasOwn(manifest, packageName)) fail('App Package Name does not exist in the selected manifest.');
        if (target !== (existingRule.target ?? '')) fail('Target App Package Name cannot be changed in a modification.');
    } else {
        if (Object.hasOwn(manifest, packageName)) fail('App Package Name already exists in the selected manifest.');
        if (['png', 'svg'].some((extension) => existsSync(join(resourceDirectory, `${packageName}.${extension}`))))
            fail('App Package Name already has an icon resource in the selected category.');
    }
    let resourceBytes;
    let optimization;
    let targetRule;
    if (target) {
        targetRule = resolveTarget(manifest, target);
        const targetFormat = targetRule.format.toLocaleLowerCase();
        if (targetFormat !== format) fail('Icon Type does not match the target resource format.');
    } else {
        const iconResource = normalizeIconResource(fields[fieldNames.iconResource]);
        // A modification without Icon Resource keeps the current icon resource.
        if (!iconResource && existingRule) {
            if (format !== existingRule.format?.toLocaleLowerCase())
                fail('Icon Type does not match the current icon resource, provide Icon Resource to change it.');
        } else {
            if (!iconResource) fail('Icon Resource is required when Target App Package Name is empty.');
            const payload = decodeIconPayload(iconResource);
            if (!hasExactKeys(payload, ['data', 'format', 'mimeType', 'schemaVersion', 'sha256', 'size']))
                fail('Icon Resource payload fields do not match the direct resource schema.');
            resourceBytes = decodeResourceData(payload, format);
            if (format === 'png') {
                try {
                    if (optimizationEnabled) optimization = await optimizePng(resourceBytes);
                    else validatePng(resourceBytes);
                } catch (error) {
                    fail(error.message);
                }
            } else {
                validateSvg(resourceBytes);
            }
        }
    }
    if (existingRule) {
        // Contributors inherited from the target count as existing contributors as well.
        const existingContributors = (existingRule.contributors ?? targetRule?.contributors ?? '').split(', ').filter(Boolean);
        if (existingContributors.some((value, index) => contributors[index] !== value))
            fail('Existing contributors cannot be removed or reordered, only new contributors can be appended.');
    }
    const rule = {};
    if (target) rule.target = target;
    if (!targetRule || !isDeepStrictEqual(label, targetRule.label)) rule.label = label;
    if (!target) rule.format = format;
    if (color && (!targetRule || color !== targetRule.color?.toLocaleUpperCase())) rule.color = color;
    if (!targetRule || overlay !== targetRule.overlay) rule.overlay = overlay;
    const contributorNames = contributors.join(', ');
    if (!targetRule || contributorNames !== targetRule.contributors) rule.contributors = contributorNames;
    const existingResourcePath = existingRule?.format
        ? join(resourceDirectory, `${packageName}.${existingRule.format.toLocaleLowerCase()}`)
        : undefined;
    const outputResourceBytes = optimization?.bytes ?? resourceBytes;
    // Appending contributors or editing remarks alone is not a modification of the resource.
    const withoutContributors = ({ contributors: _, ...value }) => value;
    if (existingRule && isDeepStrictEqual(withoutContributors(rule), withoutContributors(existingRule))) {
        const existingResourceBytes = existingResourcePath && existsSync(existingResourcePath)
            ? readFileSync(existingResourcePath)
            : undefined;
        if (!outputResourceBytes || (existingResourceBytes && outputResourceBytes.equals(existingResourceBytes)))
            fail('The modification does not change the icon resource, changes to contributors or remarks alone are not accepted.');
    }
    if (applyChanges) {
        manifest[packageName] = rule;
        writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
        if (outputResourceBytes) {
            const resourcePath = join(resourceDirectory, `${packageName}.${format}`);
            // A modification may switch the icon type, so the resource with the old extension is removed.
            if (existingResourcePath && existingResourcePath !== resourcePath) rmSync(existingResourcePath, { force: true });
            writeFileSync(resourcePath, outputResourceBytes);
        }
    }
    const actionName = modification ? 'Update' : 'Add';
    const summary = target
        ? `${actionName} \`${packageName}\` targeting \`${target}\` in ${categoryPath}.`
        : `${actionName} \`${packageName}\` in ${categoryPath}.`;
    return {
        action: actionName.toLocaleLowerCase(),
        appLabel: typeof label === 'string' ? label : Object.values(label)[0],
        branch: `anip/submit-${issue.number}`,
        packageName,
        optimizationWarning: optimization?.warning,
        summary: optimization?.note ? `${summary}\n${optimization.note}` : summary
    };
};

const main = async () => {
    const applyChanges = process.argv.includes('--apply');
    const eventPath = process.argv.find((argument) => argument.endsWith('.json')) ?? process.env.GITHUB_EVENT_PATH;
    if (!eventPath) throw new Error('GitHub event JSON path is required.');
    const event = JSON.parse(readFileSync(eventPath, 'utf8'));
    try {
        const result = await validateAndApply(event, applyChanges);
        const formattedBody = formatIconResourceBody(event.issue.body);
        const bodyFormatted = formattedBody !== event.issue.body;
        if (bodyFormatted && process.env.ANIP_FORMATTED_ISSUE_BODY_PATH)
            writeFileSync(process.env.ANIP_FORMATTED_ISSUE_BODY_PATH, formattedBody);
        writeOutput('valid', 'true');
        writeOutput('action', result.action);
        writeOutput('app_label', result.appLabel);
        writeOutput('body_formatted', String(bodyFormatted));
        writeOutput('branch', result.branch);
        writeOutput('package_name', result.packageName);
        writeOutput('summary', result.summary);
        if (result.optimizationWarning) writeOutput('optimization_warning', result.optimizationWarning);
    } catch (error) {
        if (!(error instanceof SubmissionError)) throw error;
        writeOutput('valid', 'false');
        writeOutput('errors', error.errors.map((message) => `- ${message}`).join('\n'));
    }
};

await main();