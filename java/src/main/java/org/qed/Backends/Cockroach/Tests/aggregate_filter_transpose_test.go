package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateFilterTranspose(t *testing.T) {
	rule := loadRule(t, "AggregateFilterTranspose")
	e := newEnv(t)
	a, ac := e.scan("t", 3)
	b, _ := e.scan("u", 2)
	base := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ac[1])), fcol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(ac[0])}
	p := e.f.ConstructGt(e.v(ac[0]), e.num(1))
	before := e.f.ConstructGroupBy(e.f.ConstructSelect(base, e.filters(p)), aggs, private)
	want := e.f.ConstructSelect(e.f.ConstructGroupBy(base, aggs, private), e.filters(p))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructSelect(base, e.filters(e.f.ConstructGt(e.v(ac[1]), e.num(1)))), aggs, private))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(base, aggs, private))
	e.expectNoMatch(rule, want)

	left := e.f.ConstructLeftJoin(a, b, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructSelect(left, e.filters(p)), aggs, private))
}
