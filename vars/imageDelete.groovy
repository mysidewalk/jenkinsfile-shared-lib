#!/usr/bin/env groovy

void call(String image, String tag) {
  echo "Deleting ${image}:${tag}."
  sh "docker rmi ${image}:${tag}"
  sh "gcloud container images untag -q ${image}:${tag}"
}
