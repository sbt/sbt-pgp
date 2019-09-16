## Tag Driven Releasing

### Initial setup for the repository

To configure tag driven releases from Travis CI.

Do **NOT** use your personal GPG key for CI signing. Create a fresh gpg key that you will share with Travis CI and ONLY use for this project.

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
gpg --export-secret-keys --armor <keyid> > secret-key.asc
REPO=sbt/sbt-something
travis encrypt-file secret-key.asc --repo $REPO
rm secret-key.asc
```

This should output:

```
encrypting secret-key.asc for sbt/sbt-pgp
storing result as secret-key.asc.enc
storing secure env variables for decryption

Please add the following to your build script (before_install stage in your .travis.yml, for instance):

    openssl aes-256-cbc -K $encrypted_1234567890ab_key -iv $encrypted_1234567890ab_iv -in secret-key.asc.enc -out secret-key.asc -d

Pro Tip: You can add it automatically by running with --add.

Make sure to add secret-key.asc.enc to the git repository.
Make sure not to add secret-key.asc to the git repository.
Commit all changes to your .travis.yml.
```

Follow the instruction, and double check that you have deleted `secret-key.asc`.

Edit `publish.sh` to decrypt `.travis/secret-key.asc.enc` to `.travis/secret-key.asc`:

```
openssl aes-256-cbc -K $encrypted_<1234567890ab>_key -iv $encrypted_<1234567890ab>_iv -in .travis/secret-key.asc.enc -out .travis/secret-key.asc -d
```

Add the encrypted file.

```
git add secret-key.asc.enc
```

Store PGP_PASSPHRASE as an encrypted environment variable:

```
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

### Testing

  1. Follow the release process below to create a dummy release (e.g., `v0.1.0-M1`).
     Confirm that the release was published to Bintray etc.

### Performing a release

  1. Create a git tag (e.g. `v2.0.0`), and push it.
