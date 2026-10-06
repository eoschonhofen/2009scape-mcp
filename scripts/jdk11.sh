# Sourced by rebuild.sh, start-server.sh and start-client.sh to put a JDK 11 on PATH.
#
# Server and client both need Java 11: the server runs JavaScript content through
# Nashorn, which was removed after JDK 14. The first JDK 11 found wins:
#   1. $ROOT/.toolchain/jdk-11*  (a JDK unpacked into the project)
#   2. $JAVA_HOME
#   3. java on PATH
# Expects ROOT to be set to the repository root.

jdk11_major() {
  "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}

jdk11_select() {
  local candidate
  for candidate in "$ROOT"/.toolchain/jdk-11*/ "${JAVA_HOME:+$JAVA_HOME/}"; do
    candidate="${candidate%/}"
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] && [ "$(jdk11_major "$candidate/bin/java")" = "11" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done
  if command -v java >/dev/null 2>&1 && [ "$(jdk11_major java)" = "11" ]; then
    return 0
  fi
  echo "JDK 11 not found. Install one (e.g. Temurin 11), then set JAVA_HOME to it" >&2
  echo "or unpack it into $ROOT/.toolchain/." >&2
  return 1
}

jdk11_select || exit 1
