/// Merges two consecutive Filter operators into one with AND
/// Pattern: Filter(P, Filter(Q, source)) → Filter(P AND Q, source)
crate::rule! {
    FilterMergeRule {
        schemas: {
            source: (col: T),
        },
        functions: {
            P(T) -> Bool,
            Q(T) -> Bool,
        },
        from: crate::filter!(crate::filter!(source, Q(col)), P(col)),
        to: crate::filter!(source, P(col) && Q(col)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
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
