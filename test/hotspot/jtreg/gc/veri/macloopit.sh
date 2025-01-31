#!/bin/bash


# @test
#
# @run build LTAF_MoreFonts
# @run shell RptNo_37248264_Verification.sh
#

#FIXME JNP Uncomment TIMEOUT=300 #seconds
TIMEOUT=5 #seconds
PATH_TO_TMP=$(dirname "$(mktemp --dry-run)")


echo "JNP I'm alive!"


echo "(environment"
echo " :path $(pwd)"
echo " :env-vars '("
export | while read l; do echo "   $l"; done
echo "))"

#for i in {100..200} ; do 
    ${TESTJAVA}/bin/java -cp ${TESTCLASSPATH} -XX:ErrorFile=LTAF_MoreFonts_Crash_jdk_24b21_%p.log LTAF_MoreFonts ${TESTSRC}/UbuntuMono-Bold.ttf $i & sleep 5; kill $!
    # find  ${PATH_TO_TMP} -name "*JF*.tmp" -delete
#done
