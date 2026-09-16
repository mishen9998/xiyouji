# T5 controlled browser performance baseline

This benchmark uses built production assets in a real Chromium browser. It is a local
CDP simulation, **not** a physical-device result, production SLA or backend-latency test.
Do not run other browser benchmarks or builds in parallel; use one worker.
Trace/video/automatic screenshots are disabled for these tests to avoid capture overhead;
explicit evidence screenshots are taken only after measurements.

## Run

From `frontend-vue`, build and run preview separately:

```powershell
npm exec --package=node@20.18.1 -- node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
npm exec --package=node@20.18.1 -- node node_modules/vite/bin/vite.js build
$env:VITE_API_TARGET = 'http://localhost:18085'
npm exec --package=node@20.18.1 -- node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4175 --strictPort
```

In another terminal:

```powershell
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:4175'
npm exec --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t5-performance.spec.ts --project=chromium --workers=1 --retries=0
```

The frontend preview must already be running; the spec never builds, starts servers,
or deploys remotely. The identity page uses the real app without request routing.
Combat API JSON is fixed only to obtain repeatable rendering state; actual production
components, artwork HTTP transfers, layout, selection and painting are used. Thus the
steady/input figures deliberately do **not** measure backend latency or correctness.

## Fixed scenarios and thresholds

| Scenario | Conditions | Measurement / threshold |
|---|---|---|
| Desktop steady battle | 1366×768, DPR1, CPU1×, unthrottled localhost | after fonts/visible images and 1s warmup, 240 consecutive rAF intervals, p95≤20ms |
| Simulated phone steady battle | 390×844, touch/mobile viewport, DPR1, CPU4× | same 240-interval measurement, p95≤33ms |
| Local visual feedback | six alternating actual card selections in each steady scenario | pointerdown→selected DOM state + two rAF callbacks (one paint opportunity), each≤100ms |
| Cold phone identity | five **fresh contexts**, cache cleared/disabled, SW blocked, CPU4×, 4Mbps/150ms | real LCP observations, five-run median≤3000ms |
| Cold critical images | visible `<img>` currentSrc and visible CSS backgrounds | actual CDP encoded transfer sum≤600KiB; each≤200KiB; nonempty, successful, uncached responses and unbroken DOM images |

rAF intervals are a scheduling/paint-cadence proxy, not hardware GPU render duration.
Input paint opportunities are not physical display latency. The loading records and
steady-state records are separate; loading and warmup frames are excluded from steady
statistics. Default lightweight illustration mode is used; opt-in 3D is not covertly
enabled. CPU×4 is relative to the recorded host, not equivalent to a particular phone.

## Evidence

Each test writes JSON using Playwright `testInfo.outputPath` and attaches it:

- `steady-desktop.json`, `steady-mobile-cpu4.json`: machine/Chrome/Node/viewport/CPU,
  raw 240 frame intervals, p50/p95/max, loading records, long tasks / long animation
  frames where supported, six raw input samples and methodology.
- `cold-run-1.json` … `cold-run-5.json`: LCP entries/candidate element, real network
  response encodedDataLength, MIME/status/cache flags, resource timings, critical
  image list and viewport image validity.
- `cold-summary.json`: every raw run, fixed network settings, LCP median and budgets.
- screenshots from steady modes and the first cold identity run.

CDP encodedDataLength is the actual received encoded response amount reported by
Chromium (including protocol/header overhead where reported), not the source-file
size or a precomputed manifest budget. A failure still saves the measurement JSON;
do not rerun selectively and present the best sample. Rebuild and repeat the entire
suite after final T6 imagery is installed.

## T5 first complete baseline (before the nine T6 images)

The first full run passed all 3 tests in 28.7s with retries disabled. Host: Windows
10.0.19045 x64, i5-12490F / 12 logical CPUs / 32GiB; Chrome 151.0.7922.34;
Node 20.18.1. Local run time was 2026-09-17 05:41–05:42 (+08:00).

| Metric | First complete run |
|---|---|
| Desktop 240-interval rAF p95 | 16.7ms |
| CPU4× simulated phone 240-interval rAF p95 | 16.8ms |
| Desktop maximum of six card feedback samples | 30.6ms |
| Simulated phone maximum of six card feedback samples | 25.7ms |
| Five cold LCP measurements, in run order | 1196, 1192, 1196, 1196, 1236ms |
| Cold LCP median | 1196ms |
| First-screen image transfers, each run | 73,034 bytes total / largest single response |

The critical image was a 640w AVIF response, HTTP200, `image/avif`, uncached, with
no service worker; Chromium's LCP candidate was that actual image. Full raw evidence
and screenshots are retained outside the temporary worktree in
`pipeline-game-polish/tasks/t5/evidence/performance-run1/`, with
`pipeline-game-polish/tasks/t5/evidence/performance-run1.log`.

These numbers describe this complete run only. T6 imagery, future code changes,
different machines/DPR/network settings and physical touchscreens require new full
measurements; do not present this pre-T6 baseline as final-artwork or production data.

## Final T5 frozen-build retest (run 2)

After the desktop/landscape battle layout, five-target forecast and identity-image
`cover` presentation were finalized, the **entire** unchanged three-scenario suite
was rerun against the rebuilt production preview. All 3 tests passed in 27.9s,
one worker, zero retries, no concurrent builds or browser tests. This is the final
T5 package baseline, not a selection of the best values from runs 1 and 2. Original
run 1 evidence is retained. Host/browser/Node/DPR and simulation settings are the
same; local measurement time was 2026-09-17 05:59–06:00 (+08:00).

| Metric | Final T5 run 2 |
|---|---|
| Desktop 240-interval rAF p95 | 16.7ms |
| CPU4× simulated phone 240-interval rAF p95 | 16.7ms |
| Desktop maximum of six card feedback samples | 28.9ms |
| Simulated phone maximum of six card feedback samples | 24.6ms |
| Five cold LCP measurements, in run order | 928, 916, 916, 964, 932ms |
| Cold LCP median | 928ms |
| First-screen image transfers, each run | 73,034 bytes total / largest single response |

Interpretation matters: after the image-cover layout adjustment, Chromium selected
`p.privacy-note`, not the background image, as the final LCP candidate in all five
runs. The lower LCP therefore must **not** be represented as a proportional image
loading speed-up. The actual background still downloaded as 640w AVIF (HTTP200,
`image/avif`, no disk cache or SW) and passed complete/decode/natural-size checks;
its first-run network response completed at 1141.1ms. Raw resource timings, all LCP
candidates and actual response byte counts make that distinction auditable.

Final raw JSON, attachments and screenshots:
`pipeline-game-polish/tasks/t5/evidence/performance-run2/`; command output:
`pipeline-game-polish/tasks/t5/evidence/performance-run2.log`.
T6 must still repeat the full suite with the nine final generated images.
