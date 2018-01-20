#!/bin/bash

PATH=$PATH:/c/Program\ Files/Java/jdk-9.0.4/bin

javac -classpath classes -d classes TSInfo.java

if [ $? -eq 0 ]; then
  java -classpath classes TSInfo
fi
