# Launcher tidak punya library eksternal yang rumit
# RecyclerView
-keep class androidx.recyclerview.widget.** { *; }

# Keep semua class di package kita
-keep class com.minimallauncher.** { *; }

# Optimasi agresif
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
