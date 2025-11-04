//! Interactive REPL for demonstrating query optimization rules

pub mod tables;
pub mod wrappers;

use datafusion::{
    optimizer::{Optimizer, OptimizerContext},
    prelude::*,
};
use reedline::{DefaultPrompt, DefaultPromptSegment, Reedline, Signal};
use rulescript::rule::{
    RuleWrapper,
    impls::{
        FilterAggregateTransposeRule, FilterIntoJoinRule, FilterMergeRule,
        FilterProjectTransposeRule, JoinAssociateRule, JoinLeftConditionPushRule,
        JoinLeftProjectTransposeRule, JoinRightConditionPushRule, JoinRightProjectTransposeRule,
        LeftSemiJoinFilterTransposeRule, ProjectMergeRule, ProjectRemoveRule,
    },
};
use std::sync::Arc;
use wrappers::{BiasedJoinCommuteRule, BiasedJoinExtractFilterRule};

pub struct RuleInfo {
    pub name: &'static str,
    pub description: &'static str,
    pub explanation: &'static str,
    pub example_query: &'static str,
}

pub struct OptimizerRepl {
    ctx: SessionContext,
    tables: Vec<tables::TableInfo>,
    verbose: bool,
    active_rules: Vec<String>,
}

impl OptimizerRepl {
    pub async fn new() -> Self {
        let ctx = SessionContext::new();
        let tables = tables::register_tables(&ctx).await;

        // Check for --verbose or -v flag
        let verbose = std::env::args().any(|arg| arg == "--verbose" || arg == "-v");

        Self {
            ctx,
            tables,
            verbose,
            active_rules: Vec::new(),
        }
    }

