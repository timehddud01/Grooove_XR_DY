# Audio fixture provenance

TASK-01 tests synthesize the following PCM 16-bit mono WAV fixtures at runtime:

- `normal.wav`: 440 Hz sine wave
- `silence.wav`: zero-valued PCM samples
- `noise.wav`: deterministic pseudo-random noise with seed `42`
- `too-short.wav`: one-second 440 Hz sine wave

No third-party recording is included. The generated waveforms contain no copyrighted musical
composition or performance and may be used without restriction for this project's tests.

Each test fixture exposes a SHA-256 checksum so a failed test can be reproduced from the fixture
name, sample rate, duration, and generator implementation in `TestAudioFixtures.kt`.
