#!/usr/bin/env groovy

def call(String image) {
  echo "Deleting untagged images of ${image}."
  // Filtering is done on image and then dangling on that subset, potentially resulting in not truly dangling images
  sh "docker images '${image}' -qf 'dangling=true' | xargs --no-run-if-empty docker rmi || true"
  sh """
    gcloud container images list-tags ${image} --filter='NOT tags:*' --format=json \
      | jq '.[].digest' \
      | xargs --no-run-if-empty -I {} gcloud container images delete -q ${image}@{}
  """
}
