use std::sync::Arc;

use datafusion::{
    arrow::datatypes::DataType,
    common::Column,
    error::{DataFusionError, Result},
    logical_expr::{
        Accumulator, AggregateUDF, AggregateUDFImpl, BinaryExpr, ColumnarValue, Expr, Operator,
        ScalarFunctionArgs, ScalarUDF, ScalarUDFImpl, Signature, Volatility, expr::ScalarFunction,
        function::AccumulatorArgs,
    },
    scalar::ScalarValue,
};

use crate::ast::opaque::Type;

/// Pattern for matching scalar functions with uninterpreted symbols
/// This integrates with DataFusion's scalar function system for pattern matching
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ScalarPattern {
    pub name: String,
    pub input_types: Vec<Type>,
    pub return_type: Type,
    signature: Signature,
}

impl ScalarPattern {
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

    /// Create a DataFusion Expr that calls this pattern with given arguments
    pub fn call(&self, args: Vec<Expr>) -> Expr {
        if args.len() != self.input_types.len() {
            panic!(
                "ScalarPattern '{}' expects {} arguments, got {}",
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

impl ScalarUDFImpl for ScalarPattern {
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

/// Pattern for matching aggregate functions with uninterpreted symbols
/// Like ScalarPattern, this is for pattern matching only - not concrete execution
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct AggregatePattern {
    pub name: String,
    pub input_types: Vec<Type>,
    pub return_type: Type,
    signature: Signature,
}

impl AggregatePattern {
    pub fn new(name: String, input_types: Vec<Type>, return_type: Type) -> Self {
        // All abstract types map to Binary for uniformity
        let datafusion_types = vec![DataType::Binary; input_types.len()];
        let signature = Signature::exact(datafusion_types, Volatility::Immutable);

        Self {
            name,
            input_types,
            return_type,
            signature,
        }
    }

    /// Create a DataFusion Expr that calls this pattern with given arguments
    pub fn call(&self, args: Vec<Expr>) -> Expr {
        if args.len() != self.input_types.len() {
            panic!(
                "AggregatePattern '{}' expects {} arguments, got {}",
                self.name,
                self.input_types.len(),
                args.len()
            );
        }

        let udf = AggregateUDF::new_from_impl(self.clone());
        udf.call(args)
    }
}

impl AggregateUDFImpl for AggregatePattern {
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
        Ok((&self.return_type).into())
    }

    fn accumulator(&self, _acc_args: AccumulatorArgs) -> Result<Box<dyn Accumulator>> {
        Err(DataFusionError::NotImplemented(format!(
            "AggregatePattern '{}' is for pattern matching, not execution",
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
        let func = ScalarPattern::new(name, input_types, return_type);
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
/// Supports both regular functions (parentheses) and aggregate functions (braces).
///
/// # Examples
/// ```
/// use rulescript::functions;
///
/// functions! {
///     P(T) -> Bool,           // Predicate on type T
///     f(T) -> U,              // Transform from T to U
///     SUM{T} -> U,            // Aggregate function
///     COUNT{} -> Int,         // Aggregate with no args
/// }
/// ```
#[macro_export]
macro_rules! functions {
    // Base case: empty
    (@accum [$($done:tt)*] []) => {
        $($done)*
    };

    // Empty functions block
    {} => {};

    // Regular function with args and comma: Name(Args) -> RetType,
    (@accum [$($done:tt)*] [
        $name:ident($($arg_ty:ident),+ $(,)?) -> $ret_ty:tt
        , $($rest:tt)*
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::ScalarPattern::new(
                    stringify!($name).to_string(),
                    vec![$($crate::ast::opaque::Type::Generic {
                        id: stringify!($arg_ty).to_string(),
                    }),+],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            [$($rest)*]
        );
    };

    // Regular function with args, last item: Name(Args) -> RetType
    (@accum [$($done:tt)*] [
        $name:ident($($arg_ty:ident),+ $(,)?) -> $ret_ty:tt
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::ScalarPattern::new(
                    stringify!($name).to_string(),
                    vec![$($crate::ast::opaque::Type::Generic {
                        id: stringify!($arg_ty).to_string(),
                    }),+],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            []
        );
    };

    // Regular function no args with comma: Name() -> RetType,
    (@accum [$($done:tt)*] [
        $name:ident() -> $ret_ty:tt
        , $($rest:tt)*
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::ScalarPattern::new(
                    stringify!($name).to_string(),
                    vec![],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            [$($rest)*]
        );
    };

    // Regular function no args, last item: Name() -> RetType
    (@accum [$($done:tt)*] [
        $name:ident() -> $ret_ty:tt
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::ScalarPattern::new(
                    stringify!($name).to_string(),
                    vec![],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            []
        );
    };

    // Aggregate function with args and comma: Name{Args} -> RetType,
    (@accum [$($done:tt)*] [
        $name:ident{$($arg_ty:ident),+ $(,)?} -> $ret_ty:tt
        , $($rest:tt)*
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::AggregatePattern::new(
                    stringify!($name).to_string(),
                    vec![$($crate::ast::opaque::Type::Generic {
                        id: stringify!($arg_ty).to_string(),
                    }),+],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            [$($rest)*]
        );
    };

    // Aggregate function with args, last item: Name{Args} -> RetType
    (@accum [$($done:tt)*] [
        $name:ident{$($arg_ty:ident),+ $(,)?} -> $ret_ty:tt
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::AggregatePattern::new(
                    stringify!($name).to_string(),
                    vec![$($crate::ast::opaque::Type::Generic {
                        id: stringify!($arg_ty).to_string(),
                    }),+],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            []
        );
    };

    // Aggregate function no args with comma: Name{} -> RetType,
    (@accum [$($done:tt)*] [
        $name:ident{} -> $ret_ty:tt
        , $($rest:tt)*
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::AggregatePattern::new(
                    stringify!($name).to_string(),
                    vec![],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            [$($rest)*]
        );
    };

    // Aggregate function no args, last item: Name{} -> RetType
    (@accum [$($done:tt)*] [
        $name:ident{} -> $ret_ty:tt
    ]) => {
        $crate::functions!(
            @accum
            [
                $($done)*
                let $name = $crate::ast::pattern::AggregatePattern::new(
                    stringify!($name).to_string(),
                    vec![],
                    $crate::__function_ret_type!($ret_ty),
                );
            ]
            []
        );
    };

    // Entry point: start with empty accumulator
    {
        $($func:tt)*
    } => {
        $crate::functions!(@accum [] [$($func)*]);
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

    // Munch: aggregate function call with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident{$($args:tt)*} as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func{$($args)*}).alias(stringify!($alias))] [$($($rest)*)?])
    };

    // Munch: function call with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident($($args:tt)*) as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func($($args)*)).alias(stringify!($alias))] [$($($rest)*)?])
    };

    // Munch: identifier with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$id:ident as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* datafusion::prelude::col(stringify!($id)).alias(stringify!($alias))] [$($($rest)*)?])
    };

    // Munch: aggregate function call (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident{$($args:tt)*} $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func{$($args)*})] [$($($rest)*)?])
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
    // Aggregate function call: SUM{x}, AVG{salary}, etc.
    ($func:ident{$($inside:tt)*}) => {
        $func.call($crate::__parse_exprs!($($inside)*))
    };

    // Function call: f(x), g(f(x)), etc.
    ($func:ident($($inside:tt)*)) => {
        $func.call($crate::__parse_exprs!($($inside)*))
    };

    // Plain identifier: x
    ($ident:ident) => {
        datafusion::prelude::col(stringify!($ident))
    };
}

/// Internal: Parse predicate (handles boolean literals, &&, ||, nested calls)
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_predicate {
    // Boolean literal: true or false
    (true) => {
        datafusion::prelude::lit(true)
    };
    (false) => {
        datafusion::prelude::lit(false)
    };

    // AND (chained): P(...) && <rest>
    // Recursively parse the right-hand side to support chains like P(...) && Q(...) && R(...)
    ($p:ident($($arg1:tt)*) && $($rest:tt)+) => {
        $p.and(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_predicate!($($rest)+)
        )
    };

    // OR (chained): P(...) || <rest>
    // Recursively parse the right-hand side to support chains like P(...) || Q(...) || R(...)
    ($p:ident($($arg1:tt)*) || $($rest:tt)+) => {
        $p.or(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_predicate!($($rest)+)
        )
    };

    // Simple function call: P(...)
    ($func:ident($($args:tt)*)) => {
        $func.call($crate::__parse_exprs!($($args)*))
    };
}
