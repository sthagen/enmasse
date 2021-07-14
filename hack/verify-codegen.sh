#!/usr/bin/env bash

#
# Copyright 2018, EnMasse authors.
# License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
#

set -o errexit
set -o nounset
set -o pipefail

case "$OSTYPE" in
  darwin*)  CP=gcp;;
  *)        CP=cp;;
esac


SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"

TMPBASE="$(mktemp -d)"
TMPPROJ="$TMPBASE/github.com/enmasseproject/enmasse"

cleanup() {
    echo "Cleaning up: $TMPBASE"
    rm -rf "$TMPBASE"
}

echo "Using tmp base: $TMPBASE"

trap "cleanup" EXIT SIGINT

mkdir -p "$TMPPROJ"
${CP} -dR "$SCRIPTPATH/../" "$TMPPROJ"

"$SCRIPTPATH/run-codegen.sh" --output-base "$TMPBASE"

echo "Comparing existing generated code with temporarily generated code"
if diff -Nur --exclude=.git --exclude=systemtests --exclude=target/ --no-dereference "$SCRIPTPATH/.." "$TMPPROJ" -x "go-bin"; then
    echo "No changes detected in generated code"
else
    echo "Generated code is out of date. Run hack/update-codegen.sh and commit the changes."
    exit 1 # must failed the build
fi
