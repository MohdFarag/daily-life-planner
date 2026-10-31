# Adhan library uses Kotlin enums and data classes only; no reflection rules required.

# kotlinx-datetime annotates its types for kotlinx.serialization, which this app does not use.
-dontwarn kotlinx.serialization.**
