#!/bin/sh
set -eu

MAVEN_VERSION="3.10.1"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
WRAPPER_HOME="${MAVEN_USER_HOME}/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_HOME="${WRAPPER_HOME}/apache-maven-${MAVEN_VERSION}"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${WRAPPER_HOME}"
  ARCHIVE="${WRAPPER_HOME}/apache-maven-${MAVEN_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip" -o "${ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip" -O "${ARCHIVE}"
  else
    echo "Maven Wrapper requires curl or wget to download Maven ${MAVEN_VERSION}." >&2
    exit 1
  fi
  unzip -oq "${ARCHIVE}" -d "${WRAPPER_HOME}"
  rm -f "${ARCHIVE}"
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
