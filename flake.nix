{
  inputs = {
    devenv.url = "github:cachix/devenv";
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    nixpkgs-python.url = "github:cachix/nixpkgs-python";
    nix-ai-tools.url = "github:numtide/nix-ai-tools";
  };

  outputs = inputs @ {
    self,
    devenv,
    flake-utils,
    nixpkgs,
    nix-ai-tools,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      };
      ai-tools = nix-ai-tools.packages.${system};
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
            packages = with ai-tools;
            with pkgs; [
              crush
              cvc5
            ];
          }
        ];
      };
    });
}
