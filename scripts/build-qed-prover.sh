#!/bin/bash

# Build qed-prover with Rust nightly

curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain nightly
source "${HOME}/.cargo/env"

if [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    if brew --prefix z3 >/dev/null 2>&1; then
        _z3="$(brew --prefix z3)"
        export CPATH="${_z3}/include${CPATH:+:${CPATH}}"
        export LIBRARY_PATH="${_z3}/lib${LIBRARY_PATH:+:${LIBRARY_PATH}}"
    fi
    if brew --prefix llvm >/dev/null 2>&1; then
        export LIBCLANG_PATH="$(brew --prefix llvm)/lib"
    fi
fi

ROOT_DIR="$(pwd)"
PROVER_DIR="${ROOT_DIR}/qed-prover"
if [[ ! -d "${PROVER_DIR}/.git" ]]; then
    git clone https://github.com/qed-solver/prover.git "${PROVER_DIR}"
fi

cd "${PROVER_DIR}"
cargo +nightly build --release
