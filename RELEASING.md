# Release checklist

This checklist covers a user-managed Maven Central release. A deployment is not
public until a maintainer reviews the validated bundle and manually publishes
it in Central Portal.

## Before tagging

1. Confirm that the `dev.ai-router` namespace remains verified.
2. Confirm the license, repository URL, developer identity, and third-party
   notices.
3. Run Java 17 and Java 21 tests from a clean checkout.
4. Run the local fixture matrix, package inspection, Javadoc, and dependency
   audit.
5. Review README claims and remove any provider endorsement or bypass language.
6. Create an annotated, immutable release tag only after review.
7. Confirm the POM version and SCM tag match the release tag exactly.

## Artifact preflight

~~~
mvn -B -ntp clean verify
mvn -B -ntp package
unzip -l target/openai-compatible-contract-junit-*.jar
unzip -l target/*-sources.jar
unzip -l target/*-javadoc.jar
~~~

The POM must contain name, description, URL, license, developers, SCM,
coordinates, and complete dependencies. The Central bundle must also contain
PGP signatures and the required checksums for every deployed file.

## Central handoff

The namespace owner creates an expiring Central Portal token. CI receives the
token username, token password, armored private release key, and key passphrase
only through the protected `maven-central` GitHub environment. No token, private
key, passphrase, recovery code, or endpoint credential belongs in this
repository.

The release workflow checks out an immutable tag and runs:

~~~
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Prelease deploy
~~~

The Central plugin is configured with `autoPublish=false` and waits for
`VALIDATED`. Inspect the POM, main JAR, sources JAR, Javadoc JAR, signatures,
and checksums in Central Portal before clicking Publish.

The release signing key fingerprint is:

~~~
424D 95E1 4D48 4268 F13F  0719 6EBA 89CA FFC0 E0D8
~~~

Confirm that every detached signature resolves to this full fingerprint. The
key expires in August 2028 and must be extended and redistributed before then.

Published versions are immutable. A defect requires a new version rather than
rewriting or replacing an existing coordinate.
