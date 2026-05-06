// Prunes a filter over an empty relation (result is still empty)
// Pattern: Filter(Empty, P) → Empty
crate::rule! {
    PruneEmptyFilterRule {
        schemas: {
            source: (col: T),
        },
        functions: {
            P(T) -> Bool,
        },
        from: crate::filter!(crate::empty!(source), P(col)),
        to: crate::empty!(source),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_prune_empty_filter() {
        // Filter(Empty, deptno > 5) → Empty
        let dept = dept_table();
        let empty = empty_from(&dept);

        let input = LogicalPlanBuilder::from(empty.clone())
            .filter(col("deptno").gt(lit(5)))
            .unwrap()
            .build()
            .unwrap();

        let rule = PruneEmptyFilterRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, empty);
    }

    #[test]
    fn test_no_match_filter_over_table() {
        // Filter over actual table should NOT match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .filter(col("deptno").gt(lit(5)))
            .unwrap()
            .build()
            .unwrap();

        let rule = PruneEmptyFilterRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
