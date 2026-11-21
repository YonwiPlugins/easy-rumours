# Third-party notices

This plugin is built against RuneLite, which is distributed under the BSD 2-Clause licence reproduced in RUNELITE-LICENSE.txt.

The Gradle wrapper is distributed under Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0. Gradle source and notices: https://github.com/gradle/gradle.

Old School RuneScape game content and runtime item imagery belong to Jagex. This plugin is unofficial and is not endorsed by Jagex or RuneLite.

## Hunter Rumours

Source: https://github.com/geel9/runelite-hunter-rumours

Used in Easy Rumours:
- `RumourLocation.java`: copied with only the package and imports changed.
- `Rumour.java`, `Creature.java`, `Trap.java`, `Hunter.java`: rumour, creature, trap, pity and hunter data, with item and NPC IDs re-mapped to RuneLite's gameval tables.
- `RumoursPlugin.java`: the hunter dialogue and quetzal whistle parsing approach, the finished-rumour lines and the burrows bounds.
- `Style.java`: sidebar header, card, drop-down and scrollbar styling adapted from PreferredLocationPreferencesPanel (via Ultimate Farming).

```
BSD 2-Clause License

Copyright (c) 2024, Joshua Coffey
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
