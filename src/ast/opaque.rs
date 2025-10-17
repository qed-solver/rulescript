use std::sync::Arc;

use datafusion::{
    arrow::datatypes::{DataType, Field as ArrowField, Schema as ArrowSchema},
    common::{DFSchema, DFSchemaRef},
};

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum Type {
    /// Built-in boolean type for predicates
    Boolean,
    /// Generic abstract type that can represent any concrete type
    Generic { id: String },
}
impl From<&Type> for DataType {
    fn from(abstract_type: &Type) -> Self {
        match abstract_type {
            Type::Boolean => DataType::Boolean,
            Type::Generic { .. } => {
                // Generic abstract types map to Binary for pattern matching
                DataType::Binary
            }
        }
    }
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Field {
    pub name: String,
    pub data_type: Type,
    pub nullable: bool,
}

impl Field {
    // Convert abstract field to Arrow field for DataFusion integration
    pub fn to_arrow_field(&self) -> ArrowField {
        ArrowField::new(&self.name, (&self.data_type).into(), self.nullable)
    }
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Schema {
    pub fields: Vec<Field>,
}

impl Schema {
    pub fn from_types(types: Vec<Type>) -> Self {
        let fields = types
            .into_iter()
            .enumerate()
            .map(|(i, dtype)| Field {
                name: format!("field_{}", i),
                data_type: dtype,
                nullable: true,
            })
            .collect();
        Self { fields }
    }

    pub fn field_count(&self) -> usize {
        self.fields.len()
    }

    // Convert abstract schema to DataFusion schema
    pub fn to_datafusion_schema(&self) -> DFSchemaRef {
        let arrow_fields: Vec<ArrowField> =
            self.fields.iter().map(|f| f.to_arrow_field()).collect();

        let arrow_schema = ArrowSchema::new(arrow_fields);
        Arc::new(
            DFSchema::try_from(arrow_schema)
                .expect("Failed to create DFSchema from abstract schema"),
        )
    }
}

/// Declaratively create a Schema with abstract types
///
/// # Examples
/// ```
/// use rulescript::schema;
///
/// // Single nullable field
/// let _s = schema!(col: T);
///
/// // Explicitly nullable
/// let _s = schema!(col: T nullable);
///
/// // Non-nullable field
/// let _s = schema!(col: T not_null);
///
/// // Multiple fields (all nullable)
/// let _s = schema!(x: T, y: U);
///
/// // Mixed nullability
/// let _s = schema!(x: T, y: U not_null, z: V nullable);
/// ```
#[macro_export]
macro_rules! schema {
    // General case: one or more fields with optional nullability modifiers
    ($($name:ident: $ty:ident $($modifier:ident)?),+ $(,)?) => {
        $crate::ast::opaque::Schema {
            fields: vec![
                $($crate::__schema_field!($name: $ty $($modifier)?)),+
            ],
        }
    };
}

/// Internal helper macro to parse individual field specifications
#[doc(hidden)]
#[macro_export]
macro_rules! __schema_field {
    // Field with not_null modifier
    ($name:ident: $ty:ident not_null) => {
        $crate::ast::opaque::Field {
            name: stringify!($name).to_string(),
            data_type: $crate::ast::opaque::Type::Generic {
                id: stringify!($ty).to_string(),
            },
            nullable: false,
        }
    };

    // Field with nullable modifier
    ($name:ident: $ty:ident nullable) => {
        $crate::ast::opaque::Field {
            name: stringify!($name).to_string(),
            data_type: $crate::ast::opaque::Type::Generic {
                id: stringify!($ty).to_string(),
            },
            nullable: true,
        }
    };

    // Field without modifier (defaults to nullable)
    ($name:ident: $ty:ident) => {
        $crate::ast::opaque::Field {
            name: stringify!($name).to_string(),
            data_type: $crate::ast::opaque::Type::Generic {
                id: stringify!($ty).to_string(),
            },
            nullable: true,
        }
    };
}
