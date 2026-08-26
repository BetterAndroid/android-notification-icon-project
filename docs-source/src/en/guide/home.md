# Introduction

> This is an Android notification icon project that provides standardized monochrome icon resources for apps and vendor systems that do not conform to the Android standard notification design.

## Background

This project was created to bring order to the fragmented third-party app ecosystem, so that third-party app notification icons can also follow the Android standard notification icon design guidelines.

At its root, the problem is the ecosystem fragmentation caused by MIUI's non-standard Xiaomi Push notification icons. Other vendors have not fully resolved it either. Instead, most adopted Apple's approach of using an app's launcher icon as its primary notification icon, a deceptive kind of "solution". vivo maintained its own set of monochrome notification icons, but gradually abandoned that approach in later versions of OriginOS.

As the project lead, I believe Google is responsible for this problem. Android clearly specifies notification icon design guidelines, but Google has not enforced them on vendors, allowing vendors to undermine the standard notification icon design without constraint.

Most ironically, Google itself abandoned the mandatory notification icon tinting guideline in Android 16 and set notification icons in the notification panel to app launcher icons, completing its wholesale concession to the ecosystem.

This project began in 2022 under the name **AndroidNotifyIconAdapt**. That ill-fitting name once left the project with resources but no standards. It has now been officially renamed **Android Notification Icon Project** (codenamed **ANIP**) and its original icon resource rules have been completely revised. The project will now move toward more modern, continuous community maintenance.

This project has been changed from the original **AGPL-3.0** license to **Apache-2.0**. ANIP will continue to be open under the current license, and anyone can use, modify, and distribute the resources of this project in compliance with the license. Changes to the license do not automatically apply to previous versions.

## Usage

ANIP currently uses community-supported Xposed modules to adapt the System UI and provides community-maintained monochrome notification icon resources and rules for third-party apps, games, and system apps.

The following are the current support paths of ANIP. In the future, these Xposed modules will be consolidated into a unified aggregate module maintained by the BetterAndroid organization.

1. Ready-to-use Xposed modules

- For HyperOS/MIUI, see [MIUINativeNotifyIcon](https://github.com/fankes/MIUINativeNotifyIcon)
- For ColorOS, Realme UI, or OxygenOS (>= 12), see [ColorOSNotifyIcon](https://github.com/fankes/ColorOSNotifyIcon)

2. Adaptation requests proposed by the community

- For AOSP-based and Pixel devices, see [this issue](repo://issues/102)
- For Flyme, see [this issue](repo://issues/201)
- For OneUI, see [this issue](repo://issues/475)

ANIP plans to provide icon resource downloads and an official Android dependency library for apps, published to a Maven repository. You will then be able to use this API to build your own Xposed module or notification adaptation solution.

## Noncompliant Systems

| System               | Version         | Violation                                                                                        |
| -------------------- | --------------- | ------------------------------------------------------------------------------------------------ |
| Stock Android        | >= 16           | Replaces notification icons with app launcher icons in the notification center                   |
| EMUI                 | 4.0, 4.0.1, 4.1 | Replaces all notification icons with app launcher icons                                          |
| HarmonyOS            | >= 4.0          | Replaces all notification icons with app launcher icons                                          |
| ColorOS<br/>OxygenOS | >= 15.0.1       | Replaces all notification icons with app launcher icons                                          |
| RealmeUI             | >= 6.0          | Replaces all notification icons with app launcher icons                                          |
| MIUI                 | <= 9, >= 12.5   | Replaces all notification icons with app launcher icons                                          |
| HyperOS              | All             | Whitelists system apps and replaces third-party notification icons with app launcher icons       |
| OneUI                | >= 6.0          | Replaces notification icons with app launcher icons in the notification center (can be disabled) |
| ZUI                  | All             | Replaces all notification icons with app launcher icons                                          |
| Nubia UI             | All             | Replaces all notification icons with app launcher icons                                          |
| RedMagicOS           | All             | Replaces all notification icons with app launcher icons                                          |
| 360 OS               | All             | Replaces all notification icons with app launcher icons                                          |
| CoolOS               | All             | Replaces all notification icons with app launcher icons                                          |
| WaterOS              | All             | Replaces all notification icons with app launcher icons                                          |
| Flyme                | All             | Whitelists system and third-party apps                                                           |
| OriginOS             | >= 6            | Replaces all notification icons with app launcher icons                                          |

## Contribution

The maintenance of this project is inseparable from the support and contributions of all developers.

If possible, feel free to submit a PR to contribute features you think are needed to this project, or go to [GitHub Issues](repo://issues)
to make suggestions to us.