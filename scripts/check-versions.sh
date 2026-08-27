#!/usr/bin/env bash
# =============================================================================
# check-versions.sh — print the newest STABLE release of a dependency or image.
#
# The repo pins the latest stable+compatible version of every technology, but it
# was authored offline (no Maven Central / Docker Hub access). Run this on a
# networked machine to re-confirm the pinned versions before a production build.
#
# Usage:
#   scripts/check-versions.sh <groupId> <artifactId> [versionPrefix]
#   scripts/check-versions.sh <alias> [versionPrefix]
#   scripts/check-versions.sh docker <image> [versionPrefix]
#   scripts/check-versions.sh all
#
# Examples:
#   scripts/check-versions.sh spring-boot 4        # newest stable Spring Boot 4.x
#   scripts/check-versions.sh org.apache.avro avro # newest stable Avro
#   scripts/check-versions.sh docker confluentinc/cp-kafka 8
#   scripts/check-versions.sh docker elasticsearch 9
#   scripts/check-versions.sh all
#
# "Stable" = a purely numeric dotted version (e.g. 4.1.1, 8.4, 1.12.0). Any tag
# with a qualifier (-alpha/-beta/-rc/-M/-ea/-SNAPSHOT/.Final/-jre/…) is excluded,
# which is exactly the version policy the Maven Enforcer rules enforce at build.
#
# Dependencies: curl + coreutils (grep/sed/sort -V). No jq/python required.
# =============================================================================
set -euo pipefail

MAVEN_BASE="https://repo1.maven.org/maven2"
DOCKERHUB="https://hub.docker.com/v2/repositories"
CURL="curl -fsSL --connect-timeout 8 --max-time 25"

# alias -> "groupId artifactId" (query these coordinates for the version line).
declare -A ALIASES=(
  [spring-boot]="org.springframework.boot spring-boot"
  [spring-cloud]="org.springframework.cloud spring-cloud-dependencies"
  [spring-cloud-gateway]="org.springframework.cloud spring-cloud-gateway-server-webflux"
  [spring-grpc]="org.springframework.grpc spring-grpc-core"
  [avro]="org.apache.avro avro"
  [protobuf]="com.google.protobuf protobuf-java"
  [grpc]="io.grpc grpc-core"
  [jjwt]="io.jsonwebtoken jjwt-api"
  [resilience4j]="io.github.resilience4j resilience4j-core"
  [flyway]="org.flywaydb flyway-core"
  [flyway-mysql]="org.flywaydb flyway-mysql"
  [mysql-connector]="com.mysql mysql-connector-j"
  [kafka-avro-serializer]="io.confluent kafka-avro-serializer"
  [testcontainers]="org.testcontainers testcontainers"
  [elasticsearch-java]="co.elastic.clients elasticsearch-java"
)

# Keep only pure numeric dotted versions (drops every pre-release / qualifier).
stable_only() { grep -E '^v?[0-9]+(\.[0-9]+)*$' || true; }

# Optional prefix filter, matched on whole dotted segments (4 -> 4.x, 4.1 -> 4.1.x).
prefix_filter() {
  local p="${1:-}"
  if [ -z "$p" ]; then cat; else grep -E "^v?${p//./\\.}(\.|$)" || true; fi
}

newest() { sort -V | tail -n1; }

die() { echo "ERROR: $*" >&2; exit 1; }

check_maven() {
  local group="$1" artifact="$2" prefix="${3:-}"
  local url="${MAVEN_BASE}/${group//.//}/${artifact}/maven-metadata.xml"
  local xml
  if ! xml="$(${CURL} "$url" 2>/dev/null)"; then
    die "could not fetch $url (offline, or coordinate ${group}:${artifact} not on Maven Central)"
  fi
  local latest
  latest="$(printf '%s\n' "$xml" \
    | grep -oE '<version>[^<]+</version>' \
    | sed -E 's#</?version>##g' \
    | stable_only | prefix_filter "$prefix" | newest)"
  [ -n "$latest" ] || die "no stable version found for ${group}:${artifact}${prefix:+ (prefix $prefix)}"
  printf '%-45s %s\n' "${group}:${artifact}${prefix:+ [$prefix]}" "$latest"
}

