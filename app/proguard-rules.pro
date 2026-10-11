# Arrow Escape production R8 rules.
# Android Gradle Plugin, AndroidX and Google SDK dependencies provide their own
# consumer rules. Keep this app ruleset intentionally minimal so R8 can shrink,
# optimize and obfuscate everything that is safe to transform.

# The optional HSDP shim activity is removed from the merged manifest because
# its current implementation uses the deprecated SHORT_EDGES cutout mode.
# Fail the release if R8 cannot discard it; this prevents silently shipping the
# same Play Console warning again.
# Diagnostic: report why the shim survives shrinking. The release workflow
# inspects the result before any candidate is handed to Play.
-whyareyoukeeping class com.google.android.play.core.hsdp.service.HsdpShimActivity
