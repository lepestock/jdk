#!/bin/bash


# @test
#
# @run build LTAF_MoreFonts
# @run shell/timeout=50000 RptNo_37248264_Verification.sh
#

TIMEOUT=300 #seconds
#FIXME JNP Debug mode TIMEOUT=5 #seconds
PATH_TO_TMP=$(dirname "$(mktemp --dry-run)")

echo "(environment"
echo " :path $(pwd)"
echo " :tmp-dir ${PATH_TO_TMP}"
echo " :env-vars '("
export | while read l; do echo "   $l"; done
echo "))"

for i in {100..200} ; do 
    ${TESTJAVA}/bin/java -cp ${TESTCLASSPATH} -XX:ErrorFile=LTAF_MoreFonts_Crash_jdk_24b21_%p.log LTAF_MoreFonts ${TESTSRC}/UbuntuMono-Bold.ttf $i & sleep ${TIMEOUT}; kill $!
    find  ${PATH_TO_TMP} -name "*JF*.tmp" -delete
done
