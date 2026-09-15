# Elendheim Anomalies

An offline Android game about turning dead time into progress. Creatures appear around
wherever you actually are, you catch them with a skill based throw, and they go into a
Codex that stays on your phone.

No account, no server, no connection required. Everything is stored on the device and the
whole save can be written out to a file you choose.

## What is in it

- **Map** of real streets and water from OpenStreetMap, centred on you, showing the radius
  anomalies appear in, your stops and whatever is standing around right now. Pinch to
  zoom, twist to turn, drag to look around. The spawn ring and every stop's catchment are
  real distances, so they scale with the zoom exactly as the ground does.
- **Stops further than 500 m are left off the map** until you are closer, so the screen
  only ever shows what you could actually walk to.
- **Stops** you place by hand: pick the plus button, tap the map where it goes, nudge it
  until it sits right, then confirm. Each one hands over capsules on a cooldown you set
  yourself. A stop that is ready to spin is a large square, one still counting down is a
  small grey strip with a live timer, so the shape alone tells you where to go.
- **The spin**, which throws the stop marker into a long decelerating turn and then hands
  the drops over one at a time, plainest first, so the best one is always the one you are
  still waiting for.
- **The catch.** Flick a capsule at the creature. How tight the timing ring is, whether
  the throw curves, and whether the cylinder lands upright all raise the odds, and each
  one also pays experience, so skill matters even on a common. The creature is drawn into
  the capsule, which then rocks on the ground: a catch always rocks three times before it
  clicks, and a miss rocks a number of times taken from how close the roll actually came,
  so three rocks followed by a break out genuinely means it nearly held.
- **Codex** of every creature, with the ones you have not found yet shown as silhouettes.
- **Companion** that grows from the distance you travel, the stops you spin and the
  catches you make, and metamorphoses when the lore supports it.
- **Six rarity tiers** plus an independent shiny roll on any spawn at all, with a pity
  counter so a long dry run gets shorter.
- **Empower Powder**, earned from Essence capsules and given to a creature to make it
  gain experience faster. It changes the rate, never the journey.
- **Export and import** straight from the settings screen to any file you pick.

## Accessibility

The settings screen carries high contrast, five text sizes that reach every screen,
reduce motion, large touch targets, distance labels and vibration. Reduce motion also
holds the catch ring steady, so the throw is scored on the flick alone. Quick spins and
quick catches skip the two long animations on their own, for when the flourish has
stopped being fun.

## The map

One `MapCamera` drives everything. Its scale has to agree with the tile renderer exactly,
and two things decide that: the renderer measures a zoom step against a 512 pixel tile
rather than the 256 older slippy maps used, and it counts in density independent pixels
while the overlay draws in real ones. Both corrections live in `MapProjection.metersPerPixel`
and nowhere else. Get either wrong and the ground slides out from under the markers.
 The tile renderer's own gestures are switched off and
its camera is pushed from ours, so a single gesture handler controls the map and every
marker is still positioned by the app's own projection in `MapProjection`. That means the
renderer is only ever a backdrop: if it fails to start, or a style will not load, the same
camera drives a plain drawn map instead and the game plays identically. Turning off
**Detailed map** in settings does the same thing on purpose.

Tiles come from OpenFreeMap, which needs no key. Map data is OpenStreetMap and is credited
on the map itself.

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

## Updating the schema

The database ships its migrations from version one, in `AppDatabase.MIGRATIONS`. A schema
change without a migration would wipe a real collection, so every version bump brings the
data across by hand.

## Licence

MIT. See `LICENSE`.
