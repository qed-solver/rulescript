use std::sync::Arc;

use datafusion::{
    arrow::datatypes::DataType,
    common::Column,
    error::{DataFusionError, Result},
    logical_expr::{
        BinaryExpr, ColumnarValue, Expr, Operator, ScalarFunctionArgs, ScalarUDF, ScalarUDFImpl,
        Signature, Volatility, expr::ScalarFunction,
    },
    scalar::ScalarValue,
};

use crate::ast::opaque::Type;

/// Function that can take a configurable number of inputs
/// This integrates with DataFusion's scalar function system
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Function {
    pub name: String,
    pub input_types: Vec<Type>,
    pub return_type: Type,
    signature: Signature,
}

impl Function {
    pub fn new(name: String, input_types: Vec<Type>, return_type: Type) -> Self {
        // Create signature based on input types - all abstract types map to Binary
        let datafusion_types = vec![DataType::Binary; input_types.len()];
        let signature = Signature::exact(datafusion_types, Volatility::Immutable);

        Self {
            name,
            input_types,
            return_type,
            signature,
        }
    }

    /// Get the number of input arguments
    pub fn input_count(&self) -> usize {
        self.input_types.len()
    }

    /// Create a DataFusion Expr that calls this abstract function with given arguments
    pub fn call(&self, args: Vec<Expr>) -> Expr {
        if args.len() != self.input_types.len() {
            panic!(
                "Function '{}' expects {} arguments, got {}",
                self.name,
                self.input_types.len(),
                args.len()
            );
        }

        let udf = ScalarUDF::new_from_impl(self.clone());
        Expr::ScalarFunction(ScalarFunction {
            func: Arc::new(udf),
            args,
        })
    }

    /// Build an AND expression with another expression
    pub fn and(&self, args: Vec<Expr>, other: Expr) -> Expr {
        Expr::BinaryExpr(BinaryExpr {
            left: Box::new(self.call(args)),
            op: Operator::And,
            right: Box::new(other),
        })
    }

    /// Build an OR expression with another expression
    pub fn or(&self, args: Vec<Expr>, other: Expr) -> Expr {
        Expr::BinaryExpr(BinaryExpr {
            left: Box::new(self.call(args)),
            op: Operator::Or,
            right: Box::new(other),
        })
    }

    /// Create a function call expression with an alias
    /// Useful for projections where the output needs a specific name
    pub fn call_as(&self, args: Vec<Expr>, alias: impl Into<String>) -> Expr {
        self.call(args).alias(alias)
    }
}

impl ScalarUDFImpl for Function {
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn name(&self) -> &str {
        &self.name
    }

    fn signature(&self) -> &Signature {
        &self.signature
    }

    fn return_type(&self, _arg_types: &[DataType]) -> Result<DataType> {
        // Convert abstract return type to DataFusion type (handles Bool -> Boolean automatically)
        Ok((&self.return_type).into())
    }

    fn invoke_with_args(&self, _args: ScalarFunctionArgs) -> Result<ColumnarValue> {
        // Functions shouldn't be executed - they're for pattern matching
        Err(DataFusionError::NotImplemented(format!(
            "Function '{}' is for pattern matching, not execution",
            self.name
        )))
    }
}

/// Scalar pattern that wraps DataFusion's Expr
#[derive(Debug, Clone)]
pub struct Scalar {
    pub expr: Expr,
}

impl Scalar {
    /// Create a pattern for a function call with specific types
    pub fn function(
        name: String,
        input_types: Vec<Type>,
        return_type: Type,
        args: Vec<Expr>,
    ) -> Self {
        let func = Function::new(name, input_types, return_type);
        Self {
            expr: func.call(args),
        }
    }

    /// Create a pattern for a column reference
    pub fn column(name: String) -> Self {
        Self {
            expr: Expr::Column(Column::from_name(name)),
        }
    }

    /// Create a pattern for a literal value
    pub fn literal(value: ScalarValue) -> Self {
        Self {
            expr: Expr::Literal(value, None),
        }
    }

