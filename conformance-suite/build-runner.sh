#!/bin/sh
# Builds protobuf's conformance_test_runner from the protobuf release source with CMake.
# Usage: build-runner.sh <protobuf release, e.g. 34.1> <output dir>
set -eu

version=$1
out=$(mkdir -p "$2" && cd "$2" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

curl -fsSL "https://github.com/protocolbuffers/protobuf/releases/download/v$version/protobuf-$version.tar.gz" | tar xz -C "$work"
# The release tarball has no Abseil or jsoncpp, so CMake fetches the versions protobuf pins.
# protobuf's conformance build links jsoncpp's shared library, so it is copied next to the runner,
# which finds it through an rpath relative to itself.
case "$(uname)" in Darwin) origin=@loader_path ;; *) origin='$ORIGIN' ;; esac
cmake -S "$work/protobuf-$version" -B "$work/build" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_STANDARD=17 \
  -DCMAKE_BUILD_RPATH="$origin" \
  -Dprotobuf_BUILD_CONFORMANCE=ON \
  -Dprotobuf_BUILD_TESTS=OFF \
  -Dprotobuf_INSTALL=OFF \
  -Dprotobuf_FORCE_FETCH_DEPENDENCIES=ON
cmake --build "$work/build" --target conformance_test_runner --parallel "$(getconf _NPROCESSORS_ONLN)"
cp "$work/build/conformance_test_runner" "$out/"
find "$work/build/_deps/jsoncpp-build" \( -name 'libjsoncpp.so*' -o -name 'libjsoncpp*.dylib' \) -exec cp {} "$out/" \;
