use rulescript::{
    rule::impls::*,
    verifier::{qed::QedSerializer, qed::QedError, Verifier},
};
use std::fs;
use std::path::Path;

type SerializeFn = Box<dyn Fn(&mut QedSerializer) -> Result<String, QedError>>;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Create output directory
    let output_dir = Path::new("qed-json");
    fs::create_dir_all(output_dir)?;
    println!("Exporting rules to {}/\n", output_dir.display());

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
    for (name, serialize_fn) in rules {
        let mut serializer = QedSerializer::new();
        let json = serialize_fn(&mut serializer)?;

        let filename = output_dir.join(format!("{}.json", name));
        fs::write(&filename, json)?;
        println!("✓ {}", filename.display());
    }

    println!("\nSuccessfully exported {} rules", 13);
    Ok(())
}
