// Changes join tree shape using associativity (INNER joins only)
// Pattern: (Q0 ⋈[P0(x,y)] Q1) ⋈[P1(y,z)] Q2 → Q0 ⋈[P0(x,y)] (Q1 ⋈[P1(y,z)] Q2)
// where Q1 is the "pivot" table appearing in both joins
crate::rule! {
    JoinAssociateRule {
        schemas: {
            q0: (x: T0),
            q1: (y: T1),
            q2: (z: T2),
        },
        functions: {
            P0(T0, T1) -> Bool,
            P1(T1, T2) -> Bool,
        },
        from: {
            let q01 = crate::join!(q0, q1, Inner, P0(x, y));
            crate::join!(q01, q2, Inner, P1(y, z))
        },
        to: {
            let q12 = crate::join!(q1, q2, Inner, P1(y, z));
            crate::join!(q0, q12, Inner, P0(x, y))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};

    #[test]
    fn test_join_associate_basic() {
        // Based on Calcite's testJoinAssociateRuleWithBottomAlwaysTrueCondition
        // SQL: SELECT * FROM (emp JOIN dept ON emp.deptno = dept.deptno) JOIN sales ON dept.deptno = sales.product_id
        // Pattern: ((emp JOIN dept) JOIN sales) → (emp JOIN (dept JOIN sales))
        
        let emp = emp_table();
        let dept = dept_table();
        let sales = sales_table();

        let emp_dept_cond = col("emp.deptno").eq(col("dept.deptno"));
        let emp_dept = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(emp_dept_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        let dept_sales_cond = col("dept.deptno").eq(col("sales.product_id"));
        let input = LogicalPlanBuilder::from(emp_dept)
            .join(
                sales.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(dept_sales_cond.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        let dept_sales = LogicalPlanBuilder::from(dept.clone())
            .join(
                sales.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(dept_sales_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        let emp_dept_cond_expected = col("emp.deptno").eq(col("dept.deptno"));
        let expected = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept_sales,
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(emp_dept_cond_expected),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinAssociateRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_single_join() {
        // Should not match a single join
        let emp = emp_table();
        let dept = dept_table();

        let input = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinAssociateRule;
        assert!(rule.try_apply(&input).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Should not match a single table scan
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp).build().unwrap();

        let rule = JoinAssociateRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
