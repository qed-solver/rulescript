// Pulls projection from left input of join up above the join
// Pattern: Join(Inner, P(l', r), Project(f(l), left), right) → Project(f(l), r, Join(Inner, P(f(l), r), left, right))
// where l' are projected columns from left and l are original left columns
crate::rule! {
    JoinLeftProjectTransposeRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            f(TL) -> Tf,
            P(Tf, TR) -> Bool,
        },
        from: {
            let proj_left = crate::project!(left, [f(l) as f_output]);
            crate::join!(proj_left, right, Inner, P(f_output, r))
        },
        to: {
            let new_join = crate::join!(left, right, Inner, P(f(l), r));
            crate::project!(new_join, [f(l), r])
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};

    #[test]
    fn test_join_left_project_transpose_basic() {
        // Based on Calcite's JoinProjectTranspose patterns (adapted for LEFT + INNER)
        // SQL: SELECT * FROM (SELECT ename, deptno FROM emp) a JOIN dept b ON a.deptno = b.deptno
        // Pattern: Join(Project(ename, deptno, emp), dept)
        // Result: Project(ename, deptno, dept.*, Join(emp, dept))
        
        let emp = emp_table();
        let dept = dept_table();

        // Build: project(ename, deptno).join(dept)
        let proj_emp = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("ename"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let input = LogicalPlanBuilder::from(proj_emp)
            .join(
                dept.clone(),
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.join(dept).project(ename, deptno, dept.*)
        let join_cond = col("emp.deptno").eq(col("dept.deptno"));
        let joined = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        let dept_cols: Vec<_> = dept.schema().columns().into_iter().map(|c| col(format!("dept.{}", c.name))).collect();
        let mut proj_exprs = vec![col("emp.ename"), col("emp.deptno")];
        proj_exprs.extend(dept_cols);

        let expected = LogicalPlanBuilder::from(joined)
            .project(proj_exprs)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinLeftProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_no_left_projection() {
        // Should not match when left input is not a projection
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

        let rule = JoinLeftProjectTransposeRule;
        assert!(rule.try_apply(&input).is_err());
    }

    #[test]
    fn test_no_match_right_projection_only() {
        // Should not match when only right input has projection
        let emp = emp_table();
        let dept = dept_table();

        let proj_dept = LogicalPlanBuilder::from(dept)
            .project(vec![col("deptno"), col("dname")])
            .unwrap()
            .build()
            .unwrap();

        let input = LogicalPlanBuilder::from(emp)
            .join(
                proj_dept,
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinLeftProjectTransposeRule;
        assert!(rule.try_apply(&input).is_err());
    }
}
