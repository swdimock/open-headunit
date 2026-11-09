# Phoenix changes (branch `phoenix`)

Small, rebase-friendly changes for the Phoenix F-150 head unit (launcher `com.phoenix.launcher`,
climate bar overlay 227 px at the bottom of a 1200x1600 portrait screen). Base: upstream
`ae74788d8312` (4.0.0-beta2). New code lives in `app/src/main/java/.../phoenix/`; upstream files get
one-line hooks only.

| Change | New files | Upstream files touched |
| --- | --- | --- |
| Launch extras `phoenix.transport` = `tcp` (+ `phoenix.host`, `phoenix.port`) auto-connects over TCP through the same path as the `headunit://connect?ip=` deep link, but with a configurable port, retrying 5 s after each failure while the main screen is visible, with a status banner. `usb` runs the stock USB check (`ACTION_CHECK_USB`, user requested). Also marks onboarding done (no wizard) | `phoenix/PhoenixLaunch.kt` (incl. `TcpRetryPolicy`) | `main/MainActivity.kt` (`handleLaunchIntent`, 1 line) |
| `phoenix.inset_bottom` (px) is written to upstream's own "screen margins" setting `insetBottom`, so the negotiated projection size and the view padding leave the climate bar free | (in `PhoenixLaunch.kt`) | none |
| Audio hold: broadcast `com.phoenix.launcher.action.AUDIO_PATH`, boolean `ready`. `false` makes the mixer write silence (video untouched, stream timing unchanged); `true` or never received = normal | `phoenix/PhoenixAudioGate.kt` | `App.kt` (runtime registration, 1 line), `decoder/audio/AudioMixer.kt` (1 line after `renderCycle`), `AndroidManifest.xml` (exported receiver) |
| Translucent 56 px home button top-right, 16 px margin (main screen and projection; AA's own controls and the receiver's menu status icons sit on the left) that starts `ACTION_MAIN`/`CATEGORY_HOME`; shown only after a Phoenix launch. Panel `KEYCODE_HOME` and Back keep upstream behaviour | `phoenix/PhoenixHomeButton.kt`, `res/drawable/phoenix_ic_home.xml` | `aap/AapProjectionActivity.kt` (1 line), `main/MainActivity.kt` (same line as above) |
| Loopback tunnel is not Self Mode: a `tcp` launch to a loopback host (emulator: `adb reverse tcp:5277 tcp:5277`) reaches a remote phone through adb. Upstream treats every loopback peer as Self Mode and then skips announcing the media and speech sinks, so the phone played music itself. The launch persists the endpoint (`host:port`); a session on exactly that endpoint is classified as an ordinary wireless one, so all three sinks are announced and every stream goes through `AudioMixer` (and the audio hold). The `usb` or non-loopback launches clear it. Network binding and the user's `enable-audio-sink` setting are unchanged. Caveat: manual Self Mode on the same endpoint is misclassified while the declaration stands | `phoenix/PhoenixTunnel.kt` | `connection/projection/SocketProjectionConnection.kt` (`isLoopbackPeer`, 1 line), `phoenix/PhoenixLaunch.kt` |
| Tests | `app/src/test/.../phoenix/PhoenixLaunchTest.kt` | none |

Upstream already offers `headunit://connect?ip=<ip>` (activity deep link) and the
`com.andrerinas.openheadunit.ACTION_CONNECT` broadcast with `ip`; both are fixed to port 5277 and the
deep link does not retry, which is why the `phoenix.*` extras exist.

Try it by hand:

    adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity \
      --es phoenix.transport tcp --es phoenix.host 127.0.0.1 --ei phoenix.port 5277 --ei phoenix.inset_bottom 227
    adb shell am broadcast -p com.andrerinas.headunitrevived -a com.phoenix.launcher.action.AUDIO_PATH --ez ready false

Build: `./gradlew :app:assembleGithubDebug` (JDK 17, NDK 29.0.14206865, CMake 3.22.1, platform 36).