    fn available_rules() -> Vec<RuleInfo> {
        vec![
            RuleInfo {
                name: "filter-project-transpose",
                description: "Push filter below projection",
                explanation: "Pushes a filter condition below a projection by rewriting the filter \
                             to use the projection's input columns. This enables earlier filtering \
                             of data before computing expensive expressions.\n\
                             Pattern: Filter(P(y), Project(f(x), source)) → Project(f(x), Filter(P(f(x)), source))",
                example_query: "SELECT * FROM (SELECT salary * 1.1 AS raised, deptno FROM emp) WHERE raised > 55000",
            },
            RuleInfo {
                name: "project-merge",
                description: "Merge consecutive projections",
                explanation: "Merges two consecutive projections into a single projection using \
                             function composition. The outer projection's expressions are composed \
                             with the inner projection's expressions.\n\
                             Pattern: Project(g, Project(f, source)) → Project(g∘f, source)",
                example_query: "SELECT doubled FROM (SELECT increased * 2 AS doubled FROM (SELECT salary + 1000 AS increased FROM emp))",
            },
            RuleInfo {
                name: "filter-merge",
                description: "Merge consecutive filters",
                explanation: "Combines two consecutive filter operations into a single filter with \
                             an AND condition. This reduces the number of operators in the plan.\n\
                             Pattern: Filter(P, Filter(Q, source)) → Filter(P AND Q, source)\n\n\
                             💡 TIP: Combine with project-remove to see both rules in action.",
                example_query: "SELECT empno, salary FROM (SELECT empno, salary FROM emp WHERE deptno = 10) WHERE salary > 50000",
            },
            RuleInfo {
                name: "filter-aggregate-transpose",
                description: "Push filter below aggregate",
                explanation: "Pushes filter predicates below an aggregate when they only reference \
                             GROUP BY columns. Predicates on aggregate results stay above. This enables \
                             earlier filtering before aggregation.\n\
                             Pattern: Filter(GroupCond(g) ∧ AggCond(g, a), Aggregate(G, A, source)) → \
                             Filter(AggCond(g, a), Aggregate(G, A, Filter(GroupCond(g), source)))\n\n\
                             💡 TIP: Most effective with HAVING clauses that mix GROUP BY and aggregate predicates.\n\
                             💡 NOTE: Cannot push if aggregate has no GROUP BY (would change semantics).",
                example_query: "SELECT deptno, SUM(salary) FROM emp GROUP BY deptno HAVING deptno = 10 AND SUM(salary) > 50000",
            },
            RuleInfo {
                name: "project-remove",
                description: "Remove identity projections",
                explanation: "Removes a projection that simply selects all columns in their original \
                             order without any transformations. This is a no-op that can be eliminated.\n\
                             Pattern: Project([col1, col2, ...], source) → source (if identity)",
                example_query: "SELECT empno, ename, job, mgr, hiredate, salary, commission, deptno FROM emp",
            },
            RuleInfo {
                name: "join-commute",
                description: "Swap join inputs",
                explanation: "Permutes the inputs to a join, swapping left and right sides. Adds a \
                             projection to preserve the original column order. This enables other \
                             optimizations and can improve join performance.\n\
                             Pattern: Join(Inner, P(l, r), left, right) → Project([l, r], Join(Inner, P(r, l), right, left))\n\n\
                             💡 TIP: Must combine with project-remove as SQL queries have a projection on top.\n\
                             💡 NOTE: Uses lexicographic bias to prevent infinite loops - only swaps when left > right.",
                example_query: "SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno",
            },
            RuleInfo {
                name: "filter-into-join",
                description: "Merge filter into join condition",
                explanation: "Merges a filter above an inner join into the join condition by ANDing \
                             the predicates together. This consolidates conditions and can enable \
                             better join execution strategies.\n\
                             Pattern: Filter(pred, Join(Inner, cond, L, R)) → Join(Inner, cond AND pred, L, R)\n\n\
                             💡 TIP: Works best on queries with WHERE clauses after joins.",
                example_query: "SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno WHERE emp.salary > 50000",
            },
            RuleInfo {
                name: "join-left-condition-push",
                description: "Push left predicates to left input",
                explanation: "Pushes left-table predicates from join condition down as filter on left input. \
                             More flexible than join-condition-push as it doesn't require right predicates.\n\
                             Pattern: Join(Inner, LeftCond(l) ∧ JoinCond(l, r), L, R) → \
                             Join(Inner, JoinCond(l, r), Filter(LeftCond(l), L), R)\n\n\
                             💡 TIP: Use when you only have left-side predicates to push.",
                example_query: "SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno AND emp.salary > 50000",
            },
            RuleInfo {
                name: "join-right-condition-push",
                description: "Push right predicates to right input",
                explanation: "Pushes right-table predicates from join condition down as filter on right input. \
                             More flexible than join-condition-push as it doesn't require left predicates.\n\
                             Pattern: Join(Inner, RightCond(r) ∧ JoinCond(l, r), L, R) → \
                             Join(Inner, JoinCond(l, r), L, Filter(RightCond(r), R))\n\n\
                             💡 TIP: Use when you only have right-side predicates to push.",
                example_query: "SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno AND dept.deptno > 10",
            },
            RuleInfo {
                name: "join-extract-filter",
                description: "Extract join condition to filter",
                explanation: "Extracts the join condition as a filter above a cartesian join. This is \
                             the inverse of filter-into-join.\n\
                              Pattern: Join(Inner, cond, L, R) → Filter(cond, Join(Inner, TRUE, L, R))\n\n\
                             💡 TIP: Must combine with project-remove as SQL queries have a projection on top.\n\
                             💡 NOTE: Does not apply if condition is already TRUE (prevents infinite loops).",
                example_query: "SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno",
            },
            RuleInfo {
                name: "join-left-project-transpose",
                description: "Pull projection from left join input",
                explanation: "Pulls a projection from the left input of an inner join up above the join. \
                             Rewrites join condition to reference original left columns.\n\
                              Pattern: Join(Inner, P(l', r), Project(f(l), left), right) → \
                             Project(f(l), r, Join(Inner, P(f(l), r), left, right))\n\n\
                             💡 TIP: Must combine with project-remove as SQL queries have a projection on top.\n\
                             💡 NOTE: Only applies to INNER joins on left input.",
                example_query: "SELECT * FROM (SELECT ename, deptno FROM emp) a JOIN dept b ON a.deptno = b.deptno",
            },
            RuleInfo {
                name: "join-right-project-transpose",
                description: "Pull projection from right join input",
                explanation: "Pulls a projection from the right input of an inner join up above the join. \
                             Rewrites join condition to reference original right columns.\n\
                              Pattern: Join(Inner, P(l, r'), left, Project(f(r), right)) → \
                             Project(l, f(r), Join(Inner, P(l, f(r)), left, right))\n\n\
                             💡 TIP: Must combine with project-remove as SQL queries have a projection on top.\n\
                             💡 NOTE: Only applies to INNER joins on right input.",
                example_query: "SELECT * FROM emp a JOIN (SELECT deptno, dname FROM dept) b ON a.deptno = b.deptno",
            },
            RuleInfo {
                name: "join-associate",
                description: "Restructure nested joins",
                explanation: "Changes join tree shape using associativity. Restructures nested joins \
                             while preserving semantics.\n\
                             Pattern: (Q0 ⋈[P0(x,y)] Q1) ⋈[P1(y,z)] Q2 → Q0 ⋈[P0(x,y)] (Q1 ⋈[P1(y,z)] Q2)\n\n\
                              💡 NOTE: Only applies to INNER joins. Q1 is the 'pivot' table in both joins.\n\
                             💡 NOTE: This demo only has 'emp' and 'dept' tables, so cannot demonstrate this rule.",
                example_query: "SELECT * FROM (emp JOIN dept ON emp.deptno = dept.deptno) JOIN dept AS dept2 ON dept.deptno = dept2.deptno",
            },
            RuleInfo {
                name: "left-semi-join-filter-transpose",
                description: "Pull filter above left semi-join",
                explanation: "Pulls a filter from the left input of a LeftSemi join up above the join. \
                             This exposes the semi-join to other optimization rules.\n\
                             Pattern: LeftSemi(Filter(X), Y) → Filter(LeftSemi(X, Y))\n\n\
                             💡 TIP: Must combine with project-remove as SQL queries have a projection on top.\n\
                             💡 NOTE: LeftSemi joins are used for IN/EXISTS subqueries.\n\
                             💡 NOTE: The filter only references columns from the left side, which is preserved in the result.",
                example_query: "SELECT * FROM emp WHERE deptno IN (SELECT deptno FROM dept) AND salary > 50000",
            },
        ]
    }

