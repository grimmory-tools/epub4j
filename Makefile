LATEST_TAG?=`git tag|sort -t. -k 1,1n -k 2,2n -k 3,3n -k 4,4n | tail -1`

SHELL := /bin/bash
SDK := source "$$HOME/.sdkman/bin/sdkman-init.sh" && sdk env &&

help:
	cat Makefile.txt

clean:
	$(SDK) ./gradlew clean

pull:
	git pull
	git submodule update --init --recursive
	git submodule foreach git checkout main
	git submodule foreach git pull

.PHONY: build
build:
	$(SDK) ./gradlew build --warning-mode all

build-fast:
	$(SDK) ./gradlew build -Pcheck=false -x test --warning-mode all

release:
	$(SDK) ./gradlew release --warning-mode all

publish: build
	rm -rf $$HOME/.m2/repository/org/grimmory/epub4j-core
	rm -rf $$HOME/.m2/repository/org/grimmory/epub4j-native
	rm -rf $$HOME/.m2/repository/org/grimmory/comic4j
	$(SDK) ./gradlew publishToMavenLocal --warning-mode all

publish-remote: publish
	$(SDK) ./gradlew publishMavenJavaPublicationToMavenCentralRepository --warning-mode all

publish-central: publish-remote
	@echo "Uploading staging repository to Maven Central Portal..."
	@if [ -z "$$SONATYPE_TOKEN_FILE" ]; then echo "SONATYPE_TOKEN_FILE is not set" >&2; exit 1; fi
	@if [ ! -r "$$SONATYPE_TOKEN_FILE" ]; then echo "SONATYPE_TOKEN_FILE does not exist or is not readable: $$SONATYPE_TOKEN_FILE" >&2; exit 1; fi
	@curl -fsS -X POST \
		"https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/org.grimmory?publishing_type=automatic" \
		-H "Authorization: Bearer $$(cat "$$SONATYPE_TOKEN_FILE")"
	@echo "\nDone. Check https://central.sonatype.com/publishing/deployments for status."

