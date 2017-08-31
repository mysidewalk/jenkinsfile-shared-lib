#!/usr/bin/env groovy

Boolean call(String imageName) {
  // Stage is locked if GCR tags "latest" and "latest-prerelease" are on two separate images
  sh (
    script: """
      gcloud container images list-tags ${imageName} --filter="tags=(latest,latest-prerelease)" --format=json \
        | jq -e '. | length > 1'
    """,
    returnStatus: true,
  ) == 0
}
