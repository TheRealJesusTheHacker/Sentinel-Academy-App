# ProGuard rules for the Windows release build (Compose Desktop / CMP 1.7.3 runs
# ProGuard on the release jars). The warnings this rule silences are references
# from kotlinx-datetime's serializer classes to the kotlinx-serialization runtime,
# which is not part of this app's dependency graph — the same graph ships and runs
# in the Linux .deb build. Taken from the CI build log of 2026-11-14 (886 warnings,
# all in this one family).
-dontwarn kotlinx.serialization.**
