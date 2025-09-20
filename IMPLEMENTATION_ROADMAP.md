# Implementation Roadmap for RuleScript

## Current State

### Completed ✅
- ✅ Core AST types (`Type`, `Field`, `Schema`)
- ✅ Abstract functions as DataFusion UDFs
- ✅ Custom `Source` node via `UserDefinedLogicalNodeCore`
- ✅ `RewriteRule` trait with `from`/`to` methods
- ✅ Helper methods on `Rel` (`filter`, `project`, `join`, `union`, `aggregate`, `distinct`, `limit`, `sort`)
- ✅ Helper methods on `Function` (`and`, `or`)
- ✅ `PatternMatcher` trait abstraction
- ✅ `ApplicableRule<M>` trait for rule execution
- ✅ `DefaultMatcher` structure with unified bindings
- ✅ Clean error types with descriptive messages
- ✅ **Pattern Matching Implementation**:
  - ✅ `resolve` method with recursive plan matching
  - ✅ Expression resolver methods (`resolve_abstract_function`, `resolve_binary_expr`, `resolve_column`)
  - ✅ AND/OR commutativity handling via expression flattening
  - ✅ Column validation for field partitions
  - ✅ Source pattern binding to concrete plans
  - ✅ Context-preserving instantiation principle
- ✅ **Code Optimization**:
  - ✅ Eliminated unnecessary clones throughout codebase
  - ✅ Refactored `partition` method for better API
  - ✅ Improved error reporting with concrete values

### In Progress 🚧

## Phase 1: Complete Pattern Matching (IN PROGRESS)

### 1.1 Instantiation Implementation (NEXT PRIORITY)

The `instantiate` method needs to transform template plans using captured bindings:

```rust
impl PatternMatcher for DefaultMatcher {
    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError> {
        // Transform template using:
        // - self.field_partitions: field mappings
        // - self.functions: abstract function → concrete expression
        // - self.sources: source name → concrete plan
    }
}
```

Key requirements:
- Replace abstract functions with bound concrete expressions
- Replace Source patterns with bound logical plans  
- Respect context boundaries (expressions stay in their context)
- Maintain exact structure of template while substituting bindings

### 1.2 Testing with Concrete Examples

Create comprehensive tests:
- FilterMerge rule against real DataFusion plans
- Verify AND/OR commutativity works correctly
- Test error cases (inconsistent bindings, missing patterns)
- Validate context preservation

## Phase 2: Export for Verification

### 2.1 QED Format Serialization
Based on paper Section 5.1, need to export to QED's expected format:

```rust
trait QEDSerializable {
    fn to_qed(&self) -> String;
}

impl QEDSerializable for Rel {
    fn to_qed(&self) -> String {
        match &self.plan {
            LogicalPlan::Filter(filter) => {
                format!("Filter({}, {})", 
                    predicate_to_qed(&filter.predicate),
                    plan_to_qed(&filter.input))
            }
            LogicalPlan::Extension(ext) => {
                // Handle our Source node
                if let Some(source) = ext.node.as_any().downcast_ref::<Source>() {
                    format!("Table({}: {})", 
                        source.table_name,
                        schema_to_qed(&source.schema))
                }
            }
            // ... other cases
        }
    }
}
```

### 2.2 Uninterpreted Symbol Declaration
```rust
struct RuleExport {
    uninterpreted_types: Vec<String>,      // T0, T1, ...
    uninterpreted_functions: Vec<(String, usize, Type)>, // (name, arity, return_type)
    pattern: String,                       // QED format
    replacement: String,                   // QED format
    constraints: Option<String>,           // First-order logic constraints
}
```

## Phase 3: DataFusion Optimizer Integration

### 3.1 DataFusion Optimizer Rule Integration

