# Implementation Roadmap for RuleScript

## Current State → Target State

### What We Have
- ✅ Core AST types (`Type`, `Field`, `Schema`)
- ✅ Abstract functions as DataFusion UDFs
- ✅ Custom `Source` node via `UserDefinedLogicalNodeCore`
- ✅ `RewriteRule` trait with `from`/`to` methods
- ✅ Helper methods on `Rel` (`filter`, `project`, `join`, `union`, `aggregate`, `distinct`, `limit`, `sort`)
- ✅ Helper methods on `Function` (`and`, `or`)
- ✅ `PatternMatcher` trait abstraction
- ✅ `ApplicableRule<M>` trait for rule execution
- ✅ `DefaultMatcher` structure with unified bindings/matching
- ✅ Clean error types with `thiserror`
- ✅ Working FilterMerge example

### What We Need to Build

## Phase 3: Export for Verification

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

## Phase 2: Pattern Matching Implementation (NEXT PRIORITY)

### 2.1 DefaultMatcher Implementation

The structure is ready, now need to implement:

```rust
impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError>;
    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError>;
}

/// Result of pattern matching
enum MatchResult {
    Success(MatchContext),
    Failure(String), // Reason for failure
}

/// Core matching logic
trait PatternMatcher {
    fn match_against(&self, concrete: &LogicalPlan) -> MatchResult;
}

impl PatternMatcher for Rel {
    fn match_against(&self, concrete: &LogicalPlan) -> MatchResult {
        match (&self.plan, concrete) {
            (LogicalPlan::Filter(pat_filter), LogicalPlan::Filter(con_filter)) => {
                // 1. Recursively match inputs
                let input_match = match_plan(&pat_filter.input, &con_filter.input)?;
                
                // 2. Match predicates (finding function instantiations)
                let pred_match = match_predicate(
                    &pat_filter.predicate, 
                    &con_filter.predicate,
                    &input_match
                )?;
                
                // 3. Merge contexts
                Ok(merge_contexts(input_match, pred_match))
            }
            (LogicalPlan::Extension(ext), _) if is_source_pattern(ext) => {
                // Source pattern matches any plan with compatible schema
                match_source_pattern(ext, concrete)
            }
            _ => MatchResult::Failure("Pattern structure mismatch".into())
        }
    }
}
```

### 3.2 Predicate Matching

This is the trickiest part - matching abstract predicates against concrete ones:

```rust
fn match_predicate(
    pattern: &Expr,
    concrete: &Expr,
    context: &MatchContext
) -> Result<MatchContext, String> {
    match pattern {
        Expr::ScalarFunction(f) if is_abstract_function(f) => {
            // This abstract function must match the entire concrete expression
            let func_name = f.name();
            
            // Check if we've seen this function before
            if let Some(prev_binding) = context.function_bindings.get(func_name) {
                if !expressions_equivalent(prev_binding, concrete) {
                    return Err("Inconsistent function binding");
                }
            } else {
                // New binding
                context.function_bindings.insert(func_name.to_string(), concrete.clone());
            }
            Ok(context)
        }
        Expr::BinaryExpr(BinaryExpr { left, op, right }) => {
            // For AND/OR, try to decompose both pattern and concrete
            match op {
                Operator::And => {
                    // Try to find a way to split concrete expression
                    // This might require backtracking/search
                    find_and_decomposition(left, right, concrete, context)
                }
                _ => {
                    // Other operators: match structurally
                    match_structural(pattern, concrete, context)
                }
            }
        }
        _ => match_structural(pattern, concrete, context)
    }
}
```

### 2.3 Instantiation Logic

The instantiation will use the bindings stored in DefaultMatcher to transform the template:

impl Transformer for Rel {
    fn transform(&self, context: &MatchContext) -> Result<LogicalPlan, String> {
        match &self.plan {
            LogicalPlan::Filter(filter) => {
                // Recursively transform input
                let input = transform_plan(&filter.input, context)?;
                
                // Transform predicate using bindings
                let predicate = transform_expr(&filter.predicate, context)?;
                
                // Build new filter
                Ok(LogicalPlanBuilder::from(input)
                    .filter(predicate)?
                    .build()?)
            }
            LogicalPlan::Extension(ext) if is_source_pattern(ext) => {
                // Look up the bound plan
                let source = ext.node.as_any().downcast_ref::<Source>().unwrap();
                context.plan_bindings.get(&source.table_name)
                    .cloned()
                    .ok_or("Source pattern not bound")
            }
            // ... other cases
        }
    }
}

fn transform_expr(expr: &Expr, context: &MatchContext) -> Result<Expr, String> {
    match expr {
        Expr::ScalarFunction(f) if is_abstract_function(f) => {
            // Replace with bound concrete expression
            context.function_bindings.get(f.name())
                .cloned()
                .ok_or("Function not bound")
        }
        Expr::BinaryExpr(BinaryExpr { left, right, op }) => {
            // Recursively transform operands
            Ok(Expr::BinaryExpr(BinaryExpr {
                left: Box::new(transform_expr(left, context)?),
                op: *op,
                right: Box::new(transform_expr(right, context)?),
            }))
        }
        Expr::Column(_) => Ok(expr.clone()), // Keep columns as-is
        _ => Ok(expr.clone())
    }
}
```

### 3.4 DataFusion Optimizer Rule Integration

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

## Key Implementation Challenges

1. **Predicate Decomposition**: Matching `P(x) AND Q(y)` against `x>5 AND y<10 AND x+y=15`
   - Multiple valid decompositions possible
   - May need backtracking search

2. **Type Inference**: When matching abstract types against concrete DataTypes
   - Must track consistency across entire pattern
   - Handle nullable/non-nullable variants

3. **Performance**: Pattern matching on every optimization pass
   - Consider caching/indexing strategies
   - Early rejection based on plan structure

4. **Correctness**: Ensuring soundness
   - Never apply invalid rewrites
   - OK to miss valid opportunities

## Development Order

1. **Week 1**: Complete pattern builders, basic matching for exact structural matches
2. **Week 2**: QED export, predicate matching with function bindings
3. **Week 3**: Transform engine, DataFusion integration
4. **Week 4**: Testing, optimization, documentation

## Notes from Paper for Implementation

- **Section 3.2.3**: "A valid interpretation of <Pred> may not look similar syntactically"
  - Implication: Need semantic matching, not syntactic
  
- **Section 4.1**: Custom patterns via semantics() method
  - We can extend this pattern for user-defined nodes
  
- **Section 6**: "Generated code can be stricter than rule"
  - Our interpreter can reject some valid cases for simplicity
  
- **Table 3**: Only 16 of 256 join type combinations are valid
  - Need careful testing of join rules