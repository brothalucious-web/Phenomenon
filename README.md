# Phenomenon

Minecraft Java **1.21.2** · **Fabric** · Java 21

A chaos mod where reality periodically breaks, with random automatic events and commands to trigger them manually.

## Phenomena

- **Gravity inversion:** temporarily disables normal gravity and accelerates the player upward with portal particles. This is an inversion-style gameplay effect, not a global arbitrary-gravity rewrite.
- **Time acceleration / dilation:** changes server tick rate to 45 TPS or 5 TPS for around eight seconds, then restores 20 TPS. This impacts the entire server.
- **Sandevistan frames:** renders up to seven recent full-body copies of your player model at earlier positions, producing a layered speed-streak similar to the reference image. The copies currently use the player skin and standard rendering; custom translucent/tinted ghost shaders are not included yet.
- **TNT trail:** spawns primed TNT while the player moves. Spawn rate is limited and nearby active TNT is capped.
- **Other anomalies:** levitation, random short-range teleportation, and darkness.

Random events select a player every 30–90 seconds while anyone is online.

## Commands

- `/phenomenon random`
- `/phenomenon gravity`
- `/phenomenon fast`
- `/phenomenon slow`
- `/phenomenon tnt`
- `/phenomenon sandevistan`
- `/phenomenon levitate`
- `/phenomenon teleport`
- `/phenomenon darkness`
- `/phenomenon stop`

Commands require permission to use server commands. Test TNT in a disposable world or make a backup first.

## Build and test status

Requires JDK 21 and Gradle 8.10.2. Run `gradle build`; the output JAR is in `build/libs/`. GitHub Actions builds each push and uploads the mod JAR as an artifact when successful.

The implementation has not yet been confirmed in a running Minecraft client. Run the project build and test in-game before treating any effect as verified.
