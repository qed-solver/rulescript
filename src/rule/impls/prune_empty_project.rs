// Prunes a projection over an empty relation (result is empty with projected schema)
// Pattern: Project(Empty, f) → Empty (with projected schema)
crate::rule! {
    PruneEmptyProjectRule {
        schemas: {
            source: (col: T),
        },
        functions: {
            f(T) -> U,
        },
        from: crate::project!(crate::empty!(source), [f(col) as result]),
        to: crate::empty!(crate::project!(source, [f(col) as result])),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col};

    #[test]
    fn test_prune_empty_project() {
        // Project(Empty, [deptno]) → Empty (with projected schema)
        let dept = dept_table();
        let empty = empty_from(&dept);

        let input = LogicalPlanBuilder::from(empty)
            .project(vec![col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        // Expected: Empty with projected schema (just deptno column)
        let expected = LogicalPlanBuilder::from(dept)
            .project(vec![col("deptno")])
            .unwrap()
            .build()
            .unwrap();
        let expected = empty_from(&expected);

        let rule = PruneEmptyProjectRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_project_over_table() {
        // Project over actual table should NOT match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .project(vec![col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = PruneEmptyProjectRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
