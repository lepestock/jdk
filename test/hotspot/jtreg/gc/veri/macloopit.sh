#!/bin/bash

#FIXME JNP Uncomment TIMEOUT=300 #seconds
TIMEOUT=5 #seconds

for i in {100..200} ; do 
    ~/jdk-24.jdk/Contents/Home/bin/java -XX:ErrorFile=LTAF_MoreFonts_Crash_jdk_24b21_%p.log LTAF_MoreFonts UbuntuMono-B.ttf $i & sleep 5; kill $!
    # find  /var/folders/x9 -name "*JF*.tmp" -delete
done
