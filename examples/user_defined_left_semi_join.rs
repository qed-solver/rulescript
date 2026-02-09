// User-Defined Operator Example: LeftSemiJoin with EXISTS Semantics
//
// Demonstrates defining a custom LeftSemiJoin operator that can be used as BOTH:
// 1. A pattern (via UserDefinedLogicalOperator trait) with EXISTS semantics
// 2. A concrete DataFusion plan (via UserDefinedLogicalNodeCore trait)
//
// Semantics: SELECT * FROM L WHERE EXISTS (SELECT 1 FROM R WHERE condition)
//
// This requires:
// 1. Building EXISTS subquery in semantics()
// 2. Adding EXISTS serialization to QED verifier
// 3. Proper context handling in resolve/instantiate
// 4. Implementing both UserDefinedLogicalOperator and UserDefinedLogicalNodeCore

use std::sync::Arc;

use datafusion::{
    common::{DFSchemaRef, Spans},
    error::Result as DFResult,
    logical_expr::{
        Expr, Filter, LogicalPlan, LogicalPlanBuilder, Subquery, UserDefinedLogicalNodeCore, exists,
    },
};
use rulescript::{
    ast::{
        extension::{UserDefinedLogicalOperator, UserDefinedLogicalPattern},
        opaque::{Field, Schema},
    },
    matcher::{DefaultMatcher, PatternMatcher, RuleError},
};

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct LeftSemiJoin {
    left: LogicalPlan,
    right: LogicalPlan,
    condition: Expr,
    schema: Schema,
    /// DataFusion schema (for UserDefinedLogicalNodeCore)
    df_schema: DFSchemaRef,
    /// Semantics: Filter(left, EXISTS(Subquery(right with condition)))
    semantics: LogicalPlan,
}

impl LeftSemiJoin {
    pub fn new(
        left: LogicalPlan,
        right: LogicalPlan,
        condition: Expr,
    ) -> Result<Self, datafusion::error::DataFusionError> {
        // Output schema is same as left input - preserve the original DFSchema
        let left_schema = left.schema();

        // Build opaque schema for pattern matching (used by UserDefinedLogicalOperator)
        let schema = Schema {
            fields: left_schema
                .fields()
                .iter()
                .map(|f| Field {
                    name: f.name().clone(),
                    data_type: rulescript::ast::opaque::Type::Generic {
                        id: format!("{:?}", f.data_type()),
                    },
                    nullable: f.is_nullable(),
                })
                .collect(),
        };

        // Build semantics: SELECT * FROM left WHERE EXISTS (SELECT 1 FROM right WHERE condition)
        let subquery_filter =
            LogicalPlan::Filter(Filter::try_new(condition.clone(), Arc::new(right.clone()))?);
        let subquery_plan = LogicalPlan::Subquery(Subquery {
            subquery: Arc::new(subquery_filter),
            outer_ref_columns: left_schema
                .columns()
                .into_iter()
                .map(Expr::Column)
                .collect(),
            spans: Spans::new(),
        });

        let exists_expr = exists(Arc::new(subquery_plan));

        let semantics = LogicalPlanBuilder::from(left.clone())
            .filter(exists_expr)?
            .build()?;

        // Preserve the original left schema with table qualifiers for DataFusion compatibility
        let df_schema = left_schema.clone();

        Ok(Self {
            left,
            right,
            condition,
            schema,
            df_schema,
            semantics,
        })
    }
}

impl UserDefinedLogicalOperator for LeftSemiJoin {
    fn operator_name(&self) -> &str {
        "LeftSemiJoin"
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        vec![&self.left, &self.right]
    }

    fn schema(&self) -> &Schema {
        &self.schema
    }

    fn df_schema(&self) -> Option<DFSchemaRef> {
        Some(self.df_schema.clone())
    }

    fn semantics(&self) -> &LogicalPlan {
        &self.semantics
    }

