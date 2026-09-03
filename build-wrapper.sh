#!/bin/bash
# Download Gradle wrapper if missing
if [ ! -d "gradle/wrapper" ]; then
  echo "Downloading Gradle 7.6.4 distribution..."
  mkdir -p gradle/wrapper
  curl -L https://services.gradle.org/distributions/gradle-7.6.4-bin.zip -o gradle/wrapper/gradle-7.6.4-bin.zip
  echo "distributionBase=GRADLE_USER_HOME" > gradle/wrapper/gradle-wrapper.properties
  echo "distributionPath=wrapper/dists" >> gradle/wrapper/gradle-wrapper.properties
  echo "distributionUrl=https\://services.gradle.org/distributions/gradle-7.6.4-bin.zip" >> gradle/wrapper/gradle-wrapper.properties
  echo "zipStoreBase=GRADLE_USER_HOME" >> gradle/wrapper/gradle-wrapper.properties
  echo "zipStorePath=wrapper/dists" >> gradle/wrapper/gradle-wrapper.properties
fi
