use std::sync::Arc;

use datafusion::{
    arrow::datatypes::DataType,
    common::Column,
    error::{DataFusionError, Result},
    logical_expr::{
        ColumnarValue, Expr, ScalarFunctionArgs, ScalarUDF, ScalarUDFImpl, Signature, Volatility,
        expr::ScalarFunction,
    },
    scalar::ScalarValue,
};

use crate::ast::opaque::{AbstractDataType, generate_unique_id};

/// Abstract function that can take a configurable number of inputs
/// This integrates with DataFusion's scalar function system
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct AbstractFunction {
    pub name: String,
    pub input_types: Vec<AbstractDataType>,
    pub return_type: AbstractDataType,
    signature: Signature,
}

impl AbstractFunction {
    pub fn new(
        name: String,
        input_types: Vec<AbstractDataType>,
        return_type: AbstractDataType,
    ) -> Self {
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

    /// Convenience constructor for functions with a specific number of inputs (all same type)
    pub fn with_input_count(name: String, input_count: usize) -> Self {
        let input_types = (0..input_count)
            .map(|_| AbstractDataType {
                id: generate_unique_id("input"),
            })
            .collect();
        let return_type = AbstractDataType {
            id: generate_unique_id("return"),
        };

        Self::new(name, input_types, return_type)
    }

    /// Get the number of input arguments
    pub fn input_count(&self) -> usize {
        self.input_types.len()
    }

    /// Create a DataFusion Expr that calls this abstract function with given arguments
    pub fn call(&self, args: Vec<Expr>) -> Expr {
        if args.len() != self.input_types.len() {
            panic!(
                "AbstractFunction '{}' expects {} arguments, got {}",
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
}

impl ScalarUDFImpl for AbstractFunction {
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
        // Convert abstract return type to DataFusion type
        Ok(self.return_type.to_datafusion_type())
    }

    fn invoke_with_args(&self, _args: ScalarFunctionArgs) -> Result<ColumnarValue> {
        // Abstract functions shouldn't be executed - they're for pattern matching
        Err(DataFusionError::NotImplemented(format!(
            "AbstractFunction '{}' is for pattern matching, not execution",
            self.name
        )))
    }
}

/// Scalar pattern that wraps DataFusion's Expr
#[derive(Debug, Clone)]
pub struct ScalarPattern {
    pub expr: Expr,
}

impl ScalarPattern {
    /// Create a pattern for an abstract function call with specific types
    pub fn abstract_function(
        name: String,
        input_types: Vec<AbstractDataType>,
        return_type: AbstractDataType,
        args: Vec<Expr>,
    ) -> Self {
        let abs_func = AbstractFunction::new(name, input_types, return_type);
        Self {
            expr: abs_func.call(args),
        }
    }

    /// Create a pattern for an abstract function call with input count (generates abstract types)
    pub fn abstract_function_with_count(name: String, input_count: usize, args: Vec<Expr>) -> Self {
        let abs_func = AbstractFunction::with_input_count(name, input_count);
        Self {
            expr: abs_func.call(args),
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
}
