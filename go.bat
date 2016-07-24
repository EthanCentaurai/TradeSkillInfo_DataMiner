@echo off
path %path%;C:\Program Files\Java\jdk1.8.0_102\bin

rem javac
rem pause

rem java
rem pause

rem javac -classpath classes -d classes -sourcepath source source\org\json\JSONArray.java -Xlint:unchecked
rem javac -classpath classes -d classes -sourcepath source source\org\json\JSONArray.java
rem if errorlevel 1 goto ende

javac -classpath classes -d classes TSInfo.java
if errorlevel 1 goto ende

java -classpath classes TSInfo

:ende
pause