    fn resolve(
        &self,
        pattern: &UserDefinedLogicalPattern,
        concrete: &LogicalPlan,
        matcher: &mut dyn PatternMatcher,
    ) -> Result<(), RuleError> {
        // Match against concrete LeftSemiJoin (our custom extension only)
        let concrete_lsj = match concrete {
            LogicalPlan::Extension(ext) => ext
                .node
                .as_any()
                .downcast_ref::<LeftSemiJoin>()
                .ok_or_else(|| RuleError::StructureMismatch {
                    pattern: Box::new(LogicalPlan::Extension(
                        datafusion::logical_expr::Extension {
                            node: Arc::new(pattern.clone()),
                        },
                    )),
                    target: Box::new(concrete.clone()),
                })?,
            _ => {
                return Err(RuleError::StructureMismatch {
                    pattern: Box::new(LogicalPlan::Extension(
                        datafusion::logical_expr::Extension {
                            node: Arc::new(pattern.clone()),
                        },
                    )),
                    target: Box::new(concrete.clone()),
                });
            }
        };

        // Downcast to DefaultMatcher to access internal methods
        let dm = matcher
            .as_any_mut()
            .downcast_mut::<DefaultMatcher>()
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: "LeftSemiJoin requires DefaultMatcher".to_string(),
            })?;

        // Resolve inputs and get their contexts
        let left_context = dm.resolve_plan(&self.left, &concrete_lsj.left)?;
        let right_context = dm.resolve_plan(&self.right, &concrete_lsj.right)?;

        // Merge contexts for condition resolution (condition can reference both sides)
        let mut merged_context = left_context.clone();
        merged_context.extend(right_context);

        // Resolve the join condition (pattern condition vs concrete condition)
        dm.resolve_expr(&self.condition, &concrete_lsj.condition, &merged_context)?;

        // Store ONLY left context (semi join outputs only left columns, not right)
        dm.store_user_defined_context(pattern, concrete.clone(), left_context);

        Ok(())
    }

    fn instantiate(&self, matcher: &mut dyn PatternMatcher) -> Result<LogicalPlan, RuleError> {
        // Downcast to DefaultMatcher to access instantiate_plan
        let dm = matcher
            .as_any_mut()
            .downcast_mut::<DefaultMatcher>()
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: "LeftSemiJoin requires DefaultMatcher".to_string(),
            })?;

        // Instantiate inputs and get their contexts
        let (instantiated_left, left_context) = dm.instantiate_plan(&self.left)?;
        let (instantiated_right, _right_context) = dm.instantiate_plan(&self.right)?;

        // Create new LeftSemiJoin with instantiated inputs
        let new_lsj = LogicalPlan::Extension(datafusion::logical_expr::Extension {
            node: Arc::new(UserDefinedLogicalPattern::new(Arc::new(
                LeftSemiJoin::new(
                    instantiated_left,
                    instantiated_right,
                    self.condition.clone(),
                )
                .map_err(|e| RuleError::InvalidPattern {
                    reason: format!("Failed to create LeftSemiJoin: {}", e),
                })?,
            ))),
        });

        // Store context for the new plan (only left context, semi-join semantics)
        let pattern = UserDefinedLogicalPattern::new(Arc::new(self.clone()));
        dm.store_user_defined_context(&pattern, new_lsj.clone(), left_context);

        Ok(new_lsj)
    }
}

// Implement UserDefinedLogicalNodeCore so LeftSemiJoin can be used as a concrete DataFusion plan
impl UserDefinedLogicalNodeCore for LeftSemiJoin {
    fn name(&self) -> &str {
        "LeftSemiJoin"
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        vec![&self.left, &self.right]
    }

    fn schema(&self) -> &DFSchemaRef {
        &self.df_schema
    }

    fn expressions(&self) -> Vec<Expr> {
        vec![self.condition.clone()]
    }

    fn fmt_for_explain(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(f, "LeftSemiJoin: condition={:?}", self.condition)
    }

    fn with_exprs_and_inputs(&self, exprs: Vec<Expr>, inputs: Vec<LogicalPlan>) -> DFResult<Self> {
        if exprs.len() != 1 {
            return Err(datafusion::error::DataFusionError::Plan(
                "LeftSemiJoin requires exactly one expression".to_string(),
            ));
        }
        if inputs.len() != 2 {
            return Err(datafusion::error::DataFusionError::Plan(
                "LeftSemiJoin requires exactly two inputs".to_string(),
            ));
        }
        Self::new(inputs[0].clone(), inputs[1].clone(), exprs[0].clone())
    }
}

// Implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for LeftSemiJoin {
    fn eq(&self, other: &Self) -> bool {
        self.left == other.left && self.right == other.right && self.condition == other.condition
    }
}

impl Eq for LeftSemiJoin {}

impl PartialOrd for LeftSemiJoin {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for LeftSemiJoin {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        // Simple comparison based on schema
        self.schema.cmp(&other.schema)
    }
}

impl std::hash::Hash for LeftSemiJoin {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.left.hash(state);
        self.right.hash(state);
        // condition doesn't implement Hash, skip it
    }
}

