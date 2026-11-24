#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

# Generate the API docs
./gradlew dokkaGenerate

# Copy in special files that GitHub wants in the project root.
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
