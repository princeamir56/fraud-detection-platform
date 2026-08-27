#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script (only-script distribution type).
# Bootstraps a pinned Apache Maven from .mvn/wrapper/maven-wrapper.properties.
# Licensed to the Apache Software Foundation (ASF) under the Apache License 2.0.
# ----------------------------------------------------------------------------
set -euf

# Resolve the directory of this script (POSIX-safe).
script_path="$0"
while [ -h "$script_path" ]; do
  ls=$(ls -ld "$script_path")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  case $link in /*) script_path="$link" ;; *) script_path="$(dirname "$script_path")/$link" ;; esac
done
MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$script_path")" && pwd)"
export MAVEN_PROJECTBASEDIR

wrapperProperties="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$wrapperProperties" ] || { echo "Cannot find $wrapperProperties" >&2; exit 1; }

distributionUrl=""
while IFS='=' read -r key value; do
  case "$key" in
    distributionUrl) distributionUrl="$value" ;;
  esac
done < "$wrapperProperties"
[ -n "$distributionUrl" ] || { echo "distributionUrl not set in $wrapperProperties" >&2; exit 1; }

# Compute a stable cache location for the extracted distribution.
distributionUrlName="${distributionUrl##*/}"
distributionName="${distributionUrlName%-bin.zip}"
MAVEN_HOME_PARENT="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$distributionName"
MAVEN_HOME="$MAVEN_HOME_PARENT/$distributionName"

download() {
  target="$1"; url="$2"
  mkdir -p "$(dirname "$target")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$target" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$target" "$url"
  else
    echo "Neither curl nor wget found; cannot download Maven." >&2; exit 1
  fi
}

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "Downloading Apache Maven from $distributionUrl ..." >&2
  tmpZip="$MAVEN_HOME_PARENT/$distributionUrlName"
  download "$tmpZip" "$distributionUrl"
  mkdir -p "$MAVEN_HOME_PARENT"
  # Extract with unzip when present; otherwise fall back to the JDK's `jar`
  # tool, which is always available wherever Maven can run (a JDK is required
  # to build) and can unpack any zip. This keeps container images and bare
  # Git-Bash checkouts working without an extra `unzip` package. `jar` does
  # not restore the Unix exec bit that `unzip` sets, so mark mvn executable.
  if command -v unzip >/dev/null 2>&1; then
    ( cd "$MAVEN_HOME_PARENT" && unzip -q -o "$tmpZip" )
  else
    ( cd "$MAVEN_HOME_PARENT" && jar xf "$tmpZip" )
  fi
  chmod +x "$MAVEN_HOME/bin/mvn" 2>/dev/null || true
  rm -f "$tmpZip"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
