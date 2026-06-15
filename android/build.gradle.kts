// Plugins declarados aqui (versões), aplicados no módulo :app
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Compiler de Compose (obrigatório com Kotlin 2.0+)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // KSP para o processador de anotações do Room
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
