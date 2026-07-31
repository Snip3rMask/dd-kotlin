# Keep original names (no obfuscation)
-dontobfuscate

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Gson
-keep class msr.mirudl.app.** { *; }
-keepclassmembers class msr.mirudl.app.** { *; }

# Glide
-keep class com.bumptech.glide.** { *; }

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R8 full mode
-keepclassmembers class msr.mirudl.app.R$* {
    public static <fields>;
}
