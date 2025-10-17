// Rule implementations
pub mod filter_merge;
pub mod project_merge;
pub mod project_remove;

// Re-export all rules
pub use filter_merge::FilterMergeRule;
pub use project_merge::ProjectMergeRule;
pub use project_remove::ProjectRemoveRule;
