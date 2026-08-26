# Icon Resources

> The following are the current community-maintained notification icon resource rules.
You can review these rules or go to the contribution section to request adaptations or submit new icon adaptations.

<IconResources />

## Trademark Disclaimer

The product names, logos, and brands displayed in the above resources are the property of their respective owners.
All company, product, and service names used in the icon resources are for identification, compatibility, and illustrative purposes only.
Use of these names, logos, and brands does not imply endorsement.

## Structure Description

- The resource rules directory is fixed into the following structure, all located in the project's `icons` directory.

- Third-party app rules are located at [icons/app](branch://icons/app/)
- Game rules are located at [icons/game](branch://icons/game/)
- System rules are located at [icons/system](branch://icons/system/)

In each directory, if there are no direct sub-projects, there will be a `manifest.json` file, and the corresponding registered icon resources are located in the `res` directory.

ANIP provides a brand-new rule file structure. Below is a complete example of the rules.

```json
"com.example.app": {
  "label": "Example",
  "format": "svg",
  "color": "#4F7CF7",
  "overlay": false,
  "contributors": "peter, jane"
}
```

- `label`: The official name of the app, supports i18n
- `format`: The format of the icon file, must match the file extension in the `res` directory
- `color`: The color used in the icon, format must be uppercase #RRGGBB, no alpha channel allowed
- `overlay`: Whether to force overlay all notification icons pushed by the app, regardless of whether they are native monochrome
- `contributors`: List of contributor names, multiple contributors separated by `,` (with a space following)

The `label`, `format`, and `contributors` fields are required.

The i18n support format for `label` is as follows, using standard BCP 47 language tags, with `-` separating the language and region.

```json
"label": {
  "en": "Example",
  "zh-CN": "示例应用"
}
```

Similarly, this rule must have a corresponding `com.example.app.svg` file in the `res` directory, with the package name as the file name and `format` as the extension.

In general, the file name and extension must be in lowercase.

To address the resource duplication caused by Base64 bloat in older formats and repeated adaptation of icons in channel packages, ANIP provides the `target` parameter to inherit icon resources from other package names, avoiding repeated resource occupation.

Below is an example of a rule using the `target` parameter.

```json
"com.example.app.foo": {
  "target": "com.example.app",
  "label": "Example (Foo)"
}
```

In this example, the icon resource for `com.example.app.foo` will inherit the icon resource from `com.example.app`. As long as the `target` parameter is declared, there is no need for a `com.example.app.foo.svg` file in the `res` directory. The other parameters are obtained through inheritance, and you can override them, but note that overriding the `format` parameter is invalid and will be automatically discarded.

Want to contribute to icon resources? Please go to [Ready to Submit](../contribute/submit.md) to participate immediately.

## Usage Instruction

All icon resources of ANIP are free, open, and maintained by contributors. Anyone can use, modify, and distribute the resources of this project in compliance with the Apache-2.0 license. You can check [here](../about/adopters.md) to see the projects currently using ANIP.

Each contributed icon will be automatically packaged. The latest icon resources are available in the [Releases](releases://) page of the current repository, tagged with the SHA-1 hash of each submission. The file name is similar to `anip-bundle-<SHA1>.zip`, and the package structure is as follows.

```
anip-bundle-<SHA1>.zip
├─ app/
├─ game/
└─ system/
```

We do not recommend using the project's resources directly through raw. Instead, pull the latest release package for use, or use the official Android dependency library provided by ANIP (not yet released).