#!/usr/bin/env groovy

def call(String tagName) {
  echo "Removing git tag: ${tagName}"
  sh "git push --delete origin ${tagName}"
}
