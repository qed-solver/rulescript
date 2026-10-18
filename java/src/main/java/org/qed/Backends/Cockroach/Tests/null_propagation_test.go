package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestNullPropagation(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "NullPropagation")
	scan, cols := e.scan("nn", 1)
	isNull := e.f.ConstructIs(e.v(cols[0]), e.f.ConstructNull(types.Unknown))
	before := e.f.ConstructSelect(scan, e.filters(isNull))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructConstVal(tree.DBoolFalse, types.Bool)))
	e.expectRewrite(rule, before, want)

	tableName := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, "t")
	tableID := e.f.Metadata().AddTable(e.cat.Table(tableName), tableName)
	nullableCols := []opt.ColumnID{tableID.ColumnID(1)}
	nullableScan := e.f.ConstructScan(&memo.ScanPrivate{Table: tableID, Cols: opt.MakeColSet(nullableCols...)})
	nullableIsNull := e.f.ConstructIs(e.v(nullableCols[0]), e.f.ConstructNull(types.Unknown))
	nearMiss := e.f.ConstructSelect(nullableScan, e.filters(nullableIsNull))
	e.expectNoMatch(rule, nearMiss)
}