    /// Add an alias to this scalar expression
    /// Useful for naming expressions in projections
    pub fn alias(&self, name: impl Into<String>) -> Expr {
        self.expr.clone().alias(name)
    }
}

/// Declaratively define abstract functions for pattern matching
///
/// Creates local variable bindings for each function.
///
/// # Examples
/// ```
/// use rulescript::functions;
/// 
/// functions! {
///     P(T) -> Bool,           // Predicate on type T
///     f(T) -> U,              // Transform from T to U
///     g(U, V) -> W,           // Binary function
/// }
/// // After expansion, P, f, and g are available as variables
/// assert_eq!(P.name, "P");
/// assert_eq!(f.name, "f");
/// assert_eq!(g.name, "g");
/// ```
#[macro_export]
macro_rules! functions {
    // Empty case - no functions to define
    {} => {};

    // One or more functions with comma separation
    {
        $($name:ident($($arg_ty:ident),+ $(,)?) -> $ret_ty:tt),+ $(,)?
    } => {
        $(
            let $name = $crate::ast::scalar::Function::new(
                stringify!($name).to_string(),
                vec![$($crate::ast::opaque::Type::Generic {
                    id: stringify!($arg_ty).to_string(),
                }),+],
                $crate::__function_ret_type!($ret_ty),
            );
        )+
    };
}

/// Internal helper to handle Bool vs generic return types
#[doc(hidden)]
#[macro_export]
macro_rules! __function_ret_type {
    (Bool) => {
        $crate::ast::opaque::Type::Boolean
    };
    ($ty:ident) => {
        $crate::ast::opaque::Type::Generic {
            id: stringify!($ty).to_string(),
        }
    };
}

// ============================================================================
// Expression parsing helper macros
// ============================================================================

/// Internal helper to parse expression lists using incremental munching
/// Handles: f(x), f(x, y), g(f(x)), g(a, f(x), b), etc.
/// Also handles aliases: x as y, f(x) as result (for projections)
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_exprs {
    // Base case: empty
    (@accum [] []) => { Vec::<datafusion::prelude::Expr>::new() };
    
    // Done processing: return accumulated results
    (@accum [$($result:expr),*] []) => { vec![$($result),*] };
    
    // Munch: function call with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident($($args:tt)*) as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func($($args)*)).alias(stringify!($alias))] [$($($rest)*)?])
    };
    
    // Munch: identifier with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$id:ident as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* datafusion::prelude::col(stringify!($id)).alias(stringify!($alias))] [$($($rest)*)?])
    };
    
    // Munch: function call (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident($($args:tt)*) $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func($($args)*))] [$($($rest)*)?])
    };
    
    // Munch: identifier (with optional comma + rest)
    (@accum [$($result:expr),*] [$id:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* datafusion::prelude::col(stringify!($id))] [$($($rest)*)?])
    };
    
    // Entry point: start with empty accumulator
    ($($tt:tt)*) => {
        $crate::__parse_exprs!(@accum [] [$($tt)*])
    };
}

/// Internal: Parse expression (identifier or function call)
/// Truly recursive - handles any nesting depth
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_expr {
    // Function call: f(x), g(f(x)), etc.
    ($func:ident($($inside:tt)*)) => {
        $func.call($crate::__parse_exprs!($($inside)*))
    };
    
    // Plain identifier: x
    ($ident:ident) => {
        datafusion::prelude::col(stringify!($ident))
    };
}

/// Internal: Parse predicate (handles &&, ||, and nested calls)
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_predicate {
    // AND: P(...) && Q(...)
    ($p:ident($($arg1:tt)*) && $q:ident($($arg2:tt)*)) => {
        $p.and(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_expr!($q($($arg2)*))
        )
    };

    // OR: P(...) || Q(...)
    ($p:ident($($arg1:tt)*) || $q:ident($($arg2:tt)*)) => {
        $p.or(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_expr!($q($($arg2)*))
        )
    };

    // Simple function call: P(...)
    ($func:ident($($args:tt)*)) => {
        $func.call($crate::__parse_exprs!($($args)*))
    };
}
