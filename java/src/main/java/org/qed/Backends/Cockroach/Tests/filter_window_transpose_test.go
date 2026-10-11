package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterWindowTranspose(t *testing.T) {
	rule := loadRule(t, "FilterWindowTranspose")
	e := newEnv(t)
	s, ac := e.scan("t", 2)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ac[1])), fcol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(ac[0])}
	agg := e.f.ConstructGroupBy(s, aggs, private)
	p := e.f.ConstructGt(e.v(ac[0]), e.num(1))
	q := e.f.ConstructLt(e.v(ac[0]), e.v(fcol))
	before := e.f.ConstructSelect(agg, e.filters(e.f.ConstructAnd(p, q)))
	want := e.f.ConstructSelect(
		e.f.ConstructGroupBy(e.f.ConstructSelect(s, e.filters(p)), aggs, private),
		e.filters(q),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(agg, e.filters(e.f.ConstructOr(p, q))))
	e.expectNoMatch(rule, e.f.ConstructSelect(agg, e.filters(
		e.f.ConstructAnd(e.f.ConstructGt(e.v(fcol), e.num(1)), q),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(agg, e.filters(p)))
}
