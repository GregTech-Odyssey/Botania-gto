VERSION=1.13

.PHONY: jar
jar: build
	@cd build && jar -c -f ../gardenofglass-${VERSION}.jar assets data logo_gog.png META-INF pack.mcmeta

.PHONY: build
build:
	@mkdir -p build
	@cp -r assets data logo_gog.png META-INF pack.mcmeta build/
	@sed -i 's/@VERSION@/${VERSION}/' build/META-INF/mods.toml

.PHONY: clean
clean:
	@rm -rf build gardenofglass-*.jar
