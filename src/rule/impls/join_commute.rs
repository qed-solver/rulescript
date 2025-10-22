// Permutes the inputs to a join, swapping left and right sides
// Pattern: Join(Inner, P(l, r), left, right) → Project([l, r], Join(Inner, P(r, l), right, left))
crate::rule! {
    JoinCommuteRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            P(TL, TR) -> Bool,
        },
        from: crate::join!(left, right, Inner, P(l, r)),
        to: {
            let swapped_join = crate::join!(right, left, Inner, P(r, l));
            crate::project!(swapped_join, [l, r])
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};

    #[test]
    fn test_join_commute_basic() {
        // Based on Calcite's JoinCommute test: EMP JOIN DEPT
        // Pattern: emp.join(dept, emp.deptno = dept.deptno)
        // Result: dept.join(emp, dept.deptno = emp.deptno).project(emp cols, dept cols)

        let emp = emp_table();
        let dept = dept_table();

        // Build: emp JOIN dept ON emp.deptno = dept.deptno
        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept JOIN emp with filter, then project [emp.*, dept.*]
        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let swapped = LogicalPlanBuilder::from(dept.clone())
            .join(
                emp.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let emp_cols: Vec<_> = emp.schema().columns().into_iter().map(col).collect();
        let dept_cols: Vec<_> = dept
            .schema()
            .columns()
            .into_iter()
            .map(col)
            .collect();
        let mut proj_exprs = emp_cols;
        proj_exprs.extend(dept_cols);

        let expected = LogicalPlanBuilder::from(swapped)
            .project(proj_exprs)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinCommuteRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_commute_with_filter_condition() {
        // Test join with additional filter in join condition
        // Pattern: sales.join(product, sales.prod_id = product.id AND product.price > 100)

        let sales = sales_table();
        let product = product_table();

        // Build: sales JOIN product ON sales.product_id = product.product_id AND product.price > 100
        let join_filter = col("product.price").gt(datafusion::prelude::lit(100.0));

        let input = LogicalPlanBuilder::from(sales.clone())
            .join(
                product.clone(),
                JoinType::Inner,
                (vec!["product_id"], vec!["product_id"]),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: product JOIN sales with filter, then project [sales.*, product.*]
        let full_filter = col("sales.product_id")
            .eq(col("product.product_id"))
            .and(join_filter);
        let swapped = LogicalPlanBuilder::from(product.clone())
            .join(
                sales.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(full_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let sales_cols: Vec<_> = sales
            .schema()
            .columns()
            .into_iter()
            .map(col)
            .collect();
        let product_cols: Vec<_> = product
            .schema()
            .columns()
            .into_iter()
            .map(col)
            .collect();
        let mut proj_exprs = sales_cols;
        proj_exprs.extend(product_cols);

        let expected = LogicalPlanBuilder::from(swapped)
            .project(proj_exprs)
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinCommuteRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_single_table() {
        // Should not match a single table scan
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp).build().unwrap();

        let rule = JoinCommuteRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
