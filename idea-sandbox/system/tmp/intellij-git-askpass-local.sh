#!/bin/sh
"/Library/Java/JavaVirtualMachines/jdk-11.0.9.jdk/Contents/Home/bin/java" -cp "/Applications/IntelliJ IDEA.app/Contents/plugins/git4idea/lib/git4idea-rt.jar:/Applications/IntelliJ IDEA.app/Contents/lib/xmlrpc-2.0.1.jar:/Applications/IntelliJ IDEA.app/Contents/lib/commons-codec-1.15.jar" org.jetbrains.git4idea.http.GitAskPassApp "$@"
