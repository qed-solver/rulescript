// Reduces a filter with a constant FALSE predicate to an empty relation
// Pattern: Filter(R, false) → Empty (with R's schema)
crate::rule! {
    FilterReduceFalseRule {
        schemas: {
            source: (col: T),
        },
        functions: {},
        from: crate::filter!(source, false),
        to: crate::empty!(source),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_reduce_false() {
        // Filter(dept, false) → Empty
        let dept = dept_table();

        let input = LogicalPlanBuilder::from(dept.clone())
            .filter(lit(false))
            .unwrap()
            .build()
            .unwrap();

        let expected = empty_from(&dept);

        let rule = FilterReduceFalseRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_filter_true() {
        // Filter(dept, true) should NOT match FilterReduceFalseRule
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .filter(lit(true))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterReduceFalseRule;
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

        let rule = FilterReduceFalseRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Single table without filter should NOT match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept).build().unwrap();

        let rule = FilterReduceFalseRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
