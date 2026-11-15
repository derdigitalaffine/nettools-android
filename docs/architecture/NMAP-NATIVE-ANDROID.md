# Native Nmap on Android: architecture / release gate

Status: **NOT BUNDLED**, 2026-11-15. The APK currently offers its own non-root
TCP-connect scanner and **offline import of genuine Nmap XML results**.

## Why the native binary is not silently shipped

1. Nmap is covered by the **Nmap Public Source License (NPSL)**, which has
   conditions different from a stock GPL and commercial/OEM redistribution
   restrictions. A redistributed Nmap binary or adapted source requires a
   deliberate licensing review, notices and corresponding source delivery.
   Read https://nmap.org/npsl/ before redistribution.
2. Android 10+ with current targetSdk prevents running executables copied
   into writable app-private directories (W^X). Do not download a binary into
   filesDir and try to run it. Candidate designs include a properly packaged
   native library/executable deployed in the APK's executable native-lib area,
   subject to supported Android API levels and device verification.
3. Native Nmap features depend on platform access. Unprivileged TCP-connect
   scans (-sT) are feasible; raw SYN (-sS), capture-oriented scans, many OS
   fingerprinting techniques and link-layer discovery are **not** generically
   available to an ordinary Android app.
4. A Linux Nmap binary is not automatically an Android binary: Bionic/NDK,
   libpcap, nsock, libdnet, PCRE2, Lua, OpenSSL-compatible dependencies and
   each target ABI need separate cross-compilation, tests and legal review.

## Planned implementation boundary

- `ScanBackend` capability contract: `rootless-connect`,
  `native-nmap` (only after validated package availability), `remote-agent`
  (future optional). The app must always expose the actual engine used.
- Native candidate: upstream Nmap source at a **pinned, reviewed commit**,
  reproducible Android NDK build with recorded patches and dependencies.
  ABI matrix: arm64-v8a and x86_64 first; investigate armeabi-v7a separately.
- No elevation requests, root assumptions or raw packets in the normal APK.
  `-sT` only by default, narrow host targets and limited concurrency.
  Unsupported flags are rejected, not silently substituted.
- Scan output: XML (`-oX`) mapped into the existing report importer with
  XML entity processing disabled; cancellation and resource limits mandatory.
- Release gates: license decision, source notices, SBOM, artifact provenance,
  emulator plus physical device runtime verification on API 26/29/35+, ABI
  smoke tests, user-authorized network test, benchmark and cancellation.
- Native engine enablement is blocked until **all** gates pass. Do not label
  the Java/Kotlin TCP-connect backend as native Nmap.

## Present deliverables

- Rootless parallel connect scan, limited to 1,024 selected ports and
  32 concurrent connections, with CSV results.
- Local Nmap XML import through Android Storage Access Framework with a
  2 MB input cap, 128 host cap, XML DOCTYPE and external entity rejection.
- SNMPv2c GET/GETNEXT independently implemented; SNMPv3/AuthPriv remains
  unavailable and is not emulated.

Sources: https://nmap.org/npsl/ ;
https://developer.android.com/about/versions/10/behavior-changes-all#execute-permission ;
https://nmap.org/book/man-port-scanning-techniques.html .
