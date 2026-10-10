plugins {
    java
}

group = "dev.plattnericus"
version = "1.2.2"
val releaseVersion = version.toString()
description = "Server-side CS2-style case, skin, pattern and knife system for Paper"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = providers.gradleProperty("paperApi").getOrElse("io.papermc.paper:paper-api:26.3.build.159-beta")

sourceSets {
    create("tools") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    named("toolsImplementation") { extendsFrom(configurations.compileOnly.get()) }
}

dependencies {
    compileOnly(paperApi)
    "toolsRuntimeOnly"("org.xerial:sqlite-jdbc:3.49.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

// Index of bundled default files; the plugin copies every missing one into its data folder.
val defaultsIndex by tasks.registering {
    val source = file("src/main/resources/defaults")
    val output = layout.buildDirectory.file("generated/defaults-index/defaults/index.txt")
    inputs.dir(source)
    outputs.file(output)
    doLast {
        val entries = source.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(source).invariantSeparatorsPath }
            .filter { it != "index.txt" }
            .sorted()
            .toList()
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(entries.joinToString("\n", postfix = "\n"))
        }
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/defaults-index"))
}

tasks.processResources {
    dependsOn(defaultsIndex)
    inputs.property("pluginVersion", project.version.toString())
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("MCCases-${project.version}.jar")
    from(files("LEGAL.md", "THIRD_PARTY_NOTICES.md", "docs/PRIVACY-IT.md")) {
        into("META-INF/mccases")
    }
    // Every production JAR ships the Fusion pack and the overlay used by runtime exports.
    dependsOn("resourcePack", "fusionOverlay")
    from(layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip")) {
        into("pack")
        rename { "MCCases-ResourcePack.zip" }
    }
    from(layout.buildDirectory.file("distributions/MCCases-Fusion-Overlay.zip")) {
        into("pack")
    }
}

// Regenerates the default PNG layers shipped inside the jar (weapon masks, shading, pattern textures).
tasks.register<JavaExec>("generateAssets") {
    group = "mccases"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.AssetGenerator")
    args(file("src/main/resources/defaults/textures").absolutePath)
}

// Renders a contact sheet of skins to verify the render pipeline visually.
tasks.register<JavaExec>("previewSheet") {
    group = "mccases"
    dependsOn(tasks.processResources)
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.PreviewSheet")
    args(file("src/main/resources/defaults").absolutePath, layout.buildDirectory.dir("preview").get().asFile.absolutePath)
}

// Generate managed assets first. The normal resourcePack/build path always applies Fusion.
tasks.register<JavaExec>("standardResourcePack") {
    group = "mccases"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.PackBuilder")
    args(
        file("src/main/resources/defaults").absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-Standard-${project.version}.zip").get().asFile.absolutePath
    )
}

tasks.register<Zip>("fusionOverlay") {
    group = "mccases"
    description = "Package the project's original custom fonts and icon without changing their bytes"
    archiveFileName.set("MCCases-Fusion-Overlay.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("resourcepack/fusion") {
        include("assets/**", "pack.png", "SOURCE.json")
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.FAIL
    doFirst {
        check(file("resourcepack/fusion/assets/minecraft/font/default.json").isFile) {
            "Fusion source is missing: restore resourcepack/fusion; builds never fall back to the standard pack"
        }
    }
}

tasks.register<JavaExec>("resourcePack") {
    group = "mccases"
    description = "Always merge current MCCases assets with the project's Fusion server textures"
    dependsOn("standardResourcePack", "fusionOverlay")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.FusionPackBuilder")
    args(
        providers.gradleProperty("fusionCurrentPack").getOrElse(layout.buildDirectory.file("distributions/MCCases-ResourcePack-Standard-${project.version}.zip").get().asFile.absolutePath),
        providers.gradleProperty("fusionBasePack").getOrElse(layout.buildDirectory.file("distributions/MCCases-Fusion-Overlay.zip").get().asFile.absolutePath),
        providers.gradleProperty("fusionOutputPack").getOrElse(layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip").get().asFile.absolutePath),
        project.version.toString()
    )
}

// Snapshot of the map palette used when the server API no longer exposes it.
tasks.register<JavaExec>("dumpPalette") {
    group = "mccases"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.PaletteDump")
    args(file("src/main/resources/map-palette.txt").absolutePath)
}

// Filmstrips of the inspect animations, as seen from the player's camera.
tasks.register<JavaExec>("inspectFilmstrip") {
    group = "mccases"
    dependsOn("resourcePack")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.InspectFilmstrip")
    jvmArgs("-Dmccases.filmstrip.movies=${providers.gradleProperty("filmstripMovies").getOrElse("true")}")
    args(file("src/main/resources/defaults/inspect.yml").absolutePath, layout.buildDirectory.dir("filmstrip").get().asFile.absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip").get().asFile.absolutePath)
}

val verifyFeatures by tasks.registering(JavaExec::class) {
    group = "verification"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.FeatureChecks")
    args(file("src/main/resources/defaults").absolutePath)
}

tasks.check { dependsOn(verifyFeatures) }

val verifyPack by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn("resourcePack")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.PackChecks")
    args(file("src/main/resources/defaults").absolutePath,
        providers.gradleProperty("packToVerify").orElse(layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip").get().asFile.absolutePath).get(),
        layout.buildDirectory.dir("verification").get().asFile.absolutePath)
}

tasks.check { dependsOn(verifyPack) }

val verifyFusionPack by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn("resourcePack")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.FusionPackChecks")
    args(
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-Standard-${project.version}.zip").get().asFile.absolutePath,
        layout.buildDirectory.file("distributions/MCCases-Fusion-Overlay.zip").get().asFile.absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip").get().asFile.absolutePath,
        project.version.toString()
    )
}
tasks.check { dependsOn(verifyFusionPack) }

tasks.register<JavaExec>("inspectRigPreview") {
    group = "mccases"
    dependsOn("resourcePack")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.RigMotionPreview")
    args(file("src/main/resources/defaults").absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-Fusion-HD-${project.version}.zip").get().asFile.absolutePath,
        layout.buildDirectory.dir("verification/animations").get().asFile.absolutePath)
}

tasks.register<Jar>("devChecks") {
    group = "verification"
    dependsOn(tasks.named("toolsClasses"))
    archiveFileName.set("MCCases-DevChecks-${project.version}.jar")
    from(sourceSets["tools"].output) {
        include("dev/plattnericus/cases/tools/CommandRuntimeChecks*.class", "dev/plattnericus/cases/tools/CaseGuideRuntimeChecks*.class", "dev/plattnericus/cases/tools/VisualRuntimeChecks*.class", "dev/plattnericus/cases/tools/RuntimeChecksPlugin*.class", "dev/plattnericus/cases/tools/RuntimeUiChecks*.class", "dev/plattnericus/cases/tools/CommerceRuntimeChecks*.class", "dev/plattnericus/cases/tools/ItemRuntimeChecks*.class")
    }
    val metadata = layout.buildDirectory.file("generated/dev-checks/plugin.yml")
    doFirst {
        metadata.get().asFile.apply {
            parentFile.mkdirs()
            writeText("name: MCCasesDevChecks\nversion: $releaseVersion\nmain: dev.plattnericus.cases.tools.RuntimeChecksPlugin\napi-version: '26.2'\ndepend: [MCCases]\ncommands:\n  mccasesdevcheck:\n    permission: mccases.admin\n")
        }
    }
    from(metadata) { rename { "plugin.yml" } }
}
