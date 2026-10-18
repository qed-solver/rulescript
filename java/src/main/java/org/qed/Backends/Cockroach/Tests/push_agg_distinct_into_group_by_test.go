package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushAggDistinctIntoGroupBy(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushAggDistinctIntoGroupBy")
	base, baseCol := singleColumnScan(e, "t", 0)
	cols := []opt.ColumnID{baseCol, baseCol, baseCol}
	k1 := e.f.Metadata().AddColumn("k1", types.Int)
	k2 := e.f.Metadata().AddColumn("k2", types.Int)
	x := e.f.Metadata().AddColumn("x", types.Int)
	input := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), k1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), k2),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[2]), e.num(2)), x),
	}, opt.ColSet{})
	value := e.f.Metadata().AddColumn("value", types.Int)
	before := e.f.ConstructGroupBy(input, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(x))), value),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})

	wantInput := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), k1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), k2),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[2]), e.num(2)), x),
	}, opt.MakeColSet(baseCol))
	inner := e.f.ConstructGroupBy(wantInput, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k1, k2, x),
	})
	wantValue := e.f.Metadata().AddColumn("value", types.Int)
	want := e.f.ConstructGroupBy(inner, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(x)), wantValue),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearBase, nearBaseCol := singleColumnScan(e, "t", 0)
	nearCols := []opt.ColumnID{nearBaseCol, nearBaseCol, nearBaseCol}
	nearK1 := e.f.Metadata().AddColumn("k1", types.Int)
	nearK2 := e.f.Metadata().AddColumn("k2", types.Int)
	nearX := e.f.Metadata().AddColumn("x", types.Int)
	nearInput := e.f.ConstructProject(nearBase, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearK1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(nearCols[1]), e.num(1)), nearK2),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(nearCols[2]), e.num(2)), nearX),
	}, opt.ColSet{})
	nearValue := e.f.Metadata().AddColumn("value", types.Int)
	nearOther := e.f.Metadata().AddColumn("other", types.Int)
	nearPlan := e.f.ConstructGroupBy(nearInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(nearX))), nearValue),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(nearX)), nearOther),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearK1, nearK2)})
	e.expectNoMatch(rule, nearPlan)
}
