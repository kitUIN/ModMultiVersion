import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val shadowImplementation by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)
val interpreter_version: String by project
group = "io.github.kituin"
version = project.version

repositories {
    mavenCentral()
    maven {
        name = "kituinMavenReleases"
        url = uri("https://maven.kituin.fun/releases")
    }
}

dependencies {
    shadowImplementation("io.github.kituin:ModMultiVersionInterpreter:${interpreter_version}")
}
// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2.6")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        include(dependency("io.github.kituin:ModMultiVersionInterpreter:${interpreter_version}"))
    }
    // automatically remove all classes of dependencies that are not used by the project
    minimize()
    // remove default "-all" suffix to make shadow jar look like original one.
    archiveClassifier.set("")
    // use only the dependencies from the shadowImplementation configuration
    configurations = listOf(shadowImplementation)
}
configurations {
    artifacts {
        runtimeElements(shadowJarTask)
        apiElements(shadowJarTask)
    }
}
tasks.named("build") {
    dependsOn(shadowJarTask)
}
