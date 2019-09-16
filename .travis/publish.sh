#!/bin/bash -x

# Builds of tagged revisions are published to Bintray

# Travis runs a build on revisions, including on new tags.
# Builds for a tag have TRAVIS_TAG defined, which we use for identifying tagged builds.
# Checking the local git clone would not work because git on travis does not fetch tags.

# The version number to be published is extracted from the tag, e.g., v1.2.3 publishes
# version 1.2.3 on all combinations of the travis matrix where `[ "$RELEASE_COMBO" = "true" ]`.

# In order to build a previously released version against a new (binary incompatible) Scala release,
# a new commit that modifies (and prunes) the Scala versions in .travis.yml needs to be added on top
# of the existing tag. Then a new tag can be created for that commit, e.g., `v1.2.3#2.13.0-M5`.
# Everything after the `#` in the tag name is ignored.

## cross publish everything when Scala 2.12.x is selected.
if [[ "$TRAVIS_SCALA_VERSION" =~ 2\.1[2]\..* && "${TRAVIS_PULL_REQUEST}" == "false" && "${TRAVIS_REPO_SLUG}" == "sbt/sbt-pgp" ]]; then
  RELEASE_COMBO=true;
fi

verPat="[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)?"
tagPat="^v$verPat(#.*)?$"

if [[ "$TRAVIS_TAG" =~ $tagPat ]]; then
  tagVer=${TRAVIS_TAG}
  tagVer=${tagVer#v}   # Remove `v` at beginning.
  tagVer=${tagVer%%#*} # Remove anything after `#`.
  publishVersion='set every version := "'$tagVer'"'

  if [ "$RELEASE_COMBO" = "true" ]; then
    java -version
    echo "Releasing $tagVer with Scala $TRAVIS_SCALA_VERSION"

    ## change this to match your encrypted key
    openssl aes-256-cbc -K $encrypted_fcdd190fa04a_key -iv $encrypted_fcdd190fa04a_iv -in .travis/secret-key.asc.enc -out .travis/secret-key.asc -d
    echo $PGP_PASSPHRASE | gpg --passphrase-fd 0 --batch --yes --import .travis/secret-key.asc

    ## change this to match your build
    sbt "$publishVersion" "clean" "+publishSigned"
  fi
fi
