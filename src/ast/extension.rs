use std::sync::Arc;

use datafusion::{
    common::DFSchemaRef,
    error::{DataFusionError, Result},
    logical_expr::{Expr, LogicalPlan, UserDefinedLogicalNodeCore},
};

use crate::{
    ast::opaque::Schema,
    matcher::{PatternMatcher, RuleError},
};

/// Trait for user-defined logical operators in patterns
///
/// Users implement this trait to define custom logical operators (e.g., EquiJoin, IndexScan)
/// with full control over their behavior during pattern matching.
///
/// All operator data is provided via trait methods. Users must implement
/// resolve() and instantiate() to handle pattern matching and context storage.
pub trait UserDefinedLogicalOperator: Send + Sync {
    /// Unique identifier for this operator type
    fn operator_name(&self) -> &str;

    /// Input plans that this operator operates on
    fn inputs(&self) -> Vec<&LogicalPlan>;

    /// Output schema of this operator
    fn schema(&self) -> &Schema;

    /// Equivalent core relational algebra plan (mandatory)
    ///
    /// This enables QED verification by translating to known operators.
    fn semantics(&self) -> &LogicalPlan;

    /// Resolve this pattern against a concrete plan
    ///
    /// User implementations must:
    /// 1. Validate that the concrete plan matches this pattern
    /// 2. Call matcher methods to resolve sub-patterns as needed
    /// 3. Store the output column context by downcasting to DefaultMatcher
    ///    and calling store_user_defined_context()
    ///
    /// # Parameters
    /// - `pattern`: The wrapper pattern (needed for storing context)
    /// - `concrete`: The concrete plan to match against
    /// - `matcher`: The matcher instance
    fn resolve(
        &self,
        pattern: &UserDefinedLogicalPattern,
        concrete: &LogicalPlan,
        matcher: &mut dyn PatternMatcher,
    ) -> Result<(), RuleError>;

    /// Instantiate this pattern using bound variables
    ///
    /// User implementations must:
    /// 1. Call matcher methods to instantiate sub-patterns as needed
    /// 2. Reconstruct the operator or return core algebra representation
    /// 3. Store the output column context by downcasting to DefaultMatcher
    ///    and calling store_user_defined_context()
    fn instantiate(&self, matcher: &mut dyn PatternMatcher) -> Result<LogicalPlan, RuleError>;
}

/// User-defined logical operator pattern with mandatory semantics
///
/// Thin wrapper around a trait object that implements UserDefinedLogicalOperator.
/// Integrates with DataFusion's Extension mechanism for pattern matching.
pub struct UserDefinedLogicalPattern {
    /// The implementation that defines this operator's behavior
    implementation: Arc<dyn UserDefinedLogicalOperator>,

    /// Cache the converted DataFusion schema
    df_schema: DFSchemaRef,
}

impl UserDefinedLogicalPattern {
    pub fn new(implementation: Arc<dyn UserDefinedLogicalOperator>) -> Self {
        let df_schema = implementation.schema().to_datafusion_schema();
        Self {
            implementation,
            df_schema,
        }
    }

    pub fn implementation(&self) -> &Arc<dyn UserDefinedLogicalOperator> {
        &self.implementation
    }

    /// Generate a unique ID for this pattern instance
    ///
    /// Used as a key for storing/retrieving context in the matcher.
    /// Uses Debug formatting to create a unique string identifier.
    pub fn id(&self) -> String {
        format!("{self:?}")
    }
}

impl Clone for UserDefinedLogicalPattern {
    fn clone(&self) -> Self {
        Self {
            implementation: Arc::clone(&self.implementation),
            df_schema: self.df_schema.clone(),
        }
    }
}

impl std::fmt::Debug for UserDefinedLogicalPattern {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("UserDefinedLogicalPattern")
            .field("operator_name", &self.implementation.operator_name())
            .field("inputs", &self.implementation.inputs())
            .field("schema", &self.implementation.schema())
            .field("implementation", &"<dyn UserDefinedLogicalOperator>")
            .finish()
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for UserDefinedLogicalPattern {
    fn eq(&self, other: &Self) -> bool {
        self.implementation.operator_name() == other.implementation.operator_name()
            && self.implementation.inputs() == other.implementation.inputs()
            && self.implementation.schema() == other.implementation.schema()
            && self.implementation.semantics() == other.implementation.semantics()
    }
}

impl Eq for UserDefinedLogicalPattern {}

impl PartialOrd for UserDefinedLogicalPattern {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for UserDefinedLogicalPattern {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        match self
            .implementation
            .operator_name()
            .cmp(other.implementation.operator_name())
        {
            std::cmp::Ordering::Equal => self
                .implementation
                .schema()
                .cmp(other.implementation.schema()),
            ord => ord,
        }
    }
}

impl std::hash::Hash for UserDefinedLogicalPattern {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.implementation.operator_name().hash(state);
        self.implementation.inputs().hash(state);
        self.implementation.schema().hash(state);
        // Don't hash df_schema as it's derived from schema
        // Skip semantics (LogicalPlan doesn't implement Hash)
    }
}

impl UserDefinedLogicalNodeCore for UserDefinedLogicalPattern {
    fn name(&self) -> &str {
        self.implementation.operator_name()
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        self.implementation.inputs()
    }

    fn schema(&self) -> &DFSchemaRef {
        &self.df_schema
    }

    fn expressions(&self) -> Vec<Expr> {
        Vec::new()
    }

    fn fmt_for_explain(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(
            f,
            "{}: {} inputs, {} fields",
            self.implementation.operator_name(),
            self.implementation.inputs().len(),
            self.implementation.schema().field_count()
        )
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // User-defined pattern should not be modified by optimizer
        Err(DataFusionError::NotImplemented(
            "with_exprs_and_inputs not supported for UserDefinedLogicalPattern".to_string(),
        ))
    }
}
