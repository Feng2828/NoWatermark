# 不混淆，保持代码可读性；仅保留基础规则
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn androidx.**
