-repackageclasses ""
-allowaccessmodification

# API moderna de Xposed (libxposed 101)
-dontwarn io.github.libxposed.annotation.**
-dontwarn androidx.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class io.github.auag0.pokegopip.Main { public <init>(); }

-assumenosideeffects public class android.util.Log {
    public static *** d(...);
    public static *** i(...);
}
