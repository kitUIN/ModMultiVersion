import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.0.0-beta9"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val shadowImplementation by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)
val interpreterVersion: String by project
group = "io.github.kituin"
version = project.version

repositories {
    mavenCentral()
    maven {
        name = "kituinMavenReleases"
        url = uri("https://maven.kituin.fun/releases")
    }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")

        create(type, version)
        instrumentationTools()

    }
    implementation("io.github.kituin:ModMultiVersionInterpreter:${interpreterVersion}")
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
    // dependsOn(tasks.named("prepareTestSandbox"))
    dependencies {
        include(dependency("io.github.kituin:ModMultiVersionInterpreter:${interpreterVersion}"))
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
//tasks.named("build") {
//    dependsOn(shadowJarTask)
//}
