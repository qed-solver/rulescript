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
    qualifier: Option<String>,
    name: String,
    data_type: DataType,
}

impl ColumnInfo {
    /// Strict match: both qualifier and name must match
    fn matches(&self, qualifier: &Option<datafusion::common::TableReference>, name: &str) -> bool {
        if self.name != name {
            return false;
        }
        match (&self.qualifier, qualifier) {
            (Some(q1), Some(q2)) => q1 == &q2.to_string(),
            (None, None) => true,
            _ => false,
        }
    }
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
        self.serialize_rel_with_global(plan, &[])
    }

    /// Serialize a relational plan with global column context.
    /// Global columns are from outer scopes (for OuterReferenceColumn in correlated subqueries).
    fn serialize_rel_with_global(
        &mut self,
        plan: &LogicalPlan,
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        match plan {
            LogicalPlan::Extension(ext) => {
                if let Some(source) = ext.node.as_any().downcast_ref::<Source>() {
                    let idx = self.resolve_table(source);
                    let columns = source
                        .schema
                        .to_datafusion_schema()
                        .iter()
                        .map(|(qualifier, f)| ColumnInfo {
                            qualifier: qualifier.map(|q| q.to_string()),
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
                    self.serialize_rel_with_global(
                        ud_pattern.implementation().semantics(),
                        global_columns,
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
                    .iter()
                    .map(|(qualifier, f)| ColumnInfo {
                        qualifier: qualifier.map(|q| q.to_string()),
                        name: f.name().clone(),
                        data_type: f.data_type().clone(),
                    })
                    .collect();
                Ok(SerializedRel {
                    json: serde_json::json!({"scan": idx}),
                    columns,
                })
            }
            LogicalPlan::Filter(filter) => self.serialize_filter(filter, global_columns),
            LogicalPlan::Projection(proj) => self.serialize_projection(proj, global_columns),
            LogicalPlan::Join(join) => self.serialize_join(join, global_columns),
            LogicalPlan::Aggregate(agg) => self.serialize_aggregate(agg, global_columns),
            LogicalPlan::Union(union) => self.serialize_union(union, global_columns),
            LogicalPlan::Subquery(subquery) => {
                // Subquery node wraps the inner plan - just serialize it with the same outer context
                self.serialize_rel_with_global(&subquery.subquery, global_columns)
            }
            LogicalPlan::SubqueryAlias(alias) => {
                // SubqueryAlias renames the output columns - serialize inner plan but use alias schema
                let inner = self.serialize_rel_with_global(&alias.input, global_columns)?;
                // Use SubqueryAlias's schema for output columns (has alias as qualifier)
                let columns = alias
                    .schema
                    .iter()
                    .map(|(qualifier, f)| ColumnInfo {
                        qualifier: qualifier.map(|q| q.to_string()),
                        name: f.name().clone(),
                        data_type: f.data_type().clone(),
                    })
                    .collect();
                Ok(SerializedRel {
                    json: inner.json,
                    columns,
                })
            }
            LogicalPlan::Sort(sort) => self.serialize_sort(sort, global_columns),
            LogicalPlan::Limit(limit) => self.serialize_limit(limit, global_columns),
            _ => Err(QedError::UnsupportedPlan(format!("{}", plan.display()))),
        }
    }

    fn serialize_filter(
        &mut self,
        filter: &Filter,
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_global(filter.input.as_ref(), global_columns)?;

        let condition = self.serialize_expr(&filter.predicate, global_columns, &source.columns)?;
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_global(proj.input.as_ref(), global_columns)?;

        let target: Vec<Value> = proj
            .expr
            .iter()
            .map(|e| self.serialize_expr(e, global_columns, &source.columns))
            .collect::<Result<_, _>>()?;

        // Extract output columns from projection
        let output_columns = proj
            .schema
            .iter()
            .map(|(qualifier, f)| ColumnInfo {
                qualifier: qualifier.map(|q| q.to_string()),
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let kind = self.join_type_to_string(&join.join_type);
        let left = self.serialize_rel_with_global(join.left.as_ref(), global_columns)?;
        let right = self.serialize_rel_with_global(join.right.as_ref(), global_columns)?;

        // Join output columns (local) are [left_columns..., right_columns...]
        let mut local_columns = left.columns.clone();
        local_columns.extend(right.columns.clone());

        let condition = if let Some(filter_expr) = &join.filter {
            self.serialize_expr(filter_expr, global_columns, &local_columns)?
        } else if !join.on.is_empty() {
            self.serialize_join_on_conditions(&join.on, global_columns, &local_columns)?
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
            columns: local_columns,
        })
    }

    fn serialize_join_on_conditions(
        &mut self,
        on: &[(Expr, Expr)],
        global_columns: &[ColumnInfo],
        local_columns: &[ColumnInfo],
    ) -> Result<Value, QedError> {
        if on.is_empty() {
            return Ok(serde_json::json!({"operator": "true", "operand": [], "type": "BOOLEAN"}));
        }

        let mut conditions: Vec<Value> = Vec::new();
        for (left_expr, right_expr) in on {
            let left_val = self.serialize_expr(left_expr, global_columns, local_columns)?;
            let right_val = self.serialize_expr(right_expr, global_columns, local_columns)?;
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

    /// Serialize an expression with separate global and local column contexts.
    ///
    /// - `global_columns`: Columns from outer scopes (for OuterReferenceColumn)
    /// - `local_columns`: Columns from the current operator's input
    ///
    /// Column indices:
    /// - Local columns: index = global_columns.len() + local_index
    /// - Global columns (OuterReferenceColumn): index = global_index
    ///
    /// When descending into a subquery, global + local are merged to become the new global.
    fn serialize_expr(
        &mut self,
        expr: &Expr,
        global_columns: &[ColumnInfo],
        local_columns: &[ColumnInfo],
    ) -> Result<Value, QedError> {
        match expr {
            Expr::Column(col) => {
                // Look in local columns first (strict qualifier + name match)
                if let Some(local_idx) = local_columns
                    .iter()
                    .position(|c| c.matches(&col.relation, &col.name))
                {
                    let type_str = self.datatype_to_string(&local_columns[local_idx].data_type);
                    let idx = global_columns.len() + local_idx;
                    return Ok(serde_json::json!({"column": idx, "type": type_str}));
                }
                // Fall back to global columns
                if let Some(global_idx) = global_columns
                    .iter()
                    .position(|c| c.matches(&col.relation, &col.name))
                {
                    let type_str = self.datatype_to_string(&global_columns[global_idx].data_type);
                    return Ok(serde_json::json!({"column": global_idx, "type": type_str}));
                }
                Err(QedError::ColumnNotFound(format!(
                    "Column {:?} not found in local or global",
                    col
                )))
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
                    .map(|e| self.serialize_expr(e, global_columns, local_columns))
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
                let left = self.serialize_expr(&bin.left, global_columns, local_columns)?;
                let right = self.serialize_expr(&bin.right, global_columns, local_columns)?;
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
            Expr::Alias(alias) => self.serialize_expr(&alias.expr, global_columns, local_columns),
            Expr::OuterReferenceColumn(_data_type, column) => {
                // OuterReferenceColumn references global columns only (strict match)
                let global_idx = global_columns
                    .iter()
                    .position(|c| c.matches(&column.relation, &column.name))
                    .ok_or_else(|| {
                        QedError::ColumnNotFound(format!(
                            "Outer reference column {:?} not found in global",
                            column
                        ))
                    })?;
                let type_str = self.datatype_to_string(&global_columns[global_idx].data_type);
                Ok(serde_json::json!({"column": global_idx, "type": type_str}))
            }
            Expr::Exists(exists) => {
                // Merge global + local to become new global for subquery
                let mut new_global = global_columns.to_vec();
                new_global.extend(local_columns.iter().cloned());
                let subquery_serialized =
                    self.serialize_rel_with_global(&exists.subquery.subquery, &new_global)?;
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
            Expr::Cast(cast) => self.serialize_expr(&cast.expr, global_columns, local_columns),
            Expr::TryCast(try_cast) => {
                self.serialize_expr(&try_cast.expr, global_columns, local_columns)
            }
            Expr::InList(in_list) => {
                let expr_val = self.serialize_expr(&in_list.expr, global_columns, local_columns)?;
                let list_vals: Vec<Value> = in_list
                    .list
                    .iter()
                    .map(|e| self.serialize_expr(e, global_columns, local_columns))
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
                let expr_val = self.serialize_expr(&like.expr, global_columns, local_columns)?;
                let pattern_val =
                    self.serialize_expr(&like.pattern, global_columns, local_columns)?;
                let operator = if like.negated { "NOT LIKE" } else { "LIKE" };
                Ok(serde_json::json!({
                    "operator": operator,
                    "operand": [expr_val, pattern_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::Not(inner) => {
                let inner_val = self.serialize_expr(inner, global_columns, local_columns)?;
                Ok(serde_json::json!({
                    "operator": "NOT",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::IsNull(inner) => {
                let inner_val = self.serialize_expr(inner, global_columns, local_columns)?;
                Ok(serde_json::json!({
                    "operator": "IS NULL",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::IsNotNull(inner) => {
                let inner_val = self.serialize_expr(inner, global_columns, local_columns)?;
                Ok(serde_json::json!({
                    "operator": "IS NOT NULL",
                    "operand": [inner_val],
                    "type": "BOOLEAN"
                }))
            }
            Expr::Between(between) => {
                let expr_val = self.serialize_expr(&between.expr, global_columns, local_columns)?;
                let low_val = self.serialize_expr(&between.low, global_columns, local_columns)?;
                let high_val = self.serialize_expr(&between.high, global_columns, local_columns)?;
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
                let else_val = match &case.else_expr {
                    Some(e) => self.serialize_expr(e, global_columns, local_columns)?,
                    None => serde_json::json!({"operator": "null", "operand": [], "type": "NULL"}),
                };

                let mut result = else_val;
                for (when_expr, then_expr) in case.when_then_expr.iter().rev() {
                    let when_val = self.serialize_expr(when_expr, global_columns, local_columns)?;
                    let then_val = self.serialize_expr(then_expr, global_columns, local_columns)?;
                    result = serde_json::json!({
                        "operator": "CASE",
                        "operand": [when_val, then_val, result],
                        "type": "INTEGER"
                    });
                }
                Ok(result)
            }
            Expr::ScalarSubquery(sq) => {
                // Merge global + local to become new global for subquery
                let mut new_global = global_columns.to_vec();
                new_global.extend(local_columns.iter().cloned());
                let subquery_serialized =
                    self.serialize_rel_with_global(&sq.subquery, &new_global)?;
                Ok(serde_json::json!({
                    "operator": "SCALAR_QUERY",
                    "operand": [],
                    "query": subquery_serialized.json,
                    "type": "INTEGER"
                }))
            }
            Expr::InSubquery(in_sq) => {
                let expr_val = self.serialize_expr(&in_sq.expr, global_columns, local_columns)?;
                // Merge global + local to become new global for subquery
                let mut new_global = global_columns.to_vec();
                new_global.extend(local_columns.iter().cloned());
                let subquery_serialized =
                    self.serialize_rel_with_global(&in_sq.subquery.subquery, &new_global)?;
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
                let inner_val = self.serialize_expr(inner, global_columns, local_columns)?;
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_global(agg.input.as_ref(), global_columns)?;
        let local_columns = &source.columns;

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
                        self.serialize_expr(inner_expr, global_columns, local_columns)
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
                            .map(|arg| self.serialize_expr(arg, global_columns, local_columns))
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
            .iter()
            .map(|(qualifier, f)| ColumnInfo {
                qualifier: qualifier.map(|q| q.to_string()),
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let serialized_inputs: Vec<SerializedRel> = union
            .inputs
            .iter()
            .map(|input| self.serialize_rel_with_global(input.as_ref(), global_columns))
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        let source = self.serialize_rel_with_global(&sort.input, global_columns)?;
        let local_columns = &source.columns;

        // Serialize collation (sort expressions)
        // Format: [column_index, type_string, direction_string]
        let collation: Vec<Value> = sort
            .expr
            .iter()
            .map(|sort_expr| {
                // Extract column index from the sort expression
                let (col_idx, type_str) = match &sort_expr.expr {
                    Expr::Column(col) => {
                        // Find in local columns
                        if let Some(local_idx) = local_columns
                            .iter()
                            .position(|c| c.matches(&col.relation, &col.name))
                        {
                            let idx = global_columns.len() + local_idx;
                            let type_str =
                                self.datatype_to_string(&local_columns[local_idx].data_type);
                            (idx, type_str)
                        } else {
                            return Err(QedError::ColumnNotFound(format!(
                                "Sort column {:?} not found",
                                col
                            )));
                        }
                    }
                    _ => {
                        return Err(QedError::UnsupportedExpr(format!(
                            "Sort expression must be a column: {:?}",
                            sort_expr.expr
                        )));
                    }
                };

                let direction = if sort_expr.asc {
                    "ASCENDING"
                } else {
                    "DESCENDING"
                };

                Ok(serde_json::json!([col_idx, type_str, direction]))
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
        global_columns: &[ColumnInfo],
    ) -> Result<SerializedRel, QedError> {
        // Check if input is a Sort - if so, combine them into a single sort node
        if let LogicalPlan::Sort(sort) = limit.input.as_ref() {
            let source = self.serialize_rel_with_global(&sort.input, global_columns)?;
            let local_columns = &source.columns;

            // Serialize collation
            // Format: [column_index, type_string, direction_string]
            let collation: Vec<Value> = sort
                .expr
                .iter()
                .map(|sort_expr| {
                    let (col_idx, type_str) = match &sort_expr.expr {
                        Expr::Column(col) => {
                            if let Some(local_idx) = local_columns
                                .iter()
                                .position(|c| c.matches(&col.relation, &col.name))
                            {
                                let idx = global_columns.len() + local_idx;
                                let type_str =
                                    self.datatype_to_string(&local_columns[local_idx].data_type);
                                (idx, type_str)
                            } else {
                                return Err(QedError::ColumnNotFound(format!(
                                    "Sort column {:?} not found",
                                    col
                                )));
                            }
                        }
                        _ => {
                            return Err(QedError::UnsupportedExpr(format!(
                                "Sort expression must be a column: {:?}",
                                sort_expr.expr
                            )));
                        }
                    };
                    let direction = if sort_expr.asc {
                        "ASCENDING"
                    } else {
                        "DESCENDING"
                    };
                    Ok(serde_json::json!([col_idx, type_str, direction]))
                })
                .collect::<Result<_, QedError>>()?;

            // Serialize offset and limit as expressions
            let offset_val = match &limit.skip {
                Some(skip) => self.serialize_expr(skip, global_columns, local_columns)?,
                None => Value::Null,
            };
            let limit_val = match &limit.fetch {
                Some(fetch) => self.serialize_expr(fetch, global_columns, local_columns)?,
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
            let source = self.serialize_rel_with_global(&limit.input, global_columns)?;
            let local_columns = &source.columns;

            // Serialize offset and limit as expressions
            let offset_val = match &limit.skip {
                Some(skip) => self.serialize_expr(skip, global_columns, local_columns)?,
                None => Value::Null,
            };
            let limit_val = match &limit.fetch {
                Some(fetch) => self.serialize_expr(fetch, global_columns, local_columns)?,
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
            .iter()
            .map(|(qualifier, f)| ColumnInfo {
                qualifier: qualifier.map(|q| q.to_string()),
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
