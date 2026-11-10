#!/usr/bin/env bash
# The AGP 9.0.1 fixed APK template uses Kotlin 2.2.10. Fetch its compiler JAR
# only for APK jobs; Maven Central's GitHub-release redirect returned HTTP 504
# on Actions. Accept a mirror copy ONLY if it matches JetBrains' release digest.
set -euo pipefail
: "${SAIF_VERIFIED_MAVEN_DIR:?Set a private temporary Maven directory}"
base=org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.10
name=kotlin-compiler-embeddable-2.2.10
checksum=79628fd5d64b4692a62f98962baab00e9076a37e2cffd6ace33cd0b7be7d1cdf
repo="$SAIF_VERIFIED_MAVEN_DIR"
mkdir -p "$repo/$base"
curl -fLsS --retry 3 --retry-delay 2 --connect-timeout 15 --max-time 90 \
  "https://repo.maven.apache.org/maven2/$base/$name.pom" -o "$repo/$base/$name.pom"
for mirror in \
  https://maven.aliyun.com/repository/central \
  https://mirrors.huaweicloud.com/repository/maven \
  https://mirrors.cloud.tencent.com/nexus/repository/maven-public; do
  echo "Checking the official Kotlin compiler digest from $mirror"
  if curl -fLsS --retry 2 --retry-delay 2 --connect-timeout 15 --max-time 240 \
    "$mirror/$base/$name.jar" -o "$repo/$base/$name.jar.tmp" && \
    echo "$checksum  $repo/$base/$name.jar.tmp" | sha256sum --check --status; then
    mv "$repo/$base/$name.jar.tmp" "$repo/$base/$name.jar"
    break
  fi
  rm -f "$repo/$base/$name.jar.tmp"
done
test -s "$repo/$base/$name.jar" || { echo 'Verified Kotlin compiler download failed' >&2; exit 1; }
