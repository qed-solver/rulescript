package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
)

func singleColumnScan(e *env, table string, ordinal int) (memo.RelExpr, opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	col := tabID.ColumnID(ordinal)
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(col)}), col
}

func TestEliminateDistinct(t *testing.T) {
	rule := loadRule(t, "EliminateDistinct")
	e := newEnv(t)

	u, uc := e.scan("u", 1)
	keyGroup := e.f.ConstructGroupBy(u, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(uc[0])})
	e.expectRewrite(rule, keyGroup, e.f.ConstructProject(u, nil, opt.MakeColSet(uc[0])))

	nonKey, nc := singleColumnScan(e, "t", 1)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nonKey, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nc)}))

	wide, wc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wide, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wc[0])}))

	sel := e.f.ConstructSelect(u, e.filters(e.f.ConstructEq(e.v(uc[0]), e.num(1))))
	e.expectRewrite(rule, e.f.ConstructGroupBy(sel, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(uc[0])}),
		e.f.ConstructProject(sel, nil, opt.MakeColSet(uc[0])))
}
