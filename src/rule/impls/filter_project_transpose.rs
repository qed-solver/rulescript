use crate::{
    ast::{
        opaque::{Field, Schema, Type},
        relational::Rel,
        scalar::Function,
    },
    matcher::DefaultMatcher,
    rule::{ApplicableRule, RewriteRule},
};
use datafusion::logical_expr::col;

/// Pushes a Filter below a Projection by rewriting the filter predicate
/// Pattern: Filter(P(y), Project(f(x), source)) → Project(f(x), Filter(P(f(x)), source))
/// where y are projection output columns and x are source columns
#[derive(Debug)]
pub struct FilterProjectTransposeRule;

impl RewriteRule for FilterProjectTransposeRule {
    fn from(&self) -> Rel {
        // Schema with a single abstract field representing all source columns
        let schema = Schema {
            fields: vec![Field {
                name: "x".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        // Abstract projection function f: maps source columns to projection outputs
        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );

        // Abstract predicate P: operates on projection output columns
        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Pattern: source.project(f(x) as f_output).filter(P(f_output))
        let proj = source
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap();

        proj.filter(p.call(vec![col("f_output")])).unwrap()
    }

    fn to(&self) -> Rel {
        let schema = Schema {
            fields: vec![Field {
                name: "x".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );

        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Replacement: source.filter(P(f(x))).project(f(x))
        // The nested call P(f(x)) performs the column reference rewriting
        source
            .filter(p.call(vec![f.call(vec![col("x")])]))
            .unwrap()
            .project(vec![f.call(vec![col("x")])])
            .unwrap()
    }

    fn name(&self) -> &str {
        "FilterProjectTransposeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for FilterProjectTransposeRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::test::utils::*;
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_project_transpose_basic() {
        // Based on Calcite's FilterProjectTransposeRule tests
        // Concrete plan: source → project(salary, dept_id) → filter(salary > 50000)
        // Expected: source → filter(salary > 50000) → project(salary, dept_id)

        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter(salary > 50000)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary > 50000).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_multiple_columns() {
        // Test with filter referencing multiple projected columns
        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter(salary > 50000 AND deptno = 10)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(
                col("salary")
                    .gt(lit(50000.0))
                    .and(col("deptno").eq(lit(10))),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary > 50000 AND deptno = 10).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(
                col("salary")
                    .gt(lit(50000.0))
                    .and(col("deptno").eq(lit(10))),
            )
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_with_expression() {
        // Based on Calcite's testFilterProjectTranspose
        // Test with projection containing an expression, not just column references
        // SQL equivalent: SELECT deptno * 2 AS twiceDeptno FROM dept
        //                 WHERE twiceDeptno = 20
        let dept = dept_table();

        // Build: dept.project(deptno * 2 as doubled).filter(doubled = 20)
        let input = LogicalPlanBuilder::from(dept.clone())
            .project(vec![(col("deptno") * lit(2)).alias("doubled")])
            .unwrap()
            .filter(col("doubled").eq(lit(20)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept.filter(deptno * 2 = 20).project(deptno * 2 as doubled)
        // The filter predicate is "inlined" with the projection expression
        let expected = LogicalPlanBuilder::from(dept)
            .filter((col("deptno") * lit(2)).eq(lit(20)))
            .unwrap()
            .project(vec![(col("deptno") * lit(2)).alias("doubled")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_subset_columns() {
        // Based on Calcite's testFilterProjectTransposeRule
        // Project a single column, then filter on it
        // This tests that the rule works when projection reduces columns
        let emp = emp_table();

        // Build: emp.project(salary).filter(salary = 11500)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary")])
            .unwrap()
            .filter(col("salary").eq(lit(11500.0)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary = 11500).project(salary)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").eq(lit(11500.0)))
            .unwrap()
            .project(vec![col("salary")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_complex_predicate() {
        // Test with more complex filter predicate (OR condition)
        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter((salary > 50000) OR (deptno = 10))
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(col("salary").gt(lit(50000.0)).or(col("deptno").eq(lit(10))))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter((salary > 50000) OR (deptno = 10)).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)).or(col("deptno").eq(lit(10))))
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_filter_only() {
        // Should not match a filter without a projection below it
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
