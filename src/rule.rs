use crate::Rel;

/// A rewrite rule that transforms one relational pattern to another
pub trait RewriteRule {
    /// The pattern to match (left-hand side)
    fn pattern(&self) -> Rel;
    
    /// The replacement pattern (right-hand side)
    fn replacement(&self) -> Rel;
    
    /// Optional rule name for debugging
    fn name(&self) -> &str {
        std::any::type_name::<Self>()
    }
}