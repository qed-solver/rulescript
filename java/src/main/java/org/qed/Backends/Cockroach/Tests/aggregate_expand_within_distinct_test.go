package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateExpandWithinDistinct(t *testing.T) {
	rule := loadRule(t, "AggregateExpandWithinDistinct")
	e := newEnv(t)
	input, cols := e.scan("t", 1)
	countCol := e.f.Metadata().AddColumn("count", types.Int)
	distinctCount := e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(cols[0])))
	before := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(distinctCount, countCol),
	}, memo.EmptyGroupingPrivate)
	dedup := e.f.ConstructGroupBy(input, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
	dedupCountCol := e.f.Metadata().AddColumn("count", types.Int)
	want := e.f.ConstructScalarGroupBy(dedup, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), dedupCountCol),
	}, memo.EmptyGroupingPrivate)
	e.expectRewrite(rule, before, want)

	nonDistinct := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), countCol),
	}, memo.EmptyGroupingPrivate)
	e.expectNoMatch(rule, nonDistinct)

	distinctSum := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructSumInt(e.v(cols[0]))), countCol),
	}, memo.EmptyGroupingPrivate)
	sumCol := e.f.Metadata().AddColumn("sum", types.Int)
	wantSum := e.f.ConstructScalarGroupBy(dedup, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[0])), sumCol),
	}, memo.EmptyGroupingPrivate)
	e.expectRewrite(rule, distinctSum, wantSum)
}
