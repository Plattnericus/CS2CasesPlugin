Historical MCCases export snapshot
---------------------------------
Use release/MCCases-ResourcePack-Fusion-HD-1.2.2.zip for the current catalog,
clean 128px edges, corrected ring knives and original custom server images.
The assets/mccases/ tree in this directory is historical, not the current build.

Permanent custom source assets live in fusion/. Every normal Gradle build merges
them with newly generated MCCases assets and embeds the result in the plugin JAR.
Read fusion/README.md for the source files and preservation checks.

For manual distribution, enable resource-pack.enabled in plugins/MCCases/config.yml
when players have this pack loaded. Otherwise custom items may show missing models.

For automatic distribution, enable resource-pack.distribution.enabled and configure
a public-url reachable by players. This also enables custom item models.

/csadmin exportpack rebuilds the Fusion pack for the actual server catalog at
plugins/MCCases/resourcepack/MCCases-ResourcePack.zip.

See the project README.md for current downloads, and LEGAL.md for rights and contact.
