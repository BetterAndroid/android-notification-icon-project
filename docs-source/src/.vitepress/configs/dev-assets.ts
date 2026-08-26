import { createReadStream, existsSync, statSync } from 'node:fs';
import path from 'node:path';
import type { Plugin } from 'vite';
import { configs } from './template';

const contentTypes: Record<string, string> = {
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.svg': 'image/svg+xml; charset=utf-8'
};

/** Serves repository icon files from the current Vite origin without copying them into production output. */
export const serveLocalIcons = (): Plugin => ({
    name: 'serve-local-icons',
    apply: 'serve',
    configureServer(server) {
        const iconsRoot = path.resolve(process.cwd(), '../icons');
        const prefixes = [`${configs.website.base}icons/`, '/icons/'];
        server.middlewares.use((request, response, next) => {
            const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
            const prefix = prefixes.find((candidate) => pathname.startsWith(candidate));
            if (!prefix) return next();
            const relativePath = decodeURIComponent(pathname.slice(prefix.length));
            const filePath = path.resolve(iconsRoot, relativePath);
            if (!filePath.startsWith(`${iconsRoot}${path.sep}`) || !existsSync(filePath) || !statSync(filePath).isFile())
                return next();
            response.statusCode = 200;
            response.setHeader('Content-Type', contentTypes[path.extname(filePath).toLowerCase()] ?? 'application/octet-stream');
            response.setHeader('Cache-Control', 'no-store');
            createReadStream(filePath).pipe(response);
        });
    }
});