#!/bin/bash

PATH=$PATH:/c/Program\ Files/Java/jdk1.8.0_111/bin

javac -classpath classes -d classes TSInfo.java

if [ $? -eq 0 ]; then
  java -classpath classes TSInfo
fi
