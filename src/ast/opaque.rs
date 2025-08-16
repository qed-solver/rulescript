use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use datafusion::{
    arrow::datatypes::{DataType, Field, Schema},
    common::{DFSchema, DFSchemaRef},
};

// Global counter for generating unique field names
static FIELD_COUNTER: AtomicUsize = AtomicUsize::new(0);

/// Generate a globally unique field name
pub fn generate_unique_field_name() -> String {
    let field_id = FIELD_COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("__field_{}", field_id)
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct AbstractDataType {
    pub id: String,
}

impl AbstractDataType {
    // Convert abstract type to Binary for DataFusion integration
    pub fn to_datafusion_type(&self) -> DataType {
        DataType::Binary
    }
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct AbstractField {
    pub name: String,
    pub data_type: AbstractDataType,
    pub nullable: bool,
}

impl AbstractField {
    // Convert abstract field to Arrow field for DataFusion integration
    pub fn to_arrow_field(&self) -> Field {
        Field::new(
            &self.name,
            self.data_type.to_datafusion_type(),
            self.nullable,
        )
    }
}

#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct AbstractSchema {
    pub fields: Vec<AbstractField>,
}

impl AbstractSchema {
    pub fn from_abstract_types(types: Vec<AbstractDataType>) -> Self {
        let fields = types
            .into_iter()
            .map(|dtype| AbstractField {
                name: generate_unique_field_name(),
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
        let arrow_fields: Vec<Field> = self.fields.iter().map(|f| f.to_arrow_field()).collect();

        let arrow_schema = Schema::new(arrow_fields);
        Arc::new(
            DFSchema::try_from(arrow_schema)
                .expect("Failed to create DFSchema from abstract schema"),
        )
    }
}
