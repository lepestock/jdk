#!/bin/bash

repl-build ()
{
    time make JAVAC=~/portable/jdk-25-16//bin/javac rebuild 2>&1
}

repl-run ()
{
    java -cp build/classes --add-opens java.base/java.util=ALL-UNNAMED jdk.test.lib.jittester.JavaCodeGenerator \
        --classes-file ./conf/classes.lst \
        --exclude-methods-file ./conf/exclude.methods.lst \
        --testbase-dir testbase --temp-dir tmp --print-hierarchy true --main-class Gentest $@
}

repl-gentest-build ()
{
    javac -Xlint:none -cp ./build/classes/ --release 25 --enable-preview -Xmaxerrs 5 src/jdk/test/lib/jittester/jtreg/Printer.java testbase/java_tests/Gentest.java -d testbase/java_tests/
}

repl-gentest-run()
{
    java -cp testbase/java_tests/ --enable-preview Gentest
}

repl-gentest-full() {
    rm -rf $jittester/testbase/java_tests/*
    # command to measure time... [ $(echo $(/usr/bin/time -f '%e' sleep 2 2>&1)' > 1.0' | bc -l) -eq 1 ]

    local chosen_seed=${SEED:-$RANDOM}

    local outcome=""
    local success=0;
    if [ $success -eq 0 ]; then
        export -f repl-run
        timeout 30s bash -c "repl-run --seed $chosen_seed --disable-downcasts true" 2>&1 > /dev/null
        success=$?

        if [ $success -eq "124" ]; then
            outcome="$outcome :generation_timeout"
        fi
    fi

    if [ $success -eq 0 ]; then
        repl-gentest-build > testbase/compilation.log 2>&1; success=$?
        if [ $success != 0 ]; then
            outcome="$outcome :compilation_error"
        fi
    fi

    if [ $success -eq 0 ]; then
        local before=$(date +%s)
        timeout 2m java -cp testbase/java_tests/ --enable-preview Gentest > testbase/Gentest.output 2>&1
        local elapsed=$(($(date +%s)-before))
        success=$?
        if [ $success -eq "124" ]; then
            outcome="$outcome :huge_timeout"
        else
            [ $elapsed -gt 15 ] && outcome="$outcome :timeout"
            [ $(cat testbase/Gentest.output | wc -l) -lt "30" ] && outcome="$outcome :poor_output";
        fi
        echo "$chosen_seed $outcome"
    fi
}

repl-gentest-full-dev() {
    rm -rf $jittester/testbase/java_tests/*
    # command to measure time... [ $(echo $(/usr/bin/time -f '%e' sleep 2 2>&1)' > 1.0' | bc -l) -eq 1 ]

    local chosen_seed=${SEED:-$RANDOM}
    echo "SEED: $chosen_seed"

    local success=0;
    if [ $success -eq 0 ]; then
        echo ":gentest-generate"
        export -f repl-run
        timeout 30s bash -c "repl-run --seed $chosen_seed --disable-downcasts true" 2>&1 > /dev/null
        success=$?

        if [ $success -eq "124" ]; then
            echo "Outcome: generation timeout"
#        elif [ $success -ne "0"]; then         # Maybe we'll need to separate those in the future....
#            echo "Outcome: Generation failure"
        fi
    fi

    if [ $success -eq 0 ]; then
        echo ":gentest-build"
        repl-gentest-build > testbase/compilation.log 2>&1; success=$?
        if [ $success != 0 ]; then
            echo "Outcome: Compilation error"
            cat testbase/compilation.log
        fi
    fi

    local outcome=""
    if [ $success -eq 0 ]; then
        echo ":gentest-run";
        local before=$(date +%s)
        timeout 2m java -cp testbase/java_tests/ --enable-preview Gentest > testbase/Gentest.output 2>&1
        local elapsed=$(($(date +%s)-before))
        success=$?
        if [ $success -eq "124" ]; then
            outcome="$outcome :huge_timeout"
        else
            [ $elapsed -gt 15 ] && outcome="$outcome :timeout"
            [ $(cat testbase/Gentest.output | wc -l) -lt "30" ] && outcome="$outcome :poor_output";
        fi
        echo "Outcome: $outcome"
        cat testbase/Gentest.output
    fi
}

# GENERATE(300, "generate"),
# COMPILE(300, "javac"),
# REFERENCE(10, "ref"),
# VERIFICATION(120, "ver"),
# IGNORE(60, "ignore"),           // Timeout of 60 seconds is needed for cleaning
# STABILITY_CHECK(10, "stab"),    // Checking reference stability in case of an alleged failure
# FAILED(0, "failed"),
# PASSED(60, "passed"),           // Timeout of 60 seconds is needed for cleaning
# CLEAN(0, "clean");



repl-jf-full()
{
    rm generated/Test_0.java

    local success=0;
    ruby -W0 -Ijavafuzzer/rb javafuzzer/rb/Fuzzer.rb -f javafuzzer/rb/config.yml --target-path generated/ --main-class-name Test_0

    javac generated/FuzzerUtils.java generated/Test_0.java -d generated/ > /dev/null 2>&1; success=$?
    [ $success != 0 ] && echo "Compilation error"
  
    [ $success -eq 0 ] && \
        timeout 20s java -cp generated/ Test_0 2>&1 | wc -l
}
