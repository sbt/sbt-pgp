## Tag Driven Releasing

### Initial setup for the repository

To configure tag driven releases from Travis CI.

Generate a key pair for this repository.

```
gpg --gen-key
```
- For real name, use "$PROJECT_NAME bot". For sbt-something use "sbt-something bot"
- For email, use your own email address
- For passphrase, generate a random password with a password manager

Publish the public key to `pool.sks-keyservers.net`.

```
gpg --keyserver pool.sks-keyservers.net --send-keys <keyid>
```

Export the secret key, and encrypt it.

```
mkdir .travis
cd .travis
gpg --export-secret-keys --armor <keyid> > .travis/secret-key.asc

REPO=sbt/sbt-something
travis encrypt-file .travis/secret-key.asc --add --repo $REPO
rm .travis/secret-key.asc
git add .travis/secret-key.asc.enc
echo -n 'PGP_PASSPHRASE: ' && read -s PGP_PASSPHRASE
travis encrypt PGP_PASSPHRASE="$PGP_PASSPHRASE" --add --repo $REPO
```

Store other secrets as encrypted environment variables using `travis encrypt`.

```
# For Bintray
echo -n 'BINTRAY_USER: ' && read -s BINTRAY_USER
travis encrypt BINTRAY_USER="$BINTRAY_USER" --add --repo $REPO
echo -n 'BINTRAY_PASS: ' && read -s BINTRAY_PASS
travis encrypt BINTRAY_PASS="$BINTRAY_PASS" --add --repo $REPO
```

Edit `.travis.yml` to run `.travis/publish.sh` on success, and edit that script to use the tasks required for this project. Ensure that `RELEASE_COMBO` is true for build matrix combinations that should be released (when building a tag).

Add comments in `.travis.yml` to identify the name of each environment variable encoded in a secure section.

After these steps, your `.travis.yml` should contain config of the form:

```yaml
sudo: false
language: scala
jdk: openjdk8
env:
  global:
  # PGP_PASSPHRASE=
  - secure: xxxxxx=
  # BINTRAY_USER=
  - secure: xxxxxx=
  # BINTRAY_PASS
  - secure: xxxxxx=

script:
- ....

matrix:
  include:
  - scala: 2.10.7
  - scala: 2.12.10

after_success:
  - .travis/publish.sh
```
