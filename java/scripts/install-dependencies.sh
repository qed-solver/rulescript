#!/bin/bash

sudo apt-get update
sudo apt-get install -y jq z3

sudo apt-get install -y cvc5 || (
    sudo apt-get install -y cmake libgmp-dev &&
    git clone --depth 1 https://github.com/cvc5/cvc5.git &&
    cd cvc5 &&
    ./configure.sh --auto-download &&
    cd build &&
    make -j$(nproc) &&
    sudo make install
)
