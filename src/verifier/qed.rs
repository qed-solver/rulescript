use serde::Serialize;
use serde_json::Value;

use datafusion::{
    arrow::datatypes::DataType,
    common::DFSchemaRef,
    logical_expr::{Aggregate, Expr, Filter, Join, JoinType, LogicalPlan, Operator, Projection, Union},
};

use crate::{
    ast::{opaque::Type, pattern::ScalarPattern, relational::Source},
    rule::RewriteRule,
};

use super::Verifier;

#[derive(Debug, Serialize)]
struct QedOutput {
    schemas: Vec<QedSchema>,
    queries: Vec<Value>,
    help: Vec<String>,
}

#[derive(Debug, Serialize)]
struct QedSchema {
    name: String,
    fields: Vec<String>,
    types: Vec<String>,
    nullable: Vec<bool>,
    key: Vec<Vec<usize>>,
    guaranteed: Vec<Value>,
}

#[derive(Debug)]
struct TableInfo {
    name: String,
    schema: DFSchemaRef,
}

/// Result of serializing a relational operator
/// Contains the JSON and the output columns that this operator produces
#[derive(Debug, Clone)]
struct SerializedRel {
    json: Value,
    columns: Vec<ColumnInfo>,
}

/// Information about a column in the output
#[derive(Debug, Clone)]
struct ColumnInfo {
    name: String,
    data_type: DataType,
}

pub struct QedSerializer {
    tables: Vec<TableInfo>,
}

impl Default for QedSerializer {
    fn default() -> Self {
        Self::new()
    }
}

impl QedSerializer {
    pub fn new() -> Self {
        Self {
            tables: Vec::new(),
        }
    }

    fn resolve_table(&mut self, source: &Source) -> usize {
        // Check if table already exists
        for (idx, table) in self.tables.iter().enumerate() {
            if table.name == source.table_name {
                return idx;
            }
        }

        // Add new table
        let idx = self.tables.len();
        self.tables.push(TableInfo {
            name: source.table_name.clone(),
            schema: source.schema.to_datafusion_schema(),
        });
        idx
    }

    fn extract_schemas(&self) -> Vec<QedSchema> {
        self.tables
            .iter()
            .map(|table| {
                let schema = table.schema.as_ref();
                let fields: Vec<String> = schema
                    .fields()
                    .iter()
                    .map(|f| f.name().clone())
                    .collect();

                let types: Vec<String> = schema
                    .fields()
                    .iter()
                    .map(|f| self.datatype_to_string(f.data_type()))
                    .collect();

                let nullable: Vec<bool> = schema
                    .fields()
                    .iter()
                    .map(|f| f.is_nullable())
                    .collect();

                QedSchema {
                    name: table.name.clone(),
                    fields,
                    types,
                    nullable,
                    key: vec![],
                    guaranteed: vec![],
                }
            })
            .collect()
    }

    fn datatype_to_string(&self, dt: &DataType) -> String {
        match dt {
            DataType::Boolean => "BOOLEAN".to_string(),
            // ALL other types map to INTEGER - we only support BOOLEAN as concrete type
            _ => "INTEGER".to_string(),
        }
    }

    fn type_to_string(&self, ty: &Type) -> String {
        match ty {
            Type::Boolean => "BOOLEAN".to_string(),
            Type::Generic { .. } => "INTEGER".to_string(), // All abstract types map to INTEGER
        }
    }

    fn serialize_rel(&mut self, plan: &LogicalPlan) -> Result<SerializedRel, QedError> {
        match plan {
            LogicalPlan::Extension(ext) => {
                if let Some(source) = ext.node.as_any().downcast_ref::<Source>() {
                    let idx = self.resolve_table(source);
                    let columns = source.schema.to_datafusion_schema()
                        .fields()
                        .iter()
                        .map(|f| ColumnInfo {
                            name: f.name().clone(),
                            data_type: f.data_type().clone(),
                        })
                        .collect();
                    Ok(SerializedRel {
                        json: serde_json::json!({"scan": idx}),
                        columns,
                    })
                } else {
                    Err(QedError::UnsupportedPlan(
                        "Unknown extension type".to_string(),
                    ))
                }
            }
            LogicalPlan::Filter(filter) => self.serialize_filter(filter),
            LogicalPlan::Projection(proj) => self.serialize_projection(proj),
            LogicalPlan::Join(join) => self.serialize_join(join),
            LogicalPlan::Aggregate(agg) => self.serialize_aggregate(agg),
            LogicalPlan::Union(union) => self.serialize_union(union),
            _ => Err(QedError::UnsupportedPlan(format!(
                "{}",
                plan.display()
            ))),
        }
    }

