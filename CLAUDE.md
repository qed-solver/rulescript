# RuleScript Project Context

## Project Overview
RuleScript is a Rust library for building a DSL to describe logical plan rewrite rules in databases. The project leverages DataFusion's AST representations while maintaining custom terminal nodes for pattern matching.

## Architecture

### Core Modules
- `ast::opaque` - Abstract types, fields, and schemas that map to DataFusion's type system
  - `AbstractDataType` - Maps to Binary in DataFusion
  - `AbstractField` - Field with name, type, and nullable flag
  - `AbstractSchema` - Collection of abstract fields
  - ID generation system for unique identifiers

- `ast::relational` - Relational patterns with custom nodes
  - `SourcePattern` - Custom terminal node that implements DataFusion's `UserDefinedLogicalNodeCore`
  - `RelationalPattern` - Wrapper around DataFusion's `LogicalPlan`
  - Integrates as Extension node in DataFusion's plan tree

- `ast::scalar` - Scalar patterns and abstract functions
  - `AbstractFunction` - UDF implementation for pattern matching (not execution)
  - `ScalarPattern` - Wrapper around DataFusion's `Expr`
  - Support for configurable input/output types

## Design Decisions
- Using DataFusion's native AST where possible, only adding custom terminal nodes
- Abstract types all map to Binary in DataFusion for uniformity
- Custom nodes use Extension mechanism to integrate with DataFusion optimizer
- Project is under active development - no examples or fixed API yet

## Dependencies
- DataFusion (latest via wildcard)
- SMTLib (latest via wildcard) - for future solver integration

## Known Issues/TODOs
- Builder pattern was removed - need better approach for fluent API
- API compatibility varies with DataFusion versions (e.g., `invoke` vs `invoke_with_args`)
- Project structure includes Java parser code that may be integrated later

## Development Status
- Core AST structure is functional
- Custom SourcePattern node successfully integrates with DataFusion
- Abstract function system works for pattern matching
- Not ready for production use - API still evolving