// Example rule demonstrating LeftSemiJoin with EXISTS semantics for QED verification
// Copied from LeftSemiJoinFilterTransposeRule, using custom LeftSemiJoin operator
rulescript::rule! {
    LeftSemiJoinExistsRule {
        schemas: {
            left: (x: TL),
            right: (r: TR),
        },
        functions: {
            P(TL) -> Bool,
            C(TL, TR) -> Bool,
        },
        from: {
            let filtered = rulescript::filter!(left, P(x));
            // Replace join! with custom LeftSemiJoin - wrap as pattern for matching
            let lsj = LeftSemiJoin::new(filtered.plan, right.plan, rulescript::pred!(C(x, r)))
                .expect("Failed to create LeftSemiJoin");
            rulescript::extend!(lsj)
        },
        to: {
            // Replace join! with custom LeftSemiJoin - wrap as pattern
            let lsj = LeftSemiJoin::new(left.plan, right.plan, rulescript::pred!(C(x, r)))
                .expect("Failed to create LeftSemiJoin");
            let semi_join = rulescript::extend!(lsj);
            rulescript::filter!(semi_join, P(x))
        },
    }
}

// Pushes LeftSemiJoin below aggregate when join condition only references GROUP BY columns
// Pattern: LeftSemiJoin(Aggregate(G, A, source), right, Cond(g, r))
//       → Aggregate(G, A, LeftSemiJoin(source, right, Cond(G(x), r)))
rulescript::rule! {
    LeftSemiJoinAggregateTransposeRule {
        schemas: {
            source: (x: T),
            right: (r: TR),
        },
        functions: {
            G(T) -> TG,              // GROUP BY expression
            Cond(TG, TR) -> Bool,    // Join condition on group columns only (pushable)
            Agg{T} -> U,             // Aggregate function
        },
        from: {
            let agg = rulescript::aggregate!(source, group: [G(x) as g], aggs: [Agg{x} as a]);
            let lsj = LeftSemiJoin::new(agg.plan, right.plan, rulescript::pred!(Cond(g, r)))
                .expect("Failed to create LeftSemiJoin");
            rulescript::extend!(lsj)
        },
        to: {
            // Push semi-join below aggregate, rewriting condition to use pre-agg columns
            let lsj = LeftSemiJoin::new(source.plan, right.plan, rulescript::pred!(Cond(G(x), r)))
                .expect("Failed to create LeftSemiJoin");
            let semi_joined = rulescript::extend!(lsj);
            rulescript::aggregate!(semi_joined, group: [G(x) as g], aggs: [Agg{x} as a])
        },
    }
}

fn main() {
    println!("=== User-Defined Operator: LeftSemiJoin ===\n");
    println!("This example shows how to define LeftSemiJoin that works as BOTH:");
    println!("  1. Pattern (UserDefinedLogicalOperator) - with EXISTS semantics");
    println!("  2. Concrete plan (UserDefinedLogicalNodeCore) - executable in DataFusion\n");
    println!("Semantics: SELECT * FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)");
    println!(
        "Pattern matching: Matches only LeftSemiJoin extension nodes (not DataFusion's built-in Join)"
    );

    // Demonstrate QED export with EXISTS semantics
    println!("=== QED Export Example ===\n");

    let rule = LeftSemiJoinExistsRule;
    let mut serializer = rulescript::verifier::qed::QedSerializer::new();
    let json = rulescript::verifier::Verifier::serialize_rule(&mut serializer, &rule)
        .expect("Failed to serialize rule");

    println!("LeftSemiJoinExistsRule QED JSON output:");
    println!("{}", json);

    // Also demonstrate the new aggregate transpose rule
    println!("\n=== LeftSemiJoinAggregateTransposeRule ===\n");
    let agg_rule = LeftSemiJoinAggregateTransposeRule;
    let mut serializer = rulescript::verifier::qed::QedSerializer::new();
    let agg_json = rulescript::verifier::Verifier::serialize_rule(&mut serializer, &agg_rule)
        .expect("Failed to serialize aggregate rule");
    println!("QED JSON output:");
    println!("{}", agg_json);
}

#[cfg(test)]
mod tests {
    use super::*;
    use datafusion::functions_aggregate::count::count_all;
    use datafusion::logical_expr::{LogicalPlan, LogicalPlanBuilder, col};
    use rulescript::rule::ApplicableRule;

