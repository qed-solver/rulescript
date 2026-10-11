package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterAggregateTranspose(t *testing.T) {
	rule := loadRule(t, "FilterAggregateTranspose")
	e := newEnv(t)
	a, ac := e.scan("t", 3)
	b, _ := e.scan("u", 2)
	base := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ac[1])), fcol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(ac[0])}
	p := e.f.ConstructGt(e.v(ac[0]), e.num(1))
	qa := e.f.ConstructLt(e.v(ac[0]), e.num(5))
	qb := e.f.ConstructGt(e.v(fcol), e.num(2))
	before := e.f.ConstructSelect(
		e.f.ConstructGroupBy(base, aggs, private),
		e.filters(e.f.ConstructAnd(p, e.f.ConstructAnd(qa, qb))),
	)
	want := e.f.ConstructSelect(
		e.f.ConstructGroupBy(e.f.ConstructSelect(base, e.filters(p)), aggs, private),
		e.filters(qa, qb),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructGroupBy(base, aggs, private), e.filters(p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructGroupBy(base, aggs, private),
		e.filters(e.f.ConstructOr(p, e.f.ConstructAnd(qa, qb))),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructGroupBy(a, aggs, private),
		e.filters(e.f.ConstructAnd(p, e.f.ConstructAnd(qa, qb))),
	))
}
