project.evaluationDependsOn(":launcher")

val appName           = "CodeFracture"
val appVersion        = project.version.toString().replace("-SNAPSHOT", "")
val appShadowJar      = tasks.named("shadowJar")
val launcherShadowJar = project(":launcher").tasks.named("shadowJar")
val distDir           = layout.buildDirectory.dir("dist")
val stagingDir        = layout.buildDirectory.dir("packaging/staging")
val winWorkDir        = layout.buildDirectory.dir("packaging/windows")
val winInstallerDir   = layout.buildDirectory.dir("packaging/windows-installer")
val linuxWorkDir      = layout.buildDirectory.dir("packaging/linux")
val linuxDebDir       = layout.buildDirectory.dir("packaging/linux-deb")

// ── Shared staging ────────────────────────────────────────────────────────

/** Copies the app JAR and Launcher.jar into a single input directory for jpackage. */
val stagePackagingArtifacts by tasks.registering {
    group       = "distribution"
    description = "Stages the app and launcher JARs for jpackage"
    dependsOn(appShadowJar, launcherShadowJar)

    inputs.files(appShadowJar.map { it.outputs.files })
    inputs.files(launcherShadowJar.map { it.outputs.files })
    outputs.dir(stagingDir)

    doFirst {
        val staging = stagingDir.get().asFile
        staging.deleteRecursively()
        staging.mkdirs()
        val appJar      = appShadowJar.get().outputs.files.singleFile
        val launcherJar = launcherShadowJar.get().outputs.files.singleFile
        appJar.copyTo(File(staging, appJar.name), overwrite = true)
        launcherJar.copyTo(File(staging, "Launcher.jar"), overwrite = true)
    }
}

// helper to avoid repeating the jpackage executable path
fun jpackageExe(): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    return "${System.getProperty("java.home")}/bin/jpackage${if (isWindows) ".exe" else ""}"
}

// common jpackage args shared by all Windows package types
fun commonWinArgs(staging: File): List<String> = listOf(
    "--name",         appName,
    "--app-version",  appVersion,
    "--input",        staging.absolutePath,
    "--main-jar",     "Launcher.jar",
    "--main-class",   "org.bsdevelopment.codefracture.launcher.Launcher",
    "--icon",         project.file("src/main/resources/logo.ico").absolutePath,
    "--java-options", "-Xmx512m"
)

// common jpackage args shared by all Linux package types
fun commonLinuxArgs(staging: File): List<String> = listOf(
    "--name",         appName.lowercase(),
    "--app-version",  appVersion,
    "--input",        staging.absolutePath,
    "--main-jar",     "Launcher.jar",
    "--main-class",   "org.bsdevelopment.codefracture.launcher.Launcher",
    "--icon",         project.file("src/main/resources/logo_256.png").absolutePath,
    "--java-options", "-Xmx512m"
)

// ── Windows (portable app-image) ─────────────────────────────────────────

/** Creates a Windows app-image (EXE + bundled JRE) via jpackage. */
val jpackageWindows by tasks.registering(Exec::class) {
    group       = "distribution"
    description = "Creates a Windows app-image (EXE + bundled JRE) via jpackage"
    dependsOn(stagePackagingArtifacts)

    inputs.files(stagePackagingArtifacts.map { it.outputs.files })
    inputs.file(project.file("src/main/resources/logo.ico"))
    outputs.dir(winWorkDir)

    doFirst {
        val workDir = winWorkDir.get().asFile
        workDir.deleteRecursively()
        workDir.mkdirs()
        val staging = stagingDir.get().asFile
        executable = jpackageExe()
        args(listOf("--type", "app-image", "--dest", workDir.absolutePath) + commonWinArgs(staging))
    }
}

/** Zips the Windows app-image into build/dist/CodeFracture-<version>-windows.zip. */
val buildWindowsPackage by tasks.registering(Zip::class) {
    group       = "distribution"
    description = "Packages the Windows app-image into $appName-$appVersion-windows.zip"
    dependsOn(jpackageWindows)

    from(winWorkDir.map { it.dir(appName) }) { into(appName) }

    archiveFileName.set("$appName-$appVersion-windows.zip")
    destinationDirectory.set(distDir)
}

// ── Windows (MSI installer) ───────────────────────────────────────────────

/**
 * Creates a Windows MSI installer via jpackage.
 * Requires WiX 3.x (https://wixtoolset.org) to be on PATH.
 * The MSI registers with Add/Remove Programs (per-user, no admin needed),
 * adds a Start Menu entry, and offers a Desktop shortcut.
 */
