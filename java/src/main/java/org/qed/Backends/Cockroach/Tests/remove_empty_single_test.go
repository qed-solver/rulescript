package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveEmptySingle(t *testing.T) {
	rule := loadRule(t, "RemoveEmptySingle")
	e := newEnv(t)
	inputCol := e.f.Metadata().AddColumn("input", types.Int)
	aggCol := e.f.Metadata().AddColumn("agg", types.Int)
	empty := e.empty(inputCol)
	before := e.f.ConstructGroupBy(empty,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(inputCol)), aggCol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(inputCol)},
	)
	want := e.empty(inputCol, aggCol)
	e.expectRewrite(rule, before, want)

	nonEmpty, cols := e.scan("t", 1)
	near := e.f.ConstructGroupBy(nonEmpty,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[0])), aggCol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])},
	)
	e.expectNoMatch(rule, near)
}
