plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "9.0.0"
}

group   = "org.bsdevelopment.codefracture"
version = "0.1.2"

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.bsdevelopment.codefracture.App")
}

javafx {
    version = "21.0.2"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    // AtlantaFX
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")

    // Vineflower decompiler
    implementation("org.vineflower:vineflower:1.10.1")

    // RichTextFX for syntax highlighting
    implementation("org.fxmisc.richtext:richtextfx:0.11.2")

    // Ikonli icons (used by AtlantaFX)
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-materialdesign2-pack:12.3.1")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "org.bsdevelopment.codefracture.App"
    }

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    val platform = when {
        System.getProperty("os.name", "").lowercase().contains("win") -> "windows"
        System.getProperty("os.name", "").lowercase().contains("mac") -> "mac"
        else -> "linux"
    }
    archiveClassifier.set("all-$platform")
}

tasks.processResources {
    filesMatching("**/build.properties") {
        expand("version" to project.version)
    }
}

apply(from = "gradle/packaging.gradle.kts")