val jpackageWindowsMsi by tasks.registering(Exec::class) {
    group       = "distribution"
    description = "Creates a Windows MSI installer via jpackage (requires WiX 3.x)"
    dependsOn(stagePackagingArtifacts)

    inputs.files(stagePackagingArtifacts.map { it.outputs.files })
    inputs.file(project.file("src/main/resources/logo.ico"))
    outputs.dir(winInstallerDir)

    doFirst {
        val destDir = winInstallerDir.get().asFile
        destDir.deleteRecursively()
        destDir.mkdirs()
        val staging = stagingDir.get().asFile
        executable = jpackageExe()
        args(
            listOf("--type", "msi", "--dest", destDir.absolutePath) +
            commonWinArgs(staging) +
            listOf(
                "--win-dir-chooser",
                "--win-menu",
                "--win-menu-group",  appName,
                "--win-shortcut",
                "--win-per-user-install"
            )
        )
    }
}

/** Copies the MSI into build/dist/. */
val buildWindowsInstaller by tasks.registering(Copy::class) {
    group       = "distribution"
    description = "Copies the Windows MSI installer into build/dist/"
    dependsOn(jpackageWindowsMsi)

    from(winInstallerDir) { include("*.msi") }
    into(distDir)
}

// ── Linux (portable tar.gz) ───────────────────────────────────────────────

/** Generates the Linux shell launcher, install script, and README. */
val generateLinuxFiles by tasks.registering {
    group       = "distribution"
    description = "Generates Linux shell launcher, install.sh, and README.txt"
    dependsOn(appShadowJar, launcherShadowJar)

    inputs.files(appShadowJar.map { it.outputs.files })
    inputs.files(launcherShadowJar.map { it.outputs.files })
    outputs.dir(linuxWorkDir)

    doFirst {
        val dir = linuxWorkDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()

        val launcher = File(dir, appName)
        launcher.writeText(
            "#!/bin/sh\n" +
            "SCRIPT_DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n\n" +
            "if [ -n \"\$JAVA_HOME\" ] && [ -x \"\$JAVA_HOME/bin/java\" ]; then\n" +
            "    JAVA=\"\$JAVA_HOME/bin/java\"\n" +
            "elif command -v java >/dev/null 2>&1; then\n" +
            "    JAVA=\"\$(command -v java)\"\n" +
            "elif [ -x \"\$HOME/.sdkman/candidates/java/current/bin/java\" ]; then\n" +
            "    JAVA=\"\$HOME/.sdkman/candidates/java/current/bin/java\"\n" +
            "elif [ -x \"/usr/bin/java\" ]; then\n" +
            "    JAVA=\"/usr/bin/java\"\n" +
            "elif [ -x \"/usr/local/bin/java\" ]; then\n" +
            "    JAVA=\"/usr/local/bin/java\"\n" +
            "elif [ -x \"/usr/lib/jvm/default-java/bin/java\" ]; then\n" +
            "    JAVA=\"/usr/lib/jvm/default-java/bin/java\"\n" +
            "else\n" +
            "    JAVA=\$(ls /usr/lib/jvm/*/bin/java 2>/dev/null | head -1)\n" +
            "fi\n\n" +
            "if [ -z \"\$JAVA\" ] || [ ! -x \"\$JAVA\" ]; then\n" +
            "    MSG=\"$appName requires Java 21 or later, which could not be found.\\nPlease install Java and try again.\"\n" +
            "    if command -v zenity >/dev/null 2>&1; then\n" +
            "        zenity --error --title=\"$appName\" --text=\"\$MSG\" 2>/dev/null\n" +
            "    elif command -v xmessage >/dev/null 2>&1; then\n" +
            "        xmessage -center \"\$MSG\"\n" +
            "    fi\n" +
            "    exit 1\n" +
            "fi\n\n" +
            "exec \"\$JAVA\" -jar \"\$SCRIPT_DIR/lib/Launcher.jar\" \"\$@\"\n"
        )
        launcher.setExecutable(true, false)

        val installSh = File(dir, "install.sh")
        installSh.writeText(
            "#!/bin/sh\n" +
            "set -e\n" +
            "SCRIPT_DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
            "INSTALL_DIR=\"\$HOME/.local/share/$appName\"\n" +
            "DESKTOP_DIR=\"\$HOME/.local/share/applications\"\n\n" +
            "mkdir -p \"\$INSTALL_DIR\" \"\$DESKTOP_DIR\"\n" +
            "cp -r \"\$SCRIPT_DIR/lib\" \"\$SCRIPT_DIR/icons\" \"\$SCRIPT_DIR/$appName\" \"\$INSTALL_DIR/\"\n" +
            "chmod +x \"\$INSTALL_DIR/$appName\"\n\n" +
            "printf '[Desktop Entry]\\nName=$appName\\nComment=Java Decompiler\\n" +
            "Exec=%s\\nIcon=%s\\nTerminal=false\\nType=Application\\nCategories=Development;Java;\\n' \\\n" +
            "    \"\$INSTALL_DIR/$appName\" \"\$INSTALL_DIR/icons/codefracture.png\" \\\n" +
            "    > \"\$DESKTOP_DIR/$appName.desktop\"\n\n" +
            "echo \"Installed to \$INSTALL_DIR\"\n" +
            "echo \"Desktop entry written to \$DESKTOP_DIR/$appName.desktop\"\n"
        )
        installSh.setExecutable(true, false)

        File(dir, "README.txt").writeText(
            "$appName — Java Decompiler\n" +
            "==========================\n\n" +
            "Requirements: Java 21 or later\n\n" +
            "Installation\n" +
            "------------\n" +
            "    chmod +x install.sh && ./install.sh\n\n" +
            "This installs $appName to ~/.local/share/$appName and registers\n" +
            "it with your desktop environment.\n\n" +
            "Uninstallation\n" +
            "--------------\n" +
            "    rm -rf ~/.local/share/$appName\n" +
            "    rm ~/.local/share/applications/$appName.desktop\n"
        )
    }
}

