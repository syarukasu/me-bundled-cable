# ME Bundled Cable

Route up to eight AE2 cables through one block, with individually selectable lanes and native AE2 channel management.

## Versions

- Forge 1.20.1: AE2 15.4.10 or later, Java 17.
- NeoForge 1.21.1: AE2 19.2.18 or later, Java 21.
- Install on both client and server. Ender IO is not required. Jade is optional.

## Features

- Glass, covered, smart, dense covered and dense smart cables can share a bundle.
- Supports all AE2 cable colors, including multiple lanes with the same color.
- Smart overlays show the channels actually used on each branch.
- Select a lane with an AE2-compatible wrench, then click it again to toggle a connection.
- Select one lane and click a lane in an adjacent bundle to pair them.
- Sneak with a wrench to recover the targeted cable. Breaking the bundle recovers its contents.
- AE2 facades and optional per-lane Jade information are supported.

Craft an AE2 cable by itself to obtain its bundled placement form; craft that form by itself to convert it back. Right-click a bundle with a cable to add another lane.

## ME Bundled Controller

The upgraded controller joins native ME controller structures and uses the same models, textures and animated lights.
Craft it with one ME Controller surrounded by eight Dense Smart Cables.

A face adjoining a bundle supplies up to eight lanes: eight dense lanes carry 32 channels each, or 256 channels in total at default settings. This is not one 256-channel cable. Native controller faces still expose only the selected lane, and ordinary cables retain their usual limits.

Lanes connected to the same controller are part of the same ME network. AE2 still validates controller size and shape and manages power and channels.

## Build

Each platform directory is an independent Gradle project:

- `forge-1201/gradlew build`
- `neoforge-1211/gradlew build`

Run the wrapper from its platform directory with the corresponding Java version. Distribution JARs, checksums and release notes are in GitHub Releases.

## License

Original code: LGPL-3.0-only. AE2-derived assets: CC BY-NC-SA 3.0. Ender IO-derived assets: Unlicense. See each platform's NOTICE.md and licenses directory for attribution.