```rust
/// Adapter that makes our rules work with DataFusion's optimizer
struct RuleScriptOptimizer {
    rule: Box<dyn RewriteRule>,
}

impl OptimizerRule for RuleScriptOptimizer {
    fn try_optimize(
        &self,
        plan: &LogicalPlan,
        config: &dyn OptimizerConfig,
    ) -> Result<Option<LogicalPlan>> {
        // Get pattern from our rule
        let pattern = self.rule.pattern();
        
        // Try to match
        match pattern.match_against(plan) {
            MatchResult::Success(context) => {
                // Get replacement pattern
                let replacement = self.rule.replacement();
                
                // Transform using captured context
                let new_plan = replacement.transform(&context)?;
                
                Ok(Some(new_plan))
            }
            MatchResult::Failure(_) => {
                // Rule doesn't apply
                Ok(None)
            }
        }
    }
    
    fn name(&self) -> &str {
        self.rule.name()
    }
}
```

## Phase 4: Advanced Features

### 4.1 Meta-Variables (Future)
```rust
enum MetaVariable {
    Type(String),
    Function(String),
    JoinType(String),
    Pattern(String),
}

struct RuleTemplate {
    pattern: Rel,
    replacement: Rel,
    meta_vars: Vec<MetaVariable>,
}

struct RuleFamily {
    template: RuleTemplate,
    assignments: Vec<HashMap<String, Assignment>>,
}
```

### 4.2 Constraint Support (Future)
```rust
enum Constraint {
    ForAll(Vec<String>, Box<Constraint>),
    Exists(Vec<String>, Box<Constraint>),
    Predicate(Expr),
}

impl RewriteRule {
    fn constraints(&self) -> Option<Constraint> {
        None // Default: no constraints
    }
}
```

## Testing Strategy

### Unit Tests
1. Pattern construction
2. QED serialization
3. Simple pattern matching (exact matches)
4. Function instantiation binding

### Integration Tests
1. FilterMerge rule application
2. ProjectionPushdown rule
3. JoinAssociate (when we have joins)
4. Multiple rule application

### Property Tests
1. Matching is deterministic
2. Same symbol → same binding throughout
3. Transform(Match(concrete)) preserves semantics

## Key Implementation Insights

### Completed Challenges ✅
1. **Predicate Decomposition**: Solved using expression flattening
   - Flatten AND/OR expressions into lists of conjuncts/disjuncts
   - Use `partition` method to match pattern items to concrete items
   - Handles commutativity naturally

2. **Consistent Bindings**: Implemented via DefaultMatcher
   - Single source of truth for all bindings
   - Validation on every new binding attempt
   - Clear error messages for inconsistencies

### Remaining Challenges
1. **Type Inference**: Abstract types against concrete DataTypes
   - Need to track consistency across pattern
   - Handle nullable/non-nullable variants

2. **Performance**: Pattern matching overhead
   - Consider caching strategies
   - Early rejection based on plan structure

3. **Correctness**: Ensuring soundness
   - Never apply invalid rewrites
   - OK to miss valid opportunities

## Development Timeline

### Completed (Weeks 1-2) ✅
- Core AST and rule abstractions
- Pattern builders and helper methods
- Full pattern matching implementation with expression resolvers
- AND/OR commutativity handling
- Code optimization and cleanup

### Current Sprint (Week 3) 🚧
- Implement `instantiate` method
- Test with concrete DataFusion plans
- Create more rule examples

### Upcoming (Week 4+)
- DataFusion optimizer integration
- QED export for verification
- Performance optimizations
- Documentation and examples

## Notes from Paper for Implementation

- **Section 3.2.3**: "A valid interpretation of <Pred> may not look similar syntactically"
  - Implication: Need semantic matching, not syntactic
  
- **Section 4.1**: Custom patterns via semantics() method
  - We can extend this pattern for user-defined nodes
  
- **Section 6**: "Generated code can be stricter than rule"
  - Our interpreter can reject some valid cases for simplicity
  
- **Table 3**: Only 16 of 256 join type combinations are valid
  - Need careful testing of join rules