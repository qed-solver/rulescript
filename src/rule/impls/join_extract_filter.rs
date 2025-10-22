// Extracts join condition as a filter above cartesian join
// Pattern: Join(Inner, cond, L, R) → Filter(cond, Join(Inner, TRUE, L, R))
crate::rule! {
    JoinExtractFilterRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            JoinCond(TL, TR) -> Bool,
        },
        from: crate::join!(left, right, Inner, JoinCond(l, r)),
        to: {
            let cartesian = crate::join!(left, right, Inner, true);
            crate::filter!(cartesian, JoinCond(l, r))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};
    use datafusion::prelude::lit;

    #[test]
    fn test_join_extract_filter_basic() {
        // Based on Calcite's testExtractJoinFilterRule
        // SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno
        // Pattern: Join(emp.deptno = dept.deptno, emp, dept)
        // Result: Filter(emp.deptno = dept.deptno, Join(true, emp, dept))

        let emp = emp_table();
        let dept = dept_table();

        // Build: emp JOIN dept ON emp.deptno = dept.deptno
        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Cartesian join with filter above
        let cartesian = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(lit(true)),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(cartesian)
            .filter(join_cond)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinExtractFilterRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_extract_filter_complex_condition() {
        // Test extracting complex join condition with AND
        // SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno AND emp.salary > 1000
        // Pattern: Join(emp.deptno = dept.deptno AND emp.salary > 1000, emp, dept)
        // Result: Filter(emp.deptno = dept.deptno AND emp.salary > 1000, Join(true, emp, dept))

        let emp = emp_table();
        let dept = dept_table();

        // Build join with complex condition
        let join_cond = col("emp.deptno")
            .eq(col("dept.deptno"))
            .and(col("emp.salary").gt(lit(1000.0)));

        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Cartesian join with filter above
        let cartesian = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(lit(true)),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(cartesian)
            .filter(join_cond)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinExtractFilterRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_extract_filter_cross_table_predicate() {
        // Test extracting cross-table predicate
        // SQL: SELECT * FROM emp JOIN dept ON emp.empno > dept.deptno
        // Pattern: Join(emp.empno > dept.deptno, emp, dept)
        // Result: Filter(emp.empno > dept.deptno, Join(true, emp, dept))

        let emp = emp_table();
        let dept = dept_table();

        // Build join with cross-table condition
        let join_cond = col("emp.empno").gt(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Cartesian join with filter above
        let cartesian = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(lit(true)),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(cartesian)
            .filter(join_cond)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinExtractFilterRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_filter_above_join() {
        // Should not match if there's already a filter above the join
        let emp = emp_table();
        let dept = dept_table();

        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let join = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        // Add a filter above - now the pattern doesn't match (pattern is just a join)
        let plan = LogicalPlanBuilder::from(join)
            .filter(col("emp.empno").gt(lit(100)))
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinExtractFilterRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Should not match a single table scan
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp).build().unwrap();

        let rule = JoinExtractFilterRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
