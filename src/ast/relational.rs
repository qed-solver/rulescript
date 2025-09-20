use std::{cmp::Ordering, fmt, sync::Arc};

use datafusion::{
    common::{DFSchemaRef, JoinConstraint, NullEquality},
    error::{DataFusionError, Result},
    logical_expr::{
        Aggregate, Distinct, Extension, Filter, Join, JoinType, Limit, LogicalPlan, Projection,
        Sort, SortExpr, Union, UserDefinedLogicalNodeCore, build_join_schema,
    },
    prelude::{Expr, lit},
};

use crate::ast::opaque::Schema;

// Source pattern that can be used as a variant in DataFusion's LogicalPlan
#[derive(Debug, Clone)]
pub struct Source {
    pub table_name: String,
    pub schema: Schema,
    // Cache the converted DataFusion schema
    df_schema: DFSchemaRef,
}

impl Source {
    pub fn new(table_name: String, schema: Schema) -> Self {
        let df_schema = schema.to_datafusion_schema();
        Self {
            table_name,
            schema,
            df_schema,
        }
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for Source {
    fn eq(&self, other: &Self) -> bool {
        self.table_name == other.table_name && self.schema == other.schema
    }
}

impl Eq for Source {}

impl PartialOrd for Source {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for Source {
    fn cmp(&self, other: &Self) -> Ordering {
        match self.table_name.cmp(&other.table_name) {
            Ordering::Equal => self.schema.cmp(&other.schema),
            ord => ord,
        }
    }
}

impl std::hash::Hash for Source {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.table_name.hash(state);
        self.schema.hash(state);
        // Don't hash df_schema as it's derived from schema
    }
}

impl UserDefinedLogicalNodeCore for Source {
    fn name(&self) -> &str {
        "Source"
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        // Source has no inputs
        Vec::new()
    }

    fn schema(&self) -> &DFSchemaRef {
        &self.df_schema
    }

    fn expressions(&self) -> Vec<Expr> {
        Vec::new()
    }

    fn fmt_for_explain(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "Source: {} [fields: {}]",
            self.table_name,
            self.schema.field_count()
        )
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // Source pattern should not be modified by optimizer
        Err(DataFusionError::Plan(
            "Source should not be modified by optimizer".to_string(),
        ))
    }
}

// A relational pattern is just a wrapper around a DataFusion LogicalPlan
#[derive(Debug, Clone)]
pub struct Rel {
    pub plan: LogicalPlan,
}

impl Rel {
    // Create a source pattern using our custom Source node
    pub fn source(table_name: String, schema: Schema) -> Self {
        let source = Source::new(table_name, schema);

        let plan = LogicalPlan::Extension(Extension {
            node: Arc::new(source),
        });

        Self { plan }
    }

    // Filter the relation with a predicate
    pub fn filter(self, predicate: Expr) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Filter(Filter::try_new(predicate, Arc::new(self.plan))?),
        })
    }

    // Project specific expressions
    pub fn project(self, exprs: Vec<Expr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Projection(Projection::try_new(exprs, Arc::new(self.plan))?),
        })
    }

    // Join with another relation
    pub fn join(
        self,
        right: Rel,
        join_type: JoinType,
        on_exprs: Vec<(Expr, Expr)>,
        filter: Option<Expr>,
    ) -> Result<Self> {
        let left_schema = self.plan.schema();
        let right_schema = right.plan.schema();
        let join_schema = build_join_schema(left_schema, right_schema, &join_type)?;

        Ok(Self {
            plan: LogicalPlan::Join(Join {
                left: Arc::new(self.plan),
                right: Arc::new(right.plan),
                on: on_exprs,
                filter,
                join_type,
                join_constraint: JoinConstraint::On,
                schema: Arc::new(join_schema),
                null_equality: NullEquality::NullEqualsNothing,
            }),
        })
    }

    // Union with another relation
    pub fn union(self, other: Rel) -> Result<Self> {
        let union_schema = self.plan.schema().clone();
        Ok(Self {
            plan: LogicalPlan::Union(Union {
                inputs: vec![Arc::new(self.plan), Arc::new(other.plan)],
                schema: union_schema,
            }),
        })
    }

    // Aggregate with grouping and aggregate expressions
    pub fn aggregate(self, group_exprs: Vec<Expr>, agg_exprs: Vec<Expr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Aggregate(Aggregate::try_new(
                Arc::new(self.plan),
                group_exprs,
                agg_exprs,
            )?),
        })
    }

    // Remove duplicate rows
    pub fn distinct(self) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Distinct(Distinct::All(Arc::new(self.plan))),
        })
    }

    // Limit the number of rows
    pub fn limit(self, skip: usize, fetch: Option<usize>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Limit(Limit {
                skip: if skip > 0 {
                    Some(Box::new(lit(skip as i64)))
                } else {
                    None
                },
                fetch: fetch.map(|f| Box::new(lit(f as i64))),
                input: Arc::new(self.plan),
            }),
        })
    }

    // Sort by expressions (converts Expr to SortExpr with default ascending, nulls first)
    pub fn sort(self, exprs: Vec<SortExpr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Sort(Sort {
                expr: exprs,
                input: Arc::new(self.plan),
                fetch: None,
            }),
        })
    }
}
