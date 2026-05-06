pub mod qed;

use crate::rule::RewriteRule;

/// Trait for rule verification backends
pub trait Verifier {
    type Error;

    /// Serialize a rule to the verifier's format
    fn serialize_rule<R: RewriteRule>(&mut self, rule: &R) -> Result<String, Self::Error>;
}
