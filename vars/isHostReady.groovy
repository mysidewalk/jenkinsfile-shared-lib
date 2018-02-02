#!/usr/bin/env groovy

Boolean call(String gceInstance, String zone = 'us-central1-b') {
  // Host is ready if GCE instance status is RUNNING
  sh (
    script: """
      gcloud compute instances describe ${instance} --zone ${zone} --format='get(status)'
    """,
    returnStdout: true,
  ).trim() == 'RUNNING'
}
