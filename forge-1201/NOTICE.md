# Licensing and upstream reference

Original ME Bundled Cable code: Copyright (c) 2026 syarukasu, LGPL-3.0-only.

The controller multipart blockstate is copied from Applied Energistics 2
neoforge/v19.2.18, commit db17504a86128fdf3dae31f5fb7a112a646e0b93,
assets/ae2/blockstates/controller.json. All referenced controller models and
textures are loaded from the installed AE2 mod, without modification or
redistribution. AE2 asset attribution and CC BY-NC-SA 3.0 notices are retained
in licenses/AE2-upstream-README.md.

Ender IO reference: https://github.com/Team-EnderIO/EnderIO/tree/18ee1b17bf6879298e911fff751204b07f2e4228
Branch: 1.20.1. No 1.21.1 implementation is mixed into this port.

`ConduitGeometry` adapts the slot offsets and axis translation from Ender IO's
`OffsetHelper` and `ConduitShape`. Shape-based selection follows
`ConduitShape.getLookUpValue`. The renderer reuses the connector and collar
JSON models and original connector texture. The original conduit assets
remain as unmodified reference copies. Rotation is adapted from ConduitBlockModel.
Credit: Team Ender IO and contributors, including CrazyPants, tterrag,
HenryLoenwind, CyanideX, EpicSquid, Rover656, HypherionSA, agnor99 and ferriarnus.
The pinned root LICENSE.txt is reproduced in licenses/EnderIO-Unlicense.txt.
Its README also dedicates code to the public domain under CC0. The pinned
repository's doc/license notices were checked: dust and alloy assets are CC0;
the remaining notice concerns Travel Anchors code. No narrower notice applies
to the selected conduit files. The root Unlicense is retained for these copies.
No bundled third-party API, sound, graph engine or Ender IO runtime dependency
is incorporated.

AE2 remains a required external mod. Native cable meshes and channel overlays
are obtained from installed AE2, scaled to the conduit bounds and remapped to
the 1.21.1 cable textures to match the NeoForge edition.

The PNG files under assets/mebundledcable/textures/ae2_1211/part/cable are
unmodified copies from Applied Energistics 2 neoforge/v19.2.18, commit
db17504a86128fdf3dae31f5fb7a112a646e0b93, originally at
src/main/resources/assets/ae2/textures/part/cable.
Copyright (c) 2020 Ridanisaurus Rid; (c) 2013-2020 AlgorithmX2 et al.
These textures are licensed CC BY-NC-SA 3.0, not LGPL.
https://creativecommons.org/licenses/by-nc-sa/3.0/
https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/db17504a86128fdf3dae31f5fb7a112a646e0b93
Their upstream attribution is retained in licenses/AE2-upstream-README.md.
Resource names are relocated to our namespace; normal AE2 cable textures
are not replaced. Item icons use the same texture remapping.
