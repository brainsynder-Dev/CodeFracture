plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.0.0"
}

group   = "org.bsdevelopment.codefracture"
version = rootProject.version

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.bsdevelopment.codefracture.launcher.Launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "org.bsdevelopment.codefracture.launcher.Launcher"
    }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    archiveFileName.set("Launcher.jar")
}
