# Functional consumer rules from GMA Next-Gen 1.5.0.
# The SDK's single broad "keep already-obfuscated ads_mobile_sdk names" rule is
# intentionally omitted. Google documents that rule as preserving readable
# vendor stack traces rather than runtime behavior. All functional reflection,
# Gson, mediation, WebKit and warning rules are retained below.

-keepclassmembers class * extends ads_mobile_sdk.vz0 {
  <fields>;
}

-dontwarn sun.misc.**

-keep class com.google.android.libraries.ads.mobile.sdk.internal.** extends com.google.gson.TypeAdapter
-keep class com.google.android.libraries.ads.mobile.sdk.internal.** implements com.google.gson.TypeAdapterFactory
-keep class com.google.android.libraries.ads.mobile.sdk.internal.** implements com.google.gson.JsonSerializer
-keep class com.google.android.libraries.ads.mobile.sdk.internal.** implements com.google.gson.JsonDeserializer

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep @com.google.android.libraries.ads.mobile.sdk.internal.annotations.GsonClass
    class com.google.android.libraries.ads.mobile.sdk.internal.** { <fields>; }

-keepclassmembers
  @com.google.android.libraries.ads.mobile.sdk.internal.annotations.GsonClass
  class com.google.android.libraries.ads.mobile.sdk.internal.** {
  <fields>;
  <init>();
}

-dontwarn module-info

-keep class * implements com.google.android.gms.ads.mediation.MediationAdapter {
  public *;
}
-keep class * implements com.google.ads.mediation.MediationAdapter {
  public *;
}
-keep class * implements com.google.android.gms.ads.mediation.customevent.CustomEvent {
  public *;
}
-keep class * implements com.google.ads.mediation.customevent.CustomEvent {
  public *;
}
-keep class * extends com.google.android.gms.ads.mediation.MediationAdNetworkAdapter {
  public *;
}
-keep class * extends com.google.android.gms.ads.mediation.Adapter {
  public *;
}

-keep class com.google.android.gms.measurement.api.AppMeasurementSdk {
  public <methods>;
}

-keep class androidx.webkit.WebViewCompat { public <methods>; }
-keep class androidx.webkit.WebViewStartUpConfig { public *; }
-keep class androidx.webkit.WebViewStartUpConfig$Builder { public *; }
-keep class androidx.webkit.WebViewCompat$WebViewStartUpCallback { public *; }
-keep class androidx.webkit.WebViewStartUpResult { public <methods>; }
-keep class androidx.webkit.StartUpLocation { public <methods>; }
-keep class androidx.webkit.Profile { public <methods>; }
-keep class androidx.webkit.WebViewFeature { public <fields>; }
-dontwarn androidx.webkit.**

-dontwarn android.telephony.TelephonyDisplayInfo
-dontwarn android.view.Surface
-dontwarn android.adservices.**
-dontwarn com.google.common.annotations.GoogleInternal
-dontwarn com.google.common.annotations.VisibleForTesting
-dontwarn com.google.common.annotations.VisibleForTesting$Visibility
-dontwarn com.google.crypto.tink.**
-dontwarn com.amazon.**
