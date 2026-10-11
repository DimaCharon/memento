# --- Наши кастомные View: инфлейтятся из XML по имени класса ---
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
-keep public class ru.memento.ui.view.** { *; }
-keep public class ru.memento.widget.** { *; }

# --- Компоненты, поднимаемые системой по имени (AlarmManager, RemoteViews, AppWidget) ---
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class * extends android.appwidget.AppWidgetProvider { *; }
-keep public class * extends android.widget.RemoteViewsService { *; }
-keep public class * extends android.app.Activity { *; }

# --- Модели данных сохраняются/читаются через org.json и reflection-less привязки ---
-keep class ru.memento.data.** { *; }

# --- RemoteViews: сеттеры вызываются по именам строк ---
-keepclassmembers class * extends android.widget.RemoteViews {
    public void set*(...);
}

# --- Коtlin/AndroidX ---
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,allowobfuscation class ru.memento.** { *; }
