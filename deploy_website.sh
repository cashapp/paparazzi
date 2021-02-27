#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

REPO="git@github.com:cashapp/paparazzi.git"
DIR=../temp-clone-paparazzi

# Delete any existing temporary website clone
rm -rf $DIR

# Clone the current repo into temp folder
# git clone $REPO $DIR
cp -r . $DIR

# Move working directory into temp folder
pushd $DIR

# Generate the API docs
./gradlew \
  :paparazzi:dokka

# Copy in special files that GitHub wants in the project root.
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md

# Build the site and push the new files up to GitHub
mkdocs gh-deploy

# Delete our temp folder
popd
rm -rf $DIR
