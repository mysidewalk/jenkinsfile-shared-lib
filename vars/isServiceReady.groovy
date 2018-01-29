#!/usr/bin/env groovy

Boolean call(String gceInstance, String zone = 'us-central1-b') {
  // Service is ready if system
  sh (
    script: """
      gcloud compute instances describe ${instance} --zone ${zone} | sed -n -e 's/^status: //p'
    """,
    returnStdout: true,
  ).trim() == 'RUNNING'
}
