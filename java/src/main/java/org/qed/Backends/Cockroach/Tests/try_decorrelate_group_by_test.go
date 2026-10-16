package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateGroupBy(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateGroupBy")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("nn", 2)
	f := e.f.Metadata().AddColumn("f", types.Int)
	aggregate := func(input memo.RelExpr, grouping ...opt.ColumnID) memo.RelExpr {
		return e.f.ConstructGroupBy(input, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[1])), f),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(grouping...)})
	}
	on := e.filters(e.f.ConstructGt(e.v(f), e.num(0)))
	before := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, aggregate(r, rc[0]), nil, &memo.JoinPrivate{}), on)
	want := e.f.ConstructSelect(
		aggregate(e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), lc[0], rc[0]), on)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructLeftJoin(l, aggregate(r, rc[0]), nil, &memo.JoinPrivate{}), on))
	nonUnique, _ := oneColumnUnionAll(e)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(nonUnique, aggregate(r, rc[0]), nil, &memo.JoinPrivate{}), on))
}
