#!/usr/bin/env groovy

Boolean call() {
  // Stage is locked if GCR tags "latest" and "latest-prerelease" are on two separate images
  sh (
    script: """
      gcloud container images list-tags ${IMAGE_BASE_AUTHWALK} --filter="tags=(latest,latest-prerelease)" --format=json \
        | jq -e '. | length > 1'
    """,
    returnStatus: true,
  ) == 0
}
