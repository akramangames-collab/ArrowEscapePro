#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/wallet-tests
javac -d build/wallet-tests app/src/main/java/com/arrowescape/pro/Wallet.java tests/WalletTest.java
java -cp build/wallet-tests WalletTest
