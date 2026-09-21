# Text Hub - release rules
# No reflection based parsing of user data happens anywhere in the app, so the default
# R8 configuration is enough. Keep the processor classes: they are looked up through the
# registry and referenced from tool definitions.
-keepclassmembers class com.texthub.core.processors.** { *; }
-keep class com.texthub.core.model.** { *; }

# Never obfuscate away friendly error messages that the UI shows to the user.
-keep class com.texthub.core.model.ToolException

# Cryptography: keep the JCE provider entry points used by :core.
-keep class javax.crypto.** { *; }
-keep class java.security.SecureRandom

-dontobfuscate
