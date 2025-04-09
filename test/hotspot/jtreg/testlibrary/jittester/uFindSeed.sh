#!/bin/bash

source project.sh

trap 'exit 130' INT

check_gentest_code_pattern() {
    local PATTERN="abstract value.*extends"
    rg "$PATTERN" testbase/java_tests/Gentest.java 2>&1 > /dev/null
    test $? -eq 0 && return 1
}

check_jittester_pattern() {
    local PATTERN="abstract value.*extends"
    rg ASSERT testbase/jittester.log
    test $? -eq 0 && return 1
}


for i in {1..100}
do
    seed=${RANDOM};
    echo $seed;
    repl-run --seed $seed 2>&1 > testbase/jittester.log && \
        check_jittester_pattern && \
        check_gentest_code_pattern
    test $? != 0 && echo "FOUND SEED: $seed"
done
