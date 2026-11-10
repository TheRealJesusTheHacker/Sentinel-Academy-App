# Sentinel Academy App — R8 rules for release builds.
#
# Every rule here is DELIBERATE: it names the exact reflection or
# name-based lookup that would break without it. There is no blanket
# "-keep class **" and no rule kept "just in case". If a rule's reason
# stops being true, delete the rule with the feature that needed it.
# (Adapted from the family's audited ruleset — same libraries, same
# reasoning; the mail/work rules from the sibling apps do not apply
# here because this app has neither stack in phase 1.)
#
# Manifest-declared components (MainActivity, AcademyApplication)
# are kept by the AAPT rules AGP feeds to R8 automatically — they
# are not repeated here.

# ---------------------------------------------------------------------
# Log stripping — debug/verbose log calls never survive into a
# release build. There are no Log calls in the app today (verified
# by source search when this rule was added); this rule pins that
# mechanically so a future Log.d/Log.v cannot ship. Info/warn/error
# are left alone deliberately — they are the levels a crash-adjacent
# diagnostic would use, and none exist today either.
# ---------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ---------------------------------------------------------------------
# R8 missing-class warnings — compile-time-only annotations that
# Android does not ship. Neither is ever loaded at runtime on
# Android; both are referenced (only) from library bytecode, which
# is exactly what -dontwarn is for. Named exactly, nothing broader:
#
#   com.google.errorprone.annotations.** — Error Prone's
#     compile-time-only annotations, referenced by Tink (via
#     androidx.security:security-crypto). Tink's own consumer
#     rules prescribe this dontwarn.
#   javax.annotation.Nullable / javax.annotation.concurrent.GuardedBy —
#     JSR-305 annotations referenced from Tink bytecode (via
#     security-crypto). jsr305 is not on this app's R8 library
#     classpath; the annotations are compile-time-only and never
#     loaded at runtime on Android. Provenance: R8's own
#     missing_rules.txt prescription from the family's 1.0.0
#     Linode builds (2026-11-09).
# ---------------------------------------------------------------------
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
