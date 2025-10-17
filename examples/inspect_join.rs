use datafusion::prelude::*;
use datafusion::error::Result;
use datafusion::logical_expr::LogicalPlan;

#[tokio::main]
async fn main() -> Result<()> {
    let ctx = SessionContext::new();
    
    ctx.sql("CREATE TABLE emp (deptno INT, salary INT)").await?;
    ctx.sql("CREATE TABLE dept (deptno INT, min_salary INT)").await?;
    
    println!("=== Test 1: Regular ON ===");
    let df1 = ctx.sql("SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno").await?;
    inspect_join(df1.logical_plan());
    
    println!("\n=== Test 2: Duplicate equijoin in ON ===");
    let df2 = ctx.sql("SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno AND emp.deptno = dept.deptno").await?;
    inspect_join(df2.logical_plan());
    
    println!("\n=== Test 3: After optimization ===");
    // The DataFrame has already been optimized by default
    let optimized = ctx.state().optimizer().optimize(
        df2.logical_plan().clone(),
        &ctx.state(),
        |_, _| {}
    )?;
    inspect_join(&optimized);
    
    Ok(())
}

fn inspect_join(plan: &LogicalPlan) {
    match plan {
        LogicalPlan::Projection(proj) => {
            if let LogicalPlan::Join(join) = proj.input.as_ref() {
                print_join(join);
            }
        }
        LogicalPlan::Join(join) => {
            print_join(join);
        }
        _ => {
            println!("  Not a join or projection: {:?}", plan);
        }
    }
}

fn print_join(join: &datafusion::logical_expr::Join) {
    println!("  join_constraint: {:?}", join.join_constraint);
    println!("  on.len() = {}", join.on.len());
    for (i, (left, right)) in join.on.iter().enumerate() {
        println!("    on[{}]: {:?} = {:?}", i, left, right);
    }
    if let Some(filter) = &join.filter {
        println!("  filter = Some({:?})", filter);
    } else {
        println!("  filter = None");
    }
}
