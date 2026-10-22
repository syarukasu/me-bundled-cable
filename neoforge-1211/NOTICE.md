# Licensing and upstream reference

Original ME Bundled Cable code: Copyright (c) 2026 syarukasu, LGPL-3.0-only.

The controller multipart blockstate is copied from Applied Energistics 2
neoforge/v19.2.18, commit db17504a86128fdf3dae31f5fb7a112a646e0b93,
assets/ae2/blockstates/controller.json. All referenced controller models and
textures are loaded from the installed AE2 mod, without modification or
redistribution. AE2 asset attribution and CC BY-NC-SA 3.0 notices are retained
in licenses/AE2-upstream-README.md.

Ender IO reference: https://github.com/Team-EnderIO/EnderIO/tree/18ee1b17bf6879298e911fff751204b07f2e4228
Ender IO reference branch: 1.20.1. Its conduit layout is retained in this
Minecraft 1.21.1 / NeoForge adaptation.

`ConduitGeometry` adapts the slot offsets and axis translation from Ender IO's
`OffsetHelper` and `ConduitShape`. Shape-based selection follows
`ConduitShape.getLookUpValue`. Connector plates and collars reuse the original
Ender IO JSON model and connector texture. Rotation is adapted from
ConduitBlockModel. The retained upstream assets are unmodified copies.

Cable surfaces and item icons use the installed AE2 1.21.1 cable models.
The renderer obtains native AE2 cable meshes, including native colored
textures and Smart channel overlays, and fits them to the conduit bounds.
It does not recreate these textures or redistribute them.
Credit: Team Ender IO and contributors, including CrazyPants, tterrag,
HenryLoenwind, CyanideX, EpicSquid, Rover656, HypherionSA, agnor99 and ferriarnus.
The pinned root LICENSE.txt is reproduced in licenses/EnderIO-Unlicense.txt.
Its README also dedicates code to the public domain under CC0. The pinned
repository's doc/license notices were checked: dust and alloy assets are CC0;
the remaining notice concerns Travel Anchors code. No narrower notice applies
to the selected conduit files. The root Unlicense is retained for these copies.
No bundled third-party API, sound, graph engine or Ender IO runtime dependency
is incorporated.

AE2 remains a required external mod. Its textures are resolved from the
installed resource pack, not redistributed in this JAR. Their licenses and
attribution remain with Applied Energistics 2 and its contributors.
API and rendering reference: Applied Energistics 2 neoforge/v19.2.18,
commit db17504a86128fdf3dae31f5fb7a112a646e0b93.