    fn show_welcome(&self) {
        println!("╔══════════════════════════════════════════════════════════════════════════╗");
        println!("║              RuleScript Interactive Optimizer                            ║");
        println!("╚══════════════════════════════════════════════════════════════════════════╝\n");

        println!("📊 Available Tables:");
        for table in &self.tables {
            println!("{}", table.display());
        }
        println!();
    }

    fn show_rule_menu(&self) {
        println!("Available Rules:");
        for (i, rule) in Self::available_rules().iter().enumerate() {
            let active = if self.active_rules.contains(&rule.name.to_string()) {
                "✓"
            } else {
                " "
            };
            println!(
                "  {} {}. {} - {}",
                active,
                i + 1,
                rule.name,
                rule.description
            );
        }
        println!(
            "    {}. all - Enable all rules",
            Self::available_rules().len() + 1
        );
        println!();
    }

    fn show_rule_details(&self, rule_num: usize) {
        let rules = Self::available_rules();
        if let Some(rule) = rules.get(rule_num.wrapping_sub(1)) {
            println!(
                "\n╔══════════════════════════════════════════════════════════════════════════╗"
            );
            println!("║ Rule {}: {}", rule_num, rule.name);
            println!(
                "╚══════════════════════════════════════════════════════════════════════════╝\n"
            );

            println!("📝 Description:");
            println!("   {}\n", rule.description);

            println!("🔍 How it works:");
            for line in rule.explanation.lines() {
                println!("   {}", line);
            }
            println!();

            println!("💡 Example query:");
            // Show SQL query in a code-like block with proper indentation
            let query_lines: Vec<&str> = rule
                .example_query
                .lines()
                .filter(|line| !line.trim().is_empty())
                .collect();

            for line in query_lines {
                if line.trim().starts_with("--") {
                    // Comments in gray/dim style
                    println!("   {}", line.trim());
                } else {
                    // SQL with consistent indentation
                    println!("   {}", line.trim());
                }
            }
            println!();
            println!(
                "💻 Tip: Use 'try {}' to run this example (rule must be enabled with 'set {}')",
                rule_num, rule_num
            );
            println!();
        } else {
            println!("❌ Invalid rule number. Use 'rules' to see available rules.\n");
        }
    }

    fn get_example_query(&self, rule_num: usize) -> Option<String> {
        let rules = Self::available_rules();
        rules.get(rule_num.wrapping_sub(1)).map(|rule| {
            // Extract SQL from example_query (skip comment lines and trim)
            let lines: Vec<&str> = rule
                .example_query
                .lines()
                .filter(|line| !line.trim().starts_with("--"))
                .map(|line| line.trim())
                .filter(|line| !line.is_empty())
                .collect();

            // Join with space for single-line display
            lines.join(" ")
        })
    }

