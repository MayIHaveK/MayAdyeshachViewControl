import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.Bukkit

plugins {
    java
    id("io.izzel.taboolib") version "2.0.27"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("com.mayiahvek.obfuscate")
}

taboolib {
    env {
        install(Basic)
        install(BukkitUtil)
        install(Bukkit)
        install(CommandHelper)
    }
    description {
        name = "MayAdyeshachViewControl"
        contributors {
            name("MayIHaveK")
        }
        dependencies{
            name("Adyeshach")
            name("ArcartX").optional(true)
        }
    }
    version { taboolib = "6.2.4-99fb800" }

    // 重定向第三方库，避免与其他插件冲突
    // 注意：Exposed 和 SQLite JDBC 不能重定位，因为它们依赖 META-INF/services (SPI) 机制
    relocate("com.github.lalyos.", "com.mayihavek.mayadyeshachviewcontrol.library.lalyos.")
}

repositories {
    mavenCentral()
}

val exposedVersion = "0.46.0"

dependencies {
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))

    // JFiglet - 体积很小，直接内嵌
    taboo("com.github.lalyos:jfiglet:0.0.9")

    // Exposed ORM 框架
    // 只内嵌 Exposed 本体，排除它传递带入的大型 Kotlin 运行库链
    taboo("org.jetbrains.exposed:exposed-core:$exposedVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    taboo("org.jetbrains.exposed:exposed-jdbc:$exposedVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // SQLite JDBC 驱动 - 仅保留驱动本体
    taboo("org.xerial:sqlite-jdbc:3.46.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // MySQL 预留（启用时取消注释）
    // taboo("com.mysql:mysql-connector-j:8.3.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// ===== MayObfuscate 混淆配置 =====
obfuscate {
    inPlaceOverwrite.set(true)

    stringEncryption.set(true)
    stringEncryptionKey.set("MayAdyeshachViewControl-StringObf-Key-2026!")
    stringEncryptionStrength.set("light")

    controlFlowObfuscation.set(true)
    controlFlowStrength.set("light")

    renameClasses.set(false)
    renameMethods.set(false)
    renameFields.set(false)

    junkClasses.set(true)
    junkClassCount.set(1500)

    removeLineNumbers.set(true)
    removeLocalVariables.set(true)
    removeSourceFile.set(true)

    excludeClasses.set(listOf(
        "taboolib.**",
        "io.izzel.**",
        "kotlin.**",
        "kotlinx.**",
        "org.jetbrains.**",
        "org.intellij.**",
        "ink.ptms.**"
    ))

    keepClassNames.set(listOf(
        "com.mayihavek.mayadyeshachviewcontrol.MayAdyeshachViewControl"
    ))

    mappingFile.set("build/obfuscate-mapping.txt")

    finalObfuscation.set(false)
}

tasks.named("jar") {
    dependsOn("obfuscateClasses")
}