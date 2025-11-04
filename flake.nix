{
  inputs = {
    devenv = {
      inputs.nixpkgs.follows = "nixpkgs";
      url = "github:cachix/devenv";
    };
    flake-utils.url = "github:numtide/flake-utils";
    nix-ai-tools = {
      inputs.nixpkgs.follows = "nixpkgs";
      url = "github:numtide/nix-ai-tools";
    };
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    nixpkgs-python = {
      inputs.nixpkgs.follows = "nixpkgs";
      url = "github:cachix/nixpkgs-python";
    };
  };

  outputs = inputs @ {
    self,
    devenv,
    flake-utils,
    nix-ai-tools,
    nixpkgs,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
        overlays = [(_: _: nix-ai-tools.packages.${system})];
      };
    in {
      packages = {
        devenv-up = self.devShells.${system}.default.config.procfileScript;
        devenv-test = self.devShells.${system}.default.config.test;
      };

      devShells.default = devenv.lib.mkShell {
        inherit inputs pkgs;
        modules = [
          {
            git-hooks.hooks.alejandra.enable = true;
            languages = {
              nix.enable = true;
              rust.enable = true;
            };
            packages = with pkgs; [
              cvc5
              opencode
            ];
          }
        ];
      };
    });
}