    fn parse_rule_selection(input: &str) -> Vec<String> {
        let available = Self::available_rules();

        if input.trim() == "all" {
            return available.iter().map(|r| r.name.to_string()).collect();
        }

        input
            .split(',')
            .filter_map(|s| {
                s.trim()
                    .parse::<usize>()
                    .ok()
                    .and_then(|n| available.get(n.wrapping_sub(1)))
                    .map(|r| r.name.to_string())
            })
            .collect()
    }

    fn show_config(&self) {
        println!("📊 Current Configuration:");
        if self.active_rules.is_empty() {
            println!("  • Rules: None");
        } else {
            println!("  • Rules: {}", self.active_rules.join(", "));
        }
        println!("  • Verbose: {}", if self.verbose { "ON" } else { "OFF" });
        println!();
    }

    fn get_optimizer(&self) -> Optimizer {
        let mut rules: Vec<Arc<dyn datafusion::optimizer::OptimizerRule + Send + Sync>> = vec![];

        for rule_name in &self.active_rules {
            match rule_name.as_str() {
                "filter-project-transpose" => {
                    rules.push(Arc::new(RuleWrapper::new(FilterProjectTransposeRule)));
                }
                "project-merge" => {
                    rules.push(Arc::new(RuleWrapper::new(ProjectMergeRule)));
                }
                "filter-merge" => {
                    rules.push(Arc::new(RuleWrapper::new(FilterMergeRule)));
                }
                "filter-aggregate-transpose" => {
                    rules.push(Arc::new(RuleWrapper::new(FilterAggregateTransposeRule)));
                }
                "project-remove" => {
                    rules.push(Arc::new(RuleWrapper::new(ProjectRemoveRule)));
                }
                "join-commute" => {
                    rules.push(Arc::new(BiasedJoinCommuteRule::new()));
                }
                "filter-into-join" => {
                    rules.push(Arc::new(RuleWrapper::new(FilterIntoJoinRule)));
                }

                "join-left-condition-push" => {
                    rules.push(Arc::new(RuleWrapper::new(JoinLeftConditionPushRule)));
                }
                "join-right-condition-push" => {
                    rules.push(Arc::new(RuleWrapper::new(JoinRightConditionPushRule)));
                }
                "join-extract-filter" => {
                    rules.push(Arc::new(BiasedJoinExtractFilterRule::new()));
                }
                "join-left-project-transpose" => {
                    rules.push(Arc::new(RuleWrapper::new(JoinLeftProjectTransposeRule)));
                }
                "join-right-project-transpose" => {
                    rules.push(Arc::new(RuleWrapper::new(JoinRightProjectTransposeRule)));
                }
                "join-associate" => {
                    rules.push(Arc::new(RuleWrapper::new(JoinAssociateRule)));
                }
                "left-semi-join-filter-transpose" => {
                    rules.push(Arc::new(RuleWrapper::new(LeftSemiJoinFilterTransposeRule)));
                }
                _ => {}
            }
        }

        Optimizer::with_rules(rules)
    }

    fn show_commands(&self) {
        println!("Commands:");
        println!("  • Type SQL query to see optimization");
        println!("  • 'rules' - Show active and available rules");
        println!("  • 'help <number>' - Show detailed explanation for a rule (e.g., 'help 1')");
        println!("  • 'try <number>' - Run the example query for a rule (e.g., 'try 1')");
        println!("  • 'set <numbers>' - Activate rules (e.g., 'set 1,2,3' or 'set all')");
        println!("  • 'clear' - Deactivate all rules");
        println!("  • 'verbose' - Toggle verbose mode");
        println!("  • 'help' - Show this message");
        println!("  • 'quit' or 'exit' or Ctrl+D - Exit");
        println!();
    }

