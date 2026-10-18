package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoUnorderedDistinctOn(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoUnorderedDistinctOn")
	e := newEnv(t)
	input, cols := e.scan("nn", 2)
	groups := opt.MakeColSet(cols...)
	unordered := &memo.GroupingPrivate{GroupingCols: groups}
	before := e.f.ConstructSelect(
		e.f.ConstructGroupBy(input, memo.EmptyAggregationsExpr, unordered),
		e.filters(
			e.f.ConstructEq(e.v(cols[0]), e.num(1)),
			e.f.ConstructEq(e.v(cols[1]), e.num(2)),
		),
	)
	filtered := e.f.ConstructSelect(input, e.filters(
		e.f.ConstructEq(e.v(cols[0]), e.num(1)),
		e.f.ConstructEq(e.v(cols[1]), e.num(2)),
	))
	want := e.f.ConstructGroupBy(filtered, memo.EmptyAggregationsExpr, unordered)
	e.expectRewrite(rule, before, want)

	orderedInput, orderedCols := e.scan("nn", 2)
	ordered := e.f.ConstructSelect(
		e.f.ConstructGroupBy(orderedInput, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(orderedCols[0]),
		}),
		e.filters(e.f.ConstructEq(e.v(orderedCols[0]), e.num(1))),
	)
	e.expectNoMatch(rule, ordered)
}