    fn serialize_filter(&mut self, filter: &Filter) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel(filter.input.as_ref())?;
        let condition = self.serialize_expr_with_columns(&filter.predicate, &source.columns)?;
        Ok(SerializedRel {
            json: serde_json::json!({
                "filter": {
                    "condition": condition,
                    "source": source.json
                }
            }),
            columns: source.columns, // Filter passes through columns
        })
    }

    fn serialize_projection(&mut self, proj: &Projection) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel(proj.input.as_ref())?;
        let target: Vec<Value> = proj
            .expr
            .iter()
            .map(|e| self.serialize_expr_with_columns(e, &source.columns))
            .collect::<Result<_, _>>()?;
        
        // Extract output columns from projection
        let output_columns = proj.schema
            .fields()
            .iter()
            .map(|f| ColumnInfo {
                name: f.name().clone(),
                data_type: f.data_type().clone(),
            })
            .collect();
        
        Ok(SerializedRel {
            json: serde_json::json!({
                "project": {
                    "target": target,
                    "source": source.json
                }
            }),
            columns: output_columns,
        })
    }

    fn serialize_join(&mut self, join: &Join) -> Result<SerializedRel, QedError> {
        let kind = self.join_type_to_string(&join.join_type);
        let left = self.serialize_rel(join.left.as_ref())?;
        let right = self.serialize_rel(join.right.as_ref())?;

        // Join output columns are [left_columns..., right_columns...]
        let mut output_columns = left.columns.clone();
        output_columns.extend(right.columns.clone());

        let condition = if let Some(filter_expr) = &join.filter {
            self.serialize_expr_with_columns(filter_expr, &output_columns)?
        } else if !join.on.is_empty() {
            // Build AND chain from on conditions
            self.serialize_join_on_conditions(&join.on, &output_columns)?
        } else {
            serde_json::json!({"operator": "true", "operand": [], "type": "BOOLEAN"})
        };

        Ok(SerializedRel {
            json: serde_json::json!({
                "join": {
                    "kind": kind,
                    "condition": condition,
                    "left": left.json,
                    "right": right.json
                }
            }),
            columns: output_columns,
        })
    }

    fn serialize_join_on_conditions(
        &self,
        on: &[(Expr, Expr)],
        columns: &[ColumnInfo],
    ) -> Result<Value, QedError> {
        if on.is_empty() {
            return Ok(serde_json::json!({"operator": "true", "operand": [], "type": "BOOLEAN"}));
        }

        let mut conditions: Vec<Value> = Vec::new();
        for (left_expr, right_expr) in on {
            let left_val = self.serialize_expr_with_columns(left_expr, columns)?;
            let right_val = self.serialize_expr_with_columns(right_expr, columns)?;
            conditions.push(serde_json::json!({
                "operator": "=",
                "operand": [left_val, right_val],
                "type": "BOOLEAN"
            }));
        }

        // Chain with AND
        if conditions.len() == 1 {
            Ok(conditions.into_iter().next().unwrap())
        } else {
            let mut result = conditions[0].clone();
            for cond in conditions.into_iter().skip(1) {
                result = serde_json::json!({
                    "operator": "AND",
                    "operand": [result, cond],
                    "type": "BOOLEAN"
                });
            }
            Ok(result)
        }
    }

    fn serialize_expr_with_columns(&self, expr: &Expr, columns: &[ColumnInfo]) -> Result<Value, QedError> {
        match expr {
            Expr::Column(col) => {
                // Find column by name in the column list
                let idx = columns
                    .iter()
                    .position(|c| c.name == col.name)
                    .ok_or_else(|| QedError::ColumnNotFound(format!("Column {:?} not found", col)))?;
                let type_str = self.datatype_to_string(&columns[idx].data_type);
                Ok(serde_json::json!({"column": idx, "type": type_str}))
            }
            Expr::Literal(scalar_val, _) => {
                let value = format!("{}", scalar_val);
                let type_str = self.datatype_to_string(&scalar_val.data_type());
                Ok(serde_json::json!({"operator": value, "operand": [], "type": type_str}))
            }
            Expr::ScalarFunction(func) => {
                let operator = func.func.name();
                let operand: Vec<Value> = func
                    .args
                    .iter()
                    .map(|e| self.serialize_expr_with_columns(e, columns))
                    .collect::<Result<_, _>>()?;

                let type_str = if let Some(pattern) =
                    func.func.inner().as_any().downcast_ref::<ScalarPattern>()
                {
                    self.type_to_string(&pattern.return_type)
                } else {
                    "INTEGER".to_string()
                };

                Ok(serde_json::json!({"operator": operator, "operand": operand, "type": type_str}))
            }
            Expr::BinaryExpr(bin) => {
                let operator = self.binary_op_to_string(&bin.op);
                let left = self.serialize_expr_with_columns(&bin.left, columns)?;
                let right = self.serialize_expr_with_columns(&bin.right, columns)?;
                let type_str = match bin.op {
                    Operator::Eq
                    | Operator::NotEq
                    | Operator::Lt
                    | Operator::LtEq
                    | Operator::Gt
                    | Operator::GtEq
                    | Operator::And
                    | Operator::Or => "BOOLEAN".to_string(),
                    _ => "INTEGER".to_string(),
                };
                Ok(serde_json::json!({"operator": operator, "operand": [left, right], "type": type_str}))
            }
            Expr::Alias(alias) => {
                self.serialize_expr_with_columns(&alias.expr, columns)
            }
            _ => Err(QedError::UnsupportedExpr(format!("{:?}", expr))),
        }
    }

    fn serialize_aggregate(&mut self, agg: &Aggregate) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel(agg.input.as_ref())?;

        // Serialize group-by keys
        let keys: Vec<Value> = agg
            .group_expr
            .iter()
            .map(|expr| {
                // Unwrap alias if present
                let inner_expr = match expr {
                    Expr::Alias(alias) => alias.expr.as_ref(),
                    _ => expr,
                };
                
                match inner_expr {
                    Expr::Column(_) | Expr::ScalarFunction(_) => {
                        self.serialize_expr_with_columns(inner_expr, &source.columns)
                    }
                    _ => Err(QedError::UnsupportedExpr(format!(
                        "Unsupported group key: {:?}",
                        inner_expr
                    )))
                }
            })
            .collect::<Result<_, _>>()?;

        // Serialize aggregate functions
        let functions: Vec<Value> = agg
            .aggr_expr
            .iter()
            .map(|expr| {
                // Unwrap alias if present
                let inner_expr = match expr {
                    Expr::Alias(alias) => alias.expr.as_ref(),
                    _ => expr,
                };
                
                match inner_expr {
                Expr::AggregateFunction(agg_func) => {
                    let operator = agg_func.func.name();

                    let operand: Vec<Value> = agg_func
                        .params
                        .args
                        .iter()
                        .map(|arg| self.serialize_expr_with_columns(arg, &source.columns))
                        .collect::<Result<_, _>>()?;

                    // Get return type from AggregatePattern if available
                    let return_type = if let Some(pattern) =
                        agg_func.func.inner().as_any().downcast_ref::<crate::ast::pattern::AggregatePattern>()
                    {
                        self.type_to_string(&pattern.return_type)
                    } else {
                        "INTEGER".to_string() // Default for unknown aggregate types
                    };

                    Ok(serde_json::json!({
                        "operator": operator,
                        "operand": operand,
                        "distinct": agg_func.params.distinct,
                        "ignoreNulls": false,
                        "type": return_type
                    }))
                }
                _ => Err(QedError::UnsupportedExpr(format!(
                    "Non-aggregate in aggr_expr: {:?}",
                    inner_expr
                ))),
                }
            })
            .collect::<Result<_, _>>()?;

        // Output columns from aggregate
        let output_columns = agg.schema
            .fields()
            .iter()
            .map(|f| ColumnInfo {
                name: f.name().clone(),
                data_type: f.data_type().clone(),
            })
            .collect();

        Ok(SerializedRel {
            json: serde_json::json!({
                "group": {
                    "keys": keys,
                    "function": functions,
                    "source": source.json
                }
            }),
            columns: output_columns,
        })
    }

    fn serialize_union(&mut self, union: &Union) -> Result<SerializedRel, QedError> {
        let serialized_inputs: Vec<SerializedRel> = union
            .inputs
            .iter()
            .map(|input| self.serialize_rel(input.as_ref()))
            .collect::<Result<_, _>>()?;
        
        let json_inputs: Vec<Value> = serialized_inputs.iter().map(|s| s.json.clone()).collect();
        
        // Union output columns are from the first input (all inputs must have compatible schemas)
        let output_columns = serialized_inputs
            .first()
            .map(|s| s.columns.clone())
            .unwrap_or_default();
        
        Ok(SerializedRel {
            json: serde_json::json!({"union": json_inputs}),
            columns: output_columns,
        })
    }



    fn binary_op_to_string(&self, op: &Operator) -> String {
        match op {
            Operator::Eq => "=",
            Operator::NotEq => "!=",
            Operator::Lt => "<",
            Operator::LtEq => "<=",
            Operator::Gt => ">",
            Operator::GtEq => ">=",
            Operator::Plus => "+",
            Operator::Minus => "-",
            Operator::Multiply => "*",
            Operator::Divide => "/",
            Operator::Modulo => "%",
            Operator::And => "AND",
            Operator::Or => "OR",
            _ => panic!("Unsupported operator: {:?}", op),
        }
        .to_string()
    }

    fn join_type_to_string(&self, jt: &JoinType) -> String {
        match jt {
            JoinType::Inner => "INNER",
            JoinType::Left => "LEFT",
            JoinType::Right => "RIGHT",
            JoinType::Full => "FULL",
            JoinType::LeftSemi => "SEMI",
            JoinType::LeftAnti => "ANTI",
            JoinType::RightSemi => "RIGHT_SEMI",
            JoinType::RightAnti => "RIGHT_ANTI",
            JoinType::LeftMark => "LEFT_MARK",
            JoinType::RightMark => "RIGHT_MARK",
        }
        .to_string()
    }
}

impl Verifier for QedSerializer {
    type Error = QedError;

    fn serialize_rule<R: RewriteRule>(&mut self, rule: &R) -> Result<String, Self::Error> {
        // Clear tables for fresh serialization
        self.tables.clear();

        let from = rule.from();
        let to = rule.to();

        // Serialize both patterns
        let from_serialized = self.serialize_rel(&from.plan)?;
        let to_serialized = self.serialize_rel(&to.plan)?;

        // Generate help text
        let from_help = format!("{}", from.plan.display_indent());
        let to_help = format!("{}", to.plan.display_indent());

        // Extract schemas
        let schemas = self.extract_schemas();

        let output = QedOutput {
            schemas,
            queries: vec![from_serialized.json, to_serialized.json],
            help: vec![from_help, to_help],
        };

        Ok(serde_json::to_string_pretty(&output)?)
    }
}

#[derive(Debug, thiserror::Error)]
pub enum QedError {
    #[error("Unsupported plan type: {0}")]
    UnsupportedPlan(String),
    #[error("Unsupported expression: {0}")]
    UnsupportedExpr(String),
    #[error("Column not found: {0}")]
    ColumnNotFound(String),
    #[error("JSON serialization error: {0}")]
    Json(#[from] serde_json::Error),
}
