#!/usr/bin/env groovy

void call(String tagName) {
  echo "Removing git tag: ${tagName}"
  sh "git push --delete origin ${tagName}"
}
