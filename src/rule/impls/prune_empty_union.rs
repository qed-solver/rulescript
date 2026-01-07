// Prune rules for unions involving empty relations

// Removes empty left branch from union
// Pattern: Union(Empty, B) → B
crate::rule! {
    PruneEmptyUnionLeftRule {
        schemas: {
            a: (col: T),
            b: (col: T),
        },
        functions: {},
        from: crate::union!(crate::empty!(a), b),
        to: b,
    }
}

// Removes empty right branch from union
// Pattern: Union(A, Empty) → A
crate::rule! {
    PruneEmptyUnionRightRule {
        schemas: {
            a: (col: T),
            b: (col: T),
        },
        functions: {},
        from: crate::union!(a, crate::empty!(b)),
        to: a,
    }
}

// Reduces union of two empty relations to single empty
// Pattern: Union(Empty, Empty) → Empty
crate::rule! {
    PruneEmptyUnionBothRule {
        schemas: {
            a: (col: T),
            b: (col: T),
        },
        functions: {},
        from: crate::union!(crate::empty!(a), crate::empty!(b)),
        to: crate::empty!(a),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::LogicalPlanBuilder;

    #[test]
    fn test_prune_empty_union_left() {
        // Union(Empty, dept) → dept
        let dept = dept_table();
        let empty = empty_from(&dept);

        // Build: union(empty, dept)
        let input = LogicalPlanBuilder::from(empty)
            .union(dept.clone())
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept
        let expected = dept;

        let rule = PruneEmptyUnionLeftRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_prune_empty_union_right() {
        // Union(dept, Empty) → dept
        let dept = dept_table();
        let empty = empty_from(&dept);

        // Build: union(dept, empty)
        let input = LogicalPlanBuilder::from(dept.clone())
            .union(empty)
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept
        let expected = dept;

        let rule = PruneEmptyUnionRightRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_prune_empty_union_both() {
        // Union(Empty, Empty) → Empty
        let dept = dept_table();
        let empty1 = empty_from(&dept);
        let empty2 = empty_from(&dept);

        // Build: union(empty1, empty2)
        let input = LogicalPlanBuilder::from(empty1.clone())
            .union(empty2)
            .unwrap()
            .build()
            .unwrap();

        let expected = empty1;

        let rule = PruneEmptyUnionBothRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_union_with_two_tables() {
        // Union(dept1, dept2) should NOT match any prune rule
        let dept1 = dept_table();
        let dept2 = dept_table();

        let plan = LogicalPlanBuilder::from(dept1)
            .union(dept2)
            .unwrap()
            .build()
            .unwrap();

        let rule_left = PruneEmptyUnionLeftRule;
        let rule_right = PruneEmptyUnionRightRule;
        let rule_both = PruneEmptyUnionBothRule;

        assert!(rule_left.try_apply(&plan).is_err());
        assert!(rule_right.try_apply(&plan).is_err());
        assert!(rule_both.try_apply(&plan).is_err());
    }

    #[test]
    fn test_prune_empty_union_left_does_not_match_right_empty() {
        // Union(dept, Empty) should NOT match PruneEmptyUnionLeftRule
        let dept = dept_table();
        let empty = empty_from(&dept);

        let plan = LogicalPlanBuilder::from(dept)
            .union(empty)
            .unwrap()
            .build()
            .unwrap();

        let rule = PruneEmptyUnionLeftRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_prune_empty_union_right_does_not_match_left_empty() {
        // Union(Empty, dept) should NOT match PruneEmptyUnionRightRule
        let dept = dept_table();
        let empty = empty_from(&dept);

        let plan = LogicalPlanBuilder::from(empty)
            .union(dept)
            .unwrap()
            .build()
            .unwrap();

        let rule = PruneEmptyUnionRightRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
