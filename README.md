# UltimateArtifacts 1.2.0

Paper plugin for Minecraft Java 1.21.11 / Java 21. All artifacts are issued only by administrator commands.

## Build on GitHub
1. Upload the **contents** of this project folder to the repository root (the `.github` folder must be at repository root).
2. Commit changes.
3. Open **Actions → Build plugin**.
4. Download the `UltimateArtifacts-JAR` and `UltimateArtifacts-ResourcePack` artifacts from a successful run.

The workflow uses Java 21 and Gradle 8.14. The resource-pack ZIP is included at the repository root.

## Commands
- `/artifacts list`
- `/artifacts give <player> <item_id> [amount]`
- `/artifacts giveall <player>`
- `/artifacts menu` — configure the held configurable weapon
- `/artifacts reload`

Permission: `artifacts.admin` (default: op).

## New legendary weapons
- `void_mace` — Void Mace, five skills. Its vanilla mace smash scales with fall height; the plugin adds a purple void shockwave, suction and extra area damage on a smash hit.
- `thunder_mace` — Thunder Mace, three skills. Its smash creates a burst of electric particles and a chain of lightning visual strikes around the impact.
- `void_bow` — Void Bow, five configurable skills: Singularity Arrow, Starfall, Phase Shot, Soul Piercer and Eclipse.
- `thunder_bow` — Thunder Bow, three configurable skills: Storm Arrow, Electric Volley and Thunder Mark.

## Binds
Open `/artifacts menu` while holding the Scythe, either mace, or either bow. Select a skill and choose one of: LMB, RMB, Shift+LMB, Shift+RMB, Sprint+LMB, Sprint+RMB, or None. Each player has separate saved binds; duplicate binds on the same weapon are automatically cleared. For bows, normal RMB draw-and-shoot remains available; use `/artifacts menu` to open their menu.

## Enchanting
The scythe, thunder blade and blood dagger use netherite-sword bases; both maces use mace bases; both bows use bow bases. They accept their corresponding vanilla enchantments. They retain their normal Minecraft enchantment compatibility (e.g. Density/Breach/Wind Burst for maces, Power/Punch/Flame for bows). They are unbreakable, but can still receive compatible enchantments.

## Resource pack
Install `UltimateArtifacts-ResourcePack-1.21.11.zip` from this repository in the client's `resourcepacks` folder or host it as your server resource pack. The pack contains item definitions, models and textures for all 14 artifacts.