/** Assembles the Linux tar.gz with the shell launcher, both JARs, and icon. */
val buildLinuxPackage by tasks.registering(Tar::class) {
    group       = "distribution"
    description = "Packages the JARs with a shell launcher and .desktop file for Linux"
    dependsOn(generateLinuxFiles, appShadowJar, launcherShadowJar)

    compression = Compression.GZIP
    archiveFileName.set("$appName-$appVersion-linux.tar.gz")
    destinationDirectory.set(distDir)

    from(linuxWorkDir) {
        into(appName)
        include(appName, "install.sh")
        filePermissions { unix("755") }
    }
    from(linuxWorkDir) {
        into(appName)
        include("README.txt")
        filePermissions { unix("644") }
    }
    from(appShadowJar.map { it.outputs.files.singleFile }) {
        into("$appName/lib")
        filePermissions { unix("644") }
    }
    from(launcherShadowJar.map { it.outputs.files.singleFile }) {
        into("$appName/lib")
        rename { "Launcher.jar" }
        filePermissions { unix("644") }
    }
    from(project.file("src/main/resources/logo_256.png")) {
        into("$appName/icons")
        rename { "codefracture.png" }
        filePermissions { unix("644") }
    }
}

// ── Linux (DEB installer) ─────────────────────────────────────────────────

/**
 * Creates a Linux .deb package via jpackage.
 * Requires dpkg-deb and fakeroot (both available on Ubuntu/Debian).
 * Installs to /opt/codefracture, registers a .desktop entry, and bundles the JRE.
 */
val jpackageLinuxDeb by tasks.registering(Exec::class) {
    group       = "distribution"
    description = "Creates a Linux .deb package via jpackage (requires dpkg-deb, fakeroot)"
    dependsOn(stagePackagingArtifacts)

    inputs.files(stagePackagingArtifacts.map { it.outputs.files })
    inputs.file(project.file("src/main/resources/logo_256.png"))
    outputs.dir(linuxDebDir)

    doFirst {
        val destDir = linuxDebDir.get().asFile
        destDir.deleteRecursively()
        destDir.mkdirs()
        val staging = stagingDir.get().asFile
        executable = jpackageExe()
        args(
            listOf("--type", "deb", "--dest", destDir.absolutePath) +
            commonLinuxArgs(staging) +
            listOf(
                "--linux-shortcut",
                "--linux-app-category", "Development",
                "--linux-menu-group",   "Development"
            )
        )
    }
}

/** Copies the .deb into build/dist/. */
val buildLinuxDeb by tasks.registering(Copy::class) {
    group       = "distribution"
    description = "Copies the Linux .deb package into build/dist/"
    dependsOn(jpackageLinuxDeb)

    from(linuxDebDir) { include("*.deb") }
    into(distDir)
}
