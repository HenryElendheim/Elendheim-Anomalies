# Elendheim Anomalies

An offline Android game about turning dead time into progress. Creatures appear around
wherever you actually are, you catch them with a skill based throw, and they go into a
Codex that stays on your phone.

No account, no server, no connection required. Everything is stored on the device and the
whole save can be written out to a file you choose.

## What is in it

- **Map** centred on you, showing the radius anomalies appear in, your stops and whatever
  is standing around right now.
- **Stops** you place by hand at the places you actually wait. Each one hands over
  capsules on a cooldown you set yourself.
- **The catch.** Flick a capsule at the creature. How tight the timing ring is, whether
  the throw curves, and whether the cylinder lands upright all raise the odds, and each
  one also pays experience, so skill matters even on a common.
- **Codex** of every creature, with the ones you have not found yet shown as silhouettes.
- **Companion** that grows from the distance you travel, the stops you spin and the
  catches you make, and metamorphoses when the lore supports it.
- **Six rarity tiers** plus an independent shiny roll on any spawn at all, with a pity
  counter so a long dry run gets shorter.
- **Export and import** straight from the settings screen to any file you pick.

## Accessibility

The settings screen carries high contrast, five text sizes that reach every screen,
reduce motion, large touch targets, distance labels and vibration. Reduce motion also
holds the catch ring steady, so the throw is scored on the flick alone.

## Building it

Java 17 and the Android SDK with platform 35.

```
./gradlew assembleRelease
```

The APK lands at `app/build/outputs/apk/release/Elendheim-Anomalies.apk`. The filename
never carries the version, so each new build installs over the one already on the phone.

## Signing

`keystore/elendheim.p12` is checked in so every build signs identically and updates
install cleanly. It is a convenience key, not a secret: the store and key password are
both `elendheim`. Anyone can sign a build that claims to be this app, which is fine for a
sideloaded personal project and would not be fine for a store listing. To use a private
key instead, set `ELENDHEIM_KEYSTORE_FILE`, `ELENDHEIM_KEYSTORE_PASSWORD`,
`ELENDHEIM_KEY_ALIAS` and `ELENDHEIM_KEY_PASSWORD` in the build environment.

## Adding a creature

Roster entries live in `app/src/main/assets/creatures.json`. Sprites are drawn from a
shape and a colour in code, so a new creature is a JSON entry and nothing else. Shinies
are a hue shift of the same silhouette.

## Licence

MIT. See `LICENSE`.
