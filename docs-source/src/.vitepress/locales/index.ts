import { defineConfig } from 'vitepress';
import { configs } from '../configs/template';
import en from './en';
import zhCn from './zh-cn';

export default defineConfig({
    locales: {
        en: {
            label: 'English',
            link: '/en/',
            lang: en.lang,
            title: configs.website.locales.en.title,
            description: en.description,
            themeConfig: en.themeConfig
        },
        'zh-cn': {
            label: '简体中文',
            link: '/zh-cn/',
            lang: zhCn.lang,
            title: configs.website.locales['zh-cn'].title,
            description: zhCn.description,
            themeConfig: zhCn.themeConfig
        }
    }
});