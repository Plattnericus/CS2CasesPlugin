plugins {
    java
}

group = "dev.plattnericus"
version = "1.2.1"
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
    // the plugin ships its own resource pack (extracted to plugins/MCCases/resourcepack/)
    dependsOn("resourcePack")
    from(layout.buildDirectory.file("distributions/MCCases-ResourcePack-${project.version}.zip")) {
        into("pack")
        rename { "MCCases-ResourcePack.zip" }
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

// Builds the optional, mergeable resource pack from the default catalog.
tasks.register<JavaExec>("resourcePack") {
    group = "mccases"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.PackBuilder")
    args(
        file("src/main/resources/defaults").absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-${project.version}.zip").get().asFile.absolutePath
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
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-${project.version}.zip").get().asFile.absolutePath)
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
        providers.gradleProperty("packToVerify").orElse(layout.buildDirectory.file("distributions/MCCases-ResourcePack-${project.version}.zip").get().asFile.absolutePath).get(),
        layout.buildDirectory.dir("verification").get().asFile.absolutePath)
}

tasks.check { dependsOn(verifyPack) }

tasks.register<JavaExec>("inspectRigPreview") {
    group = "mccases"
    dependsOn("resourcePack")
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("dev.plattnericus.cases.tools.RigMotionPreview")
    args(file("src/main/resources/defaults").absolutePath,
        layout.buildDirectory.file("distributions/MCCases-ResourcePack-${project.version}.zip").get().asFile.absolutePath,
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
