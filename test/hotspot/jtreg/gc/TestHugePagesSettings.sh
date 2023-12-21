#!/bin/sh
#
# Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#

##
## @test TestNameEscaping.sh
## @bug 8233624
## @summary Enhance JNI linkage
## @run shell TestHugePagesSettings.sh
##

echo "The test is alive!"

pwd

cat /sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages

sleep 20
echo "PS sample #0: "
echo "======================================================"
ps -auxww | grep "/bin/java" | wc -l

sleep 30
echo "PS sample #1"
echo "======================================================"
ps -auxww | grep "/bin/java" | wc -l

sleep 30
echo "PS sample #2"
echo "======================================================"
ps -auxww | grep "/bin/java" | wc -l

sleep 30
echo "PS sample #3"
echo "======================================================"
ps -auxww | grep "/bin/java" | wc -l


sleep 30
echo "PS sample #4"
echo "======================================================"
ps -auxww | grep "/bin/java" | wc -l

echo "Test Finished"
exit 127