    pub async fn run(&mut self) {
        self.show_welcome();
        self.show_rule_menu();
        self.show_commands();
        self.show_config();

        let mut line_editor = Reedline::create();
        let prompt = DefaultPrompt::new(
            DefaultPromptSegment::Basic("sql".to_string()),
            DefaultPromptSegment::Empty,
        );

        loop {
            let sig = line_editor.read_line(&prompt);

            match sig {
                Ok(Signal::Success(input)) => {
                    let input = input.trim();

                    if input.is_empty() {
                        continue;
                    }

                    match input {
                        "quit" | "exit" => {
                            println!("Goodbye!");
                            break;
                        }
                        "help" => {
                            self.show_commands();
                            continue;
                        }
                        cmd if cmd.starts_with("help ") => {
                            let rule_num_str = cmd.strip_prefix("help ").unwrap().trim();
                            if let Ok(rule_num) = rule_num_str.parse::<usize>() {
                                self.show_rule_details(rule_num);
                            } else {
                                println!(
                                    "❌ Invalid rule number. Use 'rules' to see available rules.\n"
                                );
                            }
                            continue;
                        }
                        cmd if cmd.starts_with("try ") => {
                            let rule_num_str = cmd.strip_prefix("try ").unwrap().trim();
                            if let Ok(rule_num) = rule_num_str.parse::<usize>() {
                                if let Some(query) = self.get_example_query(rule_num) {
                                    let rules = Self::available_rules();
                                    if let Some(rule) = rules.get(rule_num.wrapping_sub(1))
                                        && !self.active_rules.contains(&rule.name.to_string())
                                    {
                                        println!(
                                            "\n⚠️  Rule '{}' is not enabled. Use 'set {}' to enable it first.\n",
                                            rule.name, rule_num
                                        );
                                        continue;
                                    }
                                    println!("\n🚀 Running example for rule {}:\n", rule_num);
                                    println!("{}\n", query);
                                    self.process_query(&query).await;
                                } else {
                                    println!(
                                        "❌ Invalid rule number. Use 'rules' to see available rules.\n"
                                    );
                                }
                            } else {
                                println!(
                                    "❌ Invalid rule number. Use 'rules' to see available rules.\n"
                                );
                            }
                            continue;
                        }
                        "rules" => {
                            self.show_rule_menu();
                            continue;
                        }
                        "clear" => {
                            self.active_rules.clear();
                            self.show_config();
                            continue;
                        }
                        "verbose" => {
                            self.verbose = !self.verbose;
                            self.show_config();
                            continue;
                        }
                        cmd if cmd.starts_with("set ") => {
                            let selection = cmd.strip_prefix("set ").unwrap();
                            let new_rules = Self::parse_rule_selection(selection);
                            if new_rules.is_empty() {
                                println!(
                                    "❌ Invalid selection. Use numbers (e.g., 'set 1,2') or 'set all'\n"
                                );
                            } else {
                                self.active_rules = new_rules;
                                self.show_config();
                            }
                            continue;
                        }
                        _ => {
                            self.process_query(input).await;
                        }
                    }
                }
                Ok(Signal::CtrlC) => {
                    continue;
                }
                Ok(Signal::CtrlD) => {
                    println!("Goodbye!");
                    break;
                }
                Err(err) => {
                    println!("Error: {:?}", err);
                    break;
                }
            }
        }
    }

    async fn process_query(&self, sql: &str) {
        // Parse the SQL
        let df = match self.ctx.sql(sql).await {
            Ok(df) => df,
            Err(e) => {
                println!("❌ SQL Parse Error: {}\n", e);
                return;
            }
        };

        let logical_plan = df.logical_plan().clone();

        println!("\n🔍 Logical Plan (BEFORE optimization):");
        println!("{}", logical_plan.display_indent());
        println!();

        if self.active_rules.is_empty() {
            println!("⚠️  No rules active. Use 'set' command to activate rules.\n");
            return;
        }

        // Apply optimization
        let optimizer = self.get_optimizer();
        let config = OptimizerContext::new();

        match optimizer.optimize(logical_plan.clone(), &config, |_, _| {}) {
            Ok(optimized) => {
                if optimized == logical_plan {
                    println!("ℹ️  No changes - rules did not match this query pattern\n");
                } else {
                    println!("✨ Logical Plan (AFTER optimization):");
                    println!("{}", optimized.display_indent());
                    println!();
                    println!("✅ Query plan optimized!\n");
                }
            }
            Err(e) => {
                if self.verbose {
                    println!("❌ Optimization error: {:?}\n", e);
                } else {
                    println!("❌ Optimization error (use 'verbose' for details)\n");
                }
            }
        }
    }
}
