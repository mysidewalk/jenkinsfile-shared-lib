#!/usr/bin/env groovy

void call(String tagName, String message) {
  echo "Adding git tag: ${tagName}"
  sh "git tag -d ${tagName} || true"
  sh """
    git tag -a ${tagName} -m "${message}"
  """
  sh "git push -f origin ${tagName}"
}
