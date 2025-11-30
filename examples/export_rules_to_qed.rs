// Export RuleScript rules to QED JSON format for verification
//
// QED (Query Equivalence Decider) is a tool that verifies SQL query equivalence.
// This example exports all implemented rules to JSON format that QED can verify.
//
// Usage:
//   cargo run --example export_rules_to_qed
//
// For more information on QED:
//   - Project: https://github.com/qed-solver
//   - Paper: https://www.vldb.org/pvldb/vol17/p3602-wang.pdf

use std::{fs, path::Path};

use rulescript::{
    rule::impls::*,
    verifier::{Verifier, qed::QedError, qed::QedSerializer},
};

type SerializeFn = Box<dyn Fn(&mut QedSerializer) -> Result<String, QedError>>;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("=== RuleScript QED Export Tool ===\n");
    println!("This tool exports rewrite rules to QED JSON format for verification.");
    println!("QED (Query Equivalence Decider) verifies that query transformations");
    println!("preserve semantics across different database schemas.\n");

    // Create output directory
    let output_dir = Path::new("qed-json");
    fs::create_dir_all(output_dir)?;
    println!("Output directory: {}/\n", output_dir.display());

    // List of all rules to serialize
    let rules: Vec<(&str, SerializeFn)> = vec![
        (
            "FilterMergeRule",
            Box::new(|s| s.serialize_rule(&FilterMergeRule)),
        ),
        (
            "FilterProjectTransposeRule",
            Box::new(|s| s.serialize_rule(&FilterProjectTransposeRule)),
        ),
        (
            "FilterAggregateTransposeRule",
            Box::new(|s| s.serialize_rule(&FilterAggregateTransposeRule)),
        ),
        (
            "FilterIntoJoinRule",
            Box::new(|s| s.serialize_rule(&FilterIntoJoinRule)),
        ),
        (
            "ProjectMergeRule",
            Box::new(|s| s.serialize_rule(&ProjectMergeRule)),
        ),
        (
            "ProjectRemoveRule",
            Box::new(|s| s.serialize_rule(&ProjectRemoveRule)),
        ),
        (
            "JoinCommuteRule",
            Box::new(|s| s.serialize_rule(&JoinCommuteRule)),
        ),
        (
            "JoinExtractFilterRule",
            Box::new(|s| s.serialize_rule(&JoinExtractFilterRule)),
        ),
        (
            "JoinLeftConditionPushRule",
            Box::new(|s| s.serialize_rule(&JoinLeftConditionPushRule)),
        ),
        (
            "JoinRightConditionPushRule",
            Box::new(|s| s.serialize_rule(&JoinRightConditionPushRule)),
        ),
        (
            "JoinLeftProjectTransposeRule",
            Box::new(|s| s.serialize_rule(&JoinLeftProjectTransposeRule)),
        ),
        (
            "JoinRightProjectTransposeRule",
            Box::new(|s| s.serialize_rule(&JoinRightProjectTransposeRule)),
        ),
        (
            "JoinAssociateRule",
            Box::new(|s| s.serialize_rule(&JoinAssociateRule)),
        ),
    ];

    // Serialize each rule
    println!("Exporting rules:");
    for (name, serialize_fn) in &rules {
        let mut serializer = QedSerializer::new();
        let json = serialize_fn(&mut serializer)?;

        let filename = output_dir.join(format!("{}.json", name));
        fs::write(&filename, json)?;

        // Show file size for context
        let metadata = fs::metadata(&filename)?;
        println!("  ✓ {} ({} bytes)", filename.display(), metadata.len());
    }

    println!("\n=== Export Complete ===");
    println!(
        "Successfully exported {} rules to QED JSON format",
        rules.len()
    );

    println!("\n=== Next Steps ===");
    println!(
        "1. Review generated JSON files in the {} directory",
        output_dir.display()
    );
    println!("   - Each file contains a 'from' and 'to' query pair");
    println!("   - Schemas are defined at the top of each file");
    println!();
    println!("2. Verify rules using QED:");
    println!("   - Project: https://github.com/qed-solver");
    println!("   - Install with Nix:");
    println!("     nix shell github:qed-solver/parser github:qed-solver/prover");
    println!("   - Run prover:");
    println!("     qed-prover {}/YourRule.json", output_dir.display());
    println!();
    println!("3. Understand QED output:");
    println!("   - Provable: Transformation preserves semantics for all database states");
    println!("   - Not provable: QED couldn't prove equivalence (may still be correct)");
    println!("   - Output includes proof time in the .res file");
    println!();
    println!("4. Iterate on rules:");
    println!("   - If QED can't prove equivalence, review the rule in src/rule/impls/");
    println!("   - Check if the transformation is actually correct");
    println!("   - Re-export and verify again");

    Ok(())
}
