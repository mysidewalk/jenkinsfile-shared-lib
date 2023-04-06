#!/usr/bin/env groovy

void call(Set<String> hosts, String command) {
  sh """
    hosts="${hosts.sort().join(' ')}"
    tmpdir=/tmp/pssh.\$\$
    mkdir -p \$tmpdir

    for host in \${hosts[@]}; do
      timeout 300 ssh \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        -o ConnectTimeout=10 \
        -o BatchMode=yes \
        \${host} \
        "${command}" > \${tmpdir}/\${host} 2>&1 &
    done
    
    wait
    
    for host in \${hosts[@]}; do
      printf "\n\n#################\nOutput: \${host}\n#################\n"
      cat \${tmpdir}/\${host}
    done
    
    rm -rf \${tmpdir}
  """
}
