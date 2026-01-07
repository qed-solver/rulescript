use serde::Serialize;
use serde_json::Value;

use datafusion::{
    arrow::datatypes::DataType,
    common::DFSchemaRef,
    logical_expr::{
        Aggregate, Expr, Filter, Join, JoinType, Limit, LogicalPlan, Operator, Projection, Sort,
        TableScan, Union,
    },
};

use crate::{
    ast::{
        empty::Empty, extension::UserDefinedLogicalPattern, opaque::Type, pattern::ScalarPattern,
        source::Source,
    },
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
        Self { tables: Vec::new() }
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

    fn resolve_table_scan(&mut self, scan: &TableScan) -> usize {
        let table_name = scan.table_name.to_string();
        // Check if table already exists
        for (idx, table) in self.tables.iter().enumerate() {
            if table.name == table_name {
                return idx;
            }
        }

        // Add new table
        let idx = self.tables.len();
        self.tables.push(TableInfo {
            name: table_name,
            schema: scan.projected_schema.clone(),
        });
        idx
    }

    fn extract_schemas(&self) -> Vec<QedSchema> {
        self.tables
            .iter()
            .map(|table| {
                let schema = table.schema.as_ref();
                let fields: Vec<String> =
                    schema.fields().iter().map(|f| f.name().clone()).collect();

                let types: Vec<String> = schema
                    .fields()
                    .iter()
                    .map(|f| self.datatype_to_string(f.data_type()))
                    .collect();

                let nullable: Vec<bool> = schema.fields().iter().map(|f| f.is_nullable()).collect();

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
        self.serialize_rel_with_outer_columns(plan, &[])
    }

    fn serialize_rel_with_outer_columns(
        &mut self,
        plan: &LogicalPlan,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        match plan {
            LogicalPlan::Extension(ext) => {
                if let Some(source) = ext.node.as_any().downcast_ref::<Source>() {
                    let idx = self.resolve_table(source);
                    let columns = source
                        .schema
                        .to_datafusion_schema()
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
                } else if let Some(empty) = ext.node.as_any().downcast_ref::<Empty>() {
                    self.serialize_empty(empty)
                } else if let Some(ud_pattern) = ext
                    .node
                    .as_any()
                    .downcast_ref::<UserDefinedLogicalPattern>()
                {
                    // For user-defined operators, serialize their semantics
                    // The semantics represent the operator in terms of standard relational algebra
                    self.serialize_rel_with_outer_columns(
                        ud_pattern.implementation().semantics(),
                        outer_columns,
                    )
                } else {
                    Err(QedError::UnsupportedPlan(
                        "Unknown extension type".to_string(),
                    ))
                }
            }
            LogicalPlan::TableScan(scan) => {
                let idx = self.resolve_table_scan(scan);
                let columns = scan
                    .projected_schema
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
            }
            LogicalPlan::Filter(filter) => self.serialize_filter(filter, outer_columns),
            LogicalPlan::Projection(proj) => self.serialize_projection(proj, outer_columns),
            LogicalPlan::Join(join) => self.serialize_join(join, outer_columns),
            LogicalPlan::Aggregate(agg) => self.serialize_aggregate(agg, outer_columns),
            LogicalPlan::Union(union) => self.serialize_union(union, outer_columns),
            LogicalPlan::Subquery(subquery) => {
                // Subquery node wraps the inner plan - just serialize it with the same outer context
                self.serialize_rel_with_outer_columns(&subquery.subquery, outer_columns)
            }
            LogicalPlan::SubqueryAlias(alias) => {
                // SubqueryAlias is just a naming wrapper - serialize the inner plan
                self.serialize_rel_with_outer_columns(&alias.input, outer_columns)
            }
            LogicalPlan::Sort(sort) => self.serialize_sort(sort, outer_columns),
            LogicalPlan::Limit(limit) => self.serialize_limit(limit, outer_columns),
            _ => Err(QedError::UnsupportedPlan(format!("{}", plan.display()))),
        }
    }

    fn serialize_filter(
        &mut self,
        filter: &Filter,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_outer_columns(filter.input.as_ref(), outer_columns)?;

        // Build merged column context for expressions: [outer_columns..., source.columns...]
        let mut merged_columns = outer_columns.to_vec();
        merged_columns.extend(source.columns.clone());

        let condition = self.serialize_expr_with_columns(&filter.predicate, &merged_columns)?;
        Ok(SerializedRel {
            json: serde_json::json!({
                "filter": {
                    "condition": condition,
                    "source": source.json
                }
            }),
            columns: source.columns, // Filter passes through columns (not merged)
        })
    }

    fn serialize_projection(
        &mut self,
        proj: &Projection,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_outer_columns(proj.input.as_ref(), outer_columns)?;

        // Build merged column context: [outer_columns..., source.columns...]
        let mut merged_columns = outer_columns.to_vec();
        merged_columns.extend(source.columns.clone());

        let target: Vec<Value> = proj
            .expr
            .iter()
            .map(|e| self.serialize_expr_with_columns(e, &merged_columns))
            .collect::<Result<_, _>>()?;

        // Extract output columns from projection
        let output_columns = proj
            .schema
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

    fn serialize_join(
        &mut self,
        join: &Join,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let kind = self.join_type_to_string(&join.join_type);
        let left = self.serialize_rel_with_outer_columns(join.left.as_ref(), outer_columns)?;
        let right = self.serialize_rel_with_outer_columns(join.right.as_ref(), outer_columns)?;

        // Join output columns are [left_columns..., right_columns...]
        let mut output_columns = left.columns.clone();
        output_columns.extend(right.columns.clone());

        // Build merged column context: [outer_columns..., output_columns...]
        let mut merged_columns = outer_columns.to_vec();
        merged_columns.extend(output_columns.clone());

        let condition = if let Some(filter_expr) = &join.filter {
            self.serialize_expr_with_columns(filter_expr, &merged_columns)?
        } else if !join.on.is_empty() {
            // Build AND chain from on conditions
            self.serialize_join_on_conditions(&join.on, &merged_columns)?
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
        &mut self,
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

    fn serialize_expr_with_columns(
        &mut self,
        expr: &Expr,
        columns: &[ColumnInfo],
    ) -> Result<Value, QedError> {
        match expr {
            Expr::Column(col) => {
                // Find column by name in the merged column list
                let idx = columns
                    .iter()
                    .position(|c| c.name == col.name)
                    .ok_or_else(|| {
                        QedError::ColumnNotFound(format!("Column {:?} not found", col))
                    })?;
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
                Ok(
                    serde_json::json!({"operator": operator, "operand": [left, right], "type": type_str}),
                )
            }
            Expr::Alias(alias) => self.serialize_expr_with_columns(&alias.expr, columns),
            Expr::OuterReferenceColumn(_data_type, column) => {
                // OuterReferenceColumn is just like Column - find it in the merged list
                let idx = columns
                    .iter()
                    .position(|c| c.name == column.name)
                    .ok_or_else(|| {
                        QedError::ColumnNotFound(format!(
                            "Outer reference column {:?} not found",
                            column
                        ))
                    })?;
                let type_str = self.datatype_to_string(&columns[idx].data_type);
                Ok(serde_json::json!({"column": idx, "type": type_str}))
            }
            Expr::Exists(exists) => {
                // Serialize EXISTS subquery
                // QED format: {"operator": "EXISTS", "operand": [], "query": {...}}
                // For NOT EXISTS, wrap with NOT operator
                let subquery_serialized =
                    self.serialize_rel_with_outer_columns(&exists.subquery.subquery, columns)?;
                let exists_expr = serde_json::json!({
                    "operator": "EXISTS",
                    "operand": [],
                    "query": subquery_serialized.json,
                    "type": "BOOLEAN"
                });
                if exists.negated {
                    Ok(serde_json::json!({
                        "operator": "NOT",
                        "operand": [exists_expr],
                        "type": "BOOLEAN"
                    }))
                } else {
                    Ok(exists_expr)
                }
            }
            Expr::Cast(cast) => {
                // Cast is semantically transparent for equivalence checking
                // Just serialize the inner expression
                self.serialize_expr_with_columns(&cast.expr, columns)
            }
            Expr::TryCast(try_cast) => {
                // TryCast is semantically transparent for equivalence checking
                self.serialize_expr_with_columns(&try_cast.expr, columns)
            }
            Expr::InList(in_list) => {
                // IN (a, b, c) is equivalent to (= a) OR (= b) OR (= c)
                let expr_val = self.serialize_expr_with_columns(&in_list.expr, columns)?;
                let list_vals: Vec<Value> = in_list
                    .list
                    .iter()
                    .map(|e| self.serialize_expr_with_columns(e, columns))
                    .collect::<Result<_, _>>()?;

                // Build OR chain of equality comparisons
                let mut result: Option<Value> = None;
                for val in list_vals {
                    let eq = serde_json::json!({
                        "operator": "=",
                        "operand": [expr_val.clone(), val],
                        "type": "BOOLEAN"
                    });
                    result = Some(match result {
                        None => eq,
                        Some(prev) => serde_json::json!({
                            "operator": "OR",
                            "operand": [prev, eq],
                            "type": "BOOLEAN"
                        }),
                    });
                }

                let in_expr = result.unwrap_or_else(
                    || serde_json::json!({"operator": "false", "operand": [], "type": "BOOLEAN"}),
                );

                if in_list.negated {
                    Ok(serde_json::json!({
                        "operator": "NOT",
                        "operand": [in_expr],
                        "type": "BOOLEAN"
                    }))
                } else {
                    Ok(in_expr)
                }
            }
            Expr::Like(like) => {
                let expr_val = self.serialize_expr_with_columns(&like.expr, columns)?;
                let pattern_val = self.serialize_expr_with_columns(&like.pattern, columns)?;
                let operator = if like.negated { "NOT LIKE" } else { "LIKE" };
                Ok(serde_json::json!({
                    "operator": operator,
                    "operand": [expr_val, pattern_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::Not(inner) => {
                let inner_val = self.serialize_expr_with_columns(inner, columns)?;
                Ok(serde_json::json!({
                    "operator": "NOT",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::IsNull(inner) => {
                let inner_val = self.serialize_expr_with_columns(inner, columns)?;
                Ok(serde_json::json!({
                    "operator": "IS NULL",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::IsNotNull(inner) => {
                let inner_val = self.serialize_expr_with_columns(inner, columns)?;
                Ok(serde_json::json!({
                    "operator": "IS NOT NULL",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::Between(between) => {
                // BETWEEN low AND high is equivalent to (>= low) AND (<= high)
                let expr_val = self.serialize_expr_with_columns(&between.expr, columns)?;
                let low_val = self.serialize_expr_with_columns(&between.low, columns)?;
                let high_val = self.serialize_expr_with_columns(&between.high, columns)?;
                let between_expr = serde_json::json!({
                    "operator": "AND",
                    "operand": [
                        {"operator": ">=", "operand": [expr_val.clone(), low_val], "type": "BOOLEAN"},
                        {"operator": "<=", "operand": [expr_val, high_val], "type": "BOOLEAN"}
                    ],
                    "type": "BOOLEAN"
                });
                if between.negated {
                    Ok(serde_json::json!({
                        "operator": "NOT",
                        "operand": [between_expr],
                        "type": "BOOLEAN"
                    }))
                } else {
                    Ok(between_expr)
                }
            }
            Expr::Case(case) => {
                // CASE WHEN ... THEN ... ELSE ... END
                // Serialize as nested IIF (if-then-else)
                let else_val = match &case.else_expr {
                    Some(e) => self.serialize_expr_with_columns(e, columns)?,
                    None => serde_json::json!({"operator": "null", "operand": [], "type": "NULL"}),
                };

                let mut result = else_val;
                for (when_expr, then_expr) in case.when_then_expr.iter().rev() {
                    let when_val = self.serialize_expr_with_columns(when_expr, columns)?;
                    let then_val = self.serialize_expr_with_columns(then_expr, columns)?;
                    result = serde_json::json!({
                        "operator": "CASE",
                        "operand": [when_val, then_val, result],
                        "type": "INTEGER" // TODO: infer type from then_expr
                    });
                }
                Ok(result)
            }
            Expr::ScalarSubquery(sq) => {
                // Serialize scalar subquery
                let subquery_serialized =
                    self.serialize_rel_with_outer_columns(&sq.subquery, columns)?;
                Ok(serde_json::json!({
                    "operator": "SCALAR_QUERY",
                    "operand": [],
                    "query": subquery_serialized.json,
                    "type": "INTEGER" // TODO: infer type from subquery
                }))
            }
            Expr::InSubquery(in_sq) => {
                // IN (subquery) / NOT IN (subquery)
                // QED format: {"operator": "IN", "operand": [<expr>], "query": {...}}
                // For NOT IN, wrap with NOT operator
                let expr_val = self.serialize_expr_with_columns(&in_sq.expr, columns)?;
                let subquery_serialized =
                    self.serialize_rel_with_outer_columns(&in_sq.subquery.subquery, columns)?;
                let in_expr = serde_json::json!({
                    "operator": "IN",
                    "operand": [expr_val],
                    "query": subquery_serialized.json,
                    "type": "BOOLEAN"
                });
                if in_sq.negated {
                    Ok(serde_json::json!({
                        "operator": "NOT",
                        "operand": [in_expr],
                        "type": "BOOLEAN"
                    }))
                } else {
                    Ok(in_expr)
                }
            }
            Expr::Negative(inner) => {
                let inner_val = self.serialize_expr_with_columns(inner, columns)?;
                Ok(serde_json::json!({
                    "operator": "-",
                    "operand": [inner_val],
                    "type": "INTEGER"
                }))
            }
            _ => Err(QedError::UnsupportedExpr(format!("{:?}", expr))),
        }
    }

    fn serialize_aggregate(
        &mut self,
        agg: &Aggregate,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_outer_columns(agg.input.as_ref(), outer_columns)?;

        // Build merged column context: [outer_columns..., source.columns...]
        let mut merged_columns = outer_columns.to_vec();
        merged_columns.extend(source.columns.clone());

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
                        self.serialize_expr_with_columns(inner_expr, &merged_columns)
                    }
                    _ => Err(QedError::UnsupportedExpr(format!(
                        "Unsupported group key: {:?}",
                        inner_expr
                    ))),
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
                            .map(|arg| self.serialize_expr_with_columns(arg, &merged_columns))
                            .collect::<Result<_, _>>()?;

                        // Get return type from AggregatePattern if available
                        let return_type = if let Some(pattern) =
                            agg_func
                                .func
                                .inner()
                                .as_any()
                                .downcast_ref::<crate::ast::pattern::AggregatePattern>()
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
        let output_columns = agg
            .schema
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

    fn serialize_union(
        &mut self,
        union: &Union,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let serialized_inputs: Vec<SerializedRel> = union
            .inputs
            .iter()
            .map(|input| self.serialize_rel_with_outer_columns(input.as_ref(), outer_columns))
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

    fn serialize_sort(
        &mut self,
        sort: &Sort,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_outer_columns(&sort.input, outer_columns)?;

        // Build merged column context
        let mut merged_columns = outer_columns.to_vec();
        merged_columns.extend(source.columns.clone());

        // Serialize collation (sort expressions)
        let collation: Vec<Value> = sort
            .expr
            .iter()
            .map(|sort_expr| {
                // Find column index for the sort expression
                let expr_val =
                    self.serialize_expr_with_columns(&sort_expr.expr, &merged_columns)?;

                // Get type from the expression
                let type_str =
                    if let Some(col_idx) = expr_val.get("column").and_then(|v| v.as_i64()) {
                        let idx = col_idx as usize;
                        if idx < merged_columns.len() {
                            self.datatype_to_string(&merged_columns[idx].data_type)
                        } else {
                            "INTEGER".to_string()
                        }
                    } else {
                        "INTEGER".to_string()
                    };

                let direction = if sort_expr.asc {
                    "ASCENDING"
                } else {
                    "DESCENDING"
                };

                Ok(serde_json::json!([expr_val, type_str, direction]))
            })
            .collect::<Result<_, QedError>>()?;

        Ok(SerializedRel {
            json: serde_json::json!({
                "sort": {
                    "collation": collation,
                    "source": source.json,
                    "offset": Value::Null,
                    "limit": Value::Null
                }
            }),
            columns: source.columns,
        })
    }

    fn serialize_limit(
        &mut self,
        limit: &Limit,
        outer_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        // Check if input is a Sort - if so, combine them into a single sort node
        if let LogicalPlan::Sort(sort) = limit.input.as_ref() {
            let source = self.serialize_rel_with_outer_columns(&sort.input, outer_columns)?;

            // Build merged column context
            let mut merged_columns = outer_columns.to_vec();
            merged_columns.extend(source.columns.clone());

            // Serialize collation
            let collation: Vec<Value> = sort
                .expr
                .iter()
                .map(|sort_expr| {
                    let expr_val =
                        self.serialize_expr_with_columns(&sort_expr.expr, &merged_columns)?;
                    let type_str =
                        if let Some(col_idx) = expr_val.get("column").and_then(|v| v.as_i64()) {
                            let idx = col_idx as usize;
                            if idx < merged_columns.len() {
                                self.datatype_to_string(&merged_columns[idx].data_type)
                            } else {
                                "INTEGER".to_string()
                            }
                        } else {
                            "INTEGER".to_string()
                        };
                    let direction = if sort_expr.asc {
                        "ASCENDING"
                    } else {
                        "DESCENDING"
                    };
                    Ok(serde_json::json!([expr_val, type_str, direction]))
                })
                .collect::<Result<_, QedError>>()?;

            // Serialize offset and limit
            let offset_val = match &limit.skip {
                Some(skip) => self.serialize_expr_with_columns(skip, &merged_columns)?,
                None => Value::Null,
            };
            let limit_val = match &limit.fetch {
                Some(fetch) => self.serialize_expr_with_columns(fetch, &merged_columns)?,
                None => Value::Null,
            };

            Ok(SerializedRel {
                json: serde_json::json!({
                    "sort": {
                        "collation": collation,
                        "source": source.json,
                        "offset": offset_val,
                        "limit": limit_val
                    }
                }),
                columns: source.columns,
            })
        } else {
            // Limit without Sort - serialize as sort with empty collation
            let source = self.serialize_rel_with_outer_columns(&limit.input, outer_columns)?;

            let mut merged_columns = outer_columns.to_vec();
            merged_columns.extend(source.columns.clone());

            let offset_val = match &limit.skip {
                Some(skip) => self.serialize_expr_with_columns(skip, &merged_columns)?,
                None => Value::Null,
            };
            let limit_val = match &limit.fetch {
                Some(fetch) => self.serialize_expr_with_columns(fetch, &merged_columns)?,
                None => Value::Null,
            };

            Ok(SerializedRel {
                json: serde_json::json!({
                    "sort": {
                        "collation": [],
                        "source": source.json,
                        "offset": offset_val,
                        "limit": limit_val
                    }
                }),
                columns: source.columns,
            })
        }
    }

    fn serialize_empty(&mut self, empty: &Empty) -> Result<SerializedRel, QedError> {
        // Empty pattern derives its schema from the inner plan
        let schema: Vec<Value> = empty
            .inner
            .schema()
            .fields()
            .iter()
            .map(|f| serde_json::json!(self.datatype_to_string(f.data_type())))
            .collect();

        // Output columns from the inner plan's schema
        let output_columns = empty
            .inner
            .schema()
            .fields()
            .iter()
            .map(|f| ColumnInfo {
                name: f.name().clone(),
                data_type: f.data_type().clone(),
            })
            .collect();

        // Empty is serialized as values with empty content
        Ok(SerializedRel {
            json: serde_json::json!({
                "values": {
                    "schema": schema,
                    "content": []
                }
            }),
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

impl QedSerializer {
    /// Serialize a pair of concrete plans (before/after transformation) to QED JSON format
    pub fn serialize_plan_pair(
        &mut self,
        before: &LogicalPlan,
        after: &LogicalPlan,
    ) -> Result<String, QedError> {
        // Clear tables for fresh serialization
        self.tables.clear();

        // Serialize both plans
        let before_serialized = self.serialize_rel(before)?;
        let after_serialized = self.serialize_rel(after)?;

        // Generate help text
        let before_help = format!("{}", before.display_indent());
        let after_help = format!("{}", after.display_indent());

        // Extract schemas
        let schemas = self.extract_schemas();

        let output = QedOutput {
            schemas,
            queries: vec![before_serialized.json, after_serialized.json],
            help: vec![before_help, after_help],
        };

        Ok(serde_json::to_string_pretty(&output)?)
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
