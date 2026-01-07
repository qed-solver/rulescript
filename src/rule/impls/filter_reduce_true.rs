// Removes a filter with a constant TRUE predicate
// Pattern: Filter(R, true) → R
crate::rule! {
    FilterReduceTrueRule {
        schemas: {
            source: (col: T),
        },
        functions: {},
        from: crate::filter!(source, true),
        to: source,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_reduce_true() {
        // Filter(dept, true) → dept
        let dept = dept_table();

        let input = LogicalPlanBuilder::from(dept.clone())
            .filter(lit(true))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterReduceTrueRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, dept);
    }

    #[test]
    fn test_no_match_filter_false() {
        // Filter(dept, false) should NOT match FilterReduceTrueRule
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .filter(lit(false))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterReduceTrueRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_filter_with_predicate() {
        // Filter with actual predicate should NOT match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .filter(col("deptno").eq(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterReduceTrueRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Single table without filter should NOT match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept).build().unwrap();

        let rule = FilterReduceTrueRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
