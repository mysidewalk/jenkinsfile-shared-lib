#!/usr/bin/env groovy

// until this issues gets resolved https://issues.jenkins-ci.org/browse/JENKINS-44053...

void call(boolean conditional) {
  if ( !conditional ) {
    echo 'Stage skipped due to when conditional'
  }
  conditional
}
