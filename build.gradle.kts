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

    // JFiglet - 使用 taboo() 打包并重定向
    taboo("com.github.lalyos:jfiglet:0.0.9")

    // Exposed ORM 框架 - 使用 taboo() 打包并重定向
    taboo("org.jetbrains.exposed:exposed-core:$exposedVersion")
    taboo("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    taboo("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    // SQLite JDBC 驱动 - 使用 taboo() 打包并重定向
    taboo("org.xerial:sqlite-jdbc:3.46.1.0")

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