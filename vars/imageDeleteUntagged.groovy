#!/usr/bin/env groovy

void call(String image) {
  echo "Deleting untagged images of ${image}."
  // Filtering is done on image and then dangling on that subset, potentially resulting in not truly dangling images
  sh "docker images '${image}' -qf 'dangling=true' | xargs --no-run-if-empty docker rmi || true"
}
