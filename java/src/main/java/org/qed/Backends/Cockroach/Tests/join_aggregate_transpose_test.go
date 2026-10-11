package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinAggregateTranspose(t *testing.T) {
	rule := loadRule(t, "JoinAggregateTranspose")
	e := newEnv(t)
	left, lc := e.scan("t", 2)
	right, rc := e.scan("u", 1)
	sum := e.f.Metadata().AddColumn("sum", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(lc[1])), sum)}
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	groups := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}

	agg := e.f.ConstructGroupBy(left, aggs, groups)
	before := e.f.ConstructInnerJoin(agg, right, on, &memo.JoinPrivate{})
	joined := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructGroupBy(joined, aggs, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(lc[0], rc[0]),
		}),
		nil,
		opt.MakeColSet(lc[0], rc[0], sum),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(agg, right, on, &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(agg, right,
		e.filters(e.f.ConstructEq(e.v(sum), e.v(rc[0]))), &memo.JoinPrivate{}))

	nonKey, nkc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(agg,
		e.f.ConstructProject(nonKey, nil, opt.MakeColSet(nkc[1])),
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(nkc[1]))), &memo.JoinPrivate{}))
}
