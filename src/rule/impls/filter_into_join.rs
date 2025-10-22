// Merges a filter above an inner join into the join condition
// Pattern: Filter(pred, Join(Inner, cond, L, R)) → Join(Inner, cond AND pred, L, R)
crate::rule! {
    FilterIntoJoinRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            JoinCond(TL, TR) -> Bool,
            FilterPred(TL, TR) -> Bool,
        },
        from: {
            let join = crate::join!(left, right, Inner, JoinCond(l, r));
            crate::filter!(join, FilterPred(l, r))
        },
        to: crate::join!(left, right, Inner, JoinCond(l, r) && FilterPred(l, r)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};
    use datafusion::prelude::lit;

    #[test]
    fn test_filter_into_join_basic() {
        // Based on Calcite's testPushAboveFiltersIntoInnerJoinCondition
        // SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno WHERE emp.salary > 1000
        // Pattern: Filter(emp.salary > 1000, Join(emp.deptno = dept.deptno, emp, dept))
        // Result: Join(emp.deptno = dept.deptno AND emp.salary > 1000, emp, dept)

        let emp = emp_table();
        let dept = dept_table();

        // Build: emp JOIN dept ON emp.deptno = dept.deptno
        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let join = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Add filter: emp.salary > 1000
        let filter_pred = col("emp.salary").gt(lit(1000.0));
        let input = LogicalPlanBuilder::from(join)
            .filter(filter_pred.clone())
            .unwrap()
            .build()
            .unwrap();

        // Expected: Join with both conditions ANDed
        let expected = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.and(filter_pred)),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterIntoJoinRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_into_join_cross_table() {
        // Test with cross-table filter predicate
        // SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno WHERE emp.empno > dept.deptno
        // Pattern: Filter(emp.empno > dept.deptno, Join(emp.deptno = dept.deptno, emp, dept))
        // Result: Join(emp.deptno = dept.deptno AND emp.empno > dept.deptno, emp, dept)

        let emp = emp_table();
        let dept = dept_table();

        // Build join
        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let join = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Add cross-table filter: emp.empno > dept.deptno
        let filter_pred = col("emp.empno").gt(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(join)
            .filter(filter_pred.clone())
            .unwrap()
            .build()
            .unwrap();

        // Expected: Join with both conditions ANDed
        let expected = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.and(filter_pred)),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterIntoJoinRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_into_join_duplicate_predicate() {
        // Test merging when WHERE has same predicate as ON clause
        // SQL: SELECT * FROM dept JOIN emp ON d.deptno > e.empno WHERE d.deptno > e.empno
        // Pattern: Filter(d.deptno > e.empno, Join(d.deptno > e.empno, dept, emp))
        // Result: Join(d.deptno > e.empno, dept, emp) - both predicates combined

        let dept = dept_table();
        let emp = emp_table();

        // Build join with condition: dept.deptno > emp.empno
        let join_cond = col("dept.deptno").gt(col("emp.empno"));
        let join = LogicalPlanBuilder::from(dept.clone())
            .join(
                emp.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Add same filter in WHERE: dept.deptno > emp.empno
        let filter_pred = col("dept.deptno").gt(col("emp.empno"));
        let input = LogicalPlanBuilder::from(join)
            .filter(filter_pred.clone())
            .unwrap()
            .build()
            .unwrap();

        // Expected: Join with ANDed condition (pred AND pred)
        let expected = LogicalPlanBuilder::from(dept.clone())
            .join(
                emp.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.and(filter_pred)),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterIntoJoinRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_filter_only() {
        // Should not match a filter without a join underneath
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .filter(col("empno").gt(lit(100)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterIntoJoinRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_join_only() {
        // Should not match a join without a filter above
        let emp = emp_table();
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterIntoJoinRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
