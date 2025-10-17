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

/// Merges two consecutive Filter operators into one with AND
/// Pattern: Filter(P, Filter(Q, source)) → Filter(P AND Q, source)
#[derive(Debug)]
pub struct FilterMergeRule;

impl RewriteRule for FilterMergeRule {
    fn from(&self) -> Rel {
        // Create a generic schema with a single field that represents ALL columns
        // During matching, this single field will be bound to ALL columns of the concrete table
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract predicates that can use any columns from the source
        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        // Pattern: source.filter(Q(col)).filter(P(col))
        // The abstract functions P and Q can match any predicate expressions
        let source = Rel::source("source".to_string(), schema);

        source
            .filter(q.call(vec![col("col")]))
            .unwrap()
            .filter(p.call(vec![col("col")]))
            .unwrap()
    }

    fn to(&self) -> Rel {
        // Same schema and predicates as pattern
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        // Replacement: source.filter(P(col) AND Q(col))
        let source = Rel::source("source".to_string(), schema);

        source
            .filter(p.and(vec![col("col")], q.call(vec![col("col")])))
            .unwrap()
    }

    fn name(&self) -> &str {
        "FilterMergeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for FilterMergeRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::test::utils::*;
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_merge_filter_calcite() {
        // Based on Calcite's testMergeFilter:
        // SQL: select name from (select * from dept where deptno = 10) where deptno = 10
        // This tests merging identical filters

        let dept = dept_table();

        // Build: dept.filter(deptno = 10).filter(deptno = 10)
        let input = LogicalPlanBuilder::from(dept.clone())
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept.filter(deptno = 10 AND deptno = 10)
        let expected = LogicalPlanBuilder::from(dept)
            .filter(col("deptno").eq(lit(10)).and(col("deptno").eq(lit(10))))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterMergeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_merge_different_filters() {
        // Test merging different filter conditions
        // Similar to Calcite patterns but with different predicates

        let emp = emp_table();

        // Build: emp.filter(deptno = 10).filter(salary > 50000)
        let input = LogicalPlanBuilder::from(emp.clone())
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary > 50000 AND deptno = 10)
        // Note: The order is reversed because outer filter (P) comes first in the AND
        let expected = LogicalPlanBuilder::from(emp)
            .filter(
                col("salary")
                    .gt(lit(50000.0))
                    .and(col("deptno").eq(lit(10))),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterMergeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_single_filter() {
        // Should not match a single filter
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterMergeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
