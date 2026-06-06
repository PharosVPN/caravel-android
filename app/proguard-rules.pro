# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026 The PharosVPN Authors

# Keep the gomobile binding (called reflectively by CoreBridge + JNI by gomobile).
-keep class go.** { *; }
-keep class core.** { *; }
-keepclassmembers class core.** { *; }
