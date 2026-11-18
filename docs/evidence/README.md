# Reading the device receipts

- **`android/`** belongs to the promoted APK. Start with `run-source.txt`; compare `apk.sha256`, the installed base APK receipt, and the independent API 26/36 hashes. [Test report](../TESTING.md).
- **`last-failure/`**, when present, is diagnostic output from a *different, rejected* candidate. Its screenshots, assertion count, SHA and run result do not describe the distributed file.

A `[capture]` build from the delivery branch may replace the APK **and** its matching positive evidence only when the device and compatibility jobs both pass and the branch still points to the tested revision. A later docs/evidence commit does not alter the app inputs or re-sign the binary.