check_docker() {
  local image="$1" prefix="${2:-}"
  case "$image" in */*) : ;; *) image="library/${image}" ;; esac   # official images live under library/
  local tags="" page body
  for page in 1 2 3; do
    if ! body="$(${CURL} "${DOCKERHUB}/${image}/tags?page_size=100&page=${page}&ordering=last_updated" 2>/dev/null)"; then
      [ "$page" -eq 1 ] && die "could not fetch Docker Hub tags for ${image} (offline, or image not on Docker Hub — Elastic images also live at docker.elastic.co)"
      break
    fi
    tags+="$(printf '%s' "$body" | grep -oE '"name":"[^"]+"' | sed -E 's/"name":"([^"]+)"/\1/')"$'\n'
    printf '%s' "$body" | grep -q '"next":null' && break
  done
  local latest
  latest="$(printf '%s\n' "$tags" | stable_only | prefix_filter "$prefix" | newest)"
  [ -n "$latest" ] || die "no stable numeric tag found for ${image}${prefix:+ (prefix $prefix)}"
  printf '%-45s %s\n' "docker:${image}${prefix:+ [$prefix]}" "$latest"
}

check_all() {
  echo "== Maven coordinates =="
  check_maven org.springframework.boot spring-boot 4       || true
  check_maven org.springframework.cloud spring-cloud-dependencies 2025 || true
  check_maven org.apache.avro avro 1                        || true
  check_maven com.google.protobuf protobuf-java 4           || true
  check_maven io.grpc grpc-core 1                           || true
  check_maven io.jsonwebtoken jjwt-api 0                    || true
  check_maven io.github.resilience4j resilience4j-core      || true
  check_maven org.flywaydb flyway-core                      || true
  check_maven com.mysql mysql-connector-j                   || true
  check_maven co.elastic.clients elasticsearch-java 9       || true
  check_maven org.testcontainers testcontainers             || true
  echo
  echo "== Docker images =="
  check_docker confluentinc/cp-kafka 8            || true
  check_docker confluentinc/cp-schema-registry 8  || true
  check_docker library/mysql 8.4                  || true
  check_docker library/cassandra 5                || true
  check_docker docker.elastic.co/elasticsearch/elasticsearch 9 2>/dev/null \
    || check_docker elasticsearch 9               || true
}

usage() {
  cat <<'EOF'
check-versions.sh — print the newest STABLE release of a dependency or image.

Usage:
  scripts/check-versions.sh <groupId> <artifactId> [versionPrefix]
  scripts/check-versions.sh <alias> [versionPrefix]
  scripts/check-versions.sh docker <image> [versionPrefix]
  scripts/check-versions.sh all

Examples:
  scripts/check-versions.sh spring-boot 4          # newest stable Spring Boot 4.x
  scripts/check-versions.sh org.apache.avro avro   # explicit coordinate
  scripts/check-versions.sh docker confluentinc/cp-kafka 8
  scripts/check-versions.sh docker elasticsearch 9
  scripts/check-versions.sh all                    # sweep every pinned coordinate

"Stable" = a purely numeric dotted version; any qualifier
(-alpha/-beta/-rc/-M/-ea/-SNAPSHOT/.Final/-jre/…) is excluded.
EOF
  echo "Known aliases: ${!ALIASES[*]}"
}

main() {
  [ $# -ge 1 ] || { usage; exit 2; }

  case "$1" in
    -h|--help|help) usage; exit 0 ;;
    all)            check_all; exit 0 ;;
    docker)         [ $# -ge 2 ] || die "usage: $0 docker <image> [versionPrefix]"
                    check_docker "$2" "${3:-}"; exit 0 ;;
  esac

  # alias form: $1 is a known alias, optional $2 is a version prefix
  if [ -n "${ALIASES[$1]:-}" ]; then
    # shellcheck disable=SC2086
    check_maven ${ALIASES[$1]} "${2:-}"; exit 0
  fi

  # explicit coordinate form: <groupId> <artifactId> [versionPrefix]
  [ $# -ge 2 ] || die "unknown alias '$1'; use <groupId> <artifactId> [versionPrefix], 'docker <image>', or 'all' (see --help)"
  check_maven "$1" "$2" "${3:-}"
}

main "$@"
