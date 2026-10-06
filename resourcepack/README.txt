MCCases resource pack (optional)
-----------------------------------
Everything lives in assets/mccases/ and no vanilla file is replaced.
To merge with another pack, copy the assets/mccases folder into that pack.

For manual distribution, enable resource-pack.enabled in plugins/MCCases/config.yml
when players have this pack loaded. Otherwise custom items may show missing models.

For automatic distribution, enable resource-pack.distribution.enabled and configure
a public-url reachable by players. This also enables custom item models.

/csadmin exportpack rebuilds the pack for the server catalog at
plugins/MCCases/resourcepack/MCCases-ResourcePack.zip.
