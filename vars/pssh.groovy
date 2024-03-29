#!/usr/bin/env groovy

void call(Set<String> hosts, String command) {
  sh """
    set +x

    hosts="${hosts.sort().join(' ')}"
    jobs=()
    fail=0
    tmpdir=\$(mktemp -d)

    printf "Running command against hosts...\n"
    printf "command: ${command}\n"
    printf "hosts: \${hosts}\n\n"

    for host in \${hosts[@]}; do
      timeout 300 ssh \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        -o ConnectTimeout=10 \
        -o BatchMode=yes \
        \${host} \
        "${command}" > \${tmpdir}/\${host} 2>&1 &

        jobs+=(\$!)
    done
    
    for pid in \${jobs[@]}; do
      wait \$pid || let "fail=1"
    done
    
    for host in \${hosts[@]}; do
      printf "\n\n#################\nOutput: \${host}\n#################\n"
      cat \${tmpdir}/\${host}
    done
    
    rm -rf \${tmpdir}

    if (( fail != 0 )); then
      echo "One or more ssh commands had a bad exit code"
      exit 1
    fi
  """
}
