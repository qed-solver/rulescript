use datafusion::{
    error::Result,
    logical_expr::{BinaryExpr, Expr, LogicalPlan, LogicalPlanBuilder, Operator},
};

use crate::ast::{
    opaque::{AbstractDataType, AbstractField, AbstractSchema, generate_unique_id},
    relational::RelationalPattern,
    scalar::ScalarPattern,
};

/// Builder that integrates DataFusion's LogicalPlanBuilder with our patterns
pub struct PatternBuilder {
    pub builder: LogicalPlanBuilder,
}

impl PatternBuilder {
    /// Create a new PatternBuilder from a RelationalPattern
    pub fn from_pattern(pattern: RelationalPattern) -> Result<Self> {
        let builder = LogicalPlanBuilder::from(pattern.plan);
        Ok(Self { builder })
    }

    /// Create a PatternBuilder from a source pattern with abstract schema
    pub fn from_source(table_name: String, abstract_schema: AbstractSchema) -> Self {
        let source_pattern = RelationalPattern::source(table_name, abstract_schema);
        Self::from_pattern(source_pattern).expect("Failed to create builder from source pattern")
    }

    /// Create a PatternBuilder from any DataFusion LogicalPlan
    pub fn from_logical_plan(plan: LogicalPlan) -> Self {
        let builder = LogicalPlanBuilder::from(plan);
        Self { builder }
    }


    /// Build the final RelationalPattern
    pub fn build(self) -> Result<RelationalPattern> {
        let plan = self.builder.build()?;
        Ok(RelationalPattern { plan })
    }

}

/// Helper functions for creating expressions that integrate with our patterns
pub struct ExprBuilder;

impl ExprBuilder {
    /// Create a column reference expression
    pub fn col(name: &str) -> Expr {
        datafusion::prelude::col(name)
    }

    /// Create an abstract function call expression
    pub fn abstract_function(
        name: String,
        input_types: Vec<AbstractDataType>,
        return_type: AbstractDataType,
        args: Vec<Expr>,
    ) -> Expr {
        let pattern = ScalarPattern::abstract_function(name, input_types, return_type, args);
        pattern.expr
    }

    /// Create an abstract function call with auto-generated types
    pub fn abstract_function_with_count(name: String, input_count: usize, args: Vec<Expr>) -> Expr {
        let pattern = ScalarPattern::abstract_function_with_count(name, input_count, args);
        pattern.expr
    }

    /// Create a binary expression (e.g., arithmetic, comparison)
    pub fn binary_expr(left: Expr, op: Operator, right: Expr) -> Expr {
        Expr::BinaryExpr(BinaryExpr {
            left: Box::new(left),
            op,
            right: Box::new(right),
        })
    }

    /// Equality comparison
    pub fn eq(left: Expr, right: Expr) -> Expr {
        Self::binary_expr(left, Operator::Eq, right)
    }

    pub fn not_eq(left: Expr, right: Expr) -> Expr {
        Self::binary_expr(left, Operator::NotEq, right)
    }

    /// Boolean operations
    pub fn and(left: Expr, right: Expr) -> Expr {
        Self::binary_expr(left, Operator::And, right)
    }

    pub fn or(left: Expr, right: Expr) -> Expr {
        Self::binary_expr(left, Operator::Or, right)
    }

    pub fn not(expr: Expr) -> Expr {
        Expr::Not(Box::new(expr))
    }
}

/// Helper functions for creating abstract schemas and types
pub struct SchemaBuilder;

impl SchemaBuilder {
    /// Create an abstract schema with a specific number of fields
    pub fn with_field_count(field_count: usize) -> AbstractSchema {
        let fields: Vec<AbstractField> = (0..field_count)
            .map(|i| AbstractField {
                name: format!("field_{i}"),
                data_type: AbstractDataType {
                    id: generate_unique_id("type"),
                },
                nullable: true,
            })
            .collect();
        AbstractSchema { fields }
    }

    /// Create an abstract schema with specific field names
    pub fn with_field_names(field_names: Vec<String>) -> AbstractSchema {
        let fields: Vec<AbstractField> = field_names
            .into_iter()
            .map(|name| AbstractField {
                name,
                data_type: AbstractDataType {
                    id: generate_unique_id("type"),
                },
                nullable: true,
            })
            .collect();
        AbstractSchema { fields }
    }

    /// Create an abstract schema from explicit field definitions
    pub fn with_fields(fields: Vec<AbstractField>) -> AbstractSchema {
        AbstractSchema { fields }
    }

    /// Create an abstract field
    pub fn field(name: String, nullable: bool) -> AbstractField {
        AbstractField {
            name,
            data_type: AbstractDataType {
                id: generate_unique_id("type"),
            },
            nullable,
        }
    }

    /// Create an abstract data type
    pub fn abstract_type() -> AbstractDataType {
        AbstractDataType {
            id: generate_unique_id("type"),
        }
    }

    /// Create an abstract data type with a specific ID
    pub fn abstract_type_with_id(id: String) -> AbstractDataType {
        AbstractDataType { id }
    }
}
