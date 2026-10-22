# Vehicle Motion Cues for Android

Animated dots along the screen edges that move with the vehicle — sliding down when it
speeds up, up when it brakes, and sideways in turns — so what you see matches what your inner
ear feels. That sensory match is what eases motion sickness when reading in a car.

Works system-wide: the dots float over every app, and touches pass straight through them.

## Build and run

Open the folder in Android Studio (Ladybug or newer) and run the `app` configuration, or:

```
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest   # physics and projection tests
```

Requires a device or emulator on Android 8.0 (API 26) or newer. On first use the app asks for
**Display over other apps**; on Android 13+ it also asks to post its notification.

Testing on an emulator: open *Extended controls → Virtual sensors → Additional sensors* and
nudge the acceleration values to watch the dots react. A real drive is the true test.

## How it works

```
Sensors (gravity + linear acceleration, ~50 Hz)
  → VehicleFrameProjector   strip vertical bumps, find "forward" for any tilt/rotation
  → CueDynamics             low-pass, drift removal, dead zone, spring-damper
  → MotionCuesOverlayView   draws dot lattice at screen edges, offset each vsync
```

| File | Role |
| --- | --- |
| `motion/VehicleFrameProjector.kt` | Device vectors → screen-aligned lateral/longitudinal acceleration. Forward is the screen's top edge and the back of the phone, both flattened onto the horizontal plane, so it holds whether the phone is flat, upright or in between, in any display rotation. |
| `motion/CueDynamics.kt` | Signal conditioning plus a critically-ish damped spring (1.1 Hz, ζ 0.8). Pure Kotlin, unit-tested. |
| `motion/VehicleMotionSensor.kt` | Uses fused `TYPE_GRAVITY` + `TYPE_LINEAR_ACCELERATION`; falls back to a low-pass split of the raw accelerometer. |
| `overlay/MotionCuesService.kt` | Foreground service (`specialUse`) owning the overlay window, sensors and settings. Pauses sensors when the screen is off. |
| `overlay/MotionCuesOverlayView.kt` | Draws the dots; only requests display frames while they're moving. |
| `tile/MotionCuesTileService.kt` | Quick Settings toggle. |
| `ui/MainActivity.kt` | On/off, permission flow, style, size and intensity. |

### Details worth knowing

- **Touch pass-through.** Since Android 12 an untrusted overlay only lets touches through
  when its window alpha is at or below the system limit (0.8). The overlay is capped at that
  value; raising it would make the phone unusable while cues are on.
- **Drift and mounts.** A slow (30 s) bias estimate removes sensor offset and a tilted car
  mount without cancelling real turns, which last a few seconds.
- **Parked car.** Accelerations under 0.12 m/s² are ignored, so the dots sit still at a light.
- **Settings.** *Regular* dots are always shown; *Dynamic* dots fade in only during
  acceleration, braking or turns. Intensity scales travel from 0.5× to 2×.

## Not done yet

- Automatic on/off when you're in a vehicle (Activity Recognition `IN_VEHICLE` transitions;
  adds a Google Play services dependency and a background-start exemption to work through).
- Dot colour adapting to the content underneath (an overlay can't read other apps' pixels;
  would need MediaProjection, which is heavy for this).
- Instrumented tests for the service and tile.