    #[test]
    fn test_left_semi_join_filter_transpose_basic() {
        // Based on Calcite's testPushSemiJoinPastFilter
        // SQL: select e.ename from emp e, dept d
        //      where e.deptno = d.deptno and e.ename = 'foo'
        // Pattern: LeftSemiJoin(Filter(emp, ename='foo'), dept)
        // Expected: Filter(LeftSemiJoin(emp, dept), ename='foo')

        let emp = rulescript::rule::test::utils::emp_table();
        let dept = rulescript::rule::test::utils::dept_table();

        // Build: Filter(emp, ename = 'foo')
        let filtered_emp = datafusion::logical_expr::LogicalPlanBuilder::from(emp.clone())
            .filter(datafusion::logical_expr::col("ename").eq(datafusion::logical_expr::lit("foo")))
            .unwrap()
            .build()
            .unwrap();

        // Build: LeftSemiJoin(Filter(emp), dept) on emp.deptno = dept.deptno
        let join_filter = datafusion::logical_expr::col("emp.deptno")
            .eq(datafusion::logical_expr::col("dept.deptno"));
        let lsj_with_filter =
            LeftSemiJoin::new(filtered_emp, dept.clone(), join_filter.clone()).unwrap();
        let input =
            datafusion::logical_expr::LogicalPlan::Extension(datafusion::logical_expr::Extension {
                node: std::sync::Arc::new(lsj_with_filter),
            });

        // Expected: LeftSemiJoin(emp, dept) then Filter(ename = 'foo')
        let lsj = LeftSemiJoin::new(emp, dept, join_filter).unwrap();
        let wrapped_lsj =
            rulescript::ast::extension::UserDefinedLogicalPattern::new(std::sync::Arc::new(lsj));
        let semi_join =
            datafusion::logical_expr::LogicalPlan::Extension(datafusion::logical_expr::Extension {
                node: std::sync::Arc::new(wrapped_lsj),
            });

        let expected = datafusion::logical_expr::LogicalPlanBuilder::from(semi_join)
            .filter(
                datafusion::logical_expr::col("emp.ename").eq(datafusion::logical_expr::lit("foo")),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinExistsRule;
        let result = rule.try_apply(&input).unwrap();

        // Compare display strings instead of direct equality
        // (UserDefinedLogicalPattern wrapping causes structural differences but same logical plan)
        let result_str = format!("{}", result.display_indent());
        let expected_str = format!("{}", expected.display_indent());

        assert_eq!(
            result_str, expected_str,
            "\nResult and expected plans differ:\nResult:\n{}\nExpected:\n{}",
            result_str, expected_str
        );
    }

    #[test]
    fn test_left_semi_join_aggregate_transpose_basic() {
        // SQL equivalent: SELECT deptno, COUNT(*) FROM emp GROUP BY deptno
        //                 WHERE EXISTS (SELECT 1 FROM dept WHERE emp.deptno = dept.deptno)
        // Pattern: LeftSemiJoin(Aggregate(emp, GROUP BY deptno), dept)
        // Expected: Aggregate(LeftSemiJoin(emp, dept), GROUP BY deptno)

        let emp = rulescript::rule::test::utils::emp_table();
        let dept = rulescript::rule::test::utils::dept_table();

        // Build: Aggregate(emp, GROUP BY deptno, COUNT(*))
        let agg = LogicalPlanBuilder::from(emp.clone())
            .aggregate(vec![col("deptno")], vec![count_all()])
            .unwrap()
            .build()
            .unwrap();

        // Build: LeftSemiJoin(Aggregate, dept) on emp.deptno = dept.deptno
        // Note: aggregate output preserves the table qualifier from the input
        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let lsj = LeftSemiJoin::new(agg, dept.clone(), join_cond.clone()).unwrap();
        let input = LogicalPlan::Extension(datafusion::logical_expr::Extension {
            node: Arc::new(lsj),
        });

        // Expected: Aggregate(LeftSemiJoin(emp, dept), GROUP BY deptno)
        let inner_lsj = LeftSemiJoin::new(
            emp.clone(),
            dept.clone(),
            col("emp.deptno").eq(col("dept.deptno")),
        )
        .unwrap();
        let wrapped_lsj =
            rulescript::ast::extension::UserDefinedLogicalPattern::new(Arc::new(inner_lsj));
        let semi_joined = LogicalPlan::Extension(datafusion::logical_expr::Extension {
            node: Arc::new(wrapped_lsj),
        });

        let expected = LogicalPlanBuilder::from(semi_joined)
            .aggregate(vec![col("deptno")], vec![count_all()])
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinAggregateTransposeRule;
        let result = rule.try_apply(&input).unwrap();

        let result_str = format!("{}", result.display_indent());
        let expected_str = format!("{}", expected.display_indent());
        assert_eq!(
            result_str, expected_str,
            "\nResult and expected plans differ:\nResult:\n{}\nExpected:\n{}",
            result_str, expected_str
        );
    }
}
