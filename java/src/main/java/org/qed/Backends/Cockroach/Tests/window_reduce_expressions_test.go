package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestWindowReduceExpressions(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "WindowReduceExpressions")
	base, baseCols := e.scan("t", 2)
	input, inputCols := e.scan("t", 2)
	value := e.f.Metadata().AddColumn("value", types.Int)
	beforeProject := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructAnd(e.f.ConstructFalse(), e.f.ConstructEq(e.v(inputCols[1]), e.num(7))), value),
	}, opt.MakeColSet(inputCols[0]))
	aggCol := e.f.Metadata().AddColumn("agg", types.Int)
	group := e.f.ConstructGroupBy(beforeProject, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(value)), aggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(inputCols[0])})
	before := e.f.ConstructInnerJoin(base, group, e.filters(
		e.f.ConstructEq(e.v(baseCols[0]), e.v(inputCols[0])),
	), &memo.JoinPrivate{})

	afterProject := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), value),
	}, opt.MakeColSet(inputCols[0]))
	expectedGroup := e.f.ConstructGroupBy(afterProject, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(value)), aggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(inputCols[0])})
	want := e.f.ConstructInnerJoin(base, expectedGroup, e.filters(
		e.f.ConstructEq(e.v(baseCols[0]), e.v(inputCols[0])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	noMatchBase, noMatchCols := e.scan("t", 2)
	noMatchInput, noMatchInputCols := e.scan("t", 2)
	noMatchValue := e.f.Metadata().AddColumn("value", types.Int)
	noMatchProject := e.f.ConstructProject(noMatchInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructEq(e.v(noMatchInputCols[1]), e.num(7))), noMatchValue),
	}, opt.MakeColSet(noMatchInputCols[0]))
	noMatchAggCol := e.f.Metadata().AddColumn("agg", types.Int)
	noMatchGroup := e.f.ConstructGroupBy(noMatchProject, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(noMatchValue)), noMatchAggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(noMatchInputCols[0])})
	noMatch := e.f.ConstructInnerJoin(noMatchBase, noMatchGroup, e.filters(
		e.f.ConstructEq(e.v(noMatchCols[0]), e.v(noMatchInputCols[0])),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, noMatch)
}
