use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use datafusion::{
    arrow::datatypes::{DataType, Field as ArrowField, Schema as ArrowSchema},
    common::{DFSchema, DFSchemaRef},
};

// Global counter for generating unique identifiers
static ID_COUNTER: AtomicUsize = AtomicUsize::new(0);

/// Generate a globally unique identifier with a descriptive prefix
pub fn generate_unique_id(prefix: &str) -> String {
    let id = ID_COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("__{prefix}_{id}")
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Type {
    pub id: String,
}

impl Type {
    // Convert abstract type to Binary for DataFusion integration
    pub fn to_datafusion_type(&self) -> DataType {
        DataType::Binary
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
        ArrowField::new(
            &self.name,
            self.data_type.to_datafusion_type(),
            self.nullable,
        )
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
            .map(|dtype| Field {
                name: generate_unique_id("field"),
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
