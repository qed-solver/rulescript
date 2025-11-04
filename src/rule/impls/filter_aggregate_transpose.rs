// Pushes filter predicates below aggregate when they only reference GROUP BY columns
// Pattern: Filter(GroupCond(g) ∧ AggCond(g, a), Aggregate(G, A, source))
//       → Filter(AggCond(g, a), Aggregate(G, A, Filter(GroupCond(g), source)))
crate::rule! {
    FilterAggregateTransposeRule {
        schemas: {
            source: (x: T),
        },
        functions: {
            G(T) -> TG,                  // GROUP BY expression
            GroupCond(TG) -> Bool,       // Predicate on GROUP BY columns only (pushable)
            AggCond(TG, U) -> Bool,      // Predicate on group columns AND/OR aggregate results (not pushable)
            Agg{T} -> U,                 // Aggregate function
        },
        from: {
            let agg = crate::aggregate!(source, group: [G(x) as g_output], aggs: [Agg{x} as agg_output]);
            crate::filter!(agg, GroupCond(g_output) && AggCond(g_output, agg_output))
        },
        to: {
            let filtered_source = crate::filter!(source, GroupCond(G(x)));
            let new_agg = crate::aggregate!(filtered_source, group: [G(x) as g_output], aggs: [Agg{x} as agg_output]);
            crate::filter!(new_agg, AggCond(g_output, agg_output))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::functions_aggregate::expr_fn::sum;
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_aggregate_transpose_basic() {
        // Based on Calcite's testPushFilterPastAgg
        // SQL: SELECT deptno, SUM(salary) FROM emp GROUP BY deptno
        //      HAVING deptno = 10 AND SUM(salary) > 50000
        // Expected: Pushes deptno = 10 below aggregate, keeps SUM(salary) > 50000 above

        let emp = emp_table();

        // Build: emp.aggregate(GROUP BY deptno, SUM(salary)).filter(deptno = 10 AND SUM(salary) > 50000)
        let agg = LogicalPlanBuilder::from(emp.clone())
            .aggregate(vec![col("deptno")], vec![sum(col("salary"))])
            .unwrap()
            .build()
            .unwrap();

        let input = LogicalPlanBuilder::from(agg)
            .filter(
                col("deptno")
                    .eq(lit(10))
                    .and(col("sum(emp.salary)").gt(lit(50000.0))),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(deptno = 10).aggregate(GROUP BY deptno, SUM(salary)).filter(SUM(salary) > 50000)
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let new_agg = LogicalPlanBuilder::from(filtered_emp)
            .aggregate(vec![col("deptno")], vec![sum(col("salary"))])
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(new_agg)
            .filter(col("sum(emp.salary)").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterAggregateTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_aggregate_transpose_multiple_group_predicates() {
        // Based on Calcite's testPushFilterPastAggTwo pattern
        // Multiple predicates on GROUP BY column should all be pushed

        let emp = emp_table();

        // Build: emp.aggregate(GROUP BY deptno, SUM(salary)).filter(deptno = 10 AND deptno < 50 AND SUM(salary) > 30000)
        let agg = LogicalPlanBuilder::from(emp.clone())
            .aggregate(vec![col("deptno")], vec![sum(col("salary"))])
            .unwrap()
            .build()
            .unwrap();

        let input = LogicalPlanBuilder::from(agg)
            .filter(
                col("deptno")
                    .eq(lit(10))
                    .and(col("deptno").lt(lit(50)))
                    .and(col("sum(emp.salary)").gt(lit(30000.0))),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Both deptno predicates pushed, SUM predicate stays above
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("deptno").eq(lit(10)).and(col("deptno").lt(lit(50))))
            .unwrap()
            .build()
            .unwrap();

        let new_agg = LogicalPlanBuilder::from(filtered_emp)
            .aggregate(vec![col("deptno")], vec![sum(col("salary"))])
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(new_agg)
            .filter(col("sum(emp.salary)").gt(lit(30000.0)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterAggregateTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_only_aggregate_predicate() {
        // Based on Calcite's testPushFilterPastAggThree
        // Should not match when filter only references aggregate results
        // SQL: SELECT deptno FROM emp GROUP BY deptno HAVING COUNT(*) > 1

        let emp = emp_table();

        let input = LogicalPlanBuilder::from(emp.clone())
            .aggregate(vec![col("deptno")], vec![sum(col("salary"))])
            .unwrap()
            .filter(col("sum(emp.salary)").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterAggregateTransposeRule;
        assert!(rule.try_apply(&input).is_err());
    }

    #[test]
    fn test_no_match_no_aggregate() {
        // Should not match a filter without aggregate below
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterAggregateTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
