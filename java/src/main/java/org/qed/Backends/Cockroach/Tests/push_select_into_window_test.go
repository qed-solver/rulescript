package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoWindow(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoWindow")
	e := newEnv(t)
	l, lc := e.scan("nn", 2)
	r, rc := e.scan("nn", 2)
	wcol := e.f.Metadata().AddColumn("w", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), wcol)}
	window := e.f.ConstructGroupBy(r, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, window, on, &memo.JoinPrivate{})
	p := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	q := e.f.ConstructGt(e.f.ConstructPlus(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), e.v(wcol)), e.num(10))
	before := e.f.ConstructSelect(join, e.filters(p, q))
	leftFiltered := e.f.ConstructSelect(l, e.filters(p))
	rightFiltered := e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0))))
	filteredWindow := e.f.ConstructGroupBy(rightFiltered, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(leftFiltered, filteredWindow, on, &memo.JoinPrivate{}), e.filters(q))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(q, p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructLeftJoin(l, window, on, &memo.JoinPrivate{}), e.filters(p, q)))
}
