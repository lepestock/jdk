#!/bin/bash

source project.sh

trap 'exit 130' INT

check_gentest_code_pattern() {
    local PATTERN="abstract value.*extends"
    rg "$PATTERN" testbase/java_tests/Gentest.java 2>&1 > /dev/null
}

check_jittester_pattern() {
    local PATTERN="abstract value.*extends"
#    rg ASSERT testbase/jittester.log
    rg ASSERT testbase/jittester.log | rg -v "non-abstract-size 1" | rg -v "non-abstract-size 0";
}


for i in {1..100}
do
    seed=${RANDOM};
#    seed=29905
#    echo $seed;
    repl-run --seed $seed 2>&1 > testbase/jittester.log && \
        check_gentest_code_pattern && \
        check_jittester_pattern && \
        echo "FOUND SEED: $seed"

#    test $? -eq 0 && check_gentest_code_pattern || false
#    test $? -eq 0 && check_jittester_pattern || false

#    test $? != 0 && echo "FOUND SEED: $seed"
#    && \
#        check_jittester_pattern && \
#        check_gentest_code_pattern
#    test $? != 0 && echo "FOUND SEED: $seed"
done
