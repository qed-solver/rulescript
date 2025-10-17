//! Interactive REPL for demonstrating query optimization rules

pub mod tables;

use datafusion::{
    optimizer::{Optimizer, OptimizerContext},
    prelude::*,
};
use reedline::{DefaultPrompt, DefaultPromptSegment, Reedline, Signal};
use rulescript::rule::{
    RuleWrapper,
    impls::{FilterMergeRule, FilterProjectTransposeRule, ProjectMergeRule, ProjectRemoveRule},
};
use std::sync::Arc;

pub struct RuleInfo {
    pub name: &'static str,
    pub description: &'static str,
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
            },
            RuleInfo {
                name: "project-merge",
                description: "Merge consecutive projections",
            },
            RuleInfo {
                name: "filter-merge",
                description: "Merge consecutive filters",
            },
            RuleInfo {
                name: "project-remove",
                description: "Remove identity projections",
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
            "  {}. all - Enable all rules\n",
            Self::available_rules().len() + 1
        );
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

    fn select_initial_rules(&mut self) {
        self.show_rule_menu();
        print!("Select rules (comma-separated numbers, e.g., 1,2,3 or 'all'): ");
        std::io::Write::flush(&mut std::io::stdout()).unwrap();

        let mut input = String::new();
        if std::io::stdin().read_line(&mut input).is_ok() {
            self.active_rules = Self::parse_rule_selection(&input);
            if !self.active_rules.is_empty() {
                println!("✓ Active rules: {}\n", self.active_rules.join(", "));
            } else {
                println!("⚠️  No rules selected. You can select rules later with 'set' command.\n");
            }
        }
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
                "project-remove" => {
                    rules.push(Arc::new(RuleWrapper::new(ProjectRemoveRule)));
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
        println!("  • 'set <numbers>' - Activate rules (e.g., 'set 1,2,3' or 'set all')");
        println!("  • 'clear' - Deactivate all rules");
        println!("  • 'verbose' - Toggle verbose mode");
        println!("  • 'help' - Show this message");
        println!("  • 'quit' or 'exit' or Ctrl+D - Exit");
        println!();

        if self.verbose {
            println!("🔍 Verbose mode: ON");
        } else {
            println!("ℹ️  Verbose mode: OFF (use 'verbose' to enable)");
        }
        println!();
    }

    pub async fn run(&mut self) {
        self.show_welcome();
        self.select_initial_rules();
        self.show_commands();

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
                        "rules" => {
                            self.show_rule_menu();
                            if self.active_rules.is_empty() {
                                println!("⚠️  No rules active. Use 'set' to activate rules.\n");
                            } else {
                                println!("Active rules: {}\n", self.active_rules.join(", "));
                            }
                            continue;
                        }
                        "clear" => {
                            self.active_rules.clear();
                            println!("✓ Cleared all rules\n");
                            continue;
                        }
                        "verbose" => {
                            self.verbose = !self.verbose;
                            if self.verbose {
                                println!("🔍 Verbose mode: ON\n");
                            } else {
                                println!("ℹ️  Verbose mode: OFF\n");
                            }
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
                                println!("✓ Active rules: {}\n", self.active_rules.join(", "));
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